package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Riv Post: your own tags for chats (Work, Family...), kept as {"chat": id, "tags": ["Work"]} records. */
public final class RivChatTags {

    private static final String KEY = "rivChatTags";

    private RivChatTags() {
    }

    public static ArrayList<String> tagsOf(long dialogId) {
        ArrayList<String> out = new ArrayList<>();
        for (JSONObject o : RivStore.load(KEY)) {
            if (o.optLong("chat") != dialogId) continue;
            JSONArray a = o.optJSONArray("tags");
            if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        }
        return out;
    }

    public static void setTags(long dialogId, ArrayList<String> tags) {
        ArrayList<JSONObject> list = RivStore.load(KEY);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).optLong("chat") == dialogId) list.remove(i);
        if (!tags.isEmpty()) {
            try {
                JSONObject o = new JSONObject();
                o.put("chat", dialogId);
                o.put("tags", new JSONArray(tags));
                list.add(o);
            } catch (Throwable ignored) {
            }
        }
        RivStore.save(KEY, list);
    }

    /** Every tag in use, in first-seen order. */
    public static ArrayList<String> allTags() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (JSONObject o : RivStore.load(KEY)) {
            JSONArray a = o.optJSONArray("tags");
            if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.optString(i));
        }
        return new ArrayList<>(set);
    }

    public static ArrayList<Long> chatsWith(String tag) {
        ArrayList<Long> out = new ArrayList<>();
        for (JSONObject o : RivStore.load(KEY)) {
            JSONArray a = o.optJSONArray("tags");
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                if (tag.equals(a.optString(i))) {
                    out.add(o.optLong("chat"));
                    break;
                }
            }
        }
        return out;
    }

    /** A stable colour for a tag name. */
    public static int colorOf(String tag) {
        int[] palette = {0xFF4F8EF7, 0xFF43A047, 0xFFF57C00, 0xFFAB47BC, 0xFFE53935, 0xFF00ACC1, 0xFF8D6E63, 0xFF5C6BC0};
        return palette[Math.abs(tag.hashCode()) % palette.length];
    }
}
