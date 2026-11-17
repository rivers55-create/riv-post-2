package org.telegram.ui;

import org.telegram.messenger.RivT;

/** Riv Post: the Riv Tools hub. One place that opens every small tool. */
public class RivToolsActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Riv Tools", "أدوات Riv");
    }

    private void row(String en, String ar, boolean divider, Runnable open) {
        addCell(RivT.t(en, ar), "", divider, open, null);
    }

    @Override
    protected void build() {
        addHeader(RivT.t("Your messages", "رسائلك"));
        row("Starred messages", "الرسائل المميزة", true, () -> presentFragment(new RivStarredActivity()));
        row("Mentions inbox", "صندوق الإشارات", true, () -> presentFragment(new RivMentionsActivity()));
        row("Notes", "الملاحظات", true, () -> presentFragment(new RivNotesActivity()));
        row("Chat tags", "وسوم المحادثات", true, () -> presentFragment(new RivTagsActivity()));
        row("Channel digest", "ملخص القنوات", false, () -> presentFragment(new RivDigestActivity()));

        addHeader(RivT.t("Links and files", "الروابط والملفات"));
        row("Link saver", "حافظ الروابط", true, () -> presentFragment(new RivLinkSaverActivity()));
        row("Link cleaner", "منظف الروابط", true, () -> presentFragment(new RivLinkCleanerActivity()));
        row("Clipboard history", "سجل الحافظة", true, () -> presentFragment(new RivClipboardActivity()));
        row("Files (zip, unzip, rename)", "الملفات (ضغط وفك وإعادة تسمية)", true, () -> presentFragment(new RivFilesActivity()));
        row("Audio cutter", "قاص الصوت", true, () -> presentFragment(new RivAudioCutActivity()));
        row("Duplicate finder", "كاشف التكرار", false, () -> presentFragment(new RivDuplicatesActivity()));

        addHeader(RivT.t("Everyday", "يومياً"));
        row("Calculator", "الآلة الحاسبة", true, () -> presentFragment(new RivMiniTools.Calculator()));
        row("Unit & currency converter", "محول الوحدات والعملات", true, () -> presentFragment(new RivMiniTools.Converter()));
        row("Random picker", "الاختيار العشوائي", true, () -> presentFragment(new RivMiniTools.RandomPicker()));
        row("Countdowns", "العد التنازلي", false, () -> presentFragment(new RivMiniTools.Countdown()));

        addHeader(RivT.t("Network", "الشبكة"));
        row("Speed test", "اختبار السرعة", true, () -> presentFragment(new RivSpeedTestActivity()));
        row("Proxy history", "سجل الوكلاء", true, () -> presentFragment(new RivProxyHistoryActivity()));
        row("Connection log", "سجل الاتصال", false, () -> presentFragment(new RivConnLogActivity()));

        addHeader(RivT.t("Look", "المظهر"));
        row("Bottom bar tabs", "تبويبات الشريط السفلي", false, () -> presentFragment(new RivBarEditorActivity()));
        addInfo(RivT.t("Star a message: long-press it in a chat. Tags and chat notes: ⋮ → Riv options → Tags & notes. Call notes: long-press a call in Calls.",
                "لتمييز رسالة: اضغط مطولاً عليها في المحادثة. الوسوم وملاحظات المحادثة: ⋮ ← خيارات Riv ← وسوم وملاحظات. ملاحظات المكالمات: اضغط مطولاً على مكالمة في المكالمات."));
    }
}
