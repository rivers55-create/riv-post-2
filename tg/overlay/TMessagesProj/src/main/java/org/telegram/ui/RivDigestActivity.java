package org.telegram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Riv Post: channel digest. The latest few posts from the channels you pick, in one list, so you do not open each channel. */
public class RivDigestActivity extends RivToolScreen {

    private static final String KEY = "rivDigestChats";
    private final LinkedHashMap<Long, List<String[]>> posts = new LinkedHashMap<>(); // chat -> [text, date, id]
    private boolean loading;
    private int pending;

    @Override
    protected String title() {
        return RivT.t("Channel digest", "ملخص القنوات");
    }

    private ArrayList<Long> picked() {
        ArrayList<Long> out = new ArrayList<>();
        String s = RivPrefs.s(KEY, "");
        if (s != null && !s.isEmpty()) {
            for (String p : s.split(",")) {
                try {
                    out.add(Long.parseLong(p));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    private void load() {
        posts.clear();
        ArrayList<Long> chats = picked();
        pending = chats.size();
        loading = pending > 0;
        for (long id : chats) {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = getMessagesController().getInputPeer(id);
            req.limit = 4;
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
                    ArrayList<String[]> list = new ArrayList<>();
                    for (TLRPC.Message msg : res.messages) {
                        MessageObject mo = new MessageObject(currentAccount, msg, false, false);
                        String text = mo.messageText != null ? mo.messageText.toString() : "";
                        if (text.isEmpty() && msg.message != null) text = msg.message;
                        if (text.isEmpty()) text = mo.isVideo() ? "🎬" : mo.isMusic() ? "🎵" : mo.isPhoto() ? "🖼" : "…";
                        list.add(new String[]{text, String.valueOf(msg.date), String.valueOf(msg.id)});
                    }
                    posts.put(id, list);
                }
                if (pending <= 0) loading = false;
                rebuild();
            }));
        }
        if (pending <= 0) loading = false;
    }

    @Override
    protected void build() {
        ArrayList<Long> chats = picked();
        addAction(RivT.t("Choose channels", "اختيار القنوات") + (chats.isEmpty() ? "" : " (" + chats.size() + ")"), true, this::pick);
        addAction(RivT.t("Refresh", "تحديث"), false, () -> {
            load();
            rebuild();
        });
        if (chats.isEmpty()) {
            addInfo(RivT.t("Pick the channels you want a quick look at. The latest posts show here.", "اختر القنوات التي تريد نظرة سريعة عليها. تظهر آخر المنشورات هنا."));
            return;
        }
        if (loading) addInfo(RivT.t("Loading…", "جارٍ التحميل…"));
        for (Map.Entry<Long, List<String[]>> e : posts.entrySet()) {
            final long chat = e.getKey();
            addHeader(chatTitle(chat));
            for (String[] p : e.getValue()) {
                final int id = Integer.parseInt(p[2]);
                addCell(when(Long.parseLong(p[1]) * 1000L), "", false, () -> openChat(chat, id), null);
                addText(p[0].length() > 220 ? p[0].substring(0, 220) + "…" : p[0], false);
            }
        }
    }

    private void pick() {
        ArrayList<Long> cur = picked();
        RivChatSourcePicker picker = new RivChatSourcePicker(RivT.t("Digest channels", "قنوات الملخص"), RivT.t("Pick the channels for the digest.", "اختر قنوات الملخص."), cur);
        picker.setDelegate(new RivChatSourcePicker.Delegate() {
            @Override
            public void onSourcesChanged(ArrayList<Long> sources) {
                StringBuilder sb = new StringBuilder();
                for (Long l : sources) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(l);
                }
                RivPrefs.set(KEY, sb.toString());
                load();
                rebuild();
            }

            @Override
            public void onPreview(ArrayList<Long> sources) {
            }
        });
        presentFragment(picker);
    }
}
