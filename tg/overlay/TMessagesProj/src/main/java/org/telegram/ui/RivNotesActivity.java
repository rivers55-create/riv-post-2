package org.telegram.ui;

import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivNotes;
import org.telegram.messenger.RivT;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Riv Post: private notes. A notebook that is not Saved Messages: pin notes, search by tapping
 * "Search", and tie a note to a chat (open "Notes for this chat" from a chat's Riv options).
 */
public class RivNotesActivity extends RivToolScreen {

    private final long chatFilter;
    private String query = "";

    public RivNotesActivity() {
        this(0);
    }

    public RivNotesActivity(long chatFilter) {
        this.chatFilter = chatFilter;
    }

    @Override
    protected String title() {
        return chatFilter != 0 ? RivT.t("Notes · ", "ملاحظات · ") + chatTitle(chatFilter) : RivT.t("Notes", "الملاحظات");
    }

    @Override
    protected void build() {
        addAction(RivT.t("New note", "ملاحظة جديدة"), true, () -> edit(null));
        addAction(query.isEmpty() ? RivT.t("Search notes", "بحث في الملاحظات") : RivT.t("Search: ", "بحث: ") + query + "  ✕", false, () -> {
            if (!query.isEmpty()) {
                query = "";
                rebuild();
                return;
            }
            ask(RivT.t("Search notes", "بحث في الملاحظات"), RivT.t("Word", "كلمة"), "", false, v -> {
                query = v.trim().toLowerCase();
                rebuild();
            });
        });
        ArrayList<JSONObject> list = chatFilter != 0 ? RivNotes.forChat(chatFilter) : RivNotes.all();
        int shown = 0;
        for (JSONObject o : list) {
            String t = o.optString("title"), x = o.optString("text");
            if (!query.isEmpty() && !(t + "\n" + x).toLowerCase().contains(query)) continue;
            if (shown == 0) addHeader(RivT.t("Your notes", "ملاحظاتك"));
            shown++;
            String shownTitle = (o.optBoolean("pin") ? "📌 " : "") + (t.isEmpty() ? firstLine(x) : t);
            String sub = o.optLong("chat") != 0 ? chatTitle(o.optLong("chat")) : "";
            addCell(shownTitle, sub, true, () -> edit(o), () -> confirm(shownTitle, RivT.t("Delete this note?", "حذف هذه الملاحظة؟"), LocaleController.getString(R.string.Delete), () -> {
                RivNotes.remove(o.optLong("id"));
                rebuild();
            }));
            if (!x.isEmpty()) addText(x.length() > 160 ? x.substring(0, 160) + "…" : x, false);
        }
        if (shown == 0) addInfo(RivT.t("No notes yet. Tap New note. Long-press a note to delete it.", "لا توجد ملاحظات بعد. اضغط ملاحظة جديدة. اضغط مطولاً على ملاحظة لحذفها."));
        else addInfo(RivT.t("Tap a note to edit it. Long-press to delete.", "اضغط على ملاحظة لتعديلها. اضغط مطولاً للحذف."));
    }

    private static String firstLine(String s) {
        int n = s.indexOf('\n');
        String l = n > 0 ? s.substring(0, n) : s;
        return l.length() > 40 ? l.substring(0, 40) + "…" : l;
    }

    private void edit(JSONObject note) {
        if (getParentActivity() == null) return;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        EditText title = new EditText(ctx);
        title.setHint(RivT.t("Title (optional)", "العنوان (اختياري)"));
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText body = new EditText(ctx);
        body.setHint(RivT.t("Note", "الملاحظة"));
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(4);
        body.setGravity(android.view.Gravity.TOP);
        CheckBox pin = new CheckBox(ctx);
        pin.setText(RivT.t("Pin to top", "تثبيت في الأعلى"));
        if (note != null) {
            title.setText(note.optString("title"));
            body.setText(note.optString("text"));
            pin.setChecked(note.optBoolean("pin"));
        }
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        box.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        box.addView(pin, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(note == null ? RivT.t("New note", "ملاحظة جديدة") : RivT.t("Edit note", "تعديل الملاحظة"));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String t = title.getText().toString().trim(), x = body.getText().toString().trim();
            if (t.isEmpty() && x.isEmpty()) return;
            long id = note != null ? note.optLong("id") : System.currentTimeMillis();
            long chat = note != null ? note.optLong("chat") : chatFilter;
            RivNotes.put(id, t, x, chat, pin.isChecked());
            rebuild();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }
}
