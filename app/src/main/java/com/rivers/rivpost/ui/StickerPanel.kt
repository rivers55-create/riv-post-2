package com.rivers.rivpost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rivers.rivpost.data.t
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Animation
import dev.g000sha256.tdl.dto.InputAnimation
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.InputMessageAnimation
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageSticker
import dev.g000sha256.tdl.dto.InputSticker
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.StickerTypeRegular
import kotlinx.coroutines.launch

/** Cached sticker sets so the panel opens instantly the second time. */
private object StickerCache {
    var acc = -1
    var recent: List<Sticker> = emptyList()
    var favorites: List<Sticker> = emptyList()
    var sets: List<Pair<String, List<Sticker>>> = emptyList()
    var gifs: List<Animation> = emptyList()
}

private const val EMOJI =
    "😀😃😄😁😆😅😂🤣🥲☺️😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🥸🤩🥳😏😒😞😔😟😕🙁☹️😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🫣🤗🫡🤔🫢🤭🤫🤥😶😐😑😬🫨🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽👾🤖🎃😺😸😹😻😼😽🙀😿😾" +
    "👋🤚🖐️✋🖖👌🤌🤏✌️🤞🫰🤟🤘🤙👈👉👆🖕👇☝️🫵👍👎✊👊🤛🤜👏🙌🫶👐🤲🤝🙏✍️💅🤳💪🦾🦵🦶👂🦻👃🧠🫀🫁🦷🦴👀👁️👅👄🫦" +
    "❤️🧡💛💚💙💜🖤🤍🤎💔❤️‍🔥❤️‍🩹❣️💕💞💓💗💖💘💝💟☮️✝️☪️🕉️☸️✡️🔯🕎☯️☦️🛐⛎♈♉♊♋♌♍♎♏♐♑♒♓🆔⚛️" +
    "🔥✨🌟💫⭐🌈☀️🌤️⛅🌧️⛈️🌩️❄️☃️⛄🌊💧💦☔🍏🍎🍐🍊🍋🍌🍉🍇🍓🫐🍈🍒🍑🥭🍍🥥🥝🍅🍆🥑🥦🥬🥒🌶️🫑🌽🥕🧄🧅🥔🍠🥐🥯🍞🥖🥨🧀🥚🍳🧈🥞🧇🥓🥩🍗🍖🌭🍔🍟🍕🫓🥪🥙🧆🌮🌯🥗🥘🍝🍜🍲🍛🍣🍱🥟🍤🍙🍚🍘🍥🥮🍢🍡🍧🍨🍦🥧🧁🍰🎂🍮🍭🍬🍫🍿🍩🍪☕🍵🧃🥤🧋🍺🍻🥂🍷🥃🍸🍹" +
    "⚽🏀🏈⚾🥎🎾🏐🏉🥏🎱🏓🏸🏒🥊🥋⛳🎣🎽🎿🏆🥇🥈🥉🏅🎖️🎗️🎫🎟️🎪🎭🎨🎬🎤🎧🎼🎹🥁🎷🎺🎸🪕🎻🎲♟️🎯🎳🎮🎰🧩" +
    "🚗🚕🚙🚌🚎🏎️🚓🚑🚒🚐🛻🚚🚛🚜🏍️🛵🚲🛴✈️🚀🛸🚁⛵🚤🛳️🚢⚓🗺️🗽🗼🏰🏯🏟️🎡🎢🎠⛲🏖️🏝️🏜️🌋⛰️🏔️🗻🏕️🏠🏡🕌🕋⛪🏛️" +
    "⌚📱💻⌨️🖥️🖨️🖱️💾💿📷📸📹🎥📞☎️📺📻🎙️⏰⏳💡🔦🕯️💸💵💰💳💎⚖️🔧🔨⚒️🛠️⛏️🔩⚙️🔫💣🔪🗡️⚔️🛡️🔮💊💉🩸🧬🦠🧪🌡️🧹🧺🧻🚽🛁🔑🗝️🚪🛋️🛏️🎁🎈🎉🎊✉️📩📦📜📄📅📌📍✂️🔒🔓" +
    "✅☑️✔️❌❎➕➖➗✖️♾️‼️⁉️❓❔❕❗〰️💯🔟🔢#️⃣*️⃣▶️⏸️⏹️⏺️⏭️⏮️⏩⏪🔀🔁🔂◀️🔼🔽➡️⬅️⬆️⬇️↗️↘️↙️↖️↕️↔️🔄🆗🆕🆙🆒🆓🔴🟠🟡🟢🔵🟣⚫⚪🟤🔺🔻🔸🔹🔶🔷🏁🚩🎌🏴🏳️🏳️‍🌈🇮🇶🇸🇦🇦🇪🇰🇼🇶🇦🇯🇴🇸🇾🇱🇧🇪🇬🇵🇸🇹🇷🇮🇷🇺🇸🇬🇧"

private fun emojiList(): List<String> {
    val out = ArrayList<String>()
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(EMOJI)
    var start = it.first()
    var end = it.next()
    while (end != java.text.BreakIterator.DONE) {
        out.add(EMOJI.substring(start, end))
        start = end; end = it.next()
    }
    return out.distinct()
}

/** Emoji, stickers and GIFs, opened from the smiley button. */
@Composable
fun StickerPanel(acc: TgAccount, onEmoji: (String) -> Unit, onSend: (InputMessageContent) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val style = LocalStyle.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(StickerCache.acc != acc.index) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(acc) {
        if (StickerCache.acc == acc.index) return@LaunchedEffect
        loading = true
        StickerCache.recent = (acc.client.getRecentStickers(isAttached = false) as? TdlResult.Success)?.result?.stickers?.toList().orEmpty()
        StickerCache.favorites = (acc.client.getFavoriteStickers() as? TdlResult.Success)?.result?.stickers?.toList().orEmpty()
        StickerCache.gifs = (acc.client.getSavedAnimations() as? TdlResult.Success)?.result?.animations?.toList().orEmpty()
        val infos = (acc.client.getInstalledStickerSets(stickerType = StickerTypeRegular()) as? TdlResult.Success)?.result?.sets?.toList().orEmpty()
        val sets = ArrayList<Pair<String, List<Sticker>>>()
        for (i in infos) {
            val set = (acc.client.getStickerSet(setId = i.id) as? TdlResult.Success)?.result ?: continue
            sets.add(set.title to set.stickers.toList())
            if (sets.size % 4 == 0) { StickerCache.sets = sets.toList(); tick++ }
        }
        StickerCache.sets = sets
        StickerCache.acc = acc.index
        loading = false
        tick++
    }
    Column(Modifier.fillMaxWidth().height(320.dp).background(style.navBar)) {
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            for ((i, n) in listOf(t("Emoji"), t("Stickers"), "GIF").withIndex()) {
                Text(
                    n, color = if (tab == i) onColor(style.accent) else style.navBarFg, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp).clip(CircleShape).background(if (tab == i) style.accent else Color.Transparent)
                        .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        @Suppress("UNUSED_VARIABLE") val w = tick
        when (tab) {
            0 -> {
                val all = remember { emojiList() }
                var q by remember { mutableStateOf("") }
                var found by remember { mutableStateOf<List<String>?>(null) }
                LaunchedEffect(q) {
                    if (q.isBlank()) { found = null; return@LaunchedEffect }
                    kotlinx.coroutines.delay(250)
                    found = (acc.client.searchEmojis(text = q, inputLanguageCodes = arrayOf("en", "ar")) as? TdlResult.Success)?.result?.emojiKeywords?.map { it.emoji }?.distinct().orEmpty()
                }
                val list = found ?: all
                androidx.compose.foundation.text.BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = style.navBarFg, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(style.accent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(style.navBarFg.copy(alpha = 0.08f)).padding(10.dp),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text("🔍 " + t("Search emoji"), color = style.navBarFg.copy(alpha = 0.5f), fontSize = 15.sp); inner() } },
                )
                LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize()) {
                    if (found == null) item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(4.dp)) {
                            Text(t("Games") + ":", color = style.navBarFg.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                            for (d in listOf("🎲", "🎯", "🏀", "⚽", "🎳", "🎰")) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(style.accent.copy(alpha = 0.15f)).clickable {
                                    onSend(dev.g000sha256.tdl.dto.InputMessageDice(emoji = d, clearDraft = false))
                                }, contentAlignment = Alignment.Center) { Text(d, fontSize = 22.sp) }
                                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                            }
                        }
                    }
                    items(list) { e ->
                        Box(Modifier.size(44.dp).clickable { onEmoji(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 26.sp) }
                    }
                }
            }
            1 -> {
                val grid = rememberLazyGridState()
                val sections = buildList {
                    if (StickerCache.favorites.isNotEmpty()) add("⭐ " + t("Favorites") to StickerCache.favorites)
                    if (StickerCache.recent.isNotEmpty()) add(t("Recent") to StickerCache.recent)
                    addAll(StickerCache.sets)
                }
                // index of each section header in the grid, for the set icons row
                val starts = remember(sections.size, tick) {
                    var i = 0
                    sections.map { s -> val at = i; i += 1 + s.second.size; at }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 6.dp)) {
                    items(sections.indices.toList()) { si ->
                        val first = sections[si].second.firstOrNull()
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { scope.launch { grid.scrollToItem(starts[si]) } }.padding(4.dp)) {
                            if (first != null) StickerThumb(acc, first, Modifier.fillMaxSize())
                        }
                    }
                }
                if (loading && sections.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else LazyVerticalGrid(GridCells.Adaptive(72.dp), Modifier.fillMaxSize(), state = grid) {
                    for ((title, list) in sections) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(title, color = style.navBarFg.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                        }
                        items(list) { s ->
                            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(
                                onClick = { onSend(InputMessageSticker(sticker = InputSticker(sticker = InputFileId(id = s.sticker.id), thumbnail = null, width = s.width, height = s.height), emoji = s.emoji)) },
                                onLongClick = {
                                    val fav = StickerCache.favorites.any { it.sticker.id == s.sticker.id }
                                    scope.launch {
                                        if (fav) acc.client.removeFavoriteSticker(sticker = InputFileId(id = s.sticker.id)) else acc.client.addFavoriteSticker(sticker = InputFileId(id = s.sticker.id))
                                        StickerCache.favorites = (acc.client.getFavoriteStickers() as? TdlResult.Success)?.result?.stickers?.toList().orEmpty()
                                        tick++
                                        toast(ctx, if (fav) t("Removed from favorites") else t("Added to favorites"))
                                    }
                                },
                            ).padding(4.dp)) { StickerThumb(acc, s, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
            else -> {
                if (StickerCache.gifs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator() else Text(t("No saved GIFs"), color = style.navBarFg.copy(alpha = 0.7f))
                } else LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize()) {
                    items(StickerCache.gifs) { a ->
                        val th = rememberFile(acc, a.thumbnail?.file, autoDownload = true)
                        Box(Modifier.aspectRatio(1.3f).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22000000)).clickable {
                            onSend(
                                InputMessageAnimation(
                                    animation = InputAnimation(animation = InputFileId(id = a.animation.id), thumbnail = null, addedStickerFileIds = IntArray(0), duration = a.duration, width = a.width, height = a.height),
                                    caption = null, showCaptionAboveMedia = false, hasSpoiler = false,
                                ),
                            )
                        }) {
                            if (th != null && th.local.isDownloadingCompleted) AsyncImage(model = java.io.File(th.local.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerThumb(acc: TgAccount, s: Sticker, modifier: Modifier) {
    val f = if (s.format is StickerFormatWebp) s.sticker else s.thumbnail?.file
    val cur = rememberFile(acc, f, autoDownload = true)
    if (cur != null && cur.local.isDownloadingCompleted && cur.local.path.isNotEmpty()) {
        AsyncImage(model = java.io.File(cur.local.path), contentDescription = s.emoji, modifier = modifier)
    } else Box(modifier, contentAlignment = Alignment.Center) { Text(s.emoji, fontSize = 26.sp) }
}
