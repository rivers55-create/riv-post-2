package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Riv Post: auto-reply when busy. While "busy" is on, the first message from each person gets your
 * reply text, then that person is left alone for six hours. Only private chats with real people.
 */
public final class RivAutoReply implements NotificationCenter.NotificationCenterDelegate {

    public static final String TEXT = "autoReplyText";
    public static final String UNTIL = "autoReplyUntil"; // millis, "max" = until you turn it off
    private static final String SENT = "autoReplySent";
    private static final long COOLDOWN_MS = 6 * 60 * 60 * 1000L;
    private static RivAutoReply instance;

    private RivAutoReply() {
    }

    public static synchronized void start() {
        if (instance != null) return;
        instance = new RivAutoReply();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).addObserver(instance, NotificationCenter.didReceiveNewMessages);
            }
        }
    }

    public static String defaultText() {
        return RivT.t("I'm busy right now and will get back to you later.", "أنا مشغول الآن وسأرد عليك لاحقاً.");
    }

    public static String text() {
        String t = RivPrefs.s(TEXT, "");
        return t == null || t.trim().isEmpty() ? defaultText() : t;
    }

    public static long until() {
        String u = RivPrefs.s(UNTIL, "0");
        if ("max".equals(u)) return Long.MAX_VALUE;
        try {
            return Long.parseLong(u);
        } catch (Throwable e) {
            return 0;
        }
    }

    public static boolean active() {
        return until() > System.currentTimeMillis();
    }

    /** hours = 0 turns it off, negative = until you turn it off. */
    public static void setBusy(int hours) {
        if (hours == 0) RivPrefs.set(UNTIL, "0");
        else if (hours < 0) RivPrefs.set(UNTIL, "max");
        else RivPrefs.set(UNTIL, String.valueOf(System.currentTimeMillis() + hours * 3600_000L));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id != NotificationCenter.didReceiveNewMessages || !active()) return;
            if (args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2]) return; // scheduled
            long dialogId = (Long) args[0];
            if (dialogId <= 0 || dialogId == 777000) return;
            @SuppressWarnings("unchecked")
            ArrayList<MessageObject> list = (ArrayList<MessageObject>) args[1];
            if (list == null || list.isEmpty()) return;
            boolean incoming = false;
            long nowSec = System.currentTimeMillis() / 1000;
            for (MessageObject m : list) {
                if (m != null && m.messageOwner != null && !m.isOutOwner() && m.messageOwner.action == null && nowSec - m.messageOwner.date < 120) {
                    incoming = true;
                    break;
                }
            }
            if (!incoming) return;
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user == null || user.bot || user.self || user.deleted) return;
            long last = RivChatFlags.value(SENT, dialogId, 0);
            if (last != 0 && System.currentTimeMillis() - last < COOLDOWN_MS) return;
            RivChatFlags.setValue(SENT, dialogId, System.currentTimeMillis());
            RivSendHook.skip = true;
            try {
                SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text(), dialogId));
            } finally {
                RivSendHook.skip = false;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
