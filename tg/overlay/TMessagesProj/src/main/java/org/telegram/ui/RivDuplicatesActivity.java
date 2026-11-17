package org.telegram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Riv Post: duplicate finder for your Music and Video sources. Songs match on the same title,
 * artist and length; videos on the same file size and length. It compares what the files say
 * about themselves, not what is inside them.
 */
public class RivDuplicatesActivity extends RivToolScreen {

    private static class Item {
        long chat;
        int msg;
        String name;
        long size;
    }

    private final LinkedHashMap<String, List<Item>> groups = new LinkedHashMap<>();
    private boolean loading = true;
    private int pending;

    @Override
    protected String title() {
        return RivT.t("Duplicate finder", "كاشف التكرار");
    }

    private static ArrayList<Long> ids(String key) {
        ArrayList<Long> out = new ArrayList<>();
        String s = RivPrefs.s(key, "");
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
        scan();
        return super.onFragmentCreate();
    }

    private void scan() {
        groups.clear();
        loading = true;
        ArrayList<Long> music = ids("musicSources"), video = ids("videoSources");
        pending = music.size() + video.size() * 2;
        if (pending == 0) {
            loading = false;
            return;
        }
        for (long id : music) request(id, new TLRPC.TL_inputMessagesFilterMusic());
        for (long id : video) {
            request(id, new TLRPC.TL_inputMessagesFilterVideo());
            request(id, new TLRPC.TL_inputMessagesFilterDocument());
        }
    }

    private void request(long chat, TLRPC.MessagesFilter filter) {
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = getMessagesController().getInputPeer(chat);
        req.q = "";
        req.filter = filter;
        req.limit = 100;
        if (req.peer == null) {
            pending--;
            return;
        }
        RivSearchCache.send(currentAccount, req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            pending--;
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                for (TLRPC.Message msg : res.messages) {
                    MessageObject mo = new MessageObject(currentAccount, msg, false, false);
                    TLRPC.Document d = mo.getDocument();
                    if (d == null) continue;
                    String key;
                    String name;
                    if (mo.isMusic()) {
                        String t = norm(mo.getMusicTitle()), a = norm(mo.getMusicAuthor());
                        if (t.isEmpty()) continue;
                        key = "m|" + t + "|" + a + "|" + ((int) mo.getDuration() / 3);
                        name = (mo.getMusicAuthor() == null ? "" : mo.getMusicAuthor() + " – ") + mo.getMusicTitle();
                    } else if (mo.isVideo()) {
                        if (d.size <= 0) continue;
                        key = "v|" + d.size + "|" + ((int) mo.getDuration() / 2);
                        name = org.telegram.messenger.FileLoader.getDocumentFileName(d);
                    } else continue;
                    Item it = new Item();
                    it.chat = mo.getDialogId();
                    it.msg = msg.id;
                    it.name = name;
                    it.size = d.size;
                    List<Item> l = groups.get(key);
                    if (l == null) {
                        l = new ArrayList<>();
                        groups.put(key, l);
                    }
                    boolean same = false;
                    for (Item o : l) if (o.chat == it.chat && o.msg == it.msg) same = true;
                    if (!same) l.add(it);
                }
            }
            if (pending <= 0) loading = false;
            rebuild();
        }));
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    @Override
    protected void build() {
        addAction(RivT.t("Scan again", "فحص من جديد"), false, () -> {
            scan();
            rebuild();
        });
        if (loading) addInfo(RivT.t("Scanning your music and video sources…", "جارٍ فحص مصادر الموسيقى والفيديو…"));
        int found = 0;
        for (Map.Entry<String, List<Item>> e : groups.entrySet()) {
            if (e.getValue().size() < 2) continue;
            found++;
            Item first = e.getValue().get(0);
            addHeader((e.getKey().startsWith("m") ? "🎵 " : "🎬 ") + first.name + "  ×" + e.getValue().size());
            for (int i = 0; i < e.getValue().size(); i++) {
                final Item it = e.getValue().get(i);
                addCell(chatTitle(it.chat), mb(it.size), i != e.getValue().size() - 1, () -> openChat(it.chat, it.msg), null);
            }
        }
        if (!loading && found == 0) {
            addInfo(ids("musicSources").isEmpty() && ids("videoSources").isEmpty()
                    ? RivT.t("Add chats in Music or Videos sources first.", "أضف محادثات في مصادر الموسيقى أو الفيديو أولاً.")
                    : RivT.t("No duplicates found.", "لم يتم العثور على تكرار."));
        } else if (found > 0) {
            addInfo(RivT.t("Tap a row to open that copy in its chat, where you can delete it.", "اضغط على صف لفتح تلك النسخة في محادثتها حيث يمكنك حذفها."));
        }
    }

    private static String mb(long b) {
        return String.format(Locale.US, "%.1f MB", b / 1048576.0);
    }
}
