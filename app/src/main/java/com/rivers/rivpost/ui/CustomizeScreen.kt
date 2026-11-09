package com.rivers.rivpost.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.data.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Choice(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickRow(title, options.firstOrNull { it.first == current }?.second ?: "") { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                for ((key, label) in options) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(key); open = false }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == current, onClick = { onPick(key); open = false })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(t("Close")) } },
    )
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, autoAtMin: Boolean = false, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title + ": " + if (autoAtMin && value <= range.first) t("Auto") else value.toString(), fontSize = 16.sp)
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

/** One place to change every part of the look. */
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val style = LocalStyle.current
    var reset by remember { mutableStateOf(false) }
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) {
                try {
                    val out = java.io.File(ctx.filesDir, "wallpaper_" + System.currentTimeMillis() + ".jpg")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                } catch (_: Throwable) { null }
            }
            if (f != null) {
                Prefs.wallpaper = f.absolutePath
                Prefs.wallAccent = withContext(Dispatchers.IO) { wallAccentFrom(f.absolutePath) }
            }
        }
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) {
                try {
                    val out = java.io.File(ctx.filesDir, "font_" + System.currentTimeMillis() + ".ttf")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    androidx.compose.ui.text.font.Font(out)
                    android.graphics.Typeface.createFromFile(out)
                    out
                } catch (_: Throwable) { null }
            }
            if (f != null) Prefs.fontPath = f.absolutePath else toast(ctx, t("That file isn't a font"))
        }
    }
    val pickDrawer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val f = withContext(Dispatchers.IO) {
                try {
                    val out = java.io.File(ctx.filesDir, "drawer_" + System.currentTimeMillis() + ".jpg")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                } catch (_: Throwable) { null }
            }
            if (f != null) Prefs.drawerImage = f.absolutePath
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Ic.Back, null) } },
            title = { Text(t("Customize"), fontWeight = FontWeight.SemiBold) },
            actions = { TextButton(onClick = { reset = true }) { Text(t("Reset")) } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            SectionTitle(t("Style presets"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((key, label) in listOf("riv" to "Riv", "wave" to "Wave", "leaf" to "Leaf")) {
                    val sel = Prefs.style == key
                    val preview = when (key) { "wave" -> Color(0xFF2B5278); "leaf" -> Color(0xFF005C4B); else -> style.accent }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(style.navBar)
                            .border(if (sel) 2.dp else 0.dp, style.accent, RoundedCornerShape(18.dp))
                            .clickable { Prefs.style = key }.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(preview))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(60.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(style.navBarFg.copy(alpha = 0.25f)))
                        Spacer(Modifier.height(8.dp))
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Choice(t("Theme"), listOf("dark" to t("Dark"), "light" to t("Light"), "system" to t("Follow phone")), Prefs.theme) { Prefs.theme = it }

            SwitchRow(t("Pure black (OLED)"), t("True #000000 background in dark mode"), Prefs.oled) { Prefs.oled = it }
            SwitchRow(t("Flat mode"), t("No rounded cards, borders or shadows"), Prefs.flat) { Prefs.flat = it }
            SwitchRow(t("Glass look"), t("See-through menus, drawer and bars"), Prefs.glass) { Prefs.glass = it }
            SwitchRow(t("Colors from wallpaper"), t("The accent follows your chat wallpaper"), Prefs.wallTint) { Prefs.wallTint = it }
            SwitchRow(t("Haptics"), t("Small vibrations on tabs and long-press"), Prefs.haptics) { Prefs.haptics = it }
            Choice(t("Scrolling feel"), listOf("smooth" to t("Smooth"), "normal" to t("Normal"), "snappy" to t("Snappy")), Prefs.scrollFeel) { Prefs.scrollFeel = it }
            ClickRow(t("Font"), if (Prefs.fontPath.isEmpty()) t("Default") else java.io.File(Prefs.fontPath).name) { pickFont.launch("*/*") }
            if (Prefs.fontPath.isNotEmpty()) ClickRow(t("Use the default font"), null) { Prefs.fontPath = "" }
            SwitchRow(t("Phone's own emoji"), null, Prefs.systemEmoji) { Prefs.systemEmoji = it }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Drawer"))
            Choice(t("Drawer header"), listOf("accent" to t("Accent color"), "gradient" to t("Gradient"), "avatar" to t("My blurred photo"), "image" to t("A picture")), Prefs.drawerBg) {
                Prefs.drawerBg = it
                if (it == "image") pickDrawer.launch("image/*")
            }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Bottom bar"))
            Choice(t("Bar style"), listOf("floating" to t("Floating pill"), "flat" to t("Flat, full width"), "off" to t("Hidden")), Prefs.navBar) { Prefs.navBar = it }
            Choice(t("Position"), listOf("bottom" to t("Bottom"), "top" to t("Top")), Prefs.navPos) { Prefs.navPos = it }
            SwitchRow(t("Show labels"), null, Prefs.navLabels) { Prefs.navLabels = it }
            SwitchRow(t("Contacts tab"), null, Prefs.navContacts) { Prefs.navContacts = it }
            SwitchRow(t("Calls tab"), null, Prefs.navCalls) { Prefs.navCalls = it }
            SwitchRow(t("Music tab"), null, Prefs.navMusic) { Prefs.navMusic = it }
            SwitchRow(t("Videos tab"), null, Prefs.navVideos) { Prefs.navVideos = it }
            SwitchRow(t("Profile tab"), null, Prefs.navProfile) { Prefs.navProfile = it }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Folder tabs"))
            SwitchRow(t("Show folder tabs"), null, Prefs.showFolders) { Prefs.showFolders = it }
            Choice(t("Tab style"), listOf("pills" to t("Pills"), "underline" to t("Underline"), "text" to t("Text only")), Prefs.tabsStyle) { Prefs.tabsStyle = it }
            Choice(t("Position"), listOf("top" to t("Top"), "bottom" to t("Bottom")), Prefs.tabsPos) { Prefs.tabsPos = it }
            SwitchRow(t("Unread counts on tabs"), null, Prefs.tabCounts) { Prefs.tabCounts = it }
            SwitchRow(t("Count muted chats"), null, Prefs.countMuted) { Prefs.countMuted = it }
            SwitchRow(t("Archive tab"), null, Prefs.showArchive) { Prefs.showArchive = it }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Title bar"))
            Choice(t("Title size"), listOf("large" to t("Large"), "small" to t("Small")), Prefs.titleStyle) { Prefs.titleStyle = it }
            SwitchRow(t("Show logo"), t("Off shows a ☰ button instead"), Prefs.showLogo) { Prefs.showLogo = it }
            OutlinedTextField(
                value = Prefs.appTitle, onValueChange = { Prefs.appTitle = it.take(24) }, singleLine = true,
                label = { Text(t("App title")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            SwitchRow(t("New message button"), null, Prefs.fab) { Prefs.fab = it }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Chat list"))
            Choice(t("Profile picture shape"), listOf("circle" to t("Circle"), "rounded" to t("Rounded square"), "square" to t("Square")), Prefs.avatarShape) { Prefs.avatarShape = it }
            SliderRow(t("Profile picture size"), Prefs.avatarSize, 36..72, autoAtMin = true) { Prefs.avatarSize = if (it <= 36) 0 else it }
            Choice(t("Spacing"), listOf("compact" to t("Compact"), "normal" to t("Normal"), "comfy" to t("Roomy")), Prefs.density) { Prefs.density = it }
            Choice(t("Preview lines"), listOf("1" to "1", "2" to "2"), Prefs.previewLines.toString()) { Prefs.previewLines = it.toInt() }
            SwitchRow(t("Profile pictures"), null, Prefs.showAvatars) { Prefs.showAvatars = it }
            SwitchRow(t("Media thumbnails"), null, Prefs.mediaThumbs) { Prefs.mediaThumbs = it }
            SwitchRow(t("Online dot"), null, Prefs.onlineDot) { Prefs.onlineDot = it }
            Choice(t("Dividers"), listOf("auto" to t("Auto"), "on" to t("On"), "off" to t("Off")), Prefs.dividers) { Prefs.dividers = it }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Chat screen"))
            Choice(t("Header"), listOf("floating" to t("Floating pills"), "classic" to t("Classic bar")), Prefs.headerStyle) { Prefs.headerStyle = it }
            Choice(t("Message bar"), listOf("floating" to t("Floating pill"), "classic" to t("Classic bar")), Prefs.inputStyle) { Prefs.inputStyle = it }
            SwitchRow(t("GIF button"), null, Prefs.gifButton) { Prefs.gifButton = it }
            SwitchRow(t("Pinned message bar"), null, Prefs.pinnedBar) { Prefs.pinnedBar = it }
            SwitchRow(t("Views on channel posts"), null, Prefs.showViews) { Prefs.showViews = it }
            SwitchRow(t("Share button on channel posts"), null, Prefs.shareButton) { Prefs.shareButton = it }
            Choice(t("Bubble shape"), listOf("rounded" to t("Rounded"), "pill" to t("Pill"), "square" to t("Square"), "borderless" to t("No bubble")), Prefs.bubbleShape) { Prefs.bubbleShape = it }
            SliderRow(t("Bubble roundness"), Prefs.bubbleRadius, 0..28, autoAtMin = true) { Prefs.bubbleRadius = it }
            SliderRow(t("Bubble shadow"), Prefs.bubbleShadow, 0..12) { Prefs.bubbleShadow = it }
            SliderRow(t("Bubble outline"), Prefs.bubbleBorder, 0..4) { Prefs.bubbleBorder = it }
            ColorRow(t("Outline color"), Prefs.cBorder, style.border) { Prefs.cBorder = it }
            SwitchRow(t("Bubble glow"), null, Prefs.bubbleGlow) { Prefs.bubbleGlow = it }
            Choice(t("Message spacing"), listOf("normal" to t("Normal"), "compact" to t("Compact")), Prefs.chatDensity) { Prefs.chatDensity = it }
            Choice(t("Voice waveform"), listOf("bars" to t("Bars"), "neon" to t("Neon"), "particles" to t("Particles"), "line" to t("Line")), Prefs.waveStyle) { Prefs.waveStyle = it }
            SliderRow(t("Sticker size"), Prefs.stickerSize, 100..260) { Prefs.stickerSize = it }
            SwitchRow(t("Player glow"), t("Soft light behind the album cover"), Prefs.playerGlow) { Prefs.playerGlow = it }
            Choice(t("Bubble tails"), listOf("auto" to t("Auto"), "on" to t("On"), "off" to t("Off")), Prefs.bubbleTail) { Prefs.bubbleTail = it }
            SliderRow(t("Text size"), Prefs.textSize, 13..22) { Prefs.textSize = it }
            SwitchRow(t("Double-tap to react"), null, Prefs.doubleTapReact) { Prefs.doubleTapReact = it }
            Choice(t("Quick reaction"), QuickReactions.map { it to it }, Prefs.quickReaction) { Prefs.quickReaction = it }
            ClickRow(t("Wallpaper"), if (Prefs.wallpaper.isNotEmpty()) t("Photo") else t("Color")) { pickWall.launch("image/*") }
            if (Prefs.wallpaper.isNotEmpty()) {
                SwitchRow(t("Blur wallpaper"), null, Prefs.wallBlur) { Prefs.wallBlur = it }
                SliderRow(t("Darken wallpaper"), Prefs.wallDim, 0..80) { Prefs.wallDim = it }
                ClickRow(t("Remove wallpaper"), null) { Prefs.wallpaper = "" }
            }

            HorizontalDivider(Modifier.padding(top = 10.dp))
            SectionTitle(t("Colors"))
            ColorRow(t("Accent"), Prefs.cAccent, style.accent) { Prefs.cAccent = it }
            ColorRow(t("Background"), Prefs.cBg, style.bg) { Prefs.cBg = it }
            ColorRow(t("Top bar"), Prefs.cTopBar, style.topBar) { Prefs.cTopBar = it }
            ColorRow(t("Bottom bar and pills"), Prefs.cNavBar, style.navBar) { Prefs.cNavBar = it }
            ColorRow(t("Selected tab"), Prefs.cPill, style.pill) { Prefs.cPill = it }
            ColorRow(t("Unread badge"), Prefs.cBadge, style.badge) { Prefs.cBadge = it }
            ColorRow(t("Chat names"), Prefs.cTitle, style.title) { Prefs.cTitle = it }
            ColorRow(t("My bubbles"), Prefs.cOut, style.outBubble) { Prefs.cOut = it }
            ColorRow(t("My text"), Prefs.cOutText, style.outText) { Prefs.cOutText = it }
            ColorRow(t("Their bubbles"), Prefs.cIn, style.inBubble) { Prefs.cIn = it }
            ColorRow(t("Their text"), Prefs.cInText, style.inText) { Prefs.cInText = it }
            ColorRow(t("Chat background"), Prefs.cWallpaper, style.chatBg) { Prefs.cWallpaper = it }
            ColorRow(t("Links"), Prefs.cLink, style.link) { Prefs.cLink = it }
            ClickRow(t("Reset colors"), null) { Prefs.resetColors() }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (reset) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text(t("Reset the look?")) },
        text = { Text(t("Bars, list, chat screen and colors go back to their defaults.")) },
        confirmButton = { TextButton(onClick = { Prefs.resetLook(); reset = false }) { Text(t("Reset")) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(t("Cancel")) } },
    )
}
