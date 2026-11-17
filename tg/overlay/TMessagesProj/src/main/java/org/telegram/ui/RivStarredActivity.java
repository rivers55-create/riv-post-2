package org.telegram.ui;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivStarred;
import org.telegram.messenger.RivT;

import java.util.ArrayList;

/** Riv Post: every message you starred (long-press a message → Star), from all chats, in one list. */
public class RivStarredActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Starred messages", "الرسائل المميزة");
    }

    @Override
    protected void build() {
        ArrayList<JSONObject> list = RivStarred.all();
        if (list.isEmpty()) {
            addInfo(RivT.t("Nothing starred yet. In any chat, long-press a message and choose Star.",
                    "لا توجد رسائل مميزة بعد. في أي محادثة اضغط مطولاً على رسالة واختر تمييز بنجمة."));
            return;
        }
        addHeader(RivT.t("Starred", "المميزة") + " · " + list.size());
        for (int i = 0; i < list.size(); i++) {
            final JSONObject o = list.get(i);
            final long chat = o.optLong("chat");
            final int msg = o.optInt("msg");
            addCell("★  " + chatTitle(chat), o.optInt("date") > 0 ? when(o.optInt("date") * 1000L) : "", true,
                    () -> openChat(chat, msg),
                    () -> choose(chatTitle(chat), new CharSequence[]{RivT.t("Copy text", "نسخ النص"), RivT.t("Remove star", "إزالة التمييز")}, which -> {
                        if (which == 0) copy(o.optString("text"));
                        else {
                            RivStarred.remove(chat, msg);
                            rebuild();
                        }
                    }));
            addText(o.optString("text"), false);
        }
        addInfo(RivT.t("Tap to open the message in its chat. Long-press for copy or remove.", "اضغط لفتح الرسالة في محادثتها. اضغط مطولاً للنسخ أو الإزالة."));
    }
}
