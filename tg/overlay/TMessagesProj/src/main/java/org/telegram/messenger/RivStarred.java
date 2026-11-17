package org.telegram.messenger;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;

/** Riv Post: messages you starred, from any chat. Stores a short copy of the text so the list reads without loading anything. */
public final class RivStarred {

    private static final String KEY = "rivStarred";

    private RivStarred() {
    }

    public static ArrayList<JSONObject> all() {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        Collections.sort(list, (a, b) -> Long.compare(b.optLong("starredAt"), a.optLong("starredAt")));
        return list;
    }

    public static boolean has(long dialogId, int messageId) {
        for (JSONObject o : RivStore.load(KEY)) {
            if (o.optLong("chat") == dialogId && o.optInt("msg") == messageId) return true;
        }
        return false;
    }

    public static void toggle(MessageObject m) {
        if (m == null) return;
        long chat = m.getDialogId();
        int id = m.getId();
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) {
            JSONObject o = list.get(i);
            if (o.optLong("chat") == chat && o.optInt("msg") == id) {
                list.remove(i);
                RivStore.save(KEY, list);
                return;
            }
        }
        try {
            JSONObject o = new JSONObject();
            o.put("chat", chat);
            o.put("msg", id);
            String text = m.messageText != null ? m.messageText.toString() : "";
            if (text.isEmpty() && m.messageOwner != null && m.messageOwner.message != null) text = m.messageOwner.message;
            if (text.isEmpty()) text = m.isVideo() ? "[video]" : m.isMusic() ? "[music]" : m.isVoice() ? "[voice]" : "[media]";
            o.put("text", text.length() > 240 ? text.substring(0, 240) : text);
            o.put("date", m.messageOwner != null ? m.messageOwner.date : 0);
            o.put("starredAt", System.currentTimeMillis());
            list.add(o);
        } catch (Throwable ignored) {
        }
        RivStore.save(KEY, list);
    }

    public static void remove(long dialogId, int messageId) {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) {
            JSONObject o = list.get(i);
            if (o.optLong("chat") == dialogId && o.optInt("msg") == messageId) list.remove(i);
        }
        RivStore.save(KEY, list);
    }
}
