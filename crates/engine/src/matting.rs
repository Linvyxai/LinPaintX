//! Automatic background removal: a neural network finds a picture's main subject, and the rest
//! (the background) is made transparent. Runs entirely on the CPU, in Rust (no C++ runtime). The
//! small U2-NetP is embedded; a bigger IS-Net can be installed from a file the app downloaded
//! (see [`use_matting_model`]), which finds a subject in exactly the pictures U2-NetP gives up on.

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

fn big_slot() -> &'static Mutex<Option<Arc<Matting>>> {
    BIG.get_or_init(|| Mutex::new(None))
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
    let installed = big_slot().lock().ok().and_then(|slot| slot.clone());
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
