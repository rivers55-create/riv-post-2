package org.telegram.ui;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Riv Post: remembers the answers to the Music, Videos and Calls searches, in memory AND on disk.
 *
 * Without this, every visit to those pages asked Telegram again for every source chat. Telegram
 * limits how often you can search, and when you hit that limit it silently waits (sometimes for
 * minutes) before answering, so the page sat on "Loading..." the second time round.
 *
 * Now the last answers are also saved to a file, so after the app was closed the pages open at
 * once with what they showed last time. A fresh search then runs quietly in the background; if it
 * brings something different, the page is told (see {@link #setRefreshListener}) and updates.
 */
public final class RivSearchCache {

    private static final long FRESH_MS = 5 * 60 * 1000;
    private static final long DISK_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;

    private static final class Entry {
        long time;
        TLObject response;
        byte[] bytes;
    }

    private static final HashMap<String, Entry> cache = new HashMap<>();
    private static final HashSet<String> refreshing = new HashSet<>();
    private static boolean cleaned;

    private static final java.util.LinkedHashMap<Object, Runnable> listeners = new java.util.LinkedHashMap<>();
    private static boolean notifyPending;

    private RivSearchCache() {
    }

    /** Same shape as ConnectionsManager.sendRequest(req, delegate); the delegate is always called exactly once. */
    public static void send(int account, TLRPC.TL_messages_search req, RequestDelegate delegate) {
        go(account, req, delegate, false);
    }

    /**
     * Like {@link #send}, but never makes the caller wait for the network when something was saved
     * before (in memory or on disk, however old): that is returned at once and refreshed in the
     * background. The page that asked should have registered a refresh listener.
     */
    public static void sendInstant(int account, TLRPC.TL_messages_search req, RequestDelegate delegate) {
        go(account, req, delegate, true);
    }

    /** Called (on the UI thread) when a background refresh found something different from what was shown. */
    public static void setRefreshListener(Object owner, Runnable r) {
        synchronized (cache) {
            listeners.put(owner, r);
        }
    }

    public static void clearRefreshListener(Object owner) {
        synchronized (cache) {
            listeners.remove(owner);
        }
    }

    private static void go(int account, TLRPC.TL_messages_search req, RequestDelegate delegate, boolean instant) {
        final String key = keyOf(account, req);
        Entry e;
        synchronized (cache) {
            e = cache.get(key);
        }
        if (e != null && SystemClock.elapsedRealtime() - e.time < FRESH_MS) {
            final TLObject cached = e.response;
            Utilities.globalQueue.postRunnable(() -> delegate.run(cached, null));
            return;
        }
        final Entry mem = e;
        if (!instant) {
            sendNetwork(account, req, key, delegate, mem);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            Entry have = mem;
            if (have == null) have = readDisk(key);
            if (have != null) {
                synchronized (cache) {
                    if (cache.get(key) == null) cache.put(key, have);
                }
                delegate.run(have.response, null);
                refreshQuietly(account, req, key, have);
            } else {
                sendNetwork(account, req, key, delegate, null);
            }
        });
    }

    private static void sendNetwork(int account, TLRPC.TL_messages_search req, String key, RequestDelegate delegate, Entry stale) {
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error == null && response != null) {
                store(key, response);
                delegate.run(response, null);
            } else if (stale != null) {
                delegate.run(stale.response, null);
            } else {
                delegate.run(response, error);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static void refreshQuietly(int account, TLRPC.TL_messages_search req, String key, Entry shown) {
        synchronized (cache) {
            if (!refreshing.add(key)) return;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            synchronized (cache) {
                refreshing.remove(key);
            }
            if (error != null || response == null) return;
            byte[] old = shown.bytes;
            byte[] now = store(key, response);
            if (now != null && old != null && !Arrays.equals(old, now)) notifyChanged();
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static void notifyChanged() {
        synchronized (cache) {
            if (listeners.isEmpty() || notifyPending) return;
            notifyPending = true;
        }
        AndroidUtilities.runOnUIThread(() -> {
            java.util.ArrayList<Runnable> run;
            synchronized (cache) {
                notifyPending = false;
                run = new java.util.ArrayList<>(listeners.values());
            }
            for (Runnable r : run) r.run();
        }, 600);
    }

    /** Puts the answer in memory right away and on disk in the background; returns its bytes. */
    private static byte[] store(String key, TLObject response) {
        byte[] bytes = null;
        try {
            SerializedData d = new SerializedData(response.getObjectSize());
            response.serializeToStream(d);
            bytes = d.toByteArray();
        } catch (Throwable ignored) {
        }
        Entry n = new Entry();
        n.time = SystemClock.elapsedRealtime();
        n.response = response;
        n.bytes = bytes;
        synchronized (cache) {
            cache.put(key, n);
        }
        if (bytes != null) {
            final byte[] b = bytes;
            Utilities.globalQueue.postRunnable(() -> writeDisk(key, b));
        }
        return bytes;
    }

    // ---------------------------------------------------------------- disk

    private static File dir() {
        File d = new File(ApplicationLoader.getFilesDirFixed(), "riv_cache");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File fileFor(String key) {
        return new File(dir(), Utilities.MD5(key) + ".bin");
    }

    private static void writeDisk(String key, byte[] bytes) {
        try {
            File f = fileFor(key);
            File tmp = new File(f.getPath() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
                out.writeInt(BuildVars.BUILD_VERSION_STRING.hashCode());
                out.writeLong(System.currentTimeMillis());
                out.writeInt(bytes.length);
                out.write(bytes);
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (Throwable ignored) {
        }
        cleanOld();
    }

    private static Entry readDisk(String key) {
        try {
            File f = fileFor(key);
            if (!f.exists()) return null;
            byte[] bytes;
            try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                if (in.readInt() != BuildVars.BUILD_VERSION_STRING.hashCode()) {
                    f.delete();
                    return null;
                }
                long saved = in.readLong();
                if (System.currentTimeMillis() - saved > DISK_MAX_AGE_MS) {
                    f.delete();
                    return null;
                }
                int len = in.readInt();
                if (len <= 0 || len > 32 * 1024 * 1024) return null;
                bytes = new byte[len];
                in.readFully(bytes);
            }
            SerializedData s = new SerializedData(bytes);
            int constructor = s.readInt32(false);
            TLObject obj = TLRPC.messages_Messages.TLdeserialize(s, constructor, false);
            if (obj == null) {
                f.delete();
                return null;
            }
            Entry e = new Entry();
            // old enough that nothing treats it as fresh
            e.time = SystemClock.elapsedRealtime() - FRESH_MS - 1;
            e.response = obj;
            e.bytes = bytes;
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void cleanOld() {
        synchronized (cache) {
            if (cleaned) return;
            cleaned = true;
        }
        try {
            File[] files = dir().listFiles();
            if (files == null) return;
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (now - f.lastModified() > DISK_MAX_AGE_MS) f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Forget everything, so the next visit asks Telegram again. */
    public static void clear() {
        synchronized (cache) {
            cache.clear();
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File[] files = dir().listFiles();
                if (files != null) for (File f : files) f.delete();
            } catch (Throwable ignored) {
            }
        });
    }

    private static String keyOf(int account, TLRPC.TL_messages_search req) {
        StringBuilder sb = new StringBuilder();
        sb.append(account).append('|');
        TLRPC.InputPeer p = req.peer;
        if (p == null) {
            sb.append("null");
        } else {
            sb.append(p.getClass().getSimpleName()).append(':').append(p.user_id).append(':').append(p.chat_id).append(':').append(p.channel_id);
        }
        sb.append('|').append(req.filter == null ? "null" : req.filter.getClass().getSimpleName());
        sb.append('|').append(req.q);
        sb.append('|').append(req.offset_id);
        sb.append('|').append(req.limit);
        return sb.toString();
    }
}
