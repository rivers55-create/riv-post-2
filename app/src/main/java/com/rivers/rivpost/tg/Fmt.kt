package com.rivers.rivpost.tg

import com.rivers.rivpost.data.t
import dev.g000sha256.tdl.dto.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    /** Short text for a message (chat list and notifications). */
    fun preview(a: TgAccount, m: Message?): String {
        if (m == null) return ""
        return previewContent(a, m.content, m)
    }

    fun previewContent(a: TgAccount, content: MessageContent, m: Message?): String {
        return when (val c = content) {
            is MessageText -> c.text.text
            is MessagePhoto -> withCaption("🖼 " + t("Photo"), c.caption)
            is MessageVideo -> withCaption("🎬 " + t("Video"), c.caption)
            is MessageAnimation -> withCaption("GIF", c.caption)
            is MessageDocument -> withCaption("📄 " + c.document.fileName.ifBlank { t("File") }, c.caption)
            is MessageAudio -> "🎵 " + listOf(c.audio.performer, c.audio.title).filter { it.isNotBlank() }
                .joinToString(" – ").ifBlank { c.audio.fileName.ifBlank { t("Music") } }
            is MessageVoiceNote -> "🎤 " + t("Voice message") + " " + duration(c.voiceNote.duration)
            is MessageVideoNote -> "📹 " + t("Video message")
            is MessageSticker -> c.sticker.emoji + " " + t("Sticker")
            is MessageLocation -> "📍 " + t("Location")
            is MessageContact -> "👤 " + t("Contact")
            is MessagePoll -> "📊 " + t("Poll")
            is MessageCall -> "📞 " + t("Call")
            is MessageChatAddMembers -> t("New members joined")
            is MessageChatJoinByLink -> t("Joined the group")
            is MessageChatDeleteMember -> t("Left the group")
            is MessageChatChangeTitle -> t("Changed the name") + ": " + c.title
            is MessageChatChangePhoto -> t("Changed the photo")
            is MessagePinMessage -> "📌 " + t("Pinned a message")
            is MessageContactRegistered -> t("Joined Telegram")
            is MessageBasicGroupChatCreate -> t("Created the group")
            is MessageSupergroupChatCreate -> t("Created the group")
            else -> t("Message")
        }
    }

    private fun withCaption(label: String, cap: FormattedText): String =
        if (cap.text.isBlank()) label else label + " " + cap.text

    fun isService(m: Message): Boolean = when (m.content) {
        is MessageChatAddMembers, is MessageChatJoinByLink, is MessageChatDeleteMember,
        is MessageChatChangeTitle, is MessageChatChangePhoto, is MessagePinMessage,
        is MessageContactRegistered, is MessageBasicGroupChatCreate, is MessageSupergroupChatCreate -> true
        else -> false
    }

    fun duration(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    fun size(bytes: Long): String {
        val b = bytes.coerceAtLeast(0).toDouble()
        return when {
            b >= 1 shl 30 -> "%.1f GB".format(b / (1 shl 30))
            b >= 1 shl 20 -> "%.1f MB".format(b / (1 shl 20))
            b >= 1 shl 10 -> "%.0f KB".format(b / (1 shl 10))
            else -> "%.0f B".format(b)
        }
    }

    fun clock(unix: Int): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(unix * 1000L))

    /** Time for the chat list: today → clock, this week → day, else date. */
    fun listTime(unix: Int): String {
        if (unix <= 0) return ""
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = unix * 1000L }
        return when {
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) -> clock(unix)
            now.timeInMillis - c.timeInMillis < 6L * 24 * 3600 * 1000 -> t(SimpleDateFormat("EEE", Locale.US).format(c.time))
            else -> SimpleDateFormat("dd/MM/yy", Locale.US).format(c.time)
        }
    }

    fun dayLabel(unix: Int): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = unix * 1000L }
        val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
        if (sameYear && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) return t("Today")
        if (sameYear && now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) == 1) return t("Yesterday")
        return SimpleDateFormat(if (sameYear) "d MMMM" else "d MMMM yyyy", Locale.US).format(c.time)
    }

    fun dayKey(unix: Int): Int {
        val c = Calendar.getInstance().apply { timeInMillis = unix * 1000L }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    fun lastSeen(u: User?): String = lastSeenStatus(u?.status, u)

    fun lastSeenStatus(status: UserStatus?, u: User?): String {
        if (u != null && u.type is UserTypeBot) return t("bot")
        return when (val s = status) {
            is UserStatusOnline -> t("online")
            is UserStatusOffline -> t("last seen") + " " + listTime(s.wasOnline)
            is UserStatusRecently -> t("last seen recently")
            is UserStatusLastWeek -> t("last seen within a week")
            is UserStatusLastMonth -> t("last seen within a month")
            null -> ""
            else -> t("last seen a long time ago")
        }
    }
}
