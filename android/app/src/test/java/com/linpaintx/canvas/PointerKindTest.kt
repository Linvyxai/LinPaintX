package com.linpaintx.canvas

import android.view.MotionEvent
import com.linpaintx.core.PointerKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PointerKindTest {
    @Test
    fun aToolThePlatformCannotNameIsAFinger() {
        // cheap digitizers report this for an actual finger; treating it as nothing at all would make
        // the canvas answer nothing to the one tool the app is drawn with, silently
        assertEquals(PointerKind.FINGER, kindOfToolType(MotionEvent.TOOL_TYPE_UNKNOWN))
        assertEquals(PointerKind.FINGER, kindOfToolType(MotionEvent.TOOL_TYPE_FINGER))
    }

    @Test
    fun theToolsThatAreNotAFingerStayThemselves() {
        assertEquals(PointerKind.STYLUS, kindOfToolType(MotionEvent.TOOL_TYPE_STYLUS))
        assertEquals(PointerKind.STYLUS_ERASER, kindOfToolType(MotionEvent.TOOL_TYPE_ERASER))
        assertEquals(PointerKind.MOUSE, kindOfToolType(MotionEvent.TOOL_TYPE_MOUSE))
    }
}
