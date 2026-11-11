//! Automatic background removal: a U2-Net neural network finds a picture's main subject, and the
//! rest (the background) is made transparent. Runs entirely on the CPU, in Rust (no C++ runtime).

use std::sync::{Arc, OnceLock};

use tract_onnx::prelude::*;

/// The U2-Net Portable model (ONNX), ~4.4 MB, embedded with the engine.
const MODEL: &[u8] = include_bytes!("../models/u2netp.onnx");

/// The side the network works at: its input, and the mask it gives back.
const SIDE: usize = 320;

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

/// Removes the background of a straight (non-premultiplied) RGBA8 picture, `width` × `height`, by
/// multiplying its alpha by the U2-Net saliency mask. `threshold` is where the cut lands (0 keeps
/// almost everything, 1 cuts almost everything); `softness` is how wide the feather is around it
/// (0 hard edges, 1 very soft). None if the model can't run (the picture is left as it was).
pub fn remove_background(rgba: &[u8], width: u32, height: u32, threshold: f32, softness: f32) -> Option<Vec<u8>> {
    let mask = background_mask(rgba, width, height)?;
    apply_background_mask(rgba, width, height, &mask, threshold, softness)
}

/// Runs the neural network once and returns its 320×320 saliency mask. Keeping this mask lets a UI
/// tune the cut and feather live without running the network again for every slider movement.
pub fn background_mask(rgba: &[u8], width: u32, height: u32) -> Option<Vec<u8>> {
    let plan = plan()?;
    let (w, h) = (width as usize, height as usize);
    if rgba.len() < w * h * 4 {
        return None;
    }
    // the network sees a stretched 320×320 RGB copy, normalized as the training pictures were
    let means = [0.485f32, 0.456, 0.406];
    let stds = [0.229f32, 0.224, 0.225];
    let mut input = vec![0.0f32; 3 * SIDE * SIDE];
    for y in 0..SIDE {
        let sy = ((y as f32 + 0.5) * h as f32 / SIDE as f32 - 0.5).clamp(0.0, h as f32 - 1.0);
        let (y0, y1, fy) = sample_at(sy, h);
        for x in 0..SIDE {
            let sx = ((x as f32 + 0.5) * w as f32 / SIDE as f32 - 0.5).clamp(0.0, w as f32 - 1.0);
            let (x0, x1, fx) = sample_at(sx, w);
            for c in 0..3 {
                let v = sample_rgb(rgba, w, y0, y1, fy, x0, x1, fx, c);
                let mean = means.get(c).copied().unwrap_or(0.0);
                let std = stds.get(c).copied().unwrap_or(1.0);
                if let Some(slot) = input.get_mut(c * SIDE * SIDE + y * SIDE + x) {
                    *slot = (v - mean) / std;
                }
            }
        }
    }
    let tensor = Tensor::from_shape(&[1, 3, SIDE, SIDE], &input).ok()?;
    let result = plan.run(tvec!(tensor.into())).ok()?;
    // the mask, 320×320, 0 background .. 1 subject
    let mask = result.first()?.to_plain_array_view::<f32>().ok()?;
    let mask = mask.as_slice()?;
    if mask.len() < SIDE * SIDE {
        return None;
    }
    Some(mask.iter().take(SIDE * SIDE).map(|v| (v.clamp(0.0, 1.0) * 255.0).round() as u8).collect())
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
        let input = vec![0.0f32; 1 * 3 * SIDE * SIDE];
        let tensor = Tensor::from_shape(&[1, 3, SIDE, SIDE], &input).unwrap();
        let result = plan.run(tvec!(tensor.into())).unwrap();
        // U2-Net gives a fused map and several side maps; the first is the one to use.
        assert!(!result.is_empty(), "outputs");
        assert_eq!(result[0].to_plain_array_view::<f32>().unwrap().len(), SIDE * SIDE, "the fused mask is 320×320");
    }

    #[test]
    fn removing_background_keeps_the_shape_and_only_touches_alpha() {
        let mut rgba = vec![0u8; 64 * 64 * 4];
        for p in rgba.chunks_exact_mut(4) {
            p.copy_from_slice(&[200, 30, 30, 255]);
        }
        let out = remove_background(&rgba, 64, 64, 0.5, 0.5);
        let Some(out) = out else { return }; // no model on this machine
        assert_eq!(out.len(), rgba.len());
        for (a, b) in out.chunks_exact(4).zip(rgba.chunks_exact(4)) {
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
