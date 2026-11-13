//! Automatic background removal: a neural network finds a picture's main subject, and the rest
//! (the background) is made transparent. Runs entirely on the CPU, in Rust (no C++ runtime). The
//! small U2-NetP is embedded; a bigger IS-Net can be installed from a file the app downloaded
//! (see [`use_matting_model`]), which finds a subject in exactly the pictures U2-NetP gives up on.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use tract_onnx::prelude::*;

/// The U2-Net Portable model (ONNX), ~4.4 MB, embedded with the engine.
const MODEL: &[u8] = include_bytes!("../models/u2netp.onnx");

/// The side the network works at: its input, and the mask it gives back.
const SIDE: usize = 320;

/// The installed model's working square, and which of its outputs is the fused answer.
///
/// The IS-Net export takes any square pinned to it; 512 — not the 1024 it shipped at — because a
/// phone measures it in seconds rather than tens of them, and on the five test photos every
/// subject still cleared the empty-mask line by a wide margin. Its output 0 is a side map at a
/// fraction of the size (reading it turns every photo into "no subject found"); the fused answer
/// is the seventh, as rembg's own session numbers it.
const BIG_SIDE: usize = 512;
const BIG_OUTPUT: usize = 6;

/// The compiled model, ready to run (the slow part: a second on a tablet).
type Matting = tract_onnx::tract_core::plan::SimplePlan<TypedFact, Box<dyn TypedOp>>;

/// The model, loaded and compiled the first time it is wanted; None if it can't be (a damaged or
/// newer one is refused without ever panicking).
fn plan() -> Option<Arc<Matting>> {
    static READY: OnceLock<Option<Arc<Matting>>> = OnceLock::new();
    READY
        .get_or_init(|| {
            let mut bytes: &[u8] = MODEL;
            let mut model = tract_onnx::onnx().model_for_read(&mut bytes).ok()?;
            // the model's input is fixed at 1×3×320×320; pin it if the file leaves it symbolic
            let fact = InferenceFact::dt_shape(f32::datum_type(), tvec!(1usize, 3, SIDE, SIDE));
            model.set_input_fact(0, fact).ok()?;
            let typed = model.into_typed().ok()?;
            tract_onnx::tract_core::plan::SimplePlan::new(typed).ok()
        })
        .clone()
}

/// The installed model, once [`use_matting_model`] has compiled one.
static BIG: OnceLock<Mutex<Option<Arc<Matting>>>> = OnceLock::new();

/// Whether the person asked the cut to use the installed model. The default is the small one even
/// where a big one is present: measured on the same pictures, IS-Net's 512 answer is often softer
/// than U2-NetP's confident blob, and the v0.0.14 report called the change for the worse. A model
/// that downloads itself must never also install itself into charge.
static BIG_WANTED: AtomicBool = AtomicBool::new(false);

fn big_slot() -> &'static Mutex<Option<Arc<Matting>>> {
    BIG.get_or_init(|| Mutex::new(None))
}

/// Asks (or refuses) the installed model for the cut; returns whether the installed model is what
/// will now answer — false with a request no installed model can serve, or after clearing back to
/// the built-in. The built-in keeps working either way.
pub fn set_matting_choice(big: bool) -> bool {
    BIG_WANTED.store(big && matting_model_installed(), Ordering::Relaxed);
    BIG_WANTED.load(Ordering::Relaxed)
}

/// Whether the installed model is the one in charge right now.
pub fn matting_prefers_big() -> bool {
    BIG_WANTED.load(Ordering::Relaxed) && matting_model_installed()
}

/// Points background removal at the model kept in `path`: the file is read and its plan compiled
/// now, so the first photo doesn't pay for it. True when it loaded (an IS-Net export with
/// symbolic dimensions; anything else leaves the embedded U2-NetP in charge).
pub fn use_matting_model(path: &str) -> bool {
    let compiled = (|| -> Option<Arc<Matting>> {
        // SimplePlan::new already hands back the Arc, and `run` asks for one
        let bytes = std::fs::read(path).ok()?;
        let mut model = tract_onnx::onnx().model_for_read(&mut bytes.as_slice()).ok()?;
        let fact = InferenceFact::dt_shape(f32::datum_type(), tvec!(1usize, 3, BIG_SIDE, BIG_SIDE));
        model.set_input_fact(0, fact).ok()?;
        let typed = model.into_typed().ok()?;
        tract_onnx::tract_core::plan::SimplePlan::new(typed).ok()
    })();
    match (compiled, big_slot().lock()) {
        (Some(model), Ok(mut slot)) => {
            *slot = Some(model);
            true
        }
        _ => false,
    }
}

/// Whether [`use_matting_model`] installed a model; the app says which one found nothing, because
/// the two disagree on exactly the photos that matter.
pub fn matting_model_installed() -> bool {
    big_slot().lock().is_ok_and(|slot| slot.is_some())
}

/// Removes the background of a straight (non-premultiplied) RGBA8 picture, `width` × `height`, by
/// multiplying its alpha by the saliency mask. `threshold` is where the cut lands (0 keeps
/// almost everything, 1 cuts almost everything); `softness` is how wide the feather is around it
/// (0 hard edges, 1 very soft). None if the model can't run (the picture is left as it was).
pub fn remove_background(rgba: &[u8], width: u32, height: u32, threshold: f32, softness: f32) -> Option<Vec<u8>> {
    let mask = background_mask(rgba, width, height)?;
    apply_background_mask(rgba, width, height, &mask, threshold, softness)
}

/// Runs the neural network once and returns its 320×320 saliency mask. Keeping this mask lets a UI
/// tune the cut and feather live without running the network again for every slider movement.
/// Whichever model runs — installed or embedded — the mask comes back in this one shape.
pub fn background_mask(rgba: &[u8], width: u32, height: u32) -> Option<Vec<u8>> {
    let installed = matting_prefers_big().then(|| big_slot().lock().ok().and_then(|slot| slot.clone())).flatten();
    match installed {
        // an installed model that fails on one picture falls back rather than losing the cut
        // outright: the small model is still here
        Some(model) => run_model(&model, rgba, width, height, BIG_SIDE, BIG_OUTPUT).or_else(|| {
            let small = plan()?;
            run_model(&small, rgba, width, height, SIDE, 0)
        }),
        None => {
            let small = plan()?;
            run_model(&small, rgba, width, height, SIDE, 0)
        }
    }
}

/// A model's answer as the app's 320×320 mask: preprocessed at `side`, its output `output` taken
/// (its first, if the file has fewer than that), and shrunk to the mask the cut already knows.
fn run_model(
    model: &Arc<Matting>,
    rgba: &[u8],
    width: u32,
    height: u32,
    side: usize,
    output: usize,
) -> Option<Vec<u8>> {
    let (w, h) = (width as usize, height as usize);
    if rgba.len() < w * h * 4 {
        return None;
    }
    // the network sees a stretched `side`×`side` RGB copy, normalized as the training pictures were
    let means = [0.485f32, 0.456, 0.406];
    let stds = [0.229f32, 0.224, 0.225];
    let mut input = vec![0.0f32; 3 * side * side];
    for y in 0..side {
        let sy = ((y as f32 + 0.5) * h as f32 / side as f32 - 0.5).clamp(0.0, h as f32 - 1.0);
        let (y0, y1, fy) = sample_at(sy, h);
        for x in 0..side {
            let sx = ((x as f32 + 0.5) * w as f32 / side as f32 - 0.5).clamp(0.0, w as f32 - 1.0);
            let (x0, x1, fx) = sample_at(sx, w);
            for c in 0..3 {
                let v = sample_rgb(rgba, w, y0, y1, fy, x0, x1, fx, c);
                let mean = means.get(c).copied().unwrap_or(0.0);
                let std = stds.get(c).copied().unwrap_or(1.0);
                if let Some(slot) = input.get_mut(c * side * side + y * side + x) {
                    *slot = (v - mean) / std;
                }
            }
        }
    }
    let tensor = Tensor::from_shape(&[1, 3, side, side], &input).ok()?;
    let result = model.run(tvec!(tensor.into())).ok()?;
    let answer = result.get(output).or_else(|| result.first())?;
    let mask = answer.to_plain_array_view::<f32>().ok()?;
    let mask = mask.as_slice()?;
    if mask.len() < side * side {
        return None;
    }
    Some(to_mask(mask, side))
}

/// The network's square answer, 0 background .. 1 subject, taken down to the 320×320 the cut
/// reads: a copy when the model worked at 320, and bilinear otherwise (a bigger model's extra
/// resolution would not survive the cut's own upsample anyway; what matters is what it found).
fn to_mask(values: &[f32], side: usize) -> Vec<u8> {
    let as_byte = |v: f32| (v.clamp(0.0, 1.0) * 255.0).round() as u8;
    if side == SIDE {
        return values.iter().take(SIDE * SIDE).map(|v| as_byte(*v)).collect();
    }
    let mut out = vec![0u8; SIDE * SIDE];
    for y in 0..SIDE {
        let sy = ((y as f32 + 0.5) * side as f32 / SIDE as f32 - 0.5).clamp(0.0, side as f32 - 1.0);
        let (y0, y1, fy) = sample_at(sy, side);
        for x in 0..SIDE {
            let sx = ((x as f32 + 0.5) * side as f32 / SIDE as f32 - 0.5).clamp(0.0, side as f32 - 1.0);
            let (x0, x1, fx) = sample_at(sx, side);
            let g = |yy: usize, xx: usize| values.get(yy * side + xx).copied().unwrap_or(0.0);
            let v = g(y0, x0) * (1.0 - fx) * (1.0 - fy)
                + g(y0, x1) * fx * (1.0 - fy)
                + g(y1, x0) * (1.0 - fx) * fy
                + g(y1, x1) * fx * fy;
            if let Some(cell) = out.get_mut(y * SIDE + x) {
                *cell = as_byte(v);
            }
        }
    }
    out
}

/// Applies a mask returned by [`background_mask`] to a picture. This is deliberately separate from
/// inference: threshold and softness changes then remain cheap enough for a live preview.
pub fn apply_background_mask(
    rgba: &[u8],
    width: u32,
    height: u32,
    mask: &[u8],
    threshold: f32,
    softness: f32,
) -> Option<Vec<u8>> {
    let (w, h) = (width as usize, height as usize);
    if rgba.len() < w * h * 4 || mask.len() < SIDE * SIDE {
        return None;
    }
    let mut out = rgba.to_vec();
    let t = threshold.clamp(0.0, 1.0);
    let s = softness.clamp(0.0, 1.0) * 0.5 + 0.001;
    for y in 0..h {
        let my = ((y as f32 + 0.5) * SIDE as f32 / h as f32 - 0.5).clamp(0.0, SIDE as f32 - 1.0);
        let (y0, y1, fy) = sample_at(my, SIDE);
        for x in 0..w {
            let mx = ((x as f32 + 0.5) * SIDE as f32 / w as f32 - 0.5).clamp(0.0, SIDE as f32 - 1.0);
            let (x0, x1, fx) = sample_at(mx, SIDE);
            let m = sample_mask(mask, y0, y1, fy, x0, x1, fx);
            let keep = ((m - (t - s)) / (2.0 * s)).clamp(0.0, 1.0);
            if let Some(alpha) = out.get_mut(y * w * 4 + x * 4 + 3) {
                *alpha = (*alpha as f32 * keep).round() as u8;
            }
        }
    }
    Some(out)
}

/// The two whole pixels around `at`, and how far `at` is past the first of them.
fn sample_at(at: f32, len: usize) -> (usize, usize, f32) {
    let at = at.clamp(0.0, len as f32 - 1.0);
    let i = at.floor() as usize;
    let f = at - i as f32;
    (i, (i + 1).min(len - 1), f)
}

/// How far apart two colours are, 0..1 of the full span: the mean of the three channel distances.
fn colour_distance([ar, ag, ab]: [f32; 3], [br, bg, bb]: [f32; 3]) -> f32 {
    ((ar - br).abs() + (ag - bg).abs() + (ab - bb).abs()) / (3.0 * 255.0)
}

fn rgb_at(rgba: &[u8], i: usize) -> [f32; 3] {
    match rgba.get(i * 4..i * 4 + 3).map(<[u8; 3]>::try_from) {
        Some(Ok([r, g, b])) => [r as f32, g as f32, b as f32],
        _ => [0.0; 3],
    }
}

/// The magic wand: from every seed it grows over the pixels joined to it by colour — a neighbour
/// belongs while its colour is within `tolerance` (0..1 of the full span) of the seed's own. The
/// result is a mask of the picture's own size, 255 in every picked region, 0 outside; None when the
/// picture is short or a seed lies off it.
///
/// The seed's colour is the mean of the click and its eight neighbours: one noisy JPEG pixel must
/// not decide what a whole region is.
///
/// Clicks add to each other: a person taps the sky, then the lake, and both go. Unlike the neural
/// mask this one is full-size, so a click cut keeps its own edges — [`cut_wand_mask`] then feathers
/// them without the 320-square trip the network's answer takes.
pub fn wand_mask(rgba: &[u8], width: u32, height: u32, seeds: &[(u32, u32)], tolerance: f32) -> Option<Vec<u8>> {
    let (w, h) = (width as usize, height as usize);
    if w == 0 || h == 0 || rgba.len() < w * h * 4 {
        return None;
    }
    let tol = tolerance.clamp(0.0, 1.0);
    let mut mask = vec![0u8; w * h];
    let mut queue = std::collections::VecDeque::new();
    for &(sx, sy) in seeds {
        if sx as usize >= w || sy as usize >= h {
            return None;
        }
        let start = sy as usize * w + sx as usize;
        if mask.get(start).copied().unwrap_or(0) == 255 {
            continue; // this place already belongs to a region; growing it again adds nothing
        }
        let seed = seed_colour(rgba, w, h, sx as usize, sy as usize);
        if let Some(cell) = mask.get_mut(start) {
            *cell = 255;
        }
        queue.push_back(start);
        while let Some(i) = queue.pop_front() {
            let (y, x) = (i / w, i % w);
            for j in [
                y.checked_sub(1).map(|ny| ny * w + x),
                (y + 1 < h).then(|| (y + 1) * w + x),
                x.checked_sub(1).map(|nx| y * w + nx),
                (x + 1 < w).then(|| y * w + x + 1),
            ]
            .into_iter()
            .flatten()
            {
                if mask.get(j).copied().unwrap_or(0) != 0 {
                    continue;
                }
                if colour_distance(rgb_at(rgba, j), seed) <= tol {
                    if let Some(cell) = mask.get_mut(j) {
                        *cell = 255;
                    }
                    queue.push_back(j);
                }
            }
        }
    }
    Some(mask)
}

/// The colour a click stands for: the mean of the pixel and the eight around it, repeating edge
/// pixels where they do not exist (a mean of fewer real samples, never a black one).
fn seed_colour(rgba: &[u8], w: usize, h: usize, x: usize, y: usize) -> [f32; 3] {
    let mut sum = [0.0f32; 3];
    let mut n = 0.0f32;
    for dy in -1isize..=1 {
        for dx in -1isize..=1 {
            let px = (x as isize + dx).clamp(0, w as isize - 1) as usize;
            let py = (y as isize + dy).clamp(0, h as isize - 1) as usize;
            for (cell, v) in sum.iter_mut().zip(rgb_at(rgba, py * w + px)) {
                *cell += v;
            }
            n += 1.0;
        }
    }
    let [sr, sg, sb] = sum;
    [sr / n, sg / n, sb / n]
}

/// Removes the regions a [`wand_mask`] picked: their alpha goes to nothing, the rest of the
/// picture keeps its own. `softness` (0..1) feathers the picked regions' edges outwards by up to
/// eight picture pixels, a blur of the mask, so a click cut reads as the neural one does.
pub fn cut_wand_mask(rgba: &[u8], width: u32, height: u32, mask: &[u8], softness: f32) -> Option<Vec<u8>> {
    let (w, h) = (width as usize, height as usize);
    if w == 0 || h == 0 || rgba.len() < w * h * 4 || mask.len() < w * h {
        return None;
    }
    let feather = (softness.clamp(0.0, 1.0) * 8.0).round() as usize;
    let mut out = rgba.to_vec();
    if feather == 0 {
        for (i, cell) in mask.iter().enumerate().take(w * h) {
            if let Some(alpha) = out.get_mut(i * 4 + 3)
                && *cell > 127
            {
                *alpha = 0;
            }
        }
        return Some(out);
    }
    // two box passes make a near-Gaussian edge, and each line stays in cache: the blur is linear
    // in the picture, not in its area times the radius
    let mut blurred = vec![0.0f32; w * h];
    let mut from = vec![0.0f32; w];
    let mut to = vec![0.0f32; w];
    for y in 0..h {
        for (x, cell) in from.iter_mut().enumerate() {
            *cell = mask.get(y * w + x).copied().unwrap_or(0) as f32 / 255.0;
        }
        box_pass(&from, &mut to, feather);
        // the row it just blurred, at its own place in the picture: skip the finished rows first,
        // and only then count columns - enumerating before the skip would index 	o past its end
        for (x, cell) in blurred.iter_mut().skip(y * w).take(w).enumerate() {
            if let Some(v) = to.get(x) {
                *cell = *v;
            }
        }
    }
    let mut col = vec![0.0f32; h];
    let mut col2 = vec![0.0f32; h];
    for x in 0..w {
        for (v, cell) in col.iter_mut().enumerate() {
            *cell = *blurred.get(v * w + x).unwrap_or(&0.0);
        }
        box_pass(&col, &mut col2, feather);
        for (v, cell) in col2.iter().enumerate().take(h) {
            if let Some(slot) = blurred.get_mut(v * w + x) {
                *slot = *cell;
            }
        }
    }
    // the blurred pick is the amount that goes: deep inside nothing stays, past the edge the
    // original alpha comes back gradually, and the border of the picture is held, not bitten
    for (i, &amount) in blurred.iter().enumerate().take(w * h) {
        let keep = 1.0 - amount.clamp(0.0, 1.0);
        if let Some(alpha) = out.get_mut(i * 4 + 3) {
            *alpha = (*alpha as f32 * keep).round() as u8;
        }
    }
    Some(out)
}

/// A box average of `from` into `to`, a window `radius` wide on each side; the window clamps at
/// the ends, so a region touching the picture's border does not get a bite out of it.
fn box_pass(from: &[f32], to: &mut [f32], radius: usize) {
    let n = from.len();
    if n == 0 {
        return;
    }
    let mut prefix = vec![0.0f32; n + 1];
    for (i, &v) in from.iter().enumerate() {
        let running = prefix.get(i).copied().unwrap_or(0.0) + v;
        if let Some(cell) = prefix.get_mut(i + 1) {
            *cell = running;
        }
    }
    for i in 0..n {
        let lo = i.saturating_sub(radius);
        let hi = (i + radius + 1).min(n);
        if let (Some(lo_v), Some(hi_v)) = (prefix.get(lo), prefix.get(hi))
            && let Some(cell) = to.get_mut(i)
        {
            *cell = (hi_v - lo_v) / (hi - lo) as f32;
        }
    }
}

/// A bilinear sample of `rgba`'s channel `c` at the corners (`y0`,`x0`) .. (`y1`,`x1`).
fn sample_rgb(rgba: &[u8], w: usize, y0: usize, y1: usize, fy: f32, x0: usize, x1: usize, fx: f32, c: usize) -> f32 {
    let p = |y: usize, x: usize| rgba.get((y * w + x) * 4 + c).copied().unwrap_or(0) as f32 / 255.0;
    p(y0, x0) * (1.0 - fx) * (1.0 - fy)
        + p(y0, x1) * fx * (1.0 - fy)
        + p(y1, x0) * (1.0 - fx) * fy
        + p(y1, x1) * fx * fy
}

/// A bilinear sample of the 320×320 `mask` at the corners (`y0`,`x0`) .. (`y1`,`x1`).
fn sample_mask(mask: &[u8], y0: usize, y1: usize, fy: f32, x0: usize, x1: usize, fx: f32) -> f32 {
    let m = |y: usize, x: usize| mask.get(y * SIDE + x).copied().unwrap_or(0) as f32 / 255.0;
    m(y0, x0) * (1.0 - fx) * (1.0 - fy)
        + m(y0, x1) * fx * (1.0 - fy)
        + m(y1, x0) * (1.0 - fx) * fy
        + m(y1, x1) * fx * fy
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::indexing_slicing)]
mod tests {
    use super::*;

    #[test]
    fn the_model_loads_and_runs() {
        let Some(plan) = plan() else { return };
        let input = vec![0.0f32; 3 * SIDE * SIDE];
        let tensor = Tensor::from_shape(&[1, 3, SIDE, SIDE], &input).unwrap();
        let result = plan.run(tvec!(tensor.into())).unwrap();
        // U2-Net gives a fused map and several side maps; the first is the one to use.
        assert!(!result.is_empty(), "outputs");
        assert_eq!(result[0].to_plain_array_view::<f32>().unwrap().len(), SIDE * SIDE, "the fused mask is 320×320");
    }

    #[test]
    fn the_answer_comes_back_as_a_320_mask_whatever_the_model_side() {
        // a constant answer shrinks to that constant, at either size. (0.4, not 0.5: the latter
        // sits exactly on a rounding boundary, where a float hair either way changes the byte)
        let flat = vec![0.4f32; 512 * 512];
        let mask = to_mask(&flat, 512);
        assert_eq!(mask.len(), SIDE * SIDE);
        assert!(mask.iter().all(|v| *v == 102), "{:?}", &mask[..8]);
        let same = to_mask(&vec![0.4f32; SIDE * SIDE], SIDE);
        assert!(same.iter().all(|v| *v == 102));
        // half bright, half dark, split down the middle: the shrink keeps the split where it is
        let mut split = vec![0.0f32; 640 * 640];
        for (i, v) in split.iter_mut().enumerate() {
            *v = if i % 640 < 320 { 1.0 } else { 0.0 };
        }
        let mask = to_mask(&split, 640);
        assert!(mask.iter().take(SIDE * SIDE).step_by(SIDE).all(|v| *v > 200), "left half stayed bright");
        assert!(mask.iter().skip(SIDE - 1).step_by(SIDE).all(|v| *v < 60), "right half stayed dark");
        // and answers outside 0..1 are held to it, not wrapped
        assert_eq!(to_mask(&vec![7.0f32; 512 * 512], 512)[0], 255);
        assert_eq!(to_mask(&vec![-3.0f32; 512 * 512], 512)[0], 0);
    }

    #[test]
    fn the_wand_grows_over_the_colour_it_was_told_to() {
        // a picture of four quarters, each its own colour: a small tolerance keeps one quarter,
        // a big one takes the whole picture through the touching neighbours
        let (w, h) = (16usize, 16usize);
        let mut rgba = vec![0u8; w * h * 4];
        for y in 0..h {
            for x in 0..w {
                let c = match (y < 8, x < 8) {
                    (true, true) => [220, 30, 30],
                    (true, false) => [30, 220, 30],
                    (false, true) => [30, 30, 220],
                    (false, false) => [220, 220, 30],
                };
                let i = (y * w + x) * 4;
                rgba[i..i + 3].copy_from_slice(&c);
                rgba[i + 3] = 255;
            }
        }
        let mask = wand_mask(&rgba, w as u32, h as u32, &[(4, 4)], 0.05).unwrap();
        assert_eq!(mask.iter().filter(|v| **v == 255).count(), 8 * 8, "just the red quarter");
        assert_eq!(mask[15 * w + 15], 0, "the far quarter stayed");
        // a loose enough tolerance walks through the neighbours and takes everything
        let mask = wand_mask(&rgba, w as u32, h as u32, &[(4, 4)], 1.0).unwrap();
        assert!(mask.iter().all(|v| *v == 255));
        // two clicks, two quarters
        let mask = wand_mask(&rgba, w as u32, h as u32, &[(4, 4), (12, 4)], 0.05).unwrap();
        assert_eq!(mask.iter().filter(|v| **v == 255).count(), 2 * 64);
        // a repeated click changes nothing, and a click off the picture is refused, not grown
        let same = wand_mask(&rgba, w as u32, h as u32, &[(4, 4), (4, 4)], 0.05).unwrap();
        let one = wand_mask(&rgba, w as u32, h as u32, &[(4, 4)], 0.05).unwrap();
        assert_eq!(same, one);
        assert!(wand_mask(&rgba, w as u32, h as u32, &[(16, 4)], 0.05).is_none());
    }

    #[test]
    fn the_wand_cut_only_touches_the_picked_alpha() {
        // a picture whose top half is one colour and bottom half another, big enough that a
        // feather of eight pixels stays a band along the edge rather than the whole of it
        let (w, h) = (64usize, 64usize);
        let mut rgba = vec![0u8; w * h * 4];
        for (i, p) in rgba.as_chunks_mut::<4>().0.iter_mut().enumerate() {
            let y = i / w;
            p.copy_from_slice(&[if y < 32 { 200 } else { 20 }, 10, 30, 255]);
        }
        let mask = wand_mask(&rgba, w as u32, h as u32, &[(4, 2)], 0.05).unwrap();
        let cut = cut_wand_mask(&rgba, w as u32, h as u32, &mask, 0.0).unwrap();
        // hard edge: the picked half is gone, the rest keeps colour and alpha
        for x in 0..w {
            assert_eq!(cut[x * 4 + 3], 0, "picked row 0, column {x}");
            assert_eq!(cut[(63 * w + x) * 4 + 3], 255, "kept row 63, column {x}");
        }
        for (a, b) in cut.iter().zip(rgba.iter()).skip((32 * w) * 4) {
            assert_eq!(a, b, "the kept half is byte for byte the picture");
        }
        // feathering is a band, not a cliff: deep inside still nothing stays, right under the
        // edge something stays, and eight pixels further down it is all of it again
        let soft = cut_wand_mask(&rgba, w as u32, h as u32, &mask, 1.0).unwrap();
        assert_eq!(soft[(4 * w + 4) * 4 + 3], 0, "deep inside the region is fully cut at any softness");
        let just_below = soft[(33 * w + 4) * 4 + 3];
        assert!(just_below > 0 && just_below < 255, "the feather reaches under the edge: {just_below}");
        assert_eq!(soft[(48 * w + 4) * 4 + 3], 255, "and is done eight pixels on, where the blur window cannot reach");
        // and a seed off the picture is refused before anything is grown
        assert!(wand_mask(&rgba, w as u32, h as u32, &[(64, 0)], 0.5).is_none());
    }

    #[test]
    fn a_file_that_is_not_the_model_leaves_the_embedded_one_in_charge() {
        // no IS-Net file on this machine: the engine's promise is that a wrong file refuses,
        // without panic and without taking the working model away
        assert!(!use_matting_model("no/such/file.onnx"));
        assert!(!matting_model_installed());
        let rgba = vec![128u8; 32 * 32 * 4];
        let Some(mask) = background_mask(&rgba, 32, 32) else { return }; // no u2netp either
        assert_eq!(mask.len(), SIDE * SIDE, "the small model still answered, as before");
    }

    #[test]
    fn the_heavy_model_is_a_choice_not_a_takeover() {
        // nothing installed here: asking for the big model cannot get it into charge, and the
        // default answer is always the built-in one — which is what the v0.0.14 report asked for
        assert!(!set_matting_choice(true), "no installed model to prefer");
        assert!(!matting_prefers_big());
        set_matting_choice(false);
        assert!(!matting_prefers_big());
        let rgba = vec![128u8; 32 * 32 * 4];
        let Some(mask) = background_mask(&rgba, 32, 32) else { return };
        assert_eq!(mask.len(), SIDE * SIDE, "the built-in model answered while nothing is chosen");
    }

    #[test]
    fn removing_background_keeps_the_shape_and_only_touches_alpha() {
        let mut rgba = vec![0u8; 64 * 64 * 4];
        for p in rgba.as_chunks_mut::<4>().0 {
            p.copy_from_slice(&[200, 30, 30, 255]);
        }
        let out = remove_background(&rgba, 64, 64, 0.5, 0.5);
        let Some(out) = out else { return }; // no model on this machine
        assert_eq!(out.len(), rgba.len());
        for (a, b) in out.as_chunks::<4>().0.iter().zip(rgba.as_chunks::<4>().0.iter()) {
            assert_eq!(&a[..3], &b[..3], "only the alpha changes, the colours stay");
        }
    }

    #[test]
    fn a_mask_can_be_reused_for_live_cut_controls() {
        let rgba = vec![128u8; 32 * 48 * 4];
        let Some(mask) = background_mask(&rgba, 32, 48) else { return };
        assert_eq!(mask.len(), SIDE * SIDE);
        let first = apply_background_mask(&rgba, 32, 48, &mask, 0.25, 0.1).unwrap();
        let second = apply_background_mask(&rgba, 32, 48, &mask, 0.75, 0.8).unwrap();
        assert_eq!(first.len(), rgba.len());
        assert_eq!(second.len(), rgba.len());
    }
}
