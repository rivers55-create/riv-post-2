package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Riv Post: "auto-clean on this phone". For chats you chose, messages older than the time you set
 * are removed from this phone only (the other person and the server keep them). Runs when the app
 * starts, when you leave a chat, and every ten minutes while the app is open.
 */
public final class RivLocalExpire {

    private static boolean started;

    private RivLocalExpire() {
    }

    public static synchronized void start() {
        if (started) return;
        started = true;
        tick();
    }

    private static void tick() {
        AndroidUtilities.runOnUIThread(() -> {
            sweep();
            tick();
        }, 10 * 60 * 1000L);
        // first run shortly after the app comes up
    }

    public static void sweep() {
        try {
            ArrayList<String> entries = RivPrefs.list(RivChatFlags.EXPIRE);
            if (entries.isEmpty()) return;
            final long nowSec = System.currentTimeMillis() / 1000;
            for (int acc = 0; acc < UserConfig.MAX_ACCOUNT_COUNT; acc++) {
                if (!UserConfig.getInstance(acc).isClientActivated()) continue;
                final int account = acc;
                final MessagesStorage storage = MessagesStorage.getInstance(account);
                for (String e : entries) {
                    int c = e.indexOf(':');
                    if (c < 0) continue;
                    final long dialogId;
                    final long ms;
                    try {
                        dialogId = Long.parseLong(e.substring(0, c));
                        ms = Long.parseLong(e.substring(c + 1));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (ms <= 0) continue;
                    final long cutoff = nowSec - ms / 1000;
                    storage.getStorageQueue().postRunnable(() -> {
                        final ArrayList<Integer> ids = new ArrayList<>();
                        try {
                            SQLiteCursor cursor = storage.getDatabase().queryFinalized("SELECT mid FROM messages_v2 WHERE uid = " + dialogId + " AND date < " + cutoff + " AND mid > 0 LIMIT 500");
                            while (cursor.next()) {
                                ids.add(cursor.intValue(0));
                            }
                            cursor.dispose();
                        } catch (Throwable t) {
                            FileLog.e(t);
                        }
                        if (ids.isEmpty()) return;
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                MessagesController mc = MessagesController.getInstance(account);
                                long channelId = 0;
                                if (dialogId < 0) {
                                    TLRPC.Chat chat = mc.getChat(-dialogId);
                                    if (ChatObject.isChannel(chat)) channelId = chat.id;
                                }
                                mc.deleteMessagesByPush(dialogId, ids, channelId);
                            } catch (Throwable t) {
                                FileLog.e(t);
                            }
                        });
                    });
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
