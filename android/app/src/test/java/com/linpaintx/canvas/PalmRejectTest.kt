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
}
