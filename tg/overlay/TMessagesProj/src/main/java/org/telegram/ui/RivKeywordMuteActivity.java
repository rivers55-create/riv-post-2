package org.telegram.ui;

import org.telegram.messenger.RivKeywordMute;
import org.telegram.messenger.RivT;

import java.util.ArrayList;

/** Riv Post: keywords whose messages never make a notification (the message still arrives in the chat). */
public class RivKeywordMuteActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Mute by keyword", "كتم بالكلمات");
    }

    @Override
    protected void build() {
        addAction(RivT.t("Add a word or phrase", "أضف كلمة أو عبارة"), true, () -> ask(RivT.t("Word or phrase", "كلمة أو عبارة"), "", "", false, v -> {
            v = v.trim();
            if (v.isEmpty()) return;
            ArrayList<String> w = RivKeywordMute.words();
            if (!w.contains(v)) w.add(v);
            RivKeywordMute.setWords(w);
            rebuild();
        }));
        ArrayList<String> words = RivKeywordMute.words();
        if (words.isEmpty()) {
            addInfo(RivT.t("No words yet. A message in any chat that contains one of your words arrives silently, with no notification. Capital letters don't matter.",
                    "لا توجد كلمات بعد. أي رسالة في أي محادثة تحتوي إحدى كلماتك تصل بصمت دون إشعار. حالة الأحرف لا تهم."));
            return;
        }
        addHeader(RivT.t("Silenced words", "الكلمات المكتومة"));
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i);
            addCell(word, "", i != words.size() - 1, () -> confirm(word, RivT.t("Stop muting this word?", "إيقاف كتم هذه الكلمة؟"), RivT.t("Remove", "حذف"), () -> {
                ArrayList<String> w = RivKeywordMute.words();
                w.remove(word);
                RivKeywordMute.setWords(w);
                rebuild();
            }), null);
        }
        addInfo(RivT.t("Tap a word to remove it.", "اضغط على كلمة لحذفها."));
    }
}
