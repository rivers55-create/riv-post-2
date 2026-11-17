package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.List;

/** Riv Post: ping samples for every proxy over time, so you can see which one stays good. Ping -1 means it did not answer. */
public final class RivProxyHistory {

    private static final String KEY = "rivProxyHist";
    private static final int MAX_SAMPLES = 96;

    private RivProxyHistory() {
    }

    public static String keyOf(SharedConfig.ProxyInfo p) {
        return p.settings.getAddress() + ":" + p.settings.getPort();
    }

    public static synchronized void record(String key, long ping) {
        try {
            JSONObject all = new JSONObject(RivPrefs.s(KEY, "{}"));
            JSONArray arr = all.optJSONArray(key);
            if (arr == null) arr = new JSONArray();
            JSONArray n = new JSONArray();
            int from = Math.max(0, arr.length() - (MAX_SAMPLES - 1));
            for (int i = from; i < arr.length(); i++) n.put(arr.get(i));
            JSONArray s = new JSONArray();
            s.put(System.currentTimeMillis());
            s.put(ping);
            n.put(s);
            all.put(key, n);
            RivPrefs.set(KEY, all.toString());
        } catch (Throwable ignored) {
        }
    }

    /** Samples as {time, ping} pairs, oldest first. */
    public static ArrayList<long[]> samples(String key) {
        ArrayList<long[]> out = new ArrayList<>();
        try {
            JSONObject all = new JSONObject(RivPrefs.s(KEY, "{}"));
            JSONArray arr = all.optJSONArray(key);
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONArray s = arr.optJSONArray(i);
                if (s != null) out.add(new long[]{s.optLong(0), s.optLong(1)});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void clear() {
        RivPrefs.set(KEY, "{}");
    }

    /** Pings every saved proxy once and records each answer; done is called on the calling thread's UI when all have answered (or after 12 s). */
    public static void measureAll(int account, Runnable done) {
        SharedConfig.loadProxyList();
        final List<SharedConfig.ProxyInfo> list = new ArrayList<>(SharedConfig.proxyList);
        if (list.isEmpty()) {
            if (done != null) AndroidUtilities.runOnUIThread(done);
            return;
        }
        final int[] left = {list.size()};
        final boolean[] fired = {false};
        for (SharedConfig.ProxyInfo p : list) {
            final String key = keyOf(p);
            try {
                ConnectionsManager.getInstance(account).checkProxy(p.settings, time -> {
                    record(key, time);
                    synchronized (left) {
                        left[0]--;
                        if (left[0] <= 0 && !fired[0]) {
                            fired[0] = true;
                            if (done != null) AndroidUtilities.runOnUIThread(done);
                        }
                    }
                });
            } catch (Throwable t) {
                record(key, -1);
                synchronized (left) {
                    left[0]--;
                }
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (left) {
                if (!fired[0]) {
                    fired[0] = true;
                    if (done != null) done.run();
                }
            }
        }, 12000);
    }
}
