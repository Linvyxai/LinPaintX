package com.linpaintx.canvas

import org.junit.Assert.assertNull
import org.junit.Test

class PalmRejectTest {
    // a 2048-wide Legion Tab's fingertip, measured in pixels, and what each dpi figure calls it
    private val fingertip = 90f
    private val palm = 300f

    @Test
    fun aHonestScreenKnowsItsFingerFromAPalm() {
        // 262 dpi as reported: 90 px is 8.7 mm, 300 px is 29 mm
        val mm = touchMillimetres(fingertip, xdpi = 262f, densityDpi = 262)
        requireNotNull(mm)
        assert(mm < 25f)
        assert(requireNotNull(touchMillimetres(palm, 262f, 262)) >= 25f)
    }

    @Test
    fun aScreenLyingAboutItsDotsDoesNotRejectEveryTouch() {
        // the same 90 px through a 72 dpi figure would read 31.75 mm: a palm, so nothing would paint
        assert(requireNotNull(touchMillimetres(fingertip, xdpi = 72f, densityDpi = 262)) < 25f)
    }

    @Test
    fun aRidiculouslyDenseReadingIsIgnoredToo() {
        // 1200 dpi would call a real palm a fingertip; the app's own density answers instead
        val mm = requireNotNull(touchMillimetres(palm, xdpi = 1200f, densityDpi = 262))
        assert(mm >= 25f)
    }

    @Test
    fun noUsefulFigureMeansNoPalmTest() {
        assertNull(touchMillimetres(fingertip, xdpi = 0f, densityDpi = 0))
    }

    @Test
    fun theSettingsRowSaysWhichFigureTheTestTrusts() {
        // the honest screen: its own figure is used
        assert(palmDiagnostics(262f, 262, running = true).contains("достоверно"))
        // the liar: the app's density answers, and the row says so
        assert(palmDiagnostics(72f, 262, running = true).contains("недостоверно"))
        assert(palmDiagnostics(72f, 262, running = true).contains("262"))
        // nothing to go by: the test stands down
        assert(palmDiagnostics(0f, 0, running = true).contains("не действует"))
    }

    @Test
    fun theDiagnosticsLineDoesNotClaimATestThatIsSwitchedOff() {
        // the switch is off by default now, so the old sentence "the palm test works by it" sat under
        // an off switch — the row has to say which of the two the person is looking at
        val off = palmDiagnostics(420f, 420, running = false)
        assert(off.contains("выключен"))
        assert(!off.contains("работает по нему"))
        assert(off.contains("420"))
        // the same screen with the test on does say it works
        assert(palmDiagnostics(420f, 420, running = true).contains("работает по нему"))
    }

    @Test
    fun aFingerThatPaintsIsNotComplainedAbout() {
        assertNull(
            fingerSilenceReason(
                paints = true, moves = false, penHovering = true, hoverMutes = true, hoverTouch = false, droppedAsPalm = true,
            )
        )
    }

    @Test
    fun aFingerCarryingTheCanvasIsSeenAndSoNotExplained() {
        // one-finger pan: the canvas moves, which answers the touch in its own way
        assertNull(
            fingerSilenceReason(
                paints = false, moves = true, penHovering = false, hoverMutes = true, hoverTouch = false, droppedAsPalm = false,
            )
        )
    }

    @Test
    fun theHoveringPenIsNamedOnlyWhenItIsTheOneSilencingTheFinger() {
        val why = requireNotNull(
            fingerSilenceReason(paints = false, moves = false, penHovering = true, hoverMutes = true, hoverTouch = false, droppedAsPalm = false)
        )
        assert(why.contains("Заглушать палец рядом с пером"))
        // the mute off — the app's default, drawn with a finger — has no hover to blame: the finger's
        // silence comes from the gestures, and sending the person to a switch already off is a lie
        val muted = requireNotNull(
            fingerSilenceReason(paints = false, moves = false, penHovering = true, hoverMutes = false, hoverTouch = false, droppedAsPalm = false)
        )
        assert(!muted.contains("Заглушать"))
        assert(muted.contains("Рисование пальцем"))
        // Hover's own touch wants the finger: it moves the brush's opacity, which is an answer the
        // person sees, so the mute is not the reason even when it is switched on
        assertNull(
            fingerSilenceReason(paints = false, moves = false, penHovering = true, hoverMutes = true, hoverTouch = true, droppedAsPalm = false)
        )
    }

    @Test
    fun onlyATouchThePalmTestActuallyDroppedIsBlamedOnIt() {
        val dropped = requireNotNull(
            fingerSilenceReason(paints = false, moves = false, penHovering = false, hoverMutes = false, hoverTouch = false, droppedAsPalm = true)
        )
        assert(dropped.contains("Отказ ладони"))
        // the API 35 emulator, and every phone whose screen reports its dots honestly: the test is
        // switched on but this touch passed it, so the gesture switch is the one to name
        val passed = requireNotNull(
            fingerSilenceReason(paints = false, moves = false, penHovering = false, hoverMutes = false, hoverTouch = false, droppedAsPalm = false)
        )
        assert(!passed.contains("Отказ ладони"))
        assert(passed.contains("Рисование пальцем"))
    }

    @Test
    fun EveryReasonNamesTheSwitchThatEndsIt() {
        // the person is on a phone with a stylus-less driver: a reason to read is useless without the
        // way out, and the way out must be one they can reach from Settings, not only the gesture page
        for (why in listOf(
            requireNotNull(fingerSilenceReason(paints = false, moves = false, penHovering = true, hoverMutes = true, hoverTouch = false, droppedAsPalm = false)),
            requireNotNull(fingerSilenceReason(paints = false, moves = false, penHovering = false, hoverMutes = false, hoverTouch = false, droppedAsPalm = true)),
            requireNotNull(fingerSilenceReason(paints = false, moves = false, penHovering = false, hoverMutes = false, hoverTouch = false, droppedAsPalm = false)),
        )) assert(why.contains("Настройки ›"))
    }
}
