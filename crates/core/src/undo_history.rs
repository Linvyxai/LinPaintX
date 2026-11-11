//! The undo and redo history kept beyond the canvas's in-memory window: every step the canvas
//! spills is stored, compressed, in the artwork's folder, so undo and redo can reach back any
//! number of steps while the canvas holds only its last steps at once.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use procraft_engine::applz4;
use procraft_engine::document::{Document, LayerId};
use procraft_gpu::{HistoryPixels, HistoryStep, HistoryStore};

use crate::project::{file_error, write_atomically};

/// How many bytes the stored (spilled) history may take on disk; 0 keeps it all. A step past it
/// drops the oldest steps first.
static HISTORY_LIMIT: AtomicU64 = AtomicU64::new(0);

/// Sets how many bytes the stored history may take on disk (0: no limit). The canvas's last steps
/// always stay in memory regardless.
#[uniffi::export]
pub fn set_history_limit(bytes: u64) {
    HISTORY_LIMIT.store(bytes, Ordering::Relaxed);
}

pub(crate) fn history_limit() -> u64 {
    HISTORY_LIMIT.load(Ordering::Relaxed)
}

/// A step as stored on disk: its pixels compressed row by row, so a long history stays small.
#[derive(serde::Serialize, serde::Deserialize)]
struct StoredStep {
    made: u64,
    name: Option<String>,
    /// The document as JSON bytes (bincode can't read a document's lenient deserialization).
    doc: Option<Vec<u8>>,
    pixels: Vec<StoredPixels>,
    selection: Option<Option<Vec<u8>>>,
    flip: Option<(Vec<LayerId>, bool)>,
}

#[derive(serde::Serialize, serde::Deserialize)]
struct StoredPixels {
    layer: LayerId,
    rect: [i32; 4],
    lz4: Vec<u8>,
}

/// The spilled steps' numbers and names, in order (undo: oldest first; redo: deepest first), so
/// the undo button's label and count are known without reading the pixel files.
#[derive(serde::Serialize, serde::Deserialize, Default)]
struct Manifest {
    undo: Vec<(u64, Option<String>)>,
    redo: Vec<(u64, Option<String>)>,
}

/// The spilled history of one artwork, on disk in `dir/history/`: one compressed file per step,
/// and a small manifest of their numbers and names written on save.
pub(crate) struct HistoryJournal {
    dir: PathBuf,
    /// Spilled undo steps' numbers and names, oldest first.
    undo: Vec<(u64, Option<String>)>,
    /// Spilled redo steps' numbers and names, deepest first.
    redo: Vec<(u64, Option<String>)>,
    /// The compressed bytes the stored steps take on disk.
    bytes: u64,
    /// A limit overriding the global setting for tests (None: the global setting).
    limit: Option<u64>,
}

impl HistoryJournal {
    /// Opens the journal in `dir`, reading back the steps already stored there (if any).
    pub(crate) fn open(dir: &Path) -> HistoryJournal {
        let mut journal = HistoryJournal { dir: dir.to_path_buf(), undo: Vec::new(), redo: Vec::new(), bytes: 0, limit: None };
        journal.load();
        journal
    }

    fn dir(&self, redo: bool) -> PathBuf {
        self.dir.join("history").join(if redo { "redo" } else { "undo" })
    }

    fn path(&self, redo: bool, made: u64) -> PathBuf {
        self.dir(redo).join(format!("{made:020}.bin"))
    }

    fn load(&mut self) {
        let manifest = self.dir.join("history").join("manifest.json");
        if let Ok(text) = std::fs::read_to_string(&manifest)
            && let Ok(m) = serde_json::from_str::<Manifest>(&text)
        {
            self.undo = m.undo;
            self.redo = m.redo;
        } else {
            // no manifest (a save cut short): the steps' numbers come from their file names, the
            // names unknown.
            self.undo = scan(&self.dir(false), true);
            self.redo = scan(&self.dir(true), false);
        }
        self.bytes = measure(&self.dir(false)) + measure(&self.dir(true));
    }

    /// Writes the manifest (the spilled steps' numbers and names) so a reopen knows them.
    fn write_manifest(&mut self) {
        let Ok(manifest) = serde_json::to_vec(&Manifest { undo: self.undo.clone(), redo: self.redo.clone() }) else {
            return;
        };
        let to = self.dir.join("history").join("manifest.json");
        let _ = std::fs::create_dir_all(to.parent().unwrap_or(Path::new("")));
        if let Err(e) = write_atomically(&to, |p| {
            std::fs::write(p, &manifest).map_err(|e| file_error("writing undo history", e))
        }) {
            log::error!("writing the history manifest: {e}");
        }
    }

    /// Stores `step` (its pixels compressed), trimming to the configured limit.
    fn store(&mut self, redo: bool, step: HistoryStep) {
        let made = step.made;
        let name = step.name.clone();
        let stored = StoredStep {
            made: step.made,
            name: step.name,
            doc: step.doc.and_then(|d| serde_json::to_vec(&d).ok()),
            selection: step.selection,
            flip: step.flip,
            pixels: step
                .pixels
                .into_iter()
                .map(|p| StoredPixels { layer: p.layer, rect: p.rect, lz4: applz4::encode(&p.rgba) })
                .collect(),
        };
        let Ok(bytes) = bincode::serialize(&stored) else { return };
        let path = self.path(redo, made);
        if let Err(e) = std::fs::create_dir_all(self.dir(redo)) {
            log::error!("storing a history step: {e}");
            return;
        }
        if let Err(e) = write_atomically(&path, |p| {
            std::fs::write(p, &bytes).map_err(|e| file_error("writing undo history", e))
        }) {
            log::error!("storing a history step: {e}");
            return;
        }
        self.bytes += bytes.len() as u64;
        if redo {
            self.redo.push((made, name));
        } else {
            self.undo.push((made, name));
        }
        self.trim();
    }

    /// Takes the newest spilled step of `redo` or `undo` back (reads and removes its file).
    fn take(&mut self, redo: bool) -> Option<HistoryStep> {
        let (made, _) = if redo { self.redo.last() } else { self.undo.last() }.cloned()?;
        let path = self.path(redo, made);
        let bytes = std::fs::read(&path).ok()?;
        let stored: StoredStep = bincode::deserialize(&bytes).ok()?;
        // the step came back intact: commit the pop and its removal
        if redo {
            self.redo.pop();
        } else {
            self.undo.pop();
        }
        self.bytes = self.bytes.saturating_sub(bytes.len() as u64);
        let _ = std::fs::remove_file(&path);
        let mut pixels = Vec::with_capacity(stored.pixels.len());
        for p in stored.pixels {
            let size = rect_bytes(&p.rect);
            let rgba = applz4::decode(&p.lz4, size).filter(|p| p.len() == size)?;
            pixels.push(HistoryPixels { layer: p.layer, rect: p.rect, rgba });
        }
        Some(HistoryStep {
            made: stored.made,
            name: stored.name,
            doc: stored.doc.and_then(|b| serde_json::from_slice::<Document>(&b).ok()).map(Box::new),
            pixels,
            selection: stored.selection,
            flip: stored.flip,
        })
    }

    /// Drops the oldest stored steps while the total is over the configured limit.
    fn trim(&mut self) {
        let limit = self.limit.unwrap_or_else(history_limit);
        if limit == 0 {
            return;
        }
        while self.bytes > limit {
            let made = match (self.undo.first().cloned(), self.redo.first().cloned()) {
                (Some((m, _)), _) => {
                    self.undo.remove(0);
                    m
                }
                (None, Some((m, _))) => {
                    self.redo.remove(0);
                    m
                }
                (None, None) => return,
            };
            self.drop_file(made);
        }
    }

    /// Removes a step's file and takes its bytes off the total (a `made` names one of the two).
    fn drop_file(&mut self, made: u64) {
        for redo in [false, true] {
            let path = self.path(redo, made);
            if let Ok(meta) = std::fs::metadata(&path) {
                self.bytes = self.bytes.saturating_sub(meta.len());
                let _ = std::fs::remove_file(&path);
                return;
            }
        }
    }
}

impl HistoryStore for HistoryJournal {
    fn spill_undo(&mut self, step: HistoryStep) {
        self.store(false, step);
    }

    fn spill_redo(&mut self, step: HistoryStep) {
        self.store(true, step);
    }

    fn take_undo(&mut self) -> Option<HistoryStep> {
        self.take(false)
    }

    fn take_redo(&mut self) -> Option<HistoryStep> {
        self.take(true)
    }

    fn stored_undo(&self) -> usize {
        self.undo.len()
    }

    fn stored_redo(&self) -> usize {
        self.redo.len()
    }

    fn newest_undo(&self) -> Option<(u64, Option<String>)> {
        self.undo.last().cloned()
    }

    fn newest_redo(&self) -> Option<(u64, Option<String>)> {
        self.redo.last().cloned()
    }

    fn clear_redo(&mut self) {
        self.bytes = self.bytes.saturating_sub(measure(&self.dir(true)));
        let _ = std::fs::remove_dir_all(self.dir(true));
        self.redo.clear();
    }

    fn clear(&mut self) {
        let _ = std::fs::remove_dir_all(self.dir(false));
        let _ = std::fs::remove_dir_all(self.dir(true));
        self.undo.clear();
        self.redo.clear();
        self.bytes = 0;
    }

    fn flush(&mut self) {
        self.write_manifest();
    }

    fn reconcile(&mut self, min_undo: u64, max_redo: u64) {
        let mut kept = Vec::with_capacity(self.undo.len());
        for (made, name) in std::mem::take(&mut self.undo) {
            if made < min_undo {
                kept.push((made, name));
            } else {
                self.drop_file(made);
            }
        }
        self.undo = kept;
        let mut kept = Vec::with_capacity(self.redo.len());
        for (made, name) in std::mem::take(&mut self.redo) {
            if made > max_redo {
                kept.push((made, name));
            } else {
                self.drop_file(made);
            }
        }
        self.redo = kept;
    }
}

/// The steps in `dir`, numbered by their file names: oldest first (`ascending`), or deepest first.
fn scan(dir: &Path, ascending: bool) -> Vec<(u64, Option<String>)> {
    let mut made: Vec<u64> = std::fs::read_dir(dir)
        .map(|entries| {
            entries
                .filter_map(|e| e.ok())
                .filter_map(|e| {
                    let name = e.file_name();
                    let name = name.to_string_lossy();
                    name.strip_suffix(".bin").and_then(|n| n.parse::<u64>().ok())
                })
                .collect()
        })
        .unwrap_or_default();
    made.sort_unstable();
    if !ascending {
        made.reverse();
    }
    made.into_iter().map(|m| (m, None)).collect()
}

/// The total bytes the step files in `dir` take.
fn measure(dir: &Path) -> u64 {
    std::fs::read_dir(dir)
        .map(|entries| {
            entries
                .filter_map(|e| e.ok())
                .filter_map(|e| e.metadata().ok())
                .map(|m| m.len())
                .sum()
        })
        .unwrap_or(0)
}

fn rect_bytes(rect: &[i32; 4]) -> usize {
    let w = (rect[2] - rect[0]).max(0) as usize;
    let h = (rect[3] - rect[1]).max(0) as usize;
    w * h * 4
}

#[cfg(test)]
mod tests {
    use super::*;

    fn step(made: u64, name: &str) -> HistoryStep {
        HistoryStep {
            made,
            name: Some(name.to_owned()),
            doc: None,
            pixels: vec![HistoryPixels { layer: LayerId(1), rect: [0, 0, 1, 1], rgba: vec![1, 2, 3, 4] }],
            selection: None,
            flip: None,
        }
    }

    fn dir() -> PathBuf {
        static N: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let d = std::env::temp_dir().join(format!("procraft-history-{}-{}", std::process::id(), N.fetch_add(1, Ordering::Relaxed)));
        let _ = std::fs::remove_dir_all(&d);
        std::fs::create_dir_all(&d).unwrap();
        d
    }

    #[test]
    fn spilled_undo_steps_come_back_newest_first() {
        let d = dir();
        let mut j = HistoryJournal::open(&d);
        j.spill_undo(step(1, "one"));
        j.spill_undo(step(2, "two"));
        j.spill_undo(step(3, "three"));
        assert_eq!(j.stored_undo(), 3);
        assert_eq!(j.newest_undo(), Some((3, Some("three".to_owned()))));
        // taken newest first
        assert_eq!(j.take_undo().unwrap().made, 3);
        assert_eq!(j.take_undo().unwrap().made, 2);
        assert_eq!(j.newest_undo(), Some((1, Some("one".to_owned()))));
        assert_eq!(j.take_undo().unwrap().made, 1);
        assert!(j.take_undo().is_none());
        assert_eq!(j.stored_undo(), 0);
    }

    #[test]
    fn spilled_redo_steps_come_back_closest_first() {
        let d = dir();
        let mut j = HistoryJournal::open(&d);
        // redo spills deepest first: 500, 499, ...
        j.spill_redo(step(500, "deep"));
        j.spill_redo(step(499, "mid"));
        j.spill_redo(step(401, "near"));
        assert_eq!(j.stored_redo(), 3);
        assert_eq!(j.newest_redo(), Some((401, Some("near".to_owned()))));
        assert_eq!(j.take_redo().unwrap().made, 401);
        assert_eq!(j.take_redo().unwrap().made, 499);
        assert_eq!(j.take_redo().unwrap().made, 500);
        assert!(j.take_redo().is_none());
    }

    #[test]
    fn a_flush_persists_names_across_reopen() {
        let d = dir();
        {
            let mut j = HistoryJournal::open(&d);
            j.spill_undo(step(7, "paint"));
            j.spill_undo(step(8, "erase"));
            j.flush();
        }
        let mut j = HistoryJournal::open(&d);
        assert_eq!(j.stored_undo(), 2);
        assert_eq!(j.newest_undo(), Some((8, Some("erase".to_owned()))));
        assert_eq!(j.take_undo().unwrap().made, 8);
        assert_eq!(j.take_undo().unwrap().name.as_deref(), Some("paint"));
    }

    #[test]
    fn reconcile_drops_steps_overlapping_the_window() {
        let d = dir();
        let mut j = HistoryJournal::open(&d);
        for m in 1..=50 {
            j.spill_undo(step(m, "old"));
        }
        // the saved window's oldest step is 51: stored steps 1..50 are all older and kept
        j.reconcile(51, 0);
        assert_eq!(j.stored_undo(), 50);
        // a stale window whose oldest is 1 covers everything spilled since: all dropped
        j.reconcile(1, 0);
        assert_eq!(j.stored_undo(), 0);
    }

    #[test]
    fn the_limit_drops_the_oldest_steps_first() {
        let d = dir();
        let mut j = HistoryJournal::open(&d);
        j.limit = Some(1024);
        for m in 1..=20 {
            j.spill_undo(step(m, "stroke"));
        }
        // the first (oldest) steps went first: the newest remain undoable
        assert!(j.stored_undo() < 20);
        let newest = j.newest_undo().unwrap().0;
        assert_eq!(newest, 20);
    }

    #[test]
    fn clear_redo_drops_redo_but_keeps_undo() {
        let d = dir();
        let mut j = HistoryJournal::open(&d);
        j.spill_undo(step(1, "u"));
        j.spill_redo(step(2, "r"));
        j.clear_redo();
        assert_eq!(j.stored_undo(), 1);
        assert_eq!(j.stored_redo(), 0);
        j.clear();
        assert_eq!(j.stored_undo(), 0);
    }
}
