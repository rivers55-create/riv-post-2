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
}
