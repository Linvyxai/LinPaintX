package com.linpaintx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linpaintx.canvas.CanvasViewModel
import com.linpaintx.canvas.Panel
import com.linpaintx.core.ToolKind

private val SliderLeft = 5.dp
private val SizeTrackTop = 6.dp
private val OpacityTrackTop = 248.dp

/** A tap target without a ripple: the bars show their state through colour alone. */
private fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
}

/** The bar across the top of the canvas: artwork actions on the left, painting tools on the right. */
@Composable
fun TopBar(vm: CanvasViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(Pc.BarHeight).background(Pc.Bar)) {
        val fit = Pc.barFit(maxWidth)
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = fit.margin),
            horizontalArrangement = Arrangement.spacedBy(fit.actionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(PcIcons.Actions, "Действия", fit.button, selected = vm.panel == Panel.ACTIONS) { vm.toggle(Panel.ACTIONS) }
            ActionButton(PcIcons.Adjustments, "Коррекция", fit.button, selected = vm.panel == Panel.ADJUSTMENTS || vm.adjustment != null || vm.opacityLayer != null || vm.recoloring) { vm.adjustmentsPressed() }
            ActionButton(PcIcons.Selection, "Выделение", fit.button, selected = vm.selecting, glyph = 33.dp) { vm.toggleSelecting() }
            ActionButton(PcIcons.Transform, "Трансформация", fit.button, selected = vm.transforming, glyph = 44.dp) { vm.toggleTransform() }
        }
        // the layers and colour panels take the highlight from the current tool while open
        val panelOpen = vm.panel == Panel.LAYERS || vm.panel == Panel.COLOR || vm.transforming || vm.selecting || vm.adjustment != null
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = fit.margin),
            horizontalArrangement = Arrangement.spacedBy(fit.toolGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // painting an adjustment on (or cloning), the brush shows it
            ToolButton(
                if (vm.adjustPencil) PcIcons.BrushPaintedOn else PcIcons.Brush,
                "Кисть",
                fit.button,
                selected = vm.adjustPencil || (!panelOpen && vm.tool == ToolKind.PAINT),
                onHold = { vm.toolHeld(ToolKind.PAINT) },
            ) {
                vm.toolPressed(ToolKind.PAINT)
            }
            ToolButton(PcIcons.Smudge, "Размазывание", fit.button, selected = !panelOpen && vm.tool == ToolKind.SMUDGE, onHold = { vm.toolHeld(ToolKind.SMUDGE) }) {
                vm.toolPressed(ToolKind.SMUDGE)
            }
            ToolButton(PcIcons.Erase, "Ластик", fit.button, selected = !panelOpen && vm.tool == ToolKind.ERASE, onHold = { vm.toolHeld(ToolKind.ERASE) }) {
                vm.toolPressed(ToolKind.ERASE)
            }
            ToolButton(PcIcons.Layers, "Слои", fit.button, selected = vm.panel == Panel.LAYERS) { vm.toggle(Panel.LAYERS) }
            ToolButton(
                PcIcons.Checkerboard,
                if (vm.background.checkerboard) "Тёмная шахматная подложка" else "Светлая шахматная подложка",
                fit.button,
                selected = vm.background.checkerboard
            ) { vm.toggleCheckerboard() }
            ColorButton(vm, fit.button)
        }
    }
}



/** Gallery, history and real input stabilization controls kept at the safe upper-left edge. */
@Composable
fun TopLeftBar(vm: CanvasViewModel, modifier: Modifier = Modifier) {
    var stabilizerOpen by remember { mutableStateOf(false) }
    val enabled = vm.inputStabilization > 0f || vm.inputMotionFiltering > 0f
    val mode = when {
        vm.inputMotionExpression >= 0.6f -> 2
        vm.inputMotionFiltering > 0f -> 1
        else -> 0
    }
    val level = when (mode) {
        1 -> (vm.inputMotionFiltering / 0.7f * 10f).roundToInt()
        2 -> (vm.inputMotionFiltering / 0.85f * 10f).roundToInt()
        else -> (vm.inputStabilization * 10f).roundToInt()
    }.coerceIn(0, 10)

    Column(modifier.wrapContentSize()) {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(15.dp)).background(Pc.Bar)
                .border(1.dp, Pc.GlassMenuEdge, RoundedCornerShape(15.dp)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTopButton(PcIcons.MenuFolder, "Галерея", true) { vm.closeArtwork() }
            CompactTopButton(PcIcons.Undo, "Отменить", vm.canUndo) { vm.undo() }
            CompactTopButton(PcIcons.Redo, "Вернуть", vm.canRedo) { vm.redo() }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (enabled) Pc.Accent.copy(alpha = 0.25f) else Color.Transparent)
                    .tap { stabilizerOpen = !stabilizerOpen },
                contentAlignment = Alignment.Center,
            ) {
                StabilizerGlyph(if (enabled) Pc.Accent else Pc.SideGlyph)
                if (enabled) Text("$level", color = Pc.Text, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
        }
        if (stabilizerOpen) {
            StabilizerMenu(
                enabled = enabled,
                level = level,
                mode = mode,
                onEnabled = { on ->
                    if (!on) {
                        vm.changeInput(stabilization = 0f, motion = 0f, expression = 0f)
                    } else {
                        vm.enableStabilizerWithLastSettings()
                    }
                },
                onLevel = { level ->
                    vm.applyStabilizer(mode, level)
                    vm.inputStabilizerLastLevel = level
                },
                onMode = { mode ->
                    vm.applyStabilizer(mode, level)
                    vm.inputStabilizerLastMode = mode
                },
            )
        }
    }
}



@Composable
private fun CompactTopButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = label,
        tint = if (enabled) Pc.SideGlyph else Pc.SideGlyphDisabled,
        modifier = Modifier.size(40.dp).tap(enabled, onClick).padding(7.dp),
    )
}

@Composable
private fun StabilizerGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(27.dp, 22.dp)) {
        val path = Path().apply {
            moveTo(1f, size.height * 0.72f)
            cubicTo(size.width * 0.22f, size.height * 0.05f, size.width * 0.38f, size.height * 0.95f, size.width * 0.57f, size.height * 0.45f)
            cubicTo(size.width * 0.72f, size.height * 0.12f, size.width * 0.84f, size.height * 0.54f, size.width - 1f, size.height * 0.2f)
        }
        drawPath(path, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}


private data class StabilizerPresetUI(
    val title: String,
    val description: String,
    val modeIndex: Int,
)

private val StabilizerPresetsUI = listOf(
    StabilizerPresetUI("Обычный", "Сглаживает дрожание, сохраняя быстрый отклик.", 0),
    StabilizerPresetUI("Плавный", "Сильнее усредняет движение для длинных линий.", 1),
    StabilizerPresetUI("Точный", "Подавляет мелкие рывки, сохраняя форму штриха.", 2),
)

@Composable
private fun StabilizerMenu(
    enabled: Boolean,
    level: Int,
    mode: Int,
    onEnabled: (Boolean) -> Unit,
    onLevel: (Int) -> Unit,
    onMode: (Int) -> Unit,
) {
    Column(
        Modifier.padding(top = 7.dp).width(330.dp).clip(RoundedCornerShape(22.dp)).background(Pc.GlassMenuOverPanel)
            .border(1.dp, Pc.GlassMenuEdge, RoundedCornerShape(22.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Стабилизатор", color = Pc.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (enabled) "Вкл." else "Выкл.", color = if (enabled) Pc.Accent else Pc.TextDim, fontSize = 14.sp)
            Box(Modifier.padding(start = 8.dp).size(42.dp, 24.dp).clip(CircleShape).background(if (enabled) Pc.Accent else Pc.SwitchOff).tap { onEnabled(!enabled) }) {
                Box(Modifier.align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(18.dp).clip(CircleShape).background(Color.White))
            }
        }
        Text("Сила: $level", color = Pc.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0..10) {
                Box(
                    Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (i == level && enabled) Pc.Accent else Pc.Row).tap { onLevel(i) },
                    contentAlignment = Alignment.Center,
                ) { Text("$i", color = if (i == level && enabled) Color.White else Pc.TextDim, fontSize = 10.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        StabilizerPresetsUI.forEachIndexed { index, preset ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (index == mode) Pc.RowSelected.copy(alpha = 0.28f) else Color.Transparent)
                    .tap { onMode(index) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StabilizerPreview(index, Modifier.size(88.dp, 38.dp))
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(preset.title, color = Pc.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(preset.description, color = Pc.TextDim, fontSize = 10.sp, lineHeight = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StabilizerPreview(mode: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(9.dp)).background(Pc.Row)) {
        val path = Path().apply {
            moveTo(5f, size.height * 0.7f)
            when (mode) {
                0 -> {
                    lineTo(size.width * 0.2f, size.height * 0.28f)
                    lineTo(size.width * 0.36f, size.height * 0.66f)
                    lineTo(size.width * 0.55f, size.height * 0.31f)
                    lineTo(size.width * 0.72f, size.height * 0.55f)
                    lineTo(size.width - 5f, size.height * 0.25f)
                }
                1 -> cubicTo(size.width * 0.28f, size.height * 0.08f, size.width * 0.58f, size.height * 0.82f, size.width - 5f, size.height * 0.22f)
                else -> cubicTo(size.width * 0.22f, size.height * 0.42f, size.width * 0.5f, size.height * 0.5f, size.width - 5f, size.height * 0.26f)
            }
        }
        drawPath(path, if (mode == 0) Pc.TextDim else Pc.Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** An action button: its glyph on a dark disc, which turns blue while its panel is open. */
@Composable
private fun ActionButton(icon: ImageVector, label: String, width: Dp, selected: Boolean = false, glyph: Dp = 36.dp, onClick: () -> Unit = {}) {
    val lit = animateHighlight(selected)
    Box(Modifier.size(width, Pc.BarButtonHeight).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(Pc.ActionDiscSize).clip(CircleShape).background(lerp(Pc.ActionDisc, Pc.BarGlyphSelected, lit)))
        // the glyphs are drawn in a 44 frame, shown [glyph] across (most 36)
        Icon(icon, contentDescription = label, tint = lerp(Pc.ActionGlyph, Color.White, lit), modifier = Modifier.requiredSize(glyph))
    }
}

/** A tap target that also does [onHold] when held instead. */
private fun Modifier.tapOrHold(onClick: () -> Unit, onHold: () -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    pointerInput(Unit) { detectTapGestures(onLongPress = { hold() }, onTap = { tap() }) }
}

/** A tool button: tapped, the tool (or its brushes); held ([onHold]), it takes the current brush. */
@Composable
private fun ToolButton(icon: ImageVector, label: String, width: Dp, selected: Boolean, onHold: (() -> Unit)? = null, onClick: () -> Unit) {
    val touch = if (onHold != null) Modifier.tapOrHold(onClick, onHold) else Modifier.tap(onClick = onClick)
    Box(Modifier.size(width, Pc.BarButtonHeight).then(touch), contentAlignment = Alignment.Center) {
        // a 44 frame shown as it is, over the 38 tall button
        Icon(icon, contentDescription = label, tint = lerp(Pc.BarGlyph, Pc.BarGlyphSelected, animateHighlight(selected)), modifier = Modifier.requiredSize(44.dp))
    }
}

@Composable
private fun ColorButton(vm: CanvasViewModel, width: Dp) {
    val rgb = vm.colorRgb()
    // ringed only where the colour would fade into the bar: dark ones on the dark bar, light ones
    // on the light bar
    val ringed = if (Pc.light) vm.brightness > LIGHT_RING_ABOVE else vm.brightness < DARK_RING_BELOW
    // held, the colour painted with before
    // dragged out onto the canvas, it fills there (ColorDrop)
    Box(Modifier.size(width, Pc.BarButtonHeight).dropSource(vm, onHold = { vm.previousColor() }) { vm.toggle(Panel.COLOR) }, contentAlignment = Alignment.Center) {
        val dot = Modifier.size(Pc.ColorDot).clip(CircleShape).background(CanvasSpace.color(rgb))
        Box(if (ringed) dot.border(1.dp, Pc.ColorRing, CircleShape) else dot)
    }
}

/** The colour button's brightness (HSB) under which it is ringed on the dark bar. */
private const val DARK_RING_BELOW = 0.35f

/** The colour button's brightness over which it is ringed on the light bar. */
private const val LIGHT_RING_ABOVE = 0.85f

/**
 * The side bar against the left edge (the right one, [right]-handed): brush size, the modify
 * button and opacity, its tracks [trackHeight] long. On the right everything is
 * mirrored but the glyphs.
 */
@Composable
fun SideBar(vm: CanvasViewModel, modifier: Modifier = Modifier, right: Boolean = false, trackHeight: Dp = Pc.SliderHeight, shown: Float = 1f) {
    // a control's left edge, mirrored on the right
    fun x(left: Dp, width: Dp) = if (right) Pc.SideBarWidth - left - width else left
    // shorter tracks bring what is under each up with them
    val shorter = Pc.SliderHeight - trackHeight
    val opacityTop = OpacityTrackTop - shorter
    // its glass slides out with it as the interface hides, its screen side's corners off the screen
    val aside = with(androidx.compose.ui.platform.LocalDensity.current) { Pc.SideBarWidth.toPx() } * (1f - shown) * if (right) 1f else -1f
    val moved = Offset(aside, 0f)
    val body = BarGlass(shown, moved, pastLeft = if (right) 0.dp else Pc.SideBarCorner, pastRight = if (right) Pc.SideBarCorner else 0.dp)
    val track = BarGlass(shown, moved, window = true)
    Box(
        Modifier.then(rememberFrost(Pc.SideBarCorner, body)).then(modifier).size(Pc.SideBarWidth, Pc.SideBarHeight - shorter * 2)
            .drawBehind {
                // translucent, with windows where the slider tracks are: they show what is behind
                val r = Pc.SideBarCorner.toPx()
                val track = Size(Pc.SliderWidth.toPx(), trackHeight.toPx())
                val trackR = CornerRadius(Pc.SliderCorner.toPx())
                val body = Path().apply {
                    fillType = PathFillType.EvenOdd
                    val (inner, outer) = CornerRadius.Zero to CornerRadius(r)
                    addRoundRect(
                        if (right) RoundRect(0f, 0f, size.width, size.height, outer, inner, inner, outer)
                        else RoundRect(0f, 0f, size.width, size.height, inner, outer, outer, inner),
                    )
                    for (top in listOf(SizeTrackTop, opacityTop)) {
                        val o = Offset(x(SliderLeft, Pc.SliderWidth).toPx(), top.toPx())
                        addRoundRect(RoundRect(o.x, o.y, o.x + track.width, o.y + track.height, trackR))
                    }
                }
                drawPath(body, Pc.SideBar)
            },
    ) {
        BrushSlider(vm.size, vm::changeSize, Modifier.offset(x(SliderLeft, Pc.SliderWidth), SizeTrackTop).then(rememberFrost(Pc.SliderCorner, track)), panel(vm, CanvasViewModel.SliderPanel.SIZE), vm.sizeNotches, trackHeight, top = vm.sizeSliderTop)
        // the modify button: the next touch on the canvas picks a colour
        Box(
            Modifier.offset(x(5.dp, 40.dp), 191.dp - shorter).size(40.dp, 41.dp).pointerInput(Unit) {
                // held, it changes what a canvas touch does; tapped, it does what Tap □ is set to
                awaitEachGesture {
                    awaitFirstDown().consume()
                    vm.modifyPressed()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    vm.modifyReleased(cancelled = up == null)
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(Pc.ModifySize).drawBehind {
                    val w = Pc.ModifyStroke.toPx()
                    drawRoundRect(
                        if (vm.pickNext) Pc.Accent else Pc.SideGlyph,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(Pc.ModifyCorner.toPx() - w / 2f),
                        style = Stroke(w),
                    )
                },
            )
        }
        BrushSlider(vm.opacity, vm::changeOpacity, Modifier.offset(x(SliderLeft, Pc.SliderWidth), opacityTop).then(rememberFrost(Pc.SliderCorner, track)), panel(vm, CanvasViewModel.SliderPanel.OPACITY), vm.opacityNotches, trackHeight)
        // beside the slider it belongs to, a little above it, outside the bar
        vm.sliderPanel?.let { which ->
            val top = if (which == CanvasViewModel.SliderPanel.SIZE) SizeTrackTop else opacityTop
            SliderPanel(vm, which, Modifier.offset(x(SliderLeft + Pc.SliderWidth + SliderPanelGap, SliderPanelSide), top - SliderPanelRise))
        }
    }
}

/** What a slider's panel does: shown while it is dragged ([held] false), kept up when it is held still. */
class SliderPanelHooks(val show: (held: Boolean) -> Unit, val hide: () -> Unit)

private fun panel(vm: CanvasViewModel, which: CanvasViewModel.SliderPanel) = SliderPanelHooks(
    show = { held ->
        vm.sliderPanel = which
        vm.sliderPanelKept = held
    },
    hide = { if (!vm.sliderPanelKept) vm.sliderPanel = null },
)

private val SliderPanelSide = 190.dp
private val SliderPanelGap = 8.dp
private val SliderPanelRise = 10.dp
// dark glass over the canvas (what is behind it blurred), or light
private val SliderPanelFill get() = if (Pc.light) Color(0xFFE7E7E7) else Color(0xC93E3E3E)
private val SliderPanelInk get() = if (Pc.light) Color(0xFF393939) else Color(0xFFF0F0F0)
// the dot is drawn in the interface's ink, not the brush's colour
private val SliderPanelDot get() = if (Pc.light) Color.Black else Color.White

/**
 * A slider's panel: its name and value ("Size 8%"), a button saving the value as a notch on the
 * slider ("+"; "-" takes away the notch it is on), and in the middle the brush's stamp as it would
 * paint now (soft where it is soft), its size on the screen (and at its opacity).
 */
@Composable
private fun SliderPanel(vm: CanvasViewModel, which: CanvasViewModel.SliderPanel, modifier: Modifier) {
    val size = which == CanvasViewModel.SliderPanel.SIZE
    val value = if (size) vm.size else vm.opacity
    val diameter = androidx.compose.runtime.remember(vm.size) { vm.brushScreenDiameter() }
    // the brush's stamp as it paints (soft where it is soft), as many pixels across as it shows
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radius = with(density) { (diameter / 2f).coerceIn(1.5f, SliderPanelSide.toPx() / 2f - 8.dp.toPx()) }
    val side = (radius * 2f).roundToInt().coerceAtLeast(2)
    val stamp = androidx.compose.runtime.remember(vm.size, side) { vm.brushStamp(side) }
    // its own size, past the bar's edge (the bar's width would otherwise squeeze it)
    Box(
        modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(SliderPanelSide)
            .then(if (Pc.light) Modifier else rememberFrost(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(SliderPanelFill)
            // a hairline rim, as measured (about 25 over the glass)
            .border(1.dp, Pc.GlassMenuEdge, RoundedCornerShape(22.dp)),
    ) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(if (size) "Размер" else "Непрозрачность")
                pop()
                append(" ${(value * 100f).roundToInt()}%")
            },
            color = SliderPanelInk,
            fontSize = 19.sp,
            maxLines = 1,
            modifier = Modifier.offset(16.dp, 6.dp).height(32.dp).wrapContentHeight(Alignment.CenterVertically),
        )
        // read again as the notches change, so a value saved or taken away shows at once
        val notches = if (size) vm.sizeNotches else vm.opacityNotches
        val onNotch = androidx.compose.runtime.remember(notches, value) { vm.notchAt(size, value) != null }
        val canSave = androidx.compose.runtime.remember(notches, value) { vm.canSaveNotch(size) }
        if (onNotch || canSave) {
            Box(Modifier.offset(150.dp, 6.dp).size(32.dp).tap { vm.toggleNotch(size) }, contentAlignment = Alignment.Center) {
                Icon(
                    if (onNotch) PcIcons.NotchRemove else PcIcons.NotchAdd,
                    contentDescription = if (onNotch) "Убрать" else "Сохранить",
                    tint = SliderPanelInk,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val ink = SliderPanelDot.copy(alpha = if (size) 1f else value.coerceIn(0.02f, 1f))
            if (stamp == null) {
                drawCircle(ink, radius, center)
            } else {
                drawImage(
                    stamp,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - side / 2f, center.y - side / 2f),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink),
                )
            }
        }
    }
}

/// Where a drag takes a slider's value: `from` where it stands, `dy` how far the finger moved down
/// (so up is positive growth) over `travel` pixels of track, kept within `0..=top`.
///
/// `top` is 1 for a slider that ends where its track ends; the size slider passes more, and the
/// value then climbs while the thumb has nowhere left to go — the band past the brush's own maximum
/// (see [`BrushSlider`]).
internal fun sliderDrag(from: Float, dy: Float, travel: Float, top: Float): Float =
    if (travel <= 0f) from else (from - dy / travel).coerceIn(0f, top)

/**
 * An upright slider moved by dragging anywhere on it: the thumb follows the finger (relative,
 * not jump-to-touch), 1 at the top. Moved, its [panel] shows while it moves; held still, it stays up.
 * Its [notches] (saved values) show as marks on the track; a value dragged near one is pulled onto
 * it, and a tap on one goes there. On a notch, the thumb shows a blue bar.
 *
 * [top] is how far the value may go: 1 for the sliders that end where the track ends. A size slider
 * passes more, and past 100% the thumb has nowhere left to go — it stays at the top of the track with
 * a line drawn above it while the finger keeps dragging on, because the brush's own maximum is the end
 * of the track, not the end of what the app can draw. Notches belong to the track, so they only pull
 * the value in below 100%.
 */
@Composable
fun BrushSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    panel: SliderPanelHooks? = null,
    notches: List<Float> = emptyList(),
    height: Dp = Pc.SliderHeight,
    top: Float = 1f,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val hooks by rememberUpdatedState(panel)
    val marks by rememberUpdatedState(notches)
    fun near(v: Float, reach: Float = CanvasViewModel.NOTCH_NEAR) =
        marks.filter { kotlin.math.abs(it - v) < reach }.minByOrNull { kotlin.math.abs(it - v) }
    Box(
        modifier.size(Pc.SliderWidth, height)
            .pointerInput(height, top) {
                val travel = (height - Pc.ThumbHeight - Pc.ThumbInset * 2).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var last = down.position.y
                    var moved = false
                    var lifted = false
                    // the system took the touch back (the pen coming near takes the screen from a
                    // finger): it wasn't lifted, so the panel stays up for the pen to tap its +
                    var taken = false
                    // where the finger has taken it, before a notch pulls it in
                    var raw = current
                    // follows the finger; false once it is lifted. Another pointer's event (the
                    // pen hovering in) isn't this touch's.
                    suspend fun AwaitPointerEventScope.follow(): Boolean {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return true
                        if (!c.pressed) {
                            // a cancelled touch ends already consumed; a lift doesn't
                            taken = c.isConsumed
                            return false
                        }
                        val dy = c.position.y - last
                        if (dy != 0f) {
                            if (!moved) hooks?.show(false)
                            moved = true
                            last = c.position.y
                            raw = sliderDrag(raw, dy, travel, top)
                            change(if (raw >= 1f) raw else near(raw) ?: raw)
                        }
                        c.consume()
                        return true
                    }
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (!lifted && !moved) lifted = !follow()
                    }
                    if (!lifted && !moved) hooks?.show(true)
                    // a tap on a notch's mark goes to it
                    if (lifted && !moved && !taken) {
                        val inset = Pc.ThumbInset.toPx()
                        val at = 1f - (down.position.y - inset - Pc.ThumbHeight.toPx() / 2f) / travel
                        near(at, Pc.ThumbHeight.toPx() / 2f / travel)?.let { change(it) }
                    }
                    while (!lifted) lifted = !follow()
                    if (taken) hooks?.show(true) else if (moved) hooks?.hide()
                }
            }
            .drawBehind {
                val inset = Pc.ThumbInset.toPx()
                val thumb = Size(Pc.ThumbWidth.toPx(), Pc.ThumbHeight.toPx())
                // past 100% the knob has no room left to move, so it stays at the top of the track
                // and the value climbs on; the line drawn above it says which of the two is happening
                val at = Offset((size.width - thumb.width) / 2f, inset + (size.height - thumb.height - inset * 2) * (1f - value.coerceIn(0f, 1f)))
                val knob = RoundRect(at.x, at.y, at.x + thumb.width, at.y + thumb.height, CornerRadius(thumb.height / 2f))
                // the track has a window where the thumb is, so the thumb shows over the bar alone;
                // a lighter rim runs round it
                val corner = Pc.SliderCorner.toPx()
                val rim = Pc.SliderRimWidth.toPx()
                val inside = RoundRect(rim, rim, size.width - rim, size.height - rim, CornerRadius(corner - rim))
                val track = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(inside)
                    addRoundRect(knob)
                }
                drawPath(track, Pc.SliderTrack)
                val ring = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
                    addRoundRect(inside)
                }
                drawPath(ring, Pc.SliderRim)
                // the notches: short bars across the track; on one, the thumb shows a blue bar
                val bar = Size(NotchWidth.toPx(), NotchHeight.toPx())
                val travel = size.height - thumb.height - inset * 2
                for (n in notches) {
                    val y = inset + thumb.height / 2f + travel * (1f - n)
                    drawRoundRect(NotchMark, Offset((size.width - bar.width) / 2f, y - bar.height / 2f), bar, CornerRadius(bar.height / 2f))
                }
                drawPath(Path().apply { addRoundRect(knob) }, Pc.SliderThumb)
                // past 100% a line above the knob: the knob itself has no room left to say it, and
                // the panel's percent is the number for that
                if (value > 1f) {
                    drawLine(Pc.Accent, Offset(at.x, at.y - Pc.ThumbInset.toPx() / 2f), Offset(at.x + thumb.width, at.y - Pc.ThumbInset.toPx() / 2f), 2.dp.toPx(), cap = StrokeCap.Round)
                }
                if (notches.any { kotlin.math.abs(it - value) < 1e-4f }) {
                    drawRoundRect(Pc.Accent, Offset((size.width - bar.width) / 2f, at.y + (thumb.height - bar.height) / 2f), bar, CornerRadius(bar.height / 2f))
                }
            },
    )
}

/** Liquify's ring: its reach as a thin dark circle with a light edge, a fine cross in its middle. */
@Composable
private fun LiquifyRing(vm: CanvasViewModel) {
    val at = vm.liquifyRingAt ?: return
    val r = vm.liquifyRingRadius
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        for ((width, color) in listOf(3.dp.toPx() to Color.White, 1.dp.toPx() to Color.Black)) {
            drawCircle(color, r, at, style = Stroke(width))
            val arm = 5.dp.toPx()
            drawLine(color, at - Offset(arm, 0f), at + Offset(arm, 0f), width)
            drawLine(color, at - Offset(0f, arm), at + Offset(0f, arm), width)
        }
    }
}

private val NotchWidth = 20.dp
private val NotchHeight = 4.dp
private val NotchMark = Color(0x1AFFFFFF)

/**
 * The brush's outline where the pen paints or hovers, in the brush's own shape (the round tip's a
 * circle): a thin dark line with a light edge, or in the colour painted with.
 */
@Composable
fun BrushCursor(vm: CanvasViewModel) {
    LiquifyRing(vm)
    val at = vm.cursorAt ?: return
    val r = vm.cursorRadius
    if (r < 2f) return
    val style = vm.cursorStyle
    val paint = CanvasSpace.color(vm.colorRgb())
    val shape = vm.cursorShape
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        // its loops across the brush's diameter, about where it is
        val paths = shape.map { ring ->
            Path().apply {
                for (i in 0 until ring.size / 2) {
                    val x = at.x + ring[i * 2] * r * 2f
                    val y = at.y + ring[i * 2 + 1] * r * 2f
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
        fun outline(color: Color, width: Float) {
            if (paths.isEmpty()) drawCircle(color, r, at, style = Stroke(width))
            for (p in paths) drawPath(p, color, style = Stroke(width))
        }
        val contrast = { outline(Color.White, 3.dp.toPx()); outline(Color.Black, 1.dp.toPx()) }
        val colour = { outline(Color(0x99FFFFFF), 3.dp.toPx()); outline(paint, 1.5.dp.toPx()) }
        when (style) {
            CanvasViewModel.CursorStyle.CONTRAST -> contrast()
            CanvasViewModel.CursorStyle.COLOR -> colour()
            // as the brush says: none, contrasting lines, or the colour
            CanvasViewModel.CursorStyle.BRUSH -> when (vm.cursorOutline) {
                0 -> Unit
                2 -> colour()
                else -> contrast()
            }
        }
    }
}
