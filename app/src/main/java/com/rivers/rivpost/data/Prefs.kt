package com.rivers.rivpost.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** All settings. Each value is Compose state, so the UI updates when it changes. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(c: Context) {
        sp = c.getSharedPreferences("riv_settings", Context.MODE_PRIVATE)
    }

    // Telegram API keys (entered by the user, kept only on this phone)
    var apiId by IntPref("api_id", 0)
    var apiHash by StrPref("api_hash", "")
    val hasKeys: Boolean get() = apiId > 0 && apiHash.length >= 16

    // Look
    var lang by StrPref("lang", "system")          // system / en / ar
    var theme by StrPref("theme", "dark")          // dark / light / system
    var accent by IntPref("accent", 0)
    var style by StrPref("style", "riv")           // riv / wave / leaf
    var icon by StrPref("icon", "purple")          // purple / black / blue
    var textSize by IntPref("text_size", 16)
    var showFolders by BoolPref("folders", true)
    var showAvatars by BoolPref("avatars", true)

    // Chats
    var autoPhotos by BoolPref("auto_photos", true)
    var enterSends by BoolPref("enter_sends", false)
    var showArchive by BoolPref("show_archive", true)

    // Notifications
    var background by BoolPref("background", true)
    var notifPreview by BoolPref("notif_preview", true)
    var notifGroups by BoolPref("notif_groups", true)
    var notifChannels by BoolPref("notif_channels", false)

    // Privacy
    var lock by BoolPref("lock", false)
    var pinHash by StrPref("pin_hash", "")
    var fingerprint by BoolPref("fingerprint", true)
    var lockDelay by IntPref("lock_delay", 0)       // seconds away before locking
    var secureScreen by BoolPref("secure_screen", false)

    // Proxy
    var proxyOn by BoolPref("proxy_on", false)
    var proxyType by StrPref("proxy_type", "socks5") // socks5 / mtproto
    var proxyHost by StrPref("proxy_host", "")
    var proxyPort by IntPref("proxy_port", 1080)
    var proxyUser by StrPref("proxy_user", "")
    var proxyPass by StrPref("proxy_pass", "")
    var proxySecret by StrPref("proxy_secret", "")


    // ---------- Customize: bars ----------
    var navBar by StrPref("nav_bar", "floating")      // floating / flat / off
    var navPos by StrPref("nav_pos", "bottom")        // bottom / top
    var navLabels by BoolPref("nav_labels", true)
    var navContacts by BoolPref("nav_contacts", true)
    var navCalls by BoolPref("nav_calls", true)
    var navProfile by BoolPref("nav_profile", true)
    var navMusic by BoolPref("nav_music", true)
    var navVideos by BoolPref("nav_videos", true)
    var tabsStyle by StrPref("tabs_style", "pills")   // pills / underline / text
    var tabsPos by StrPref("tabs_pos", "top")         // top / bottom
    var tabCounts by BoolPref("tab_counts", true)
    var countMuted by BoolPref("count_muted", false)
    var titleStyle by StrPref("title_style", "large") // large / small
    var showLogo by BoolPref("show_logo", true)
    var appTitle by StrPref("app_title", "Riv Post")
    var fab by BoolPref("fab", true)

    // ---------- Customize: chat list ----------
    var avatarShape by StrPref("avatar_shape", "circle") // circle / rounded / square
    var avatarSize by IntPref("avatar_size", 0)          // 0 = style default
    var density by StrPref("density", "normal")          // compact / normal / comfy
    var previewLines by IntPref("preview_lines", 1)
    var mediaThumbs by BoolPref("media_thumbs", true)
    var dividers by StrPref("dividers", "auto")          // auto / on / off
    var onlineDot by BoolPref("online_dot", true)

    // ---------- Customize: chat ----------
    var bubbleRadius by IntPref("bubble_radius", 0)      // 0 = style default
    var bubbleTail by StrPref("bubble_tail", "auto")     // auto / on / off
    var groupAvatars by BoolPref("group_avatars", true)
    var doubleTapReact by BoolPref("double_tap_react", true)
    var quickReaction by StrPref("quick_reaction", "❤️")

    // ---------- Customize: colors (0 = automatic) ----------
    var cAccent by IntPref("c_accent", 0)
    var cBg by IntPref("c_bg", 0)
    var cTopBar by IntPref("c_topbar", 0)
    var cNavBar by IntPref("c_navbar", 0)
    var cPill by IntPref("c_pill", 0)
    var cBadge by IntPref("c_badge", 0)
    var cOut by IntPref("c_out", 0)
    var cIn by IntPref("c_in", 0)
    var cOutText by IntPref("c_out_text", 0)
    var cInText by IntPref("c_in_text", 0)
    var cWallpaper by IntPref("c_wallpaper", 0)
    var cLink by IntPref("c_link", 0)
    var cTitle by IntPref("c_title", 0)

    // ---------- Media ----------
    var autoGifs by BoolPref("auto_gifs", true)
    var animStickers by BoolPref("anim_stickers", true)
    var builtInVideo by BoolPref("builtin_video", true)
    var autoVoice by BoolPref("auto_voice", true)

    // ---------- Music player ----------
    var mWavy by BoolPref("m_wavy", true)
    var mSkipButtons by BoolPref("m_skip_buttons", true)
    var mSkipSecs by IntPref("m_skip_secs", 5)
    var mBg by StrPref("m_bg", "gradient")           // gradient / solid / blur
    var mOutput by BoolPref("m_output", true)
    var mLyrics by BoolPref("m_lyrics", true)
    var mOnlineLyrics by BoolPref("m_online_lyrics", true)
    var mFormat by BoolPref("m_format", true)
    var mMini by BoolPref("m_mini", true)
    var mSpeed by FloatPref("m_speed", 1f)
    var mPitch by FloatPref("m_pitch", 1f)
    var eqOn by BoolPref("eq_on", false)
    var eqLevels by StrPref("eq_levels", "")
    var bass by IntPref("bass", 0)
    var favorites by StrPref("favorites", "")
    var musicSources by StrPref("music_sources", "")   // chat ids, empty = all chats
    var videoSources by StrPref("video_sources", "")
    var videoMinMinutes by IntPref("video_min_minutes", 0)

    var videoList by StrPref("video_list", "")         // "chatId:msgId" lines added by the user
    var wallpaper by StrPref("wallpaper", "")          // path of a picture chosen by the user
    var wallBlur by BoolPref("wall_blur", false)
    var wallDim by IntPref("wall_dim", 25)             // 0..80 percent darker
    var headerStyle by StrPref("header_style", "floating") // floating / classic
    var inputStyle by StrPref("input_style", "floating")   // floating / classic
    var showViews by BoolPref("show_views", true)
    var shareButton by BoolPref("share_button", true)
    var pinnedBar by BoolPref("pinned_bar", true)
    var gifButton by BoolPref("gif_button", true)

    // ---------- 1.2: look ----------
    var oled by BoolPref("oled", false)                // pure black
    var flat by BoolPref("flat", false)                // no rounded cards, borders or shadows
    var glass by BoolPref("glass2", false)               // see-through menus, drawer and sheets
    var wallTint by BoolPref("wall_tint", false)       // accent from wallpaper
    var wallAccent by IntPref("wall_accent", 0)
    var bubbleShape by StrPref("bubble_shape", "rounded") // rounded / pill / square / borderless
    var bubbleShadow by IntPref("bubble_shadow", 0)    // dp
    var bubbleBorder by IntPref("bubble_border", 0)    // dp
    var cBorder by IntPref("c_border", 0)
    var bubbleGlow by BoolPref("bubble_glow", false)
    var waveStyle by StrPref("wave_style", "bars")     // bars / neon / particles / line
    var haptics by BoolPref("haptics", true)
    var scrollFeel by StrPref("scroll_feel", "normal") // smooth / normal / snappy
    var stickerSize by IntPref("sticker_size", 170)
    var systemEmoji by BoolPref("system_emoji", true)
    var fontPath by StrPref("font_path", "")
    var drawerBg by StrPref("drawer_bg", "accent")     // accent / gradient / avatar / image
    var drawerImage by StrPref("drawer_image", "")
    var chatDensity by StrPref("chat_density", "normal") // compact / normal
    var playerGlow by BoolPref("player_glow", true)

    // ---------- 1.2: privacy ----------
    var ghost by BoolPref("ghost", false)               // no read receipts, appear offline
    var vaultChats by StrPref("vault_chats", "")
    var vaultPin by StrPref("vault_pin", "")
    var secureChats by StrPref("secure_chats", "")      // screenshot lock per chat
    var cleanForward by BoolPref("clean_forward", false)
    var readFreeze by BoolPref("read_freeze", false)
    var autoMute by BoolPref("auto_mute", false)
    var drawerLock by BoolPref("drawer_lock", false)
    var antiRevoke by BoolPref("anti_revoke", false)    // keep deleted messages + edit history (local only)

    // ---------- 1.2: power user ----------
    var filterTabs by StrPref("filter_tabs", "")        // unread,groups,channels,private,bots
    var swipeAccounts by BoolPref("swipe_accounts", true)
    var localPins by StrPref("local_pins", "")
    var swipeForward by BoolPref("swipe_forward2", false)
    var swipeReply by BoolPref("swipe_reply", true)
    var quickForwardChat by StrPref("quick_forward_chat", "")  // empty = Saved Messages
    var markdown by BoolPref("markdown", true)
    var saveFolder by StrPref("save_folder", "Riv Post")
    var saveByType by BoolPref("save_by_type", true)
    var editLinks by BoolPref("edit_links", false)
    var selectText by BoolPref("select_text", true)
    var builtInCamera by BoolPref("builtin_camera", true)
    var proxies by StrPref("proxies", "")               // lines: type|host|port|user|pass|secret
    var proxyAuto by BoolPref("proxy_auto", false)
    var powerSave by BoolPref("power_save", false)

    // ---------- 1.3 ----------
    var longPressSelect by BoolPref("long_press_select", true) // long-press = select, tap = menu
    var musicTabs by StrPref("music_tabs", "all,selected,playlists,albums,artists,favorites")
    var musicTabsOff by StrPref("music_tabs_off", "")
    var selectedSplit by BoolPref("selected_split", false)
    var hiddenSongs by StrPref("hidden_songs", "")
    var hiddenChats by StrPref("hidden_song_chats", "")
    var videoNotes by BoolPref("video_notes", true)
    var sendAlbums by BoolPref("send_albums", true)
    var dlMobile by BoolPref("dl_mobile", true)
    var dlRoaming by BoolPref("dl_roaming", false)
    var autoVideos by BoolPref("auto_videos", false)
    var autoVideoMb by IntPref("auto_video_mb", 15)
    var autoFiles by BoolPref("auto_files", false)
    var autoFileMb by IntPref("auto_file_mb", 5)
    var autoplayVideos by BoolPref("autoplay_videos", true)
    var earpiece by BoolPref("earpiece", true)
    var voiceCalls by BoolPref("voice_calls", true)
    var callP2p by BoolPref("call_p2p", true)
    // calls
    var callRecord by BoolPref("call_record", false)
    var callReplies by StrPref("call_replies", "")
    var callQuiet by BoolPref("call_quiet", false)
    var callQuietFrom by IntPref("call_quiet_from", 23)
    var callQuietTo by IntPref("call_quiet_to", 7)
    var callQuietMode by StrPref("call_quiet_mode", "silent")
    var callQuietMsg by StrPref("call_quiet_msg", "")
    var callAutoHeadset by BoolPref("call_auto_headset", false)
    var callFlip by BoolPref("call_flip", true)
    var callShake by BoolPref("call_shake", false)
    var callNotes by BoolPref("call_notes", true)
    var callMicGain by FloatPref("call_mic_gain", 1f)
    var callPlayGain by FloatPref("call_play_gain", 1f)
    var callBass by FloatPref("call_bass", 0f)
    var callTreble by FloatPref("call_treble", 0f)
    var callCaptions by BoolPref("call_captions", false)
    var callTranslate by BoolPref("call_translate", false)
    var callVoiceFx by BoolPref("call_voice_fx", false)
    var callRedial by IntPref("call_redial", 0)
    var callWaiting by BoolPref("call_waiting", true)
    var callWifiOnly by BoolPref("call_wifi_only", false)
    var callSaver by BoolPref("call_saver", true)
    var callNotifButton by BoolPref("call_notif_button", true)
    var callHideVault by BoolPref("call_hide_vault", true)
    var callHidden by StrPref("call_hidden", "")
    var callMuteReminder by BoolPref("call_mute_reminder", true)
    var callVolKeys by BoolPref("call_vol_keys", true)
    var callVibrate by BoolPref("call_vibrate", true)
    var callUnknownSilent by BoolPref("call_unknown_silent", false)
    var callTimeAlert by IntPref("call_time_alert", 0)
    var callLastMsg by BoolPref("call_last_msg", true)
    var callChat by BoolPref("call_chat", true)
    var callWeakWarn by BoolPref("call_weak_warn", true)
    var callStyle by StrPref("call_style", "violet")
    var callTelecom by BoolPref("call_telecom", false)
    var callHeadsetButton by BoolPref("call_headset_button", true)
    var callStats by BoolPref("call_stats", true)
    var callPeople by StrPref("call_people", "{}")
    var callHoldMusic by StrPref("call_hold_music", "")
    var callMissedNotif by BoolPref("call_missed_notif", true)
    var nightFrom by IntPref("night_from", 20)
    var nightTo by IntPref("night_to", 7)
    var saveEdited by BoolPref("save_edited", false)
    var channelPlain by BoolPref("channel_plain", false)
    var loopStickers by BoolPref("loop_stickers", true)
    var hideKbScroll by BoolPref("hide_kb_scroll", true)
    var animatedEmoji by BoolPref("animated_emoji", true)
    var mapPreviews by BoolPref("map_previews", true)
    var autoTranslateChats by StrPref("auto_translate_chats", "")
    var reduceMotion by BoolPref("reduce_motion", false)
    var mathFormulas by BoolPref("math_formulas", true)
    var codeColors by BoolPref("code_colors", true)
    var bigEmoji by BoolPref("big_emoji", true)
    var swipeTranslate by BoolPref("swipe_translate", true)
    var seenRow by BoolPref("seen_row", true)
    var liveFormat by BoolPref("live_format", true)
    var instantView by BoolPref("instant_view", true)
    var mediaGallery by BoolPref("media_gallery", true)
    var sendTyping by BoolPref("send_typing", true)
    var debugLog by BoolPref("debug_log", false)
    var videoPanel by BoolPref("video_panel", true)
    var miniStyle by StrPref("mini_style2", "pill")   // card / pill
    var miniPos by StrPref("mini_pos", "bottom")      // bottom / top
    var swipeTabs by BoolPref("swipe_tabs", true)
    var cleanTitles by BoolPref("clean_titles", true)
    var tmdbKey by StrPref("tmdb_key", "")
    var tvdbKey by StrPref("tvdb_key", "")
    var tvdbPin by StrPref("tvdb_pin", "")
    var tmdbOn by BoolPref("tmdb_on", true)
    var tvdbOn by BoolPref("tvdb_on", true)
    var showSource by StrPref("show_source", "tmdb")
    var storiesRow by BoolPref("stories_row", true)
    var commentsButton by BoolPref("comments_button", true)
    var forumTopicsView by BoolPref("forum_topics_view", true)
    var fbOn by BoolPref("fb_on", false)
    var fbApiKey by StrPref("fb_api_key", "")
    var fbAppId by StrPref("fb_app_id", "")
    var fbProject by StrPref("fb_project", "")
    var fbSender by StrPref("fb_sender", "")
    var mentionHints by BoolPref("mention_hints", true)
    var translateButton by BoolPref("translate_button", true)
    var linkPreviews by BoolPref("link_previews", true)
    var showDrafts by BoolPref("show_drafts", true)
    var hideShort by BoolPref("hide_short", true)
    var minSongSecs by IntPref("min_song_secs", 60)
    var prefetchWifiOnly by BoolPref("prefetch_wifi_only", true)
    var playlists by StrPref("playlists", "")           // lines: name|key1,key2
    var musicDrawer by BoolPref("music_drawer", true)
    var decoder by StrPref("decoder", "hw")             // hw / hwplus / sw
    var ffmpeg by BoolPref("ffmpeg", true)
    var bgVideo by BoolPref("bg_video", false)
    var autoPip by BoolPref("auto_pip", true)
    var subSize by IntPref("sub_size", 20)
    var subBg by BoolPref("sub_bg", true)
    var subColor by IntPref("sub_color", 0)
    var iptvLists by StrPref("iptv_lists", "")         // lines: name|url
    var smbServers by StrPref("smb_servers", "")       // lines: name|host|share|user|pass|domain

    fun toggleId(set: String, id: Long): String {
        val s = idSet(set).toMutableSet()
        if (!s.remove(id)) s.add(id)
        return s.joinToString(",")
    }

    fun idSet(s: String): Set<Long> = s.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    var videoPositions by StrPref("video_positions", "")   // uniqueId=ms;...

    fun resetLook() {
        navBar = "floating"; navPos = "bottom"; navLabels = true; navContacts = true; navCalls = true; navProfile = true
        navMusic = true; navVideos = true
        tabsStyle = "pills"; tabsPos = "top"; tabCounts = true; countMuted = false
        titleStyle = "large"; showLogo = true; appTitle = "Riv Post"; fab = true
        avatarShape = "circle"; avatarSize = 0; density = "normal"; previewLines = 1; mediaThumbs = true
        dividers = "auto"; onlineDot = true; bubbleRadius = 0; bubbleTail = "auto"; groupAvatars = true
        headerStyle = "floating"; inputStyle = "floating"; showViews = true; shareButton = true; pinnedBar = true; gifButton = true
        wallpaper = ""; wallBlur = false; wallDim = 25
        resetColors()
    }

    fun resetColors() {
        cAccent = 0; cBg = 0; cTopBar = 0; cNavBar = 0; cPill = 0; cBadge = 0; cOut = 0; cIn = 0
        cOutText = 0; cInText = 0; cWallpaper = 0; cLink = 0; cTitle = 0
    }

    // Accounts
    var accounts by StrPref("accounts", "0")
    var active by IntPref("active", 0)
    var nextIndex by IntPref("next_index", 1)

    fun accountList(): List<Int> =
        accounts.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()

    fun setAccountList(list: List<Int>) {
        accounts = list.distinct().joinToString(",")
    }

    fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("riv-post:" + pin).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}

class BoolPref(private val key: String, private val def: Boolean) : ReadWriteProperty<Any?, Boolean> {
    private var state: MutableState<Boolean>? = null
    private fun s(): MutableState<Boolean> =
        state ?: mutableStateOf(Prefs.sp.getBoolean(key, def)).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        s().value = value
        Prefs.sp.edit().putBoolean(key, value).apply()
    }
}

class IntPref(private val key: String, private val def: Int) : ReadWriteProperty<Any?, Int> {
    private var state: MutableState<Int>? = null
    private fun s(): MutableState<Int> =
        state ?: mutableStateOf(Prefs.sp.getInt(key, def)).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        s().value = value
        Prefs.sp.edit().putInt(key, value).apply()
    }
}

class StrPref(private val key: String, private val def: String) : ReadWriteProperty<Any?, String> {
    private var state: MutableState<String>? = null
    private fun s(): MutableState<String> =
        state ?: mutableStateOf(Prefs.sp.getString(key, def) ?: def).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): String = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        s().value = value
        Prefs.sp.edit().putString(key, value).apply()
    }
}

class FloatPref(private val key: String, private val def: Float) : ReadWriteProperty<Any?, Float> {
    private var state: MutableState<Float>? = null
    private fun s(): MutableState<Float> =
        state ?: mutableStateOf(Prefs.sp.getFloat(key, def)).also { state = it }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Float = s().value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        s().value = value
        Prefs.sp.edit().putFloat(key, value).apply()
    }
}
