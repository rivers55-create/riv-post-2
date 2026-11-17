package org.telegram.ui;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivChatTags;
import org.telegram.messenger.RivT;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Riv Post: your own tags for chats (Work, Family...). Give a chat tags from its Riv options; browse the chats under each tag here. */
public class RivTagsActivity extends RivToolScreen {

    private String openTag;

    @Override
    protected String title() {
        return RivT.t("Chat tags", "وسوم المحادثات");
    }

    @Override
    protected void build() {
        ArrayList<String> tags = RivChatTags.allTags();
        if (openTag != null) {
            addAction("←  " + RivT.t("All tags", "كل الوسوم"), true, () -> {
                openTag = null;
                rebuild();
            });
            addHeader("#" + openTag);
            ArrayList<Long> chats = RivChatTags.chatsWith(openTag);
            for (int i = 0; i < chats.size(); i++) {
                final long id = chats.get(i);
                addCell(chatTitle(id), "", i != chats.size() - 1, () -> openChat(id, 0), () -> showTagDialog(this, id, this::rebuild));
            }
            addInfo(RivT.t("Tap to open the chat. Long-press to change its tags.", "اضغط لفتح المحادثة. اضغط مطولاً لتغيير وسومها."));
            return;
        }
        if (tags.isEmpty()) {
            addInfo(RivT.t("No tags yet. Open a chat, tap ⋮ → Riv options → Tags & notes → Tags, and add one.",
                    "لا توجد وسوم بعد. افتح محادثة ثم ⋮ ← خيارات Riv ← وسوم وملاحظات ← الوسوم، وأضف وسماً."));
            return;
        }
        addHeader(RivT.t("Tags", "الوسوم"));
        for (int i = 0; i < tags.size(); i++) {
            final String tag = tags.get(i);
            addCell("●  #" + tag, String.valueOf(RivChatTags.chatsWith(tag).size()), i != tags.size() - 1, () -> {
                openTag = tag;
                rebuild();
            }, null).setTextColor(RivChatTags.colorOf(tag));
        }
        addInfo(RivT.t("Tags are private to this phone.", "الوسوم خاصة بهذا الهاتف فقط."));
    }

    /** The tick-boxes dialog for one chat. */
    public static void showTagDialog(BaseFragment fragment, long dialogId, Runnable done) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        final ArrayList<String> mine = RivChatTags.tagsOf(dialogId);
        final ArrayList<String> all = RivChatTags.allTags();
        for (String t : mine) if (!all.contains(t)) all.add(t);
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), 0);
        final boolean[] on = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            on[i] = mine.contains(all.get(i));
            CheckBoxCell cell = new CheckBoxCell(activity, 1);
            cell.setText("#" + all.get(i), "", on[i], false);
            final int idx = i;
            cell.setOnClickListener(v -> {
                on[idx] = !on[idx];
                cell.setChecked(on[idx], true);
            });
            col.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }
        EditText fresh = new EditText(activity);
        fresh.setHint(RivT.t("New tag (words separated by commas)", "وسم جديد (افصل بفاصلة)"));
        fresh.setInputType(InputType.TYPE_CLASS_TEXT);
        col.addView(fresh, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 6, 18, 0));
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(RivT.t("Tags", "الوسوم"));
        b.setView(col);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            ArrayList<String> result = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) if (on[i]) result.add(all.get(i));
            for (String t : fresh.getText().toString().split(",")) {
                String s = t.trim().replace("#", "");
                if (!s.isEmpty() && !result.contains(s)) result.add(s);
            }
            RivChatTags.setTags(dialogId, result);
            if (done != null) done.run();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }
}
