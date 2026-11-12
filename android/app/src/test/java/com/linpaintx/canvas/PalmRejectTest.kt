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
        assert(palmDiagnostics(262f, 262).contains("достоверно"))
        // the liar: the app's density answers, and the row says so
        assert(palmDiagnostics(72f, 262).contains("недостоверно"))
        assert(palmDiagnostics(72f, 262).contains("262"))
        // nothing to go by: the test stands down
        assert(palmDiagnostics(0f, 0).contains("не действует"))
    }
}
