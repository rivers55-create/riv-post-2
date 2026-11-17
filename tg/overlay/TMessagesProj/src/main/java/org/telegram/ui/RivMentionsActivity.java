package org.telegram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.RivT;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;

/** Riv Post: one inbox for every unread mention of you, from all chats (asks each chat that has unread mentions). */
public class RivMentionsActivity extends RivToolScreen {

    private static class Row {
        long chat;
        int msg;
        int date;
        String text;
    }

    private final ArrayList<Row> rows = new ArrayList<>();
    private boolean loading = true;
    private int pending;

    @Override
    protected String title() {
        return RivT.t("Mentions inbox", "صندوق الإشارات");
    }

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    private void load() {
        loading = true;
        rows.clear();
        ArrayList<Long> chats = new ArrayList<>();
        for (TLRPC.Dialog d : getMessagesController().getAllDialogs()) {
            if (d.unread_mentions_count > 0 && chats.size() < 40) chats.add(d.id);
        }
        pending = chats.size();
        if (pending == 0) {
            loading = false;
            return;
        }
        for (long id : chats) {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = getMessagesController().getInputPeer(id);
            req.q = "";
            req.filter = new TLRPC.TL_inputMessagesFilterMyMentions();
            req.limit = 15;
            if (req.peer == null) {
                pending--;
                continue;
            }
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                pending--;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    getMessagesController().putUsers(res.users, false);
                    getMessagesController().putChats(res.chats, false);
                    for (TLRPC.Message msg : res.messages) {
                        MessageObject mo = new MessageObject(currentAccount, msg, false, false);
                        Row r = new Row();
                        r.chat = mo.getDialogId();
                        r.msg = msg.id;
                        r.date = msg.date;
                        r.text = mo.messageText != null ? mo.messageText.toString() : (msg.message == null ? "" : msg.message);
                        rows.add(r);
                    }
                }
                if (pending <= 0) {
                    loading = false;
                    Collections.sort(rows, (a, b) -> Integer.compare(b.date, a.date));
                }
                rebuild();
            }));
        }
        if (pending <= 0) loading = false;
    }

    @Override
    protected void build() {
        addAction(RivT.t("Refresh", "تحديث"), false, () -> {
            load();
            rebuild();
        });
        if (loading) {
            addInfo(RivT.t("Looking for mentions…", "جارٍ البحث عن الإشارات…"));
            return;
        }
        if (rows.isEmpty()) {
            addInfo(RivT.t("No unread mentions.", "لا توجد إشارات غير مقروءة."));
            return;
        }
        addHeader(RivT.t("Unread mentions", "الإشارات غير المقروءة") + " · " + rows.size());
        for (Row r : rows) {
            addCell("@  " + chatTitle(r.chat), when(r.date * 1000L), true, () -> openChat(r.chat, r.msg), null);
            addText(r.text.length() > 200 ? r.text.substring(0, 200) + "…" : r.text, false);
        }
        addInfo(RivT.t("Only mentions you have not read yet are listed. Tap one to jump to it.", "تظهر الإشارات التي لم تقرأها فقط. اضغط على واحدة للانتقال إليها."));
    }
}
