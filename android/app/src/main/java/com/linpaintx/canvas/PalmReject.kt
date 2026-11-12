package com.linpaintx.canvas

import kotlin.math.roundToInt

/**
 * How wide a finger's contact is, in millimetres, or null when the screen cannot say.
 *
 * The system's `xdpi` is a lie on a number of tablets — some report the panel's native dot pitch,
 * others round it to a stock value, and some give no useful figure at all. The palm test compares a
 * pixel width against millimetres, so a too-small `xdpi` reads an ordinary fingertip as 30 mm wide:
 * every touch would be taken for a palm, and the canvas would paint nothing at all. So `xdpi` is
 * trusted only between [SANE_DPI_LOW] and [SANE_DPI_HIGH], and the density the app itself is laid
 * out by answers instead; neither of them usable means no palm test at all.
 */
fun touchMillimetres(majorPx: Float, xdpi: Float, densityDpi: Int): Float? {
    val dpi = when {
        xdpi >= SANE_DPI_LOW && xdpi <= SANE_DPI_HIGH -> xdpi
        densityDpi >= SANE_DPI_LOW -> densityDpi.toFloat()
        else -> return null
    }
    return majorPx / dpi * 25.4f
}

private const val SANE_DPI_LOW = 100f
private const val SANE_DPI_HIGH = 600f

/**
 * Why a finger's touch does not paint, in the user's words, or null when the finger does paint (or
 * moves the canvas, which the person sees and so is not a mystery). Phones are the reason this is
 * asked: their stylus drivers hover forever and their panels report a nonsense dot pitch, so a phone
 * can lose every finger touch without losing a menu.
 *
 * [droppedAsPalm] is whether the palm test dropped this very touch, not whether the test is switched
 * on: a phone whose screen reports honest dots passes every fingertip through, and blaming the palm
 * test there would send the person to a switch that was never the reason. Checked on the API 35
 * emulator, where the reason was the gesture switch and the palm test was on: the first wording
 * pointed at the wrong one.
 */
fun fingerSilenceReason(
    paints: Boolean,
    moves: Boolean,
    penHovering: Boolean,
    hoverMutes: Boolean,
    droppedAsPalm: Boolean,
): String? = when {
    paints -> null
    moves -> null
    penHovering && hoverMutes ->
        "Перо наводится над холстом — палец не рисует. Разрешить: Настройки › «Заглушать палец рядом с пером»"
    droppedAsPalm ->
        "Касание широким пальцем считается ладонью и не рисует. Разрешить: Настройки › «Рисовать пальцем в обход жестов», либо «Отказ ладони»"
    else ->
        "Палец не рисует: Действия › Управление жестами › «Общие» › «Рисование пальцем» выключено. Разрешить: Настройки › «Рисовать пальцем в обход жестов»"
}

/**
 * What the screen says about its dots, in words, for the settings row that can switch the palm test
 * off: on a tablet that paints nothing the first question is whether this figure is the liar, and
 * the answer should be on the screen, not in a log the user cannot read.
 */
fun palmDiagnostics(xdpi: Float, densityDpi: Int): String = when {
    xdpi >= SANE_DPI_LOW && xdpi <= SANE_DPI_HIGH ->
        "Экран сообщает ${xdpi.roundToInt()} dpi — значение достоверно, тест ладони работает по нему."
    densityDpi >= SANE_DPI_LOW ->
        "Экран сообщает ${xdpi.roundToInt()} dpi — недостоверно; тест считает по плотности интерфейса (${densityDpi} dpi)."
    else ->
        "Плотность точек неизвестна — тест ладони не действует, пальцем рисуется всегда"
}
