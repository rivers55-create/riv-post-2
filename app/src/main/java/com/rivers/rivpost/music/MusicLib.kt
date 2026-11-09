package com.rivers.rivpost.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rivers.rivpost.data.Prefs
import com.rivers.rivpost.tg.TgAccount
import dev.g000sha256.tdl.dto.SearchMessagesFilterAudio
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument

/** The music library: every song in your chats (or only the chats you picked). */
object MusicLib {
    var songs by mutableStateOf(listOf<Track>())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadedFor = -1
        private set

    fun isSource(chatId: Long): Boolean = chatId in Prefs.idSet(Prefs.musicSources)

    fun toggleSource(chatId: Long) {
        val s = Prefs.idSet(Prefs.musicSources).toMutableSet()
        if (!s.remove(chatId)) s.add(chatId)
        Prefs.musicSources = s.joinToString(",")
    }

    suspend fun load(a: TgAccount, force: Boolean = false) {
        if (loading || (!force && loadedFor == a.index && songs.isNotEmpty())) return
        loading = true
        try {
            val out = ArrayList<Track>()
            val sources = Prefs.idSet(Prefs.musicSources)
            if (sources.isEmpty()) {
                var offset = ""
                for (round in 0 until 20) {
                    val found = a.searchAll(SearchMessagesFilterAudio(), "", offset) ?: break
                    if (found.messages.isEmpty()) break
                    for (m in found.messages) Track.from(a.index, m)?.let { out.add(it) }
                    offset = found.nextOffset
                    if (offset.isEmpty()) break
                    if (round % 3 == 2) songs = out.distinctBy { it.key }
                }
                // music sent as files (FLAC etc.)
                var off2 = ""
                for (round in 0 until 5) {
                    val found = a.searchAll(SearchMessagesFilterDocument(), "", off2) ?: break
                    if (found.messages.isEmpty()) break
                    for (m in found.messages) Track.from(a.index, m)?.let { out.add(it) }
                    off2 = found.nextOffset
                    if (off2.isEmpty()) break
                }
            } else {
                for (chatId in sources) out.addAll(Music.chatTracks(a, chatId))
            }
            songs = out.distinctBy { it.key }.sortedByDescending { it.date }
            loadedFor = a.index
        } finally {
            loading = false
        }
    }

    fun artists(): List<Pair<String, List<Track>>> =
        songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }

    fun byChat(): List<Pair<Long, List<Track>>> =
        songs.groupBy { it.chatId }.toList().sortedByDescending { it.second.size }

    fun favorites(): List<Track> = songs.filter { Music.isFavorite(it) }
}
