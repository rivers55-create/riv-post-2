package org.telegram.ui;

import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivT;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Riv Post: a toolbox button in the Chats top bar, next to the ghost and the shield. Tap it for a
 * menu (same look as the three-dot menu) with every Riv tool, so nobody has to dig through
 * Settings. Can be switched off in Settings.
 */
public class RivToolsButton {

    public static final int MENU_ID = -4749;
    private static final int GAP_BASE = -5100;

    private interface Make {
        BaseFragment make();
    }

    public static void add(BaseFragment fragment, ActionBarMenu menu) {
        if (menu == null || !RivPrefs.b(RivPrefs.TOOLS_BUTTON, true)) return;
        RivGhostButton.GhostDrawable icon = new RivGhostButton.GhostDrawable("🧰");
        icon.setOn(true);
        final ActionBarMenuItem item = menu.addItem(MENU_ID, icon);
        item.setContentDescription(RivT.t("Riv Tools", "أدوات Riv"));

        int id = 1;
        // your messages
        row(fragment, item, id++, R.drawable.msg_fave, "Starred messages", "الرسائل المميزة", RivStarredActivity::new);
        row(fragment, item, id++, R.drawable.msg_mention, "Mentions inbox", "صندوق الإشارات", RivMentionsActivity::new);
        row(fragment, item, id++, R.drawable.msg_edit, "Notes", "الملاحظات", RivNotesActivity::new);
        row(fragment, item, id++, R.drawable.menu_tag_plus, "Chat tags", "وسوم المحادثات", RivTagsActivity::new);
        row(fragment, item, id++, R.drawable.msg_channel, "Channel digest", "ملخص القنوات", RivDigestActivity::new);
        item.addGap(GAP_BASE);
        // links and files
        row(fragment, item, id++, R.drawable.msg_link, "Link saver", "حافظ الروابط", RivLinkSaverActivity::new);
        row(fragment, item, id++, R.drawable.msg_clear, "Link cleaner", "منظف الروابط", RivLinkCleanerActivity::new);
        row(fragment, item, id++, R.drawable.msg_copy, "Clipboard history", "سجل الحافظة", RivClipboardActivity::new);
        row(fragment, item, id++, R.drawable.msg_view_file, "Files (zip, unzip)", "الملفات (ضغط وفك)", RivFilesActivity::new);
        row(fragment, item, id++, R.drawable.msg_voice_headphones, "Audio cutter", "قاص الصوت", RivAudioCutActivity::new);
        row(fragment, item, id++, R.drawable.msg_replace, "Duplicate finder", "كاشف التكرار", RivDuplicatesActivity::new);
        item.addGap(GAP_BASE - 1);
        // everyday
        row(fragment, item, id++, R.drawable.msg_stats, "Calculator", "الآلة الحاسبة", RivMiniTools.Calculator::new);
        row(fragment, item, id++, R.drawable.msg_forward_replace, "Unit & currency converter", "محول الوحدات والعملات", RivMiniTools.Converter::new);
        row(fragment, item, id++, R.drawable.menu_random, "Random picker", "الاختيار العشوائي", RivMiniTools.RandomPicker::new);
        row(fragment, item, id++, R.drawable.menu_premium_clock, "Countdowns", "العد التنازلي", RivMiniTools.Countdown::new);
        item.addGap(GAP_BASE - 2);
        // network
        row(fragment, item, id++, R.drawable.msg_speed, "Speed test", "اختبار السرعة", RivSpeedTestActivity::new);
        row(fragment, item, id++, R.drawable.msg_zoomout_stats, "Proxy history", "سجل الوكلاء", RivProxyHistoryActivity::new);
        row(fragment, item, id++, R.drawable.msg_log, "Connection log", "سجل الاتصال", RivConnLogActivity::new);
        item.addGap(GAP_BASE - 3);
        // privacy, scanners and one hand
        row(fragment, item, id++, R.drawable.msg_secret, "Privacy & chat extras", "الخصوصية وإضافات المحادثة", RivPrivacyActivity::new);
        row(fragment, item, id++, R.drawable.msg_mute, "Mute by keyword", "كتم بالكلمات", RivKeywordMuteActivity::new);
        row(fragment, item, id++, R.drawable.msg_reply_small, "Auto-reply when busy", "رد تلقائي عند الانشغال", RivAutoReplyActivity::new);
        row(fragment, item, id++, R.drawable.msg_photos, "Text scanner", "ماسح النصوص", () -> new RivScanActivity(RivScanActivity.TEXT));
        row(fragment, item, id++, R.drawable.msg_openin, "Document scanner", "ماسح المستندات", () -> new RivScanActivity(RivScanActivity.DOCUMENT));
        row(fragment, item, id++, R.drawable.msg_view_file, "PDF tools", "أدوات PDF", RivPdfToolsActivity::new);
        ActionBarMenuSubItem oneHand = item.addSubItem(id++, R.drawable.msg_screencast, RivT.t("One-hand mode", "وضع اليد الواحدة"));
        oneHand.setOnClickListener(v -> {
            item.toggleSubMenu();
            RivOneHand.toggle(fragment.getParentActivity());
        });
        item.addGap(GAP_BASE - 4);
        row(fragment, item, id++, R.drawable.msg_reorder, "Bottom bar tabs", "تبويبات الشريط السفلي", RivBarEditorActivity::new);
        row(fragment, item, id++, R.drawable.msg_settings, "All Riv settings", "كل إعدادات Riv", RivSettingsActivity::new);
    }

    private static void row(BaseFragment fragment, ActionBarMenuItem item, int id, int icon, String en, String ar, Make make) {
        ActionBarMenuSubItem sub = item.addSubItem(id, icon, RivT.t(en, ar));
        sub.setOnClickListener(v -> {
            item.toggleSubMenu();
            fragment.presentFragment(make.make());
        });
    }
}
