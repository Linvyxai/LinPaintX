package com.linpaintx.canvas

import com.linpaintx.core.Adjustment
import com.linpaintx.core.adjustmentGroups
import com.linpaintx.core.adjustmentLabel
import com.linpaintx.core.adjustmentPaintable
import com.linpaintx.core.adjustmentSwiped

// The Adjustments menu: each adjustment's name, group, and whether it is swiped or painted on are
// the core's (adjust_menu.rs).

private val labels: Map<Adjustment, String> by lazy { Adjustment.entries.associateWith { adjustmentLabel(it) } }
private val menuGroups: List<List<Adjustment>> by lazy { adjustmentGroups() }

val Adjustment.label: String get() = labels.getValue(this)

/** Its one amount is dragged across the canvas. */
val Adjustment.swiped: Boolean get() = adjustmentSwiped(this)

/** Can be painted on with the brush (Pencil) instead of changing the whole layer. */
val Adjustment.paintable: Boolean get() = adjustmentPaintable(this)

/** The menu's groups: colour, blurs, effects, painting. */
val Adjustment.Companion.groups: List<List<Adjustment>> get() = menuGroups
