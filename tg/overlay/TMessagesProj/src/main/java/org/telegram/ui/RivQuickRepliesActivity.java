package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivQuickReplies;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Riv Post: saved quick-reply snippets. Tap a snippet to copy it, ready to paste into any
 * chat's message box. (Auto-inserting straight into the compose field needs a hook into
 * Telegram's own ChatActivity input bar, which this build does not add yet -- see the TODO
 * in apply.sh. Copy-and-paste works today without touching any real Telegram file.)
 */
public class RivQuickRepliesActivity extends RivFragment {

    private LinearLayout layout;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivQuickReplies));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        ScrollView scroll = new ScrollView(context);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        frame.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        rebuild(context);
        return fragmentView;
    }

    private void rebuild(Context context) {
        layout.removeAllViews();

        TextSettingsCell add = new TextSettingsCell(context);
        add.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setText(LocaleController.getString(R.string.RivAddQuickReply), true);
        add.setOnClickListener(v -> askText(context));
        layout.addView(add);

        ArrayList<String> items = RivQuickReplies.load();
        if (!items.isEmpty()) {
            HeaderCell header = new HeaderCell(context);
            header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            header.setText(LocaleController.getString(R.string.RivQuickReplies));
            layout.addView(header);
            for (int i = 0; i < items.size(); i++) {
                String text = items.get(i);
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                cell.setText(text, i != items.size() - 1);
                cell.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("riv_quick_reply", text));
                    android.widget.Toast.makeText(context, LocaleController.getString(R.string.RivCopied), android.widget.Toast.LENGTH_SHORT).show();
                });
                cell.setOnLongClickListener(v -> {
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle(text);
                    b.setMessage(LocaleController.getString(R.string.RivDeleteQuickReply));
                    b.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                        RivQuickReplies.remove(text);
                        rebuild(context);
                    });
                    b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    showDialog(b.create());
                    return true;
                });
                layout.addView(cell);
            }
        }

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivQuickRepliesInfo));
        layout.addView(info);
    }

    private void askText(Context context) {
        if (getParentActivity() == null) return;
        EditText edit = new EditText(context);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setHint(LocaleController.getString(R.string.RivQuickReplyHint));
        FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivAddQuickReply));
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String text = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (!text.isEmpty()) {
                RivQuickReplies.add(text);
                rebuild(context);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }
}
