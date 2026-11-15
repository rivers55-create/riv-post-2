package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** Riv Post: settings for the extra features. Every extra is optional; official behaviour is the default. */
public class RivPrefs {

    // Privacy
    public static final String GHOST = "ghost";                 // don't send read receipts
    public static final String HIDE_TYPING = "hideTyping";      // don't send "typing…"
    public static final String HIDE_ONLINE = "hideOnline";      // never tell the server you are online
    public static final String KEEP_DELETED = "keepDeleted";    // keep messages others delete

    // Notifications and look
    public static final String KEEP_ALIVE = "keepAlive";         // background connection for notifications (default on)
    public static final String HIDE_SPONSORED = "hideSponsored"; // no sponsored messages in channels
    public static final String HIDE_STORIES = "hideStories";     // no stories row above chats
    public static final String STICKER_SIZE = "stickerSize";     // percent, 100 = official

    // Video & music libraries (metadata lookup, My List, playback position)
    public static final String TMDB_KEY = "tmdbKey";             // user's own TMDb API key
    public static final String TVDB_KEY = "tvdbKey";             // user's own TheTVDB v4 API key
    public static final String TVDB_PIN = "tvdbPin";             // TheTVDB subscriber PIN, optional
    public static final String TMDB_ON = "tmdbOn";               // use TMDb for lookups (default on once a key is set)
    public static final String TVDB_ON = "tvdbOn";               // use TheTVDB for lookups
    public static final String SHOW_SOURCE = "showSource";       // "tmdb" or "tvdb": preferred source for TV shows
    public static final String CLEAN_TITLES = "cleanTitles";     // show tidy titles instead of raw file names
    public static final String MY_LIST_VIDEOS = "myListVideos";  // comma list of "acc_dialogId_msgId" keys
    public static final String FAV_SONGS = "favSongs";           // comma list of "acc_dialogId_msgId" keys
    public static final String HIDDEN_SONGS = "hiddenSongs";     // comma list of "acc_dialogId_msgId" keys
    public static final String MUSIC_SELECTED_CHATS = "musicSelectedChats"; // comma list of dialog ids, subset of musicSources
    public static final String VIDEO_SELECTED_CHATS = "videoSelectedChats"; // comma list of dialog ids, subset of videoSources
    public static final String HIDE_SOURCE_PREVIEW_BUTTON = "hideSourcePreviewButton"; // hide the floating play button on the chat-picker screen
    public static final String IN_CHAT_PLAYER_RTP_STYLE = "inChatPlayerRtpStyle"; // style the in-chat music bar like RTP instead of stock Telegram
    public static final String TAP_OPENS_RTP_MINI_PLAYER = "tapOpensRtpMiniPlayer"; // tapping a chat's music bar opens RTP's mini player (default on)

    // Added in this round
    public static final String NAME_FIT = "nameFit";                 // shrink long chat names to fit (default on)
    public static final String GHOST_BUTTON = "ghostButton";         // ghost button in the Chats top bar (default on)
    public static final String HIDE_UPDATE_BANNER = "hideUpdateBanner"; // hide "Update Telegram" (default on)
    public static final String HIDE_ADS = "hideAds";                 // hide sponsored chats / proxy sponsor (default on)
    public static final String MARK_DELETED = "markDeleted";         // label kept messages as deleted (default on)
    public static final String EDIT_HISTORY = "editHistory";         // remember earlier versions of edited messages (default on)
    public static final String EXACT_LAST_SEEN = "exactLastSeen";    // exact last seen time on profiles (default on)
    public static final String SHOW_USER_ID = "showUserId";          // show the person's ID on profiles (default on)
    public static final String FAST_DOWNLOADS = "fastDownloads";     // more download pieces at once (default on)
    public static final String RANGE_SELECT = "rangeSelect";         // select-in-between + mass download (default on)
    public static final String MUSIC_TAB_ORDER = "musicTabOrder";    // comma list of tab ids
    public static final String LYRICS = "lyrics";                    // synced lyrics in the player (default on)
    public static final String LYRICS_ONLINE = "lyricsOnline";       // look lyrics up online (LRCLIB) (default on)
    public static final String EQ_ON = "eqOn";                       // equalizer enabled
    public static final String EQ_BANDS = "eqBands";                 // comma list of band levels (millibels)
    public static final String EQ_PRESET = "eqPreset";               // chosen preset name
    public static final String PLAYER_RTP = "playerRtp";             // RTP-style Now Playing (default on)
    public static final String VIDEO_PLAYER_RVP = "videoPlayerRvp";  // RVP-style video player (default on)
    public static final String ACCENT = "accent";                    // custom accent colour, 0 = theme default
    public static final String ACCENT_FROM_COVER = "accentFromCover"; // tint the player from the song cover
    public static final String LAYOUT_STYLE = "layoutStyle";         // 0 classic bar, 1 floating bar, 2 compact (labels hidden)
    public static final String BAR_ROUNDED = "barRounded";           // rounded vs flat bottom bar

    private static SharedPreferences prefs;

    private static SharedPreferences prefs() {
        if (prefs == null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("rivconfig", Context.MODE_PRIVATE);
        }
        return prefs;
    }

    public static boolean b(String key) {
        return b(key, false);
    }

    public static boolean b(String key, boolean def) {
        try {
            return prefs().getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void set(String key, boolean value) {
        try {
            prefs().edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    public static String s(String key, String def) {
        try {
            return prefs().getString(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void set(String key, String value) {
        try {
            prefs().edit().putString(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    public static int i(String key, int def) {
        try {
            return prefs().getInt(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void set(String key, int value) {
        try {
            prefs().edit().putInt(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Sticker size factor; 1.0 is official. */
    public static float stickerScale() {
        return i(STICKER_SIZE, 100) / 100f;
    }

    /** Ghost mode = no read ticks, no "typing...", never online. One switch for all three. */
    public static boolean ghost() {
        return b(GHOST);
    }

    public static void setGhost(boolean on) {
        set(GHOST, on);
        set(HIDE_TYPING, on);
        set(HIDE_ONLINE, on);
    }

    /**
     * A chat name that is a little too long for its row is drawn smaller (down to 80%) so the
     * whole name fits on one line; anything longer still ends in "..." as before.
     */
    public static CharSequence fitName(CharSequence name, android.text.TextPaint paint, int width) {
        try {
            if (name == null || width <= 0 || !b(NAME_FIT, true)) return name;
            float w = paint.measureText(name.toString());
            if (w <= width) return name;
            float scale = Math.max(0.8f, width / w);
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(name);
            sb.setSpan(new android.text.style.RelativeSizeSpan(scale), 0, sb.length(), android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return sb;
        } catch (Throwable t) {
            return name;
        }
    }

    /** Comma list of longs (chat ids etc.). */
    public static java.util.ArrayList<String> list(String key) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        String raw = s(key, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String p : raw.split(",")) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    public static void setList(String key, java.util.List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String it : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(it);
        }
        set(key, sb.toString());
    }
}
