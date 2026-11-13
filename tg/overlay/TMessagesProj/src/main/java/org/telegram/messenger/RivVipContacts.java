package org.telegram.messenger;

import org.json.JSONArray;

import java.util.LinkedHashSet;

/**
 * Riv Post: contacts marked VIP. Stored as a JSON array of user ids in RivPrefs.
 * This flag is currently inert: Quiet Hours / Do Not Disturb has not been built yet in
 * this app, so there is nothing for "bypass Quiet Hours" to hook into. Once Quiet Hours
 * exists, its notification check should read isVip(userId) and let VIP senders through.
 * See the TODO in apply.sh.
 */
public class RivVipContacts {

    private static final String KEY = "vipContacts";

    public static LinkedHashSet<Long> load() {
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        try {
            JSONArray arr = new JSONArray(RivPrefs.s(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.optLong(i));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void save(LinkedHashSet<Long> set) {
        try {
            RivPrefs.set(KEY, new JSONArray(set).toString());
        } catch (Throwable ignored) {
        }
    }

    public static boolean isVip(long userId) {
        return load().contains(userId);
    }

    public static void toggle(long userId) {
        LinkedHashSet<Long> set = load();
        if (!set.remove(userId)) set.add(userId);
        save(set);
    }
}
