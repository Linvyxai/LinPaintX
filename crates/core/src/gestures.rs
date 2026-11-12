//! Gesture controls, the same in every app: what each way of touching can be set to do (a page of
//! switches for each purpose), what starts on, the delays, and the one rule across pages: a way of
//! touching does one thing at most, so turning it on for one page turns it off on the others. The
//! app keeps the settings between runs (each switch by its name, see [`GestureSettings::kept`]);
//! the touches follow them as [`TouchSettings`].

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use crate::touch::{GestureAction, TouchSettings};

/// What a gesture can do: the pages of Gesture controls.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum GesturePurpose {
    Smudge,
    Erase,
    Assisted,
    Eyedropper,
    Quickshape,
    Quickmenu,
    FullScreen,
    ClearLayer,
    CopyPaste,
    LayerSelect,
    Hover,
    General,
}

/// A way of touching: each does one thing at most (General's and Hover's settings aside).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum Trigger {
    TapModify,
    Touch,
    Pen,
    ModifyTouch,
    ModifyPen,
    ThreeSwipe,
    FourTap,
    /// The pen's side buttons.
    PenDoubleTap,
    PenSqueeze,
    Hold,
    Scrub,
}

/// One switch on a page: its name (a trigger's, or a setting of its own), what it says and does,
/// whether it starts on, and whether it has a delay slider under it.
#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct GestureOption {
    pub key: String,
    pub label: String,
    pub detail: String,
    pub default: bool,
    pub trigger: Option<Trigger>,
    pub delayed: bool,
}

/// The pages in order, with their names as kept, titles and headings.
const PURPOSES: [(GesturePurpose, &str, &str, &str); 12] = [
    (GesturePurpose::Smudge, "SMUDGE", "Размазывание", "Настроить размазывание"),
    (GesturePurpose::Erase, "ERASE", "Ластик", "Настроить ластик"),
    (GesturePurpose::Assisted, "ASSISTED", "Рисование с привязкой", "Настроить рисование с привязкой"),
    (GesturePurpose::Eyedropper, "EYEDROPPER", "Пипетка", "Настроить пипетку"),
    (GesturePurpose::Quickshape, "QUICKSHAPE", "Быстрая фигура", "Настроить быструю фигуру"),
    (GesturePurpose::Quickmenu, "QUICKMENU", "Быстрое меню", "Настроить быстрое меню"),
    (GesturePurpose::FullScreen, "FULL_SCREEN", "Полный экран", "Настроить полный экран"),
    (GesturePurpose::ClearLayer, "CLEAR_LAYER", "Очистка слоя", "Настроить очистку слоя"),
    (GesturePurpose::CopyPaste, "COPY_PASTE", "Копирование и вставка", "Настроить копирование и вставку"),
    (GesturePurpose::LayerSelect, "LAYER_SELECT", "Выбор слоя", "Настроить выбор слоя"),
    (GesturePurpose::Hover, "HOVER", "Наведение", "Настроить наведение"),
    (GesturePurpose::General, "GENERAL", "Общие", "Общие"),
];

/// Each trigger's name as kept and its label.
const TRIGGERS: [(Trigger, &str, &str); 11] = [
    (Trigger::TapModify, "TAP_MODIFY", "Касание □"),
    (Trigger::Touch, "TOUCH", "Касание"),
    (Trigger::Pen, "PEN", "Перо"),
    (Trigger::ModifyTouch, "MODIFY_TOUCH", "□ + касание"),
    (Trigger::ModifyPen, "MODIFY_PEN", "□ + перо"),
    (Trigger::ThreeSwipe, "THREE_SWIPE", "Свайп тремя пальцами"),
    (Trigger::FourTap, "FOUR_TAP", "Касание четырьмя пальцами"),
    (Trigger::PenDoubleTap, "PEN_DOUBLE_TAP", "Кнопка пера"),
    (Trigger::PenSqueeze, "PEN_SQUEEZE", "Вторая кнопка пера"),
    (Trigger::Hold, "HOLD", "Удержание"),
    (Trigger::Scrub, "SCRUB", "Трение"),
];

/// The shortest and longest delay, seconds.
pub const MIN_DELAY_S: f32 = 0.1;
pub const MAX_DELAY_S: f32 = 1.5;

fn purpose_entry(p: GesturePurpose) -> (GesturePurpose, &'static str, &'static str, &'static str) {
    PURPOSES.iter().copied().find(|e| e.0 == p).unwrap_or(PURPOSES[0])
}

fn trigger_entry(t: Trigger) -> (Trigger, &'static str, &'static str) {
    TRIGGERS.iter().copied().find(|e| e.0 == t).unwrap_or(TRIGGERS[0])
}

/// A switch for a trigger (a hold has a delay slider under it).
fn opt(t: Trigger, detail: &str, default: bool) -> GestureOption {
    let (_, key, label) = trigger_entry(t);
    GestureOption {
        key: key.to_owned(),
        label: label.to_owned(),
        detail: detail.to_owned(),
        default,
        trigger: Some(t),
        delayed: t == Trigger::Hold,
    }
}

/// A setting of a page's own.
fn own(key: &str, label: &str, detail: &str, default: bool, delayed: bool) -> GestureOption {
    GestureOption {
        key: key.to_owned(),
        label: label.to_owned(),
        detail: detail.to_owned(),
        default,
        trigger: None,
        delayed,
    }
}

/// A page's title (its row in the list) and heading.
#[uniffi::export]
pub fn gesture_purpose_title(purpose: GesturePurpose) -> String {
    purpose_entry(purpose).2.to_owned()
}

#[uniffi::export]
pub fn gesture_purpose_heading(purpose: GesturePurpose) -> String {
    purpose_entry(purpose).3.to_owned()
}

/// The pages in order.
#[uniffi::export]
pub fn gesture_purposes() -> Vec<GesturePurpose> {
    PURPOSES.iter().map(|e| e.0).collect()
}

/// A page's switches, in order.
#[uniffi::export]
pub fn gesture_options(purpose: GesturePurpose) -> Vec<GestureOption> {
    use Trigger as T;
    match purpose {
        GesturePurpose::Smudge => vec![
            opt(T::Touch, "Касания пальцем размазывают", false),
            opt(T::Pen, "Штрихи пером размазывают", false),
            opt(T::ModifyTouch, "Касания пальцем размазывают при удержании □", false),
            opt(T::ModifyPen, "Штрихи пером размазывают при удержании □", false),
        ],
        GesturePurpose::Erase => vec![
            opt(T::Touch, "Касания пальцем стирают", false),
            opt(T::Pen, "Штрихи пером стирают", false),
            opt(T::ModifyTouch, "Касания пальцем стирают при удержании □", false),
            opt(T::ModifyPen, "Штрихи пером стирают при удержании □", false),
        ],
        GesturePurpose::Assisted => vec![
            opt(T::TapModify, "Включает или выключает привязку для этого слоя", false),
            opt(T::Touch, "Касания пальцем следуют за направляющей", false),
            opt(T::Pen, "Штрихи пером следуют за направляющей", false),
            opt(T::ModifyTouch, "Касания пальцем следуют за направляющей при удержании □", false),
            opt(T::ModifyPen, "Штрихи пером следуют за направляющей при удержании □", false),
            opt(T::PenDoubleTap, "Включает или выключает привязку для этого слоя", false),
        ],
        GesturePurpose::Eyedropper => vec![
            opt(T::TapModify, "Открывает или закрывает пипетку", true),
            opt(T::Touch, "Палец на холсте выбирает цвет", false),
            opt(T::ModifyTouch, "Палец выбирает цвет при удержании □", true),
            opt(T::ModifyPen, "Перо выбирает цвет при удержании □", true),
            opt(T::PenDoubleTap, "Открывает пипетку", false),
            opt(T::PenSqueeze, "Открывает пипетку", false),
            opt(T::Hold, "Неподвижный палец на холсте выбирает цвет", true),
        ],
        GesturePurpose::Quickshape => vec![
            opt(T::TapModify, "Превращает последний штрих в фигуру", false),
            opt(T::Touch, "Касание пальцем превращает последний штрих в фигуру", false),
            opt(T::ThreeSwipe, "Свайп тремя пальцами вниз превращает последний штрих в фигуру", false),
            opt(T::FourTap, "Касание четырьмя пальцами превращает последний штрих в фигуру", false),
            opt(T::PenDoubleTap, "Превращает последний штрих в фигуру", false),
            opt(T::PenSqueeze, "Превращает последний штрих в фигуру", false),
            own("DRAW_HOLD", "Рисуй и держи", "Пауза в конце штриха превращает его в фигуру", true, true),
        ],
        GesturePurpose::Quickmenu => vec![
            opt(T::TapModify, "Открывает или закрывает быстрое меню", false),
            opt(T::Touch, "Касание пальцем открывает быстрое меню", false),
            opt(T::ModifyTouch, "Палец открывает быстрое меню при удержании □", false),
            opt(T::ModifyPen, "Перо открывает быстрое меню при удержании □", false),
            opt(T::ThreeSwipe, "Свайп тремя пальцами вниз открывает быстрое меню", false),
            opt(T::FourTap, "Касание четырьмя пальцами открывает или закрывает быстрое меню", false),
            opt(T::PenDoubleTap, "Открывает быстрое меню", false),
            opt(T::PenSqueeze, "Открывает быстрое меню", false),
            opt(T::Hold, "Неподвижный палец на холсте открывает быстрое меню", false),
        ],
        GesturePurpose::FullScreen => vec![
            opt(T::Touch, "Касание пальцем показывает или скрывает интерфейс", false),
            opt(T::ThreeSwipe, "Свайп тремя пальцами вниз показывает или скрывает интерфейс", false),
            opt(T::FourTap, "Касание четырьмя пальцами показывает или скрывает интерфейс", true),
            opt(T::PenDoubleTap, "Показывает или скрывает интерфейс", false),
            own("AUTO", "Автоматический полный экран", "Интерфейс уходит в сторону пока вы рисуете", false, true),
        ],
        GesturePurpose::ClearLayer => vec![
            opt(T::Scrub, "Трение тремя пальцами туда-обратно очищает слой", true),
            opt(T::ThreeSwipe, "Свайп тремя пальцами вниз очищает слой", false),
            opt(T::FourTap, "Касание четырьмя пальцами очищает слой", false),
            opt(T::PenDoubleTap, "Очищает слой", false),
            opt(T::Hold, "Неподвижный палец на холсте очищает слой", false),
        ],
        GesturePurpose::CopyPaste => vec![
            opt(T::TapModify, "Открывает меню копирования и вставки", false),
            opt(T::Touch, "Касание пальцем открывает меню копирования и вставки", false),
            opt(T::ModifyTouch, "Палец открывает меню копирования и вставки при удержании □", false),
            opt(T::ModifyPen, "Перо открывает меню копирования и вставки при удержании □", false),
            opt(T::ThreeSwipe, "Свайп тремя пальцами вниз открывает меню копирования и вставки", true),
            opt(T::FourTap, "Касание четырьмя пальцами открывает меню копирования и вставки", false),
            opt(T::PenDoubleTap, "Открывает меню копирования и вставки", false),
            opt(T::Hold, "Неподвижный палец на холсте открывает меню копирования и вставки", false),
        ],
        GesturePurpose::LayerSelect => vec![
            opt(T::Touch, "Касание пальцем выбирает слой, нарисованный там", false),
            opt(T::ModifyTouch, "Палец выбирает слой, нарисованный там, при удержании □", false),
            opt(T::ModifyPen, "Перо выбирает слой, нарисованный там, при удержании □", false),
            opt(T::PenSqueeze, "Выбирает слой, нарисованный под пером", false),
            opt(T::Hold, "Неподвижный палец выбирает слой, нарисованный там", false),
        ],
        GesturePurpose::Hover => vec![own(
            "HOVER_TOUCH",
            "Палец при наведении пера",
            "Пока перо наводится, тяга пальцем меняет непрозрачность, щипок — размер, а не глохнет: на планшетах, где перо наводится всегда, это единственное, что оставляет палец полезным.",
            false,
            false,
        )],
        GesturePurpose::General => vec![
            own("FINGER_PAINT", "Рисование пальцем", "Один палец рисует как перо", true, false),
            own(
                "ONE_FINGER_PAN",
                "Перемещение холста одним пальцем",
                "Один палец перетаскивает холст вместо рисования; перо всё равно рисует",
                false,
                false,
            ),
            own(
                "NO_UNDO",
                "Отключить жесты отмены и возврата",
                "Касания двумя и тремя пальцами не трогают историю",
                false,
                false,
            ),
            own("ROTATE", "Вращение при масштабировании", "Два пальца поворачивают холст вместе с масштабом", true, false),
            own(
                "MODEL_FINGER",
                "3D-рисование пальцем",
                "Палец рисует на модели; с удержанным □ поворачивает её",
                false,
                false,
            ),
        ],
    }
}

/// A trigger's label.
#[uniffi::export]
pub fn gesture_trigger_label(trigger: Trigger) -> String {
    trigger_entry(trigger).2.to_owned()
}

/// A page's delay at first, seconds: holds 0.40 s, QuickShape 0.65 s, automatic full screen 0.66 s.
fn default_delay(p: GesturePurpose) -> f32 {
    match p {
        GesturePurpose::Quickshape => 0.65,
        GesturePurpose::FullScreen => 0.66,
        _ => 0.40,
    }
}

/// The settings as kept: each switch by its name (`PAGE.KEY`, as `EYEDROPPER.HOLD`) and each page's
/// delay by `PAGE.delay`, seconds.
#[derive(Clone, Debug, Default, PartialEq, uniffi::Record)]
pub struct KeptGestures {
    pub switches: HashMap<String, bool>,
    pub delays: HashMap<String, f32>,
}

#[derive(Default)]
struct State {
    on: HashMap<String, bool>,
    delays: HashMap<GesturePurpose, f32>,
}

/// The gesture settings: the app holds one, made from what it kept, and keeps what changes.
#[derive(uniffi::Object)]
pub struct GestureSettings {
    state: Mutex<State>,
}

#[uniffi::export]
impl GestureSettings {
    /// The settings from what was `kept` (anything not there as the app ships).
    #[uniffi::constructor]
    pub fn new(kept: KeptGestures) -> Arc<GestureSettings> {
        let mut st = State::default();
        for &(p, page, _, _) in &PURPOSES {
            for o in gesture_options(p) {
                let name = format!("{page}.{}", o.key);
                let on = kept.switches.get(&name).copied().unwrap_or(o.default);
                st.on.insert(name, on);
            }
            let delay = kept.delays.get(&format!("{page}.delay")).copied().unwrap_or_else(|| default_delay(p));
            st.delays.insert(p, delay.clamp(MIN_DELAY_S, MAX_DELAY_S));
        }
        Arc::new(GestureSettings { state: Mutex::new(st) })
    }

    pub fn is_on(&self, purpose: GesturePurpose, key: String) -> bool {
        self.st().on.get(&name(purpose, &key)).copied().unwrap_or(false)
    }

    /// Turns a switch on or off; on, its trigger goes off on every other page. Returns the
    /// switches that changed, by name, to keep.
    pub fn set(&self, purpose: GesturePurpose, key: String, value: bool) -> HashMap<String, bool> {
        let mut st = self.st();
        let mut changed = HashMap::new();
        let trigger = gesture_options(purpose).into_iter().find(|o| o.key == key).and_then(|o| o.trigger);
        if let (true, Some(t)) = (value, trigger) {
            for &(other, _, _, _) in &PURPOSES {
                if other == purpose {
                    continue;
                }
                for o in gesture_options(other).into_iter().filter(|o| o.trigger == Some(t)) {
                    let n = name(other, &o.key);
                    if st.on.get(&n) == Some(&true) {
                        st.on.insert(n.clone(), false);
                        changed.insert(n, false);
                    }
                }
            }
        }
        // one finger moves the canvas, paints, or does what a page's Touch is set to: one at a time
        if value {
            let general = |key: &str| name(GesturePurpose::General, key);
            let off: Vec<String> = if purpose == GesturePurpose::General && key == "ONE_FINGER_PAN" {
                PURPOSES
                    .iter()
                    .flat_map(|&(p, _, _, _)| {
                        gesture_options(p)
                            .into_iter()
                            .filter(|o| o.trigger == Some(Trigger::Touch))
                            .map(move |o| name(p, &o.key))
                    })
                    .chain([general("FINGER_PAINT")])
                    .collect()
            } else if (purpose == GesturePurpose::General && key == "FINGER_PAINT") || trigger == Some(Trigger::Touch) {
                vec![general("ONE_FINGER_PAN")]
            } else {
                Vec::new()
            };
            for n in off {
                if st.on.get(&n) == Some(&true) {
                    st.on.insert(n.clone(), false);
                    changed.insert(n, false);
                }
            }
        }
        let n = name(purpose, &key);
        st.on.insert(n.clone(), value);
        changed.insert(n, value);
        changed
    }

    /// The page a trigger is set to, if any.
    pub fn purpose_of(&self, trigger: Trigger) -> Option<GesturePurpose> {
        let st = self.st();
        PURPOSES.iter().map(|e| e.0).find(|&p| {
            gesture_options(p).iter().any(|o| o.trigger == Some(trigger) && st.on.get(&name(p, &o.key)) == Some(&true))
        })
    }

    /// A page's delay, seconds.
    pub fn delay(&self, purpose: GesturePurpose) -> f32 {
        self.st().delays.get(&purpose).copied().unwrap_or_else(|| default_delay(purpose))
    }

    /// Sets a page's delay (kept within its range); returns it as set.
    pub fn set_delay(&self, purpose: GesturePurpose, seconds: f32) -> f32 {
        let s = if seconds.is_finite() { seconds.clamp(MIN_DELAY_S, MAX_DELAY_S) } else { default_delay(purpose) };
        self.st().delays.insert(purpose, s);
        s
    }

    /// Every switch and delay as the app ships them.
    pub fn reset_all(&self) {
        let mut st = self.st();
        for &(p, _, _, _) in &PURPOSES {
            for o in gesture_options(p) {
                st.on.insert(name(p, &o.key), o.default);
            }
            st.delays.insert(p, default_delay(p));
        }
    }

    /// Everything, to keep.
    pub fn kept(&self) -> KeptGestures {
        let st = self.st();
        KeptGestures {
            switches: st.on.clone(),
            delays: st.delays.iter().map(|(&p, &d)| (format!("{}.delay", purpose_entry(p).1), d)).collect(),
        }
    }

    /// The settings as the canvas's touches take them; `rapid_undo_ms` is how long two fingers are
    /// held before undo repeats (a setting of its own).
    pub fn touch_settings(&self, rapid_undo_ms: f64) -> TouchSettings {
        let action = |p: Option<GesturePurpose>| {
            Some(match p? {
                GesturePurpose::Smudge => GestureAction::Smudge,
                GesturePurpose::Erase => GestureAction::Erase,
                GesturePurpose::Assisted => GestureAction::Assisted,
                GesturePurpose::Eyedropper => GestureAction::Eyedropper,
                GesturePurpose::Quickshape => GestureAction::QuickShape,
                GesturePurpose::Quickmenu => GestureAction::QuickMenu,
                GesturePurpose::FullScreen => GestureAction::FullScreen,
                GesturePurpose::ClearLayer => GestureAction::ClearLayer,
                GesturePurpose::CopyPaste => GestureAction::CopyPaste,
                GesturePurpose::LayerSelect => GestureAction::LayerSelect,
                GesturePurpose::Hover | GesturePurpose::General => return None,
            })
        };
        let general = |key: &str| self.is_on(GesturePurpose::General, key.to_owned());
        let hold = self.purpose_of(Trigger::Hold);
        // moving the canvas with one finger, the finger neither paints nor does a page's Touch
        let pan = general("ONE_FINGER_PAN");
        TouchSettings {
            finger_paint: general("FINGER_PAINT") && !pan,
            one_finger_pan: pan,
            undo_taps: !general("NO_UNDO"),
            rotate: general("ROTATE"),
            model_finger_paint: general("MODEL_FINGER"),
            draw_and_hold: self.is_on(GesturePurpose::Quickshape, "DRAW_HOLD".to_owned()),
            touch: if pan { None } else { action(self.purpose_of(Trigger::Touch)) },
            pen: action(self.purpose_of(Trigger::Pen)),
            hold: action(hold),
            modify_touch: action(self.purpose_of(Trigger::ModifyTouch)),
            modify_pen: action(self.purpose_of(Trigger::ModifyPen)),
            four_tap: action(self.purpose_of(Trigger::FourTap)),
            three_swipe: action(self.purpose_of(Trigger::ThreeSwipe)),
            pen_button: action(self.purpose_of(Trigger::PenDoubleTap)),
            pen_second_button: action(self.purpose_of(Trigger::PenSqueeze)),
            scrub: self.is_on(GesturePurpose::ClearLayer, "SCRUB".to_owned()),
            hover_touch: self.is_on(GesturePurpose::Hover, "HOVER_TOUCH".to_owned()),
            hold_ms: f64::from(self.delay(hold.unwrap_or(GesturePurpose::Eyedropper))) * 1000.0,
            quickshape_ms: f64::from(self.delay(GesturePurpose::Quickshape)) * 1000.0,
            rapid_undo_ms,
        }
    }
}

impl GestureSettings {
    fn st(&self) -> std::sync::MutexGuard<'_, State> {
        self.state.lock().unwrap_or_else(|e| e.into_inner())
    }
}

fn name(p: GesturePurpose, key: &str) -> String {
    format!("{}.{key}", purpose_entry(p).1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn as_shipped_the_touches_get_their_defaults() {
        let g = GestureSettings::new(KeptGestures::default());
        let mut t = g.touch_settings(650.0);
        let shipped = TouchSettings::default();
        // the defaults round 0.40 s and 0.65 s through f32
        assert!((t.hold_ms - shipped.hold_ms).abs() < 0.01 && (t.quickshape_ms - shipped.quickshape_ms).abs() < 0.01);
        t.hold_ms = shipped.hold_ms;
        t.quickshape_ms = shipped.quickshape_ms;
        assert_eq!(t, shipped);
    }

    #[test]
    fn a_trigger_does_one_thing() {
        let g = GestureSettings::new(KeptGestures::default());
        assert_eq!(g.purpose_of(Trigger::Hold), Some(GesturePurpose::Eyedropper));
        let changed = g.set(GesturePurpose::Quickmenu, "HOLD".into(), true);
        assert_eq!(changed.get("EYEDROPPER.HOLD"), Some(&false));
        assert_eq!(changed.get("QUICKMENU.HOLD"), Some(&true));
        assert_eq!(g.purpose_of(Trigger::Hold), Some(GesturePurpose::Quickmenu));
        assert_eq!(g.touch_settings(650.0).hold, Some(GestureAction::QuickMenu));
        // a page's own setting touches nothing else
        assert_eq!(g.set(GesturePurpose::General, "NO_UNDO".into(), true).len(), 1);
        assert!(!g.touch_settings(650.0).undo_taps);
    }

    #[test]
    fn what_was_kept_comes_back() {
        let g = GestureSettings::new(KeptGestures::default());
        g.set(GesturePurpose::Smudge, "PEN".into(), true);
        assert_eq!(g.set_delay(GesturePurpose::Eyedropper, 9.0), MAX_DELAY_S);
        let again = GestureSettings::new(g.kept());
        assert!(again.is_on(GesturePurpose::Smudge, "PEN".into()));
        assert_eq!(again.delay(GesturePurpose::Eyedropper), MAX_DELAY_S);
        again.reset_all();
        assert!(!again.is_on(GesturePurpose::Smudge, "PEN".into()));
        assert_eq!(again.delay(GesturePurpose::Quickshape), 0.65);
    }
}
