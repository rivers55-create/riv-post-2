package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Riv Post: remembers which messages someone else deleted while "Keep deleted messages" was on,
 * so they can be shown with a "deleted" label next to their time.
 *
 * Key format: account_dialogId_messageId. Ordinary chats and groups share one message-id space
 * per account (Telegram's own deletion update carries no chat id for them), so those are stored
 * with dialogId 0; channels and supergroups carry their own chat id.
 */
public class RivDeleted {

    private static final int MAX = 5000;
    private static LinkedHashSet<String> keys;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("rivdeleted", Context.MODE_PRIVATE);
    }

    private static synchronized LinkedHashSet<String> keys() {
        if (keys == null) {
            keys = new LinkedHashSet<>();
            try {
                String raw = prefs().getString("k", "");
                if (raw != null && !raw.isEmpty()) {
                    for (String k : raw.split(",")) {
                        if (!k.isEmpty()) keys.add(k);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return keys;
    }

    public static synchronized void add(int account, long dialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        try {
            LinkedHashSet<String> set = keys();
            for (Integer id : ids) {
                if (id != null) set.add(account + "_" + dialogId + "_" + id);
            }
            while (set.size() > MAX) {
                String first = set.iterator().next();
                set.remove(first);
            }
            StringBuilder sb = new StringBuilder();
            for (String k : set) {
                if (sb.length() > 0) sb.append(',');
                sb.append(k);
            }
            prefs().edit().putString("k", sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static synchronized boolean isDeleted(MessageObject m) {
        if (m == null || m.messageOwner == null) return false;
        LinkedHashSet<String> set = keys();
        if (set.isEmpty()) return false;
        int id = m.getId();
        long dialogId = m.getDialogId();
        return set.contains(m.currentAccount + "_" + dialogId + "_" + id)
                || (!ChatObject.isChannel(MessagesController.getInstance(m.currentAccount).getChat(-dialogId)) && set.contains(m.currentAccount + "_0_" + id));
    }

    /** Adds the "deleted" label in front of a message's time when it was deleted by someone else. */
    public static String decorate(MessageObject m, String time) {
        try {
            if (time == null || time.isEmpty()) return time;
            if (!RivPrefs.b(RivPrefs.KEEP_DELETED) || !RivPrefs.b(RivPrefs.MARK_DELETED, true)) return time;
            if (!isDeleted(m)) return time;
            return LocaleController.getString(R.string.RivDeletedLabel) + " " + time;
        } catch (Throwable t) {
            return time;
        }
    }
}
