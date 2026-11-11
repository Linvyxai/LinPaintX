package com.linpaintx.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class UiSafeLayoutTest {
    private val safe = UiSafeInsets(left = 12, top = 12, right = 12, bottom = 56)

    @Test
    fun popupStaysInsideEverySafeEdge() {
        val window = IntSize(360, 640)
        val popup = IntSize(200, 180)

        assertEquals(IntOffset(12, 12), safePopupPosition(IntOffset(-100, -100), window, popup, safe))
        assertEquals(IntOffset(148, 404), safePopupPosition(IntOffset(400, 700), window, popup, safe))
    }

    @Test
    fun popupNeverOverlapsPersistentBottomToolbar() {
        val window = IntSize(744, 480)
        val popup = IntSize(260, 220)
        val placed = safePopupPosition(IntOffset(300, 400), window, popup, safe)

        assertEquals(204, placed.y)
        assertEquals(window.height - safe.bottom, placed.y + popup.height)
    }

    @Test
    fun windowSizedModalKeepsItsOrigin() {
        val window = IntSize(360, 640)
        assertEquals(IntOffset.Zero, safePopupPosition(IntOffset.Zero, window, window, safe))
    }

    @Test
    fun panelMenuEndsAboveItsPanel() {
        val menuBottom = floatingPanelMenuBottom(640.dp, 92.dp, 200.dp)
        assertEquals(336.dp, menuBottom)
        assertEquals(Pc.PopoverGap, 640.dp - 92.dp - 200.dp - menuBottom)
    }
}
