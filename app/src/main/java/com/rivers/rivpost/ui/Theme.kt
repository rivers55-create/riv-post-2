package com.rivers.rivpost.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

fun accentColor(): Color = Accents.getOrElse(Prefs.accent) { Accents[0] }.second

/** What a UI style changes: bubbles, chat background, bars and list look. */
data class StyleSpec(
    val name: String,
    val dark: Boolean,
    val accent: Color,
    val outBubble: Color,
    val inBubble: Color,
    val outText: Color,
    val inText: Color,
    val outMeta: Color,
    val inMeta: Color,
    val chatBg: Color,
    val radius: Dp,
    val tail: Boolean,
    val filledBar: Boolean,
    val dividers: Boolean,
    val avatar: Dp,
    val unreadBadge: Color,
)

val LocalStyle = staticCompositionLocalOf<StyleSpec> { error("no style") }

fun isDark(system: Boolean): Boolean = when (Prefs.theme) {
    "light" -> false
    "system" -> system
    else -> true
}

fun makeStyle(dark: Boolean): StyleSpec {
    val a = accentColor()
    return when (Prefs.style) {
        "wave" -> StyleSpec(
            name = "wave", dark = dark, accent = a,
            outBubble = if (dark) Color(0xFF2B5278) else Color(0xFFEFFDDE),
            inBubble = if (dark) Color(0xFF182533) else Color.White,
            outText = if (dark) Color.White else Color(0xFF111111),
            inText = if (dark) Color.White else Color(0xFF111111),
            outMeta = if (dark) Color(0xFF7DA8D3) else Color(0xFF5DAE53),
            inMeta = if (dark) Color(0xFF6D7F8F) else Color(0xFF9AA4AD),
            chatBg = if (dark) Color(0xFF0E1621) else Color(0xFFD6E2EC),
            radius = 16.dp, tail = true, filledBar = false, dividers = true, avatar = 54.dp,
            unreadBadge = a,
        )
        "leaf" -> StyleSpec(
            name = "leaf", dark = dark, accent = a,
            outBubble = if (dark) Color(0xFF005C4B) else Color(0xFFD9FDD3),
            inBubble = if (dark) Color(0xFF202C33) else Color.White,
            outText = if (dark) Color(0xFFE9EDEF) else Color(0xFF111B21),
            inText = if (dark) Color(0xFFE9EDEF) else Color(0xFF111B21),
            outMeta = if (dark) Color(0xFF8BB8AE) else Color(0xFF667781),
            inMeta = if (dark) Color(0xFF8696A0) else Color(0xFF667781),
            chatBg = if (dark) Color(0xFF0B141A) else Color(0xFFEFEAE2),
            radius = 10.dp, tail = true, filledBar = !dark, dividers = false, avatar = 50.dp,
            unreadBadge = Color(0xFF25D366).takeIf { Prefs.accent == 0 } ?: a,
        )
        else -> StyleSpec(
            name = "riv", dark = dark, accent = a,
            outBubble = a,
            inBubble = if (dark) Color(0xFF241A33) else Color(0xFFEDE7F6),
            outText = Color.White,
            inText = if (dark) Color(0xFFECE6F5) else Color(0xFF1D1726),
            outMeta = Color(0xCCFFFFFF),
            inMeta = if (dark) Color(0xFF9D92AE) else Color(0xFF7A708A),
            chatBg = if (dark) Color(0xFF120B1C) else Color(0xFFFAF8FD),
            radius = 20.dp, tail = false, filledBar = false, dividers = false, avatar = 52.dp,
            unreadBadge = a,
        )
    }
}

@Composable
fun RivTheme(content: @Composable () -> Unit) {
    val dark = isDark(isSystemInDarkTheme())
    val a = accentColor()
    val scheme = if (dark) darkColorScheme(
        primary = a,
        onPrimary = Color.White,
        secondary = a,
        background = Color(0xFF120B1C),
        onBackground = Color(0xFFECE6F5),
        surface = Color(0xFF1A1226),
        onSurface = Color(0xFFECE6F5),
        surfaceVariant = Color(0xFF241A33),
        onSurfaceVariant = Color(0xFFB9AEC9),
        surfaceContainer = Color(0xFF1E1529),
        surfaceContainerHigh = Color(0xFF241A33),
        surfaceContainerHighest = Color(0xFF2B2040),
        surfaceContainerLow = Color(0xFF170F22),
        outline = Color(0xFF5A4E6B),
        outlineVariant = Color(0xFF3A2F4A),
    ) else lightColorScheme(
        primary = a,
        onPrimary = Color.White,
        secondary = a,
        background = Color(0xFFFAF8FD),
        onBackground = Color(0xFF1D1726),
        surface = Color.White,
        onSurface = Color(0xFF1D1726),
        surfaceVariant = Color(0xFFF0EAF8),
        onSurfaceVariant = Color(0xFF5E5470),
        surfaceContainer = Color(0xFFF4EFFA),
        surfaceContainerHigh = Color(0xFFEDE7F6),
        surfaceContainerHighest = Color(0xFFE6DFF1),
        surfaceContainerLow = Color(0xFFF8F5FC),
        outline = Color(0xFF9C92AC),
        outlineVariant = Color(0xFFDDD5E8),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalStyle provides makeStyle(dark)) {
            content()
        }
    }
}
