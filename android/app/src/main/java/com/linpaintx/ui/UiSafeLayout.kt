package com.linpaintx.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.PopupPositionProvider

internal data class UiSafeInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Keeps a popup inside the usable window, including the persistent bottom toolbar. */
internal fun safePopupPosition(
    wanted: IntOffset,
    window: IntSize,
    content: IntSize,
    insets: UiSafeInsets,
): IntOffset {
    fun place(at: Int, windowSize: Int, contentSize: Int, before: Int, after: Int): Int {
        val last = windowSize - after - contentSize
        return if (last >= before) at.coerceIn(before, last) else ((windowSize - contentSize) / 2).coerceAtLeast(0)
    }
    return IntOffset(
        place(wanted.x, window.width, content.width, insets.left, insets.right),
        place(wanted.y, window.height, content.height, insets.top, insets.bottom),
    )
}

/** The same anchor-relative placement used by Popup's alignment/offset overload. */
internal class AlignedPopupPositionProvider(
    private val alignment: Alignment,
    private val offset: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchorPoint = alignment.align(IntSize.Zero, anchorBounds.size, layoutDirection)
        val popupPoint = alignment.align(IntSize.Zero, popupContentSize, layoutDirection)
        val direction = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
        return anchorBounds.topLeft + anchorPoint - popupPoint + IntOffset(offset.x * direction, offset.y)
    }
}

internal class SafePopupPositionProvider(
    private val delegate: PopupPositionProvider,
    private val insets: UiSafeInsets,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = safePopupPosition(
        delegate.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize),
        windowSize,
        popupContentSize,
        insets,
    )
}

/** Bottom edge of a menu stacked above a floating panel rather than over its controls. */
internal fun floatingPanelMenuBottom(screenHeight: Dp, panelBottom: Dp, panelHeight: Dp): Dp =
    screenHeight - panelBottom - panelHeight - Pc.PopoverGap
