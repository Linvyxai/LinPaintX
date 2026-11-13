package com.linpaintx.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderDragTest {
    @Test
    fun aDragMovesTheValueByItsShareOfTheTrack() {
        // 300 px of travel; 30 px up is a tenth more
        assertEquals(0.6f, sliderDrag(from = 0.5f, dy = -30f, travel = 300f, top = 1f), 1e-4f)
        // and down takes it back the same way
        assertEquals(0.4f, sliderDrag(from = 0.5f, dy = 30f, travel = 300f, top = 1f), 1e-4f)
    }

    @Test
    fun aSliderThatEndsWhereItsTrackEndsStopsThere() {
        assertEquals(1f, sliderDrag(0.9f, -300f, 300f, 1f), 1e-4f)
        assertEquals(0f, sliderDrag(0.1f, 300f, 300f, 1f), 1e-4f)
    }

    @Test
    fun theSizeSliderKeepsGrowingPastTheEndOfItsTrack() {
        // the brush's own maximum is at the top of the track; the drag going on past it is the band
        // above 100%, whose top is half the canvas (the engine's number, asked for as `top`)
        assertEquals(1.4f, sliderDrag(1.0f, -120f, 300f, 2f), 1e-4f)
        // and it stops there, however far the finger travels
        assertEquals(2f, sliderDrag(1.0f, -1500f, 300f, 2f), 1e-4f)
        assertEquals(2f, sliderDrag(2f, -30f, 300f, 2f), 1e-4f)
        // back down from the band comes off by the same arithmetic, with nothing caught at 100%:
        // a 120 px drag is 0.4 of a 300 px track, wherever it starts
        assertEquals(1.9f, sliderDrag(2f, 30f, 300f, 2f), 1e-4f)
        assertEquals(0.7f, sliderDrag(1.1f, 120f, 300f, 2f), 1e-4f)
        assertEquals(0.9f, sliderDrag(1f, 30f, 300f, 2f), 1e-4f)
    }

    @Test
    fun aTrackWithNoTravelMovesNothing() {
        // measured before the view has a height: the drag must not invent a huge step
        assertEquals(0.3f, sliderDrag(0.3f, -100f, 0f, 2f), 1e-4f)
    }
}
