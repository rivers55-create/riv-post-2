package org.telegram.messenger;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;

/** Riv Post: private notes (a notebook that is not Saved Messages), optionally tied to a chat. */
public final class RivNotes {

    private static final String KEY = "rivNotes";

    private RivNotes() {
    }

    public static ArrayList<JSONObject> all() {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        Collections.sort(list, (a, b) -> {
            int pa = a.optBoolean("pin") ? 1 : 0, pb = b.optBoolean("pin") ? 1 : 0;
            if (pa != pb) return pb - pa;
            return Long.compare(b.optLong("time"), a.optLong("time"));
        });
        return list;
    }

    public static ArrayList<JSONObject> forChat(long dialogId) {
        ArrayList<JSONObject> out = new ArrayList<>();
        for (JSONObject o : all()) if (o.optLong("chat") == dialogId) out.add(o);
        return out;
    }

    /** Adds a new note, or replaces the one with this id. */
    public static void put(long id, String title, String text, long chat, boolean pin) {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        JSONObject target = null;
        for (JSONObject o : list) if (o.optLong("id") == id) target = o;
        try {
            if (target == null) {
                target = new JSONObject();
                target.put("id", id);
                list.add(target);
            }
            target.put("title", title);
            target.put("text", text);
            target.put("chat", chat);
            target.put("pin", pin);
            target.put("time", System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
        RivStore.save(KEY, list);
    }

    public static void remove(long id) {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).optLong("id") == id) list.remove(i);
        RivStore.save(KEY, list);
    }
}
