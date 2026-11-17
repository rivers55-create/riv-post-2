package org.telegram.ui;

import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.RivLinkClean;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/** Riv Post: link saver. Every link posted in the chats you pick, in one searchable list (newest first). */
public class RivLinkSaverActivity extends RivToolScreen {

    private static final String KEY = "rivLinkChats";

    private static class Row {
        String url;
        long chat;
        int msg;
        int date;
    }

    private final ArrayList<Row> rows = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    private boolean loading;
    private int pending;
    private String query = "";

    @Override
    protected String title() {
        return RivT.t("Link saver", "حافظ الروابط");
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
        RivSearchCache.setRefreshListener(this, this::load);
        load();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        RivSearchCache.clearRefreshListener(this);
        super.onFragmentDestroy();
    }

    private void load() {
        rows.clear();
        seen.clear();
        ArrayList<Long> chats = picked();
        pending = chats.size();
        loading = pending > 0;
        for (long id : chats) {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = getMessagesController().getInputPeer(id);
            req.q = "";
            req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            req.limit = 100;
            if (req.peer == null) {
                pending--;
                continue;
            }
            RivSearchCache.sendInstant(currentAccount, req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                pending--;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    getMessagesController().putUsers(res.users, false);
                    getMessagesController().putChats(res.chats, false);
                    for (TLRPC.Message msg : res.messages) collect(msg);
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

    private void collect(TLRPC.Message msg) {
        long chat = MessageObject.getDialogId(msg);
        String text = msg.message == null ? "" : msg.message;
        ArrayList<String> urls = new ArrayList<>();
        if (msg.entities != null) {
            for (TLRPC.MessageEntity e : msg.entities) {
                try {
                    if (e instanceof TLRPC.TL_messageEntityTextUrl) urls.add(((TLRPC.TL_messageEntityTextUrl) e).url);
                    else if (e instanceof TLRPC.TL_messageEntityUrl && e.offset >= 0 && e.offset + e.length <= text.length()) {
                        urls.add(text.substring(e.offset, e.offset + e.length));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (urls.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s]+").matcher(text);
            while (m.find()) urls.add(m.group());
        }
        for (String u : urls) {
            if (u == null || u.isEmpty()) continue;
            if (!seen.add(chat + "|" + u)) continue;
            Row r = new Row();
            r.url = u;
            r.chat = chat;
            r.msg = msg.id;
            r.date = msg.date;
            rows.add(r);
        }
    }

    @Override
    protected void build() {
        ArrayList<Long> chats = picked();
        addAction(RivT.t("Choose chats", "اختيار المحادثات") + (chats.isEmpty() ? "" : " (" + chats.size() + ")"), true, this::pick);
        addAction(query.isEmpty() ? RivT.t("Search links", "بحث في الروابط") : RivT.t("Search: ", "بحث: ") + query + "  ✕", false, () -> {
            if (!query.isEmpty()) {
                query = "";
                rebuild();
                return;
            }
            ask(RivT.t("Search links", "بحث في الروابط"), RivT.t("Word in the link", "كلمة في الرابط"), "", false, v -> {
                query = v.trim().toLowerCase();
                rebuild();
            });
        });
        if (chats.isEmpty()) {
            addInfo(RivT.t("Pick the chats whose links you want to keep in one place.", "اختر المحادثات التي تريد جمع روابطها في مكان واحد."));
            return;
        }
        if (loading && rows.isEmpty()) addInfo(RivT.t("Loading…", "جارٍ التحميل…"));
        int shown = 0;
        for (Row r : rows) {
            if (!query.isEmpty() && !r.url.toLowerCase().contains(query)) continue;
            shown++;
            if (shown > 300) break;
            String host = "";
            try {
                host = Uri.parse(r.url).getHost();
            } catch (Throwable ignored) {
            }
            final String url = r.url;
            addCell(host == null || host.isEmpty() ? url : host, chatTitle(r.chat), true,
                    () -> org.telegram.messenger.browser.Browser.openUrl(ctx, url),
                    () -> choose(url, new CharSequence[]{RivT.t("Open in chat", "فتح في المحادثة"), RivT.t("Copy link", "نسخ الرابط"), RivT.t("Copy clean link", "نسخ رابط نظيف")}, which -> {
                        if (which == 0) openChat(r.chat, r.msg);
                        else if (which == 1) copy(url);
                        else copy(RivLinkClean.clean(url));
                    }));
            addText(url.length() > 120 ? url.substring(0, 120) + "…" : url, false);
        }
        if (shown == 0 && !loading) addInfo(RivT.t("No links found.", "لم يتم العثور على روابط."));
        else addInfo(RivT.t("Tap to open. Long-press to copy, copy without tracking, or jump to the message.", "اضغط للفتح. اضغط مطولاً للنسخ أو النسخ بدون تتبع أو الانتقال للرسالة."));
    }

    private void pick() {
        RivChatSourcePicker picker = new RivChatSourcePicker(RivT.t("Link sources", "مصادر الروابط"), RivT.t("Pick the chats to collect links from.", "اختر المحادثات لجمع الروابط منها."), picked());
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
