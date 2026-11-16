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
    public static final String HIDE_WATCHED = "hideWatched";          // hide watched videos from chat shelves (default off)
    public static final String AUTO_NEXT_EPISODE = "autoNextEpisode"; // 10-second countdown to the next video (default on)
    public static final String SERIES_SHELVES = "seriesShelves";      // group episodes of a show into one shelf (default on)
    public static final String CROSSFADE_SEC = "crossfadeSec";        // fade between songs, seconds (0 = off)
    public static final String TRANSLATE_DEFAULT = "translateDefault"; // Telegram's translate button on by default
    public static final String DATA_SAVER = "dataSaver";              // one-switch data saver
    public static final String PROXY_CHANNEL = "proxyChannel";        // channel id to import proxy links from (0 = off)
    public static final String PROXY_CHANNEL_LAST = "proxyChannelLast"; // last import time
    public static final String LOCKED_CHATS = "lockedChats";          // comma list of dialog ids locked with fingerprint
    public static final String PROXY_BUTTON = "proxyButton";        // shield button on Chats (default on)
    public static final String PROXY_MOBILE_ONLY = "proxyMobileOnly"; // proxy only on mobile data, direct on Wi-Fi (default off)
    public static final String NOTIF_OPENS_RTP = "notifOpensRtp"; // music notification opens the RTP-style full player (default on)
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

    // Round 3
    public static final String BADGE_MUTED = "badgeMuted";           // count muted chats in the Chats badge (default off)
    public static final String SWIPE_ACTION = "swipeAction";         // what swiping a chat does: 0 = Telegram's own
    public static final String NO_AUTODL_CHATS = "noAutoDlChats";    // comma list of chat ids that never auto-download
    public static final String QUIET_CHATS = "quietChats";           // comma list of chat ids silenced during quiet hours
    public static final String QUIET_FROM = "quietFrom";             // minutes after midnight
    public static final String QUIET_TO = "quietTo";                 // minutes after midnight
    public static final String VAULT_FINGERPRINT = "vaultFingerprint"; // open the Vault with a fingerprint (default on)
    public static final String SUBTITLES = "subtitles";              // load .srt subtitles in the video player (default on)
    public static final String SLEEP_FADE = "sleepFade";             // fade out at the end of the sleep timer (default on)
    public static final String QUICK_REPLY_INSERT = "quickReplyInsert"; // quick replies button in the chat menu (default on)

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
    /** Data saver: on mobile data, nothing downloads by itself (tap still downloads). */
    public static boolean dataSaverActive() {
        try {
            return b(DATA_SAVER, false) && !ApplicationLoader.isConnectedToWiFi();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Turns the data saver on or off, also switching Telegram's video/GIF autoplay off (and back). */
    public static void setDataSaver(boolean on) {
        set(DATA_SAVER, on);
        try {
            if (on) {
                set("dsVideo", LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_VIDEOS));
                set("dsGifs", LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_GIFS));
                LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, false);
                LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, false);
            } else {
                if (b("dsVideo", true)) LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS, true);
                if (b("dsGifs", true)) LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, true);
            }
        } catch (Throwable ignored) {
        }
    }

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

    public static boolean inList(String key, long id) {
        return list(key).contains(String.valueOf(id));
    }

    public static void toggleInList(String key, long id) {
        java.util.ArrayList<String> l = list(key);
        String v = String.valueOf(id);
        if (!l.remove(v)) l.add(v);
        setList(key, l);
    }

    /** All Riv settings as one JSON text, for backup. */
    public static String exportAll() {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            for (java.util.Map.Entry<String, ?> e : prefs().getAll().entrySet()) {
                Object v = e.getValue();
                org.json.JSONObject item = new org.json.JSONObject();
                if (v instanceof Boolean) item.put("b", v);
                else if (v instanceof Integer) item.put("i", v);
                else if (v instanceof Long) item.put("l", v);
                else if (v instanceof Float) item.put("f", (double) (Float) v);
                else if (v instanceof String) item.put("s", v);
                else continue;
                o.put(e.getKey(), item);
            }
            o.put("_rivpost_backup", new org.json.JSONObject().put("i", 1));
        } catch (Throwable ignored) {
        }
        return o.toString();
    }

    /** Restores a backup made by exportAll(); returns how many settings were read. */
    public static int importAll(String json) throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject(json);
        if (!o.has("_rivpost_backup")) throw new org.json.JSONException("not a Riv Post backup");
        SharedPreferences.Editor ed = prefs().edit();
        int n = 0;
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (k.startsWith("_")) continue;
            org.json.JSONObject item = o.optJSONObject(k);
            if (item == null) continue;
            if (item.has("b")) ed.putBoolean(k, item.getBoolean("b"));
            else if (item.has("i")) ed.putInt(k, item.getInt("i"));
            else if (item.has("l")) ed.putLong(k, item.getLong("l"));
            else if (item.has("f")) ed.putFloat(k, (float) item.getDouble("f"));
            else if (item.has("s")) ed.putString(k, item.getString("s"));
            else continue;
            n++;
        }
        ed.apply();
        return n;
    }
}
