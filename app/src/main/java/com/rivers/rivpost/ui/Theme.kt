package com.rivers.rivpost.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rivers.rivpost.data.Prefs

val Accents = listOf(
    "Purple" to Color(0xFF7C4DFF),
    "Violet" to Color(0xFFAB47BC),
    "Blue" to Color(0xFF2F80ED),
    "Teal" to Color(0xFF00A884),
    "Green" to Color(0xFF43A047),
    "Orange" to Color(0xFFFF8A3D),
    "Pink" to Color(0xFFE5508A),
    "Red" to Color(0xFFE53935),
)

fun accentColor(): Color = when {
    Prefs.cAccent != 0 -> Color(Prefs.cAccent)
    Prefs.wallTint && Prefs.wallAccent != 0 && Prefs.wallpaper.isNotEmpty() -> Color(Prefs.wallAccent)
    else -> Accents.getOrElse(Prefs.accent) { Accents[0] }.second
}

fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

private fun pick(custom: Int, auto: Color): Color = if (custom != 0) Color(custom) else auto

/** Everything the look depends on: colors, bars, bubbles and list layout. */
data class StyleSpec(
    val name: String,
    val dark: Boolean,
    val accent: Color,
    val bg: Color,
    val surface: Color,
    val topBar: Color,
    val topBarFg: Color,
    val navBar: Color,
    val navBarFg: Color,
    val pill: Color,
    val pillFg: Color,
    val badge: Color,
    val badgeMuted: Color,
    val title: Color,
    val outBubble: Color,
    val inBubble: Color,
    val outText: Color,
    val inText: Color,
    val outMeta: Color,
    val inMeta: Color,
    val chatBg: Color,
    val link: Color,
    val radius: Dp,
    val tail: Boolean,
    val avatar: Dp,
    val avatarShape: Shape,
    val dividers: Boolean,
    val rowPad: Dp,
    val previewLines: Int,
    val flat: Boolean = false,
    val glass: Boolean = false,
    val border: Color = Color.Transparent,
    val card: Dp = 22.dp,
)

val LocalStyle = staticCompositionLocalOf<StyleSpec> { error("no style") }

fun isDark(system: Boolean): Boolean = when (Prefs.theme) {
    "light" -> false
    "system" -> system
    "schedule" -> nightNow()
    else -> true
}

fun makeStyle(dark: Boolean): StyleSpec {
    val a = accentColor()
    val oled = dark && Prefs.oled
    val bg = pick(Prefs.cBg, if (oled) Color.Black else if (dark) Color(0xFF120B1C) else Color(0xFFFAF8FD))
    val surface = if (oled) Color(0xFF0A0A0A) else if (dark) lerp(bg, Color.White, 0.06f) else Color.White
    val panel = if (oled) Color(0xFF111111) else if (dark) lerp(bg, Color.White, 0.09f) else lerp(bg, Color.Black, 0.04f)

    // style defaults
    var out: Color; var inn: Color; var outT: Color; var inT: Color; var outM: Color; var inM: Color
    var wall: Color; var radius: Dp; var tail: Boolean; var dividers: Boolean; var avatar: Dp
    var badge = a
    var topBar = bg
    when (Prefs.style) {
        "wave" -> {
            out = if (dark) Color(0xFF2B5278) else Color(0xFFEFFDDE)
            inn = if (dark) Color(0xFF182533) else Color.White
            outT = if (dark) Color.White else Color(0xFF111111); inT = outT
            outM = if (dark) Color(0xFF7DA8D3) else Color(0xFF5DAE53)
            inM = if (dark) Color(0xFF6D7F8F) else Color(0xFF9AA4AD)
            wall = if (dark) Color(0xFF0E1621) else Color(0xFFD6E2EC)
            radius = 16.dp; tail = true; dividers = true; avatar = 54.dp
        }
        "leaf" -> {
            out = if (dark) Color(0xFF005C4B) else Color(0xFFD9FDD3)
            inn = if (dark) Color(0xFF202C33) else Color.White
            outT = if (dark) Color(0xFFE9EDEF) else Color(0xFF111B21); inT = outT
            outM = if (dark) Color(0xFF8BB8AE) else Color(0xFF667781)
            inM = if (dark) Color(0xFF8696A0) else Color(0xFF667781)
            wall = if (dark) Color(0xFF0B141A) else Color(0xFFEFEAE2)
            radius = 10.dp; tail = true; dividers = false; avatar = 50.dp
            if (Prefs.accent == 0 && Prefs.cAccent == 0) badge = Color(0xFF25D366)
            if (!dark) topBar = Color(0xFF008069)
        }
        else -> {
            out = a; inn = if (oled) Color(0xFF1A1A1A) else panel
            outT = onColor(a); inT = if (dark) Color(0xFFECE6F5) else Color(0xFF1D1726)
            outM = outT.copy(alpha = 0.75f)
            inM = if (dark) Color(0xFF9D92AE) else Color(0xFF7A708A)
            wall = bg
            radius = 20.dp; tail = false; dividers = false; avatar = 52.dp
        }
    }
    out = pick(Prefs.cOut, out)
    inn = pick(Prefs.cIn, inn)
    if (Prefs.cOutText != 0) { outT = Color(Prefs.cOutText); outM = outT.copy(alpha = 0.7f) }
    if (Prefs.cInText != 0) { inT = Color(Prefs.cInText); inM = inT.copy(alpha = 0.6f) }
    topBar = pick(Prefs.cTopBar, topBar)
    val navBar = pick(Prefs.cNavBar, panel)
    val pill = pick(Prefs.cPill, a.copy(alpha = 0.22f).compositeOver(navBar))
    val avatarShape: Shape = when (Prefs.avatarShape) {
        "rounded" -> RoundedCornerShape(30)
        "square" -> RectangleShape
        else -> CircleShape
    }
    val size = if (Prefs.avatarSize > 0) Prefs.avatarSize.dp else avatar
    val rowPad = when (Prefs.density) { "compact" -> 5.dp; "comfy" -> 13.dp; else -> 9.dp }
    return StyleSpec(
        name = Prefs.style, dark = dark, accent = a, bg = bg, surface = surface,
        topBar = topBar, topBarFg = onColor(topBar),
        navBar = navBar, navBarFg = onColor(navBar),
        pill = pill, pillFg = if (Prefs.cPill != 0) onColor(pill) else a,
        badge = pick(Prefs.cBadge, badge), badgeMuted = if (dark) Color(0xFF5A5466) else Color(0xFFB0A8BC),
        title = pick(Prefs.cTitle, if (dark) Color(0xFFF2EEF8) else Color(0xFF1D1726)),
        outBubble = out, inBubble = inn, outText = outT, inText = inT, outMeta = outM, inMeta = inM,
        chatBg = pick(Prefs.cWallpaper, wall),
        link = pick(Prefs.cLink, if (dark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)),
        radius = if (Prefs.bubbleRadius > 0) Prefs.bubbleRadius.dp else radius,
        tail = when (Prefs.bubbleTail) { "on" -> true; "off" -> false; else -> tail },
        avatar = size, avatarShape = avatarShape,
        dividers = when (Prefs.dividers) { "on" -> true; "off" -> false; else -> dividers },
        rowPad = rowPad, previewLines = Prefs.previewLines.coerceIn(1, 2),
        flat = Prefs.flat, glass = Prefs.glass && !Prefs.flat,
        border = pick(Prefs.cBorder, a),
        card = if (Prefs.flat) 0.dp else 22.dp,
    )
}

private fun Color.compositeOver(under: Color): Color {
    val a = alpha
    return Color(
        red = red * a + under.red * (1 - a),
        green = green * a + under.green * (1 - a),
        blue = blue * a + under.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun RivTheme(content: @Composable () -> Unit) {
    val tickState = androidx.compose.runtime.produceState(0) { while (true) { kotlinx.coroutines.delay(60_000); value++ } }
    @Suppress("UNUSED_VARIABLE") val tick = tickState.value
    val dark = isDark(isSystemInDarkTheme())
    val st = makeStyle(dark)
    val a = st.accent
    val bg = st.bg
    val scheme = if (dark) darkColorScheme(
        primary = a, onPrimary = onColor(a), secondary = a, primaryContainer = a.copy(alpha = 0.35f).compositeOver(bg),
        background = bg, onBackground = Color(0xFFECE6F5),
        surface = st.surface, onSurface = Color(0xFFECE6F5),
        surfaceVariant = lerp(bg, Color.White, 0.1f), onSurfaceVariant = Color(0xFFB9AEC9),
        surfaceContainer = lerp(bg, Color.White, 0.07f), surfaceContainerHigh = lerp(bg, Color.White, 0.1f),
        surfaceContainerHighest = lerp(bg, Color.White, 0.14f), surfaceContainerLow = lerp(bg, Color.White, 0.04f),
        outline = Color(0xFF5A4E6B), outlineVariant = Color(0xFF3A2F4A),
    ) else lightColorScheme(
        primary = a, onPrimary = onColor(a), secondary = a, primaryContainer = a.copy(alpha = 0.25f).compositeOver(bg),
        background = bg, onBackground = Color(0xFF1D1726),
        surface = st.surface, onSurface = Color(0xFF1D1726),
        surfaceVariant = lerp(bg, Color.Black, 0.05f), onSurfaceVariant = Color(0xFF5E5470),
        surfaceContainer = lerp(bg, Color.Black, 0.03f), surfaceContainerHigh = lerp(bg, Color.Black, 0.06f),
        surfaceContainerHighest = lerp(bg, Color.Black, 0.09f), surfaceContainerLow = lerp(bg, Color.Black, 0.015f),
        outline = Color(0xFF9C92AC), outlineVariant = Color(0xFFDDD5E8),
    )
    MaterialTheme(colorScheme = scheme, typography = rivTypography()) {
        CompositionLocalProvider(LocalStyle provides st) {
            content()
        }
    }
}

private var cachedFont: Pair<String, androidx.compose.ui.text.font.FontFamily?>? = null

private fun fontFamily(): androidx.compose.ui.text.font.FontFamily? {
    val p = Prefs.fontPath
    cachedFont?.let { if (it.first == p) return it.second }
    val ff = if (p.isEmpty() || !java.io.File(p).exists()) null else try {
        androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(java.io.File(p)))
    } catch (_: Throwable) { null }
    cachedFont = p to ff
    return ff
}

fun rivTypography(): androidx.compose.material3.Typography {
    val b = androidx.compose.material3.Typography()
    val ff = fontFamily()
    val plat = if (Prefs.systemEmoji) androidx.compose.ui.text.PlatformTextStyle(
        emojiSupportMatch = androidx.compose.ui.text.EmojiSupportMatch.None
    ) else null
    fun f(t: androidx.compose.ui.text.TextStyle) = t.copy(fontFamily = ff ?: t.fontFamily, platformStyle = plat ?: t.platformStyle, textDirection = androidx.compose.ui.text.style.TextDirection.Content)
    return androidx.compose.material3.Typography(
        displayLarge = f(b.displayLarge), displayMedium = f(b.displayMedium), displaySmall = f(b.displaySmall),
        headlineLarge = f(b.headlineLarge), headlineMedium = f(b.headlineMedium), headlineSmall = f(b.headlineSmall),
        titleLarge = f(b.titleLarge), titleMedium = f(b.titleMedium), titleSmall = f(b.titleSmall),
        bodyLarge = f(b.bodyLarge), bodyMedium = f(b.bodyMedium), bodySmall = f(b.bodySmall),
        labelLarge = f(b.labelLarge), labelMedium = f(b.labelMedium), labelSmall = f(b.labelSmall),
    )
}

/** Menus, drawer and sheets: see-through when "glass" is on. */
@Composable
fun glassColor(base: Color): Color = if (LocalStyle.current.glass) base.copy(alpha = 0.78f) else base
