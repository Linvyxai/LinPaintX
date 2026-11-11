package com.linpaintx

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linpaintx.canvas.CanvasViewModel
import com.linpaintx.canvas.keptPlugins
import com.linpaintx.ui.Switch
import com.linpaintx.ui.fittedInterfaceSize

/**
 * The app's own settings, apart from the canvas: what Android's page for the app links to as its
 * settings, and Help's Advanced settings opens. The canvas reads them again when it comes back.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("interface", Context.MODE_PRIVATE)
        val plugins = keptPlugins(this, prefs)
        setContent {
            val size = fittedInterfaceSize(plugins)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * size, density.fontScale)) {
                SettingsScreen(prefs) { finish() }
            }
        }
    }
}

private val Back = Color(0xFF000000)
private val Group = Color(0xFF1C1C1E)
private val Ink = Color(0xFFFFFFFF)
private val Dim = Color(0xFF8E8E93)
private val Line = Color(0xFF38383A)
private val Blue = Color(0xFF0A84FF)

/** A row of the page. */
private sealed class Setting

/** A switch: its key in the preferences, its name, what it does, and where it starts. */
private class Toggle(val key: String, val label: String, val detail: String, val default: Boolean) : Setting()

/** A slider from [min] to [max]. */
private class Amount(val key: String, val detail: String, val min: Float, val max: Float, val default: Float) : Setting()

/** One of [options] (kept by its index). */
private class Choice(
    val key: String,
    val label: String,
    val options: List<String>,
    val default: Int,
    val onChanged: ((Int) -> Unit)? = null,
) : Setting()

/** The groups of rows, each with its heading (none for "") */
private val Groups: List<Pair<String, List<Setting>>> = listOf(
    "" to listOf(
        Toggle(CanvasViewModel.ORIENTATION_MEMORY, "Запоминать положение холста", "Работа открывается с прежними масштабом и поворотом.", true),
        Toggle(CanvasViewModel.FIT_INSIDE, "Вписывать холст в интерфейс", "Вписанный холст не заходит под панели интерфейса.", false),
    ),
    "" to listOf(
        Toggle(CanvasViewModel.NO_TIME_LAPSE, "Отключить таймлапс", "Для новых работ таймлапс не записывается.", false),
        Toggle(CanvasViewModel.SIMPLIFIED_UNDOS, "Упрощённая отмена", "Штрихи коррекции отменяются вместе после выхода из неё.", true),
    ),
    "История отмены" to listOf(
        Choice(
            CanvasViewModel.HISTORY_LIMIT,
            "Объём истории на диске",
            CanvasViewModel.HISTORY_LIMIT_CHOICES,
            0,
            onChanged = { i -> com.linpaintx.core.setHistoryLimit(CanvasViewModel.HISTORY_LIMIT_BYTES[i].toULong()) },
        ),
    ),
    "Максимальная дистанция скорости" to listOf(
        Amount(CanvasViewModel.SPEED_DISTANCE, "Расстояние движения пера, после которого динамика кисти считает его быстрым.", 0.25f, 1f, 1f),
    ),
    "Экспорт перетаскиванием" to listOf(
        Choice(CanvasViewModel.DRAG_FORMAT, "Предпочтительный формат файла", CanvasViewModel.DRAG_FORMATS, 0),
        Choice(CanvasViewModel.DRAG_IMAGE_FORMAT, "Предпочтительный формат изображения", CanvasViewModel.DRAG_IMAGE_FORMATS, 0),
    ),
    "Accessibility" to listOf(
        Toggle(CanvasViewModel.SINGLE_TOUCH_COMPANION, "Панель жестов одним пальцем", "Плавающая панель отмены, возврата, масштаба, перемещения и вписывания.", false),
        Toggle(CanvasViewModel.COLOR_NAMES, "Названия цветов", "После выбора показывается название цвета.", false),
        Toggle(CanvasViewModel.FEEDBACK_SOUNDS, "Звуки интерфейса", "Отмена, возврат, инструменты и панели сопровождаются щелчками.", false),
        Toggle(
            CanvasViewModel.TAP_ASSISTANCE, "Помощь при касании — начальная точка",
            "A tap lands where the finger first touched, even if it drifts before lifting.", false,
        ),
    ),
)

@Composable
private fun SettingsScreen(prefs: android.content.SharedPreferences, onDone: () -> Unit) {
    var licences by remember { mutableStateOf(false) }
    if (licences) return Licences { licences = false }
    Box(Modifier.fillMaxSize().background(Back).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LinPaintX", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Готово", color = Blue, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onDone).padding(8.dp))
            }
            for ((heading, rows) in Groups) {
                Text(heading.uppercase(), color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Group)) {
                    rows.forEachIndexed { i, s ->
                        if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Line))
                        when (s) {
                            is Toggle -> ToggleRow(prefs, s)
                            is Amount -> AmountRow(prefs, s)
                            is Choice -> ChoiceRow(prefs, s)
                        }
                    }
                }
            }
            // the licences of what the app is made with
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Group)) {
                Row(Modifier.fillMaxWidth().clickable { licences = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Лицензии", color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text("›", color = Dim, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(prefs: android.content.SharedPreferences, s: Toggle) {
    var on by remember { mutableStateOf(prefs.getBoolean(s.key, s.default)) }
    Row(
        Modifier.fillMaxWidth().clickable {
            on = !on
            prefs.edit().putBoolean(s.key, on).apply()
        }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.label, color = Ink, fontSize = 17.sp)
            Text(s.detail, color = Dim, fontSize = 13.sp)
        }
        Switch(on, enabled = true)
    }
}

@Composable
private fun AmountRow(prefs: android.content.SharedPreferences, s: Amount) {
    var value by remember { mutableFloatStateOf(prefs.getFloat(s.key, s.default)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // a thin track and a round knob; dragged anywhere along it
        val thumb = 14.dp
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).pointerInput(s) {
                val r = thumb.toPx()
                fun at(x: Float) = s.min + ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f) * (s.max - s.min)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    value = at(down.position.x)
                    drag(down.id) {
                        value = at(it.position.x)
                        it.consume()
                    }
                    prefs.edit().putFloat(s.key, value).apply()
                }
            },
        ) {
            val r = thumb.toPx()
            val y = size.height / 2
            val x = r + (size.width - 2 * r) * (value - s.min) / (s.max - s.min)
            drawLine(Line, Offset(r, y), Offset(size.width - r, y), 4.dp.toPx(), StrokeCap.Round)
            drawLine(Blue, Offset(r, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(Ink, r, Offset(x, y))
        }
        Text(s.detail, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun ChoiceRow(prefs: android.content.SharedPreferences, s: Choice) {
    var chosen by remember { mutableStateOf(prefs.getInt(s.key, s.default).coerceIn(s.options.indices)) }
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
        // the menu opens under the value
        Box {
            Text(s.options[chosen], color = Dim, fontSize = 17.sp)
            DropdownMenu(open, { open = false }, containerColor = Group) {
                s.options.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name, color = if (i == chosen) Blue else Ink, fontSize = 17.sp) },
                        onClick = {
                            chosen = i
                            prefs.edit().putInt(s.key, i).apply()
                            s.onChanged?.invoke(i)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * The licences of what the app is made with: the interface font's and icons', then the third-party
 * software's (assets/licenses.txt, written by tools/licenses.py), a paragraph a row.
 */
@Composable
private fun Licences(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val paragraphs = remember {
        fun asset(name: String) = runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")
        val gap = "\n\n"
        val text = "Шрифт интерфейса Inter создан авторами проекта Inter и распространяется по лицензии SIL Open Font License 1.1." + gap +
            asset("fonts/Inter-OFL.txt").trim() + gap +
            "Приложение использует значки Lucide (lucide.dev), созданные Lucide Icons и участниками проекта по лицензии ISC." + gap +
            asset("icons/LICENSE-lucide.txt").trim() + gap + asset("licenses.txt")
        text.split(gap).map { it.trimEnd() }.filter { it.isNotBlank() }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Back).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            item {
                Text("‹ Settings", color = Blue, fontSize = 17.sp, modifier = Modifier.padding(top = 24.dp).clickable(onClick = onBack).padding(vertical = 8.dp))
                Text("Лицензии", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            }
            items(paragraphs.size) { i -> Text(paragraphs[i], color = Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
        }
    }
}
