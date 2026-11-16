package org.telegram.ui;

import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;

/**
 * Riv Post: remembers the answers to the Music, Videos and Calls searches.
 *
 * Without this, every visit to those pages asked Telegram again for every source chat. Telegram
 * limits how often you can search, and when you hit that limit it silently waits (sometimes for
 * minutes) before answering, so the page sat on "Loading..." the second time round.
 *
 * Now a page that was loaded in the last few minutes opens straight away from memory. When a
 * fresh search is needed it is sent so that a "too many searches" answer comes back at once
 * instead of hanging; if that happens, the last good answer (if any) is shown instead.
 */
public final class RivSearchCache {

    private static final long FRESH_MS = 5 * 60 * 1000;

    private static final class Entry {
        long time;
        TLObject response;
    }

    private static final HashMap<String, Entry> cache = new HashMap<>();

    private RivSearchCache() {
    }

    /** Same shape as ConnectionsManager.sendRequest(req, delegate); the delegate is always called exactly once. */
    public static void send(int account, TLRPC.TL_messages_search req, RequestDelegate delegate) {
        final String key = keyOf(account, req);
        Entry e;
        synchronized (cache) {
            e = cache.get(key);
        }
        if (e != null && SystemClock.elapsedRealtime() - e.time < FRESH_MS) {
            final TLObject cached = e.response;
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> delegate.run(cached, null));
            return;
        }
        final Entry stale = e;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error == null && response != null) {
                Entry n = new Entry();
                n.time = SystemClock.elapsedRealtime();
                n.response = response;
                synchronized (cache) {
                    cache.put(key, n);
                }
                delegate.run(response, null);
            } else if (stale != null) {
                delegate.run(stale.response, null);
            } else {
                delegate.run(response, error);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    /** Forget everything, so the next visit asks Telegram again. */
    public static void clear() {
        synchronized (cache) {
            cache.clear();
        }
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
