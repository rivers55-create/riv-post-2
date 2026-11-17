package org.telegram.ui;

import android.app.Activity;

import org.telegram.messenger.RivChatFlags;
import org.telegram.messenger.RivLinkSafety;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivSendHook;
import org.telegram.messenger.RivT;
import org.telegram.messenger.RivVault;
import org.telegram.messenger.RivWatchers;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/** Riv Post: privacy and chat extras in one place (undo send, link check, decoy view, one-hand mode, scanners). */
public class RivPrivacyActivity extends RivToolScreen {

    @Override
    protected String title() {
        return RivT.t("Privacy & chat extras", "الخصوصية وإضافات المحادثة");
    }

    @Override
    protected void build() {
        addHeader(RivT.t("Sending and reading", "الإرسال والقراءة"));
        addSwitch(RivT.t("Undo send (5 seconds)", "التراجع عن الإرسال (5 ثوانٍ)"),
                RivT.t("A text message waits five seconds with an Undo button before it goes out.", "تنتظر الرسالة النصية خمس ثوانٍ مع زر تراجع قبل إرسالها."),
                RivSendHook.UNDO_SEND, false, true);
        addSwitch(RivT.t("Link safety check", "فحص أمان الروابط"),
                RivT.t("Asks first when a link looks like a trick: raw IP, look-alike letters, shortened, fake brand, program download.", "يسأل أولاً عندما يبدو الرابط خدعة: IP مباشر أو أحرف مشابهة أو مختصر أو علامة مزيفة أو تنزيل برنامج."),
                RivLinkSafety.KEY, true, true);
        addSwitch(RivT.t("Call recording reminder", "تذكير تسجيل المكالمات"),
                RivT.t("Shows a short reminder when a call starts: tell the other person before you record.", "يعرض تذكيراً قصيراً عند بدء المكالمة: أخبر الطرف الآخر قبل التسجيل."),
                RivWatchers.CALL_REMINDER, false, true);
        addCell(RivT.t("Mute by keyword", "كتم بالكلمات"), "", true, () -> presentFragment(new RivKeywordMuteActivity()), null);
        addCell(RivT.t("Auto-reply when busy", "رد تلقائي عند الانشغال"), org.telegram.messenger.RivAutoReply.active() ? RivT.t("On", "يعمل") : RivT.t("Off", "متوقف"), false, () -> presentFragment(new RivAutoReplyActivity()), null);
        addInfo(RivT.t("Per-chat switches (block screenshots, hide link previews, pin on top, auto-clean, text size) are in a chat's ⋮ menu → Riv options.",
                "مفاتيح كل محادثة (منع لقطات الشاشة، إخفاء معاينة الروابط، التثبيت في الأعلى، التنظيف التلقائي، حجم النص) في قائمة ⋮ داخل المحادثة ← خيارات Riv."));

        if (!RivVault.decoyActive()) {
            addHeader(RivT.t("Decoy view", "العرض الوهمي"));
            addCell(RivT.t("Decoy PIN", "رمز العرض الوهمي"), RivVault.hasDecoyPin() ? RivT.t("Set", "مضبوط") : RivT.t("Not set", "غير مضبوط"), true, this::decoyPin, null);
            ArrayList<String> ids = RivPrefs.list(RivVault.DECOY_IDS);
            addCell(RivT.t("Chats to show in the decoy view", "المحادثات الظاهرة في العرض الوهمي"), String.valueOf(ids.size()), false, this::decoyChats, null);
            addInfo(RivT.t("Type # and the decoy PIN into the chat-list search box and only the chats you picked stay visible; everything else is hidden. To come back, type # and your Vault PIN. You need a Vault PIN first (Settings → Riv Post → Vault). Notifications from hidden chats can still appear.",
                    "اكتب # ثم رمز العرض الوهمي في مربع البحث بقائمة المحادثات فتبقى المحادثات التي اخترتها فقط ظاهرة ويُخفى الباقي. للعودة اكتب # ثم رمز الخزنة. تحتاج رمز الخزنة أولاً (الإعدادات ← Riv Post ← الخزنة). قد تظهر إشعارات المحادثات المخفية."));
        }

        addHeader(RivT.t("One hand", "اليد الواحدة"));
        addAction(RivOneHand.isOn() ? RivT.t("Turn off one-hand mode", "إيقاف وضع اليد الواحدة") : RivT.t("Turn on one-hand mode", "تشغيل وضع اليد الواحدة"), true, () -> {
            Activity a = getParentActivity();
            if (a != null) RivOneHand.toggle(a);
        });
        addSwitch(RivT.t("Shrink toward the right side", "التصغير نحو الجهة اليمنى"),
                RivT.t("Off = shrink toward the left (left-handed).", "إيقاف = التصغير نحو اليسار (لليد اليسرى)."), RivOneHand.RIGHT, true, false);

        addHeader(RivT.t("Scanners", "الماسحات"));
        addCell(RivT.t("Text scanner", "ماسح النصوص"), "", true, () -> presentFragment(new RivScanActivity(RivScanActivity.TEXT)), null);
        addCell(RivT.t("Document scanner (to PDF)", "ماسح المستندات (إلى PDF)"), "", true, () -> presentFragment(new RivScanActivity(RivScanActivity.DOCUMENT)), null);
        addCell(RivT.t("PDF tools", "أدوات PDF"), "", false, () -> presentFragment(new RivPdfToolsActivity()), null);
    }

    private void decoyPin() {
        if (!RivVault.hasPin()) {
            info(RivT.t("Set a Vault PIN first", "اضبط رمز الخزنة أولاً"), RivT.t("The Vault PIN is what brings you back from the decoy view. Settings → Riv Post → Vault.", "رمز الخزنة هو ما يعيدك من العرض الوهمي. الإعدادات ← Riv Post ← الخزنة."));
            return;
        }
        if (RivVault.hasDecoyPin()) {
            choose(RivT.t("Decoy PIN", "رمز العرض الوهمي"), new CharSequence[]{RivT.t("Change it", "تغييره"), RivT.t("Remove it", "إزالته")}, i -> {
                if (i == 0) newDecoyPin();
                else {
                    RivVault.clearDecoy();
                    rebuild();
                }
            });
        } else {
            newDecoyPin();
        }
    }

    private void newDecoyPin() {
        ask(RivT.t("New decoy PIN (4 or more digits)", "رمز وهمي جديد (4 أرقام أو أكثر)"), "", "", false, v -> {
            v = v.trim();
            if (v.length() < 4) {
                toast(RivT.t("Use at least 4 characters.", "استخدم 4 رموز على الأقل."));
                return;
            }
            if (!RivVault.setDecoyPin(v)) {
                toast(RivT.t("It must be different from your Vault PIN.", "يجب أن يختلف عن رمز الخزنة."));
                return;
            }
            toast(RivT.t("Decoy PIN saved.", "تم حفظ الرمز الوهمي."));
            rebuild();
        });
    }

    private void decoyChats() {
        if (getParentActivity() == null) return;
        ArrayList<TLRPC.Dialog> all = new ArrayList<>(getMessagesController().getAllDialogs());
        final ArrayList<Long> ids = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();
        for (TLRPC.Dialog d : all) {
            if (d == null || d instanceof TLRPC.TL_dialogFolder) continue;
            ids.add(d.id);
            names.add(chatTitle(d.id));
            if (ids.size() >= 150) break;
        }
        final boolean[] checked = new boolean[ids.size()];
        android.widget.LinearLayout col = new android.widget.LinearLayout(getParentActivity());
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (int i = 0; i < checked.length; i++) {
            checked[i] = RivPrefs.inList(RivVault.DECOY_IDS, ids.get(i));
            final int idx = i;
            org.telegram.ui.Cells.CheckBoxCell cell = new org.telegram.ui.Cells.CheckBoxCell(getParentActivity(), 1);
            cell.setText(names.get(i), "", checked[i], false);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx], true);
            });
            col.addView(cell, org.telegram.ui.Components.LayoutHelper.createLinear(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, 50));
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(getParentActivity());
        scroll.addView(col);
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(RivT.t("Chats to show", "المحادثات الظاهرة"));
        b.setView(scroll);
        b.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OK), (d, w) -> {
            for (int i = 0; i < checked.length; i++) RivChatFlags.set(RivVault.DECOY_IDS, ids.get(i), checked[i]);
            rebuild();
        });
        b.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        showDialog(b.create());
    }
}
