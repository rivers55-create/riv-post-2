package org.telegram.ui;

import org.telegram.messenger.RivAutoReply;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;

/** Riv Post: auto-reply when busy. Pick for how long, and what the reply says. */
public class RivAutoReplyActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Auto-reply when busy", "رد تلقائي عند الانشغال");
    }

    @Override
    protected void build() {
        boolean on = RivAutoReply.active();
        long until = RivAutoReply.until();
        String state = !on ? RivT.t("Off", "متوقف") : until == Long.MAX_VALUE ? RivT.t("On until you turn it off", "يعمل حتى توقفه") : RivT.t("On until ", "يعمل حتى ") + when(until);
        addCell(RivT.t("Status", "الحالة"), state, true, null, null);
        if (on) addAction(RivT.t("Turn off", "إيقاف"), true, () -> {
            RivAutoReply.setBusy(0);
            rebuild();
        });
        addHeader(RivT.t("Busy for", "مشغول لمدة"));
        addAction(RivT.t("1 hour", "ساعة"), true, () -> set(1));
        addAction(RivT.t("3 hours", "3 ساعات"), true, () -> set(3));
        addAction(RivT.t("8 hours", "8 ساعات"), true, () -> set(8));
        addAction(RivT.t("Until I turn it off", "حتى أوقفه"), false, () -> set(-1));
        addHeader(RivT.t("Reply text", "نص الرد"));
        addCell(RivAutoReply.text(), "", false, () -> ask(RivT.t("Reply text", "نص الرد"), RivAutoReply.defaultText(), RivAutoReply.text(), true, v -> {
            RivPrefs.set(RivAutoReply.TEXT, v.trim());
            rebuild();
        }), null);
        addInfo(RivT.t("While busy, the first message from each person in a private chat gets this reply once. That person isn't answered again for six hours. Bots, channels and groups are skipped. It only works while Riv Post is running.",
                "أثناء الانشغال، تحصل أول رسالة من كل شخص في محادثة خاصة على هذا الرد مرة واحدة، ولا يُرد على الشخص نفسه مجدداً قبل ست ساعات. تُتجاهل البوتات والقنوات والمجموعات. يعمل فقط أثناء تشغيل Riv Post."));
    }

    private void set(int hours) {
        RivAutoReply.setBusy(hours);
        toast(RivT.t("Auto-reply is on.", "الرد التلقائي يعمل."));
        rebuild();
    }
}
