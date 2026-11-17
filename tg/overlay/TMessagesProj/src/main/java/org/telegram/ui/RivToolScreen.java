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
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Riv Post: base for the small Riv Tools screens. A title, a scrolling column, and short helpers
 * for the rows every tool needs (header, note, tap row, switch, input box, confirm, pick-one).
 * A screen only fills {@link #build()}; call {@link #rebuild()} after anything changes.
 */
public abstract class RivToolScreen extends RivFragment {

    protected LinearLayout layout;
    protected Context ctx;

    protected abstract String title();

    protected abstract void build();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        ctx = context;
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(title());
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
        rebuild();
        return fragmentView;
    }

    protected void rebuild() {
        if (layout == null) return;
        layout.removeAllViews();
        try {
            build();
        } catch (Throwable t) {
            addInfo("Error: " + t);
        }
    }

    // ---------------------------------------------------------------- rows

    protected void addHeader(String text) {
        HeaderCell cell = new HeaderCell(ctx);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setText(text);
        layout.addView(cell);
    }

    protected void addInfo(String text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(ctx);
        cell.setBackground(Theme.getThemedDrawableByKey(ctx, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        cell.setText(text);
        layout.addView(cell);
    }

    /** Blue "button" row. */
    protected void addAction(String text, boolean divider, Runnable click) {
        TextSettingsCell cell = new TextSettingsCell(ctx);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        cell.setText(text, divider);
        cell.setOnClickListener(v -> click.run());
        layout.addView(cell);
    }

    /** Plain tap row with an optional value on the right. */
    protected TextSettingsCell addCell(String title, String value, boolean divider, Runnable click, Runnable longClick) {
        TextSettingsCell cell = new TextSettingsCell(ctx);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (value == null || value.isEmpty()) cell.setText(title, divider);
        else cell.setTextAndValue(title, value, divider, true);
        if (click != null) cell.setOnClickListener(v -> click.run());
        if (longClick != null) {
            cell.setOnLongClickListener(v -> {
                longClick.run();
                return true;
            });
        }
        layout.addView(cell);
        return cell;
    }

    /** A row of wrapped body text (results, logs). */
    protected TextView addText(CharSequence text, boolean mono) {
        TextView tv = new TextView(ctx);
        tv.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setTextSize(15);
        tv.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        if (mono) tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setText(text);
        layout.addView(tv);
        return tv;
    }

    protected void addSwitch(String title, String info, String prefKey, boolean def, boolean divider) {
        TextCheckCell cell = new TextCheckCell(ctx);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        boolean cur = RivPrefs.b(prefKey, def);
        if (info != null) cell.setTextAndValueAndCheck(title, info, cur, true, divider);
        else cell.setTextAndCheck(title, cur, divider);
        cell.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(prefKey, def);
            RivPrefs.set(prefKey, now);
            cell.setChecked(now);
        });
        layout.addView(cell);
    }

    // ---------------------------------------------------------------- dialogs

    protected void toast(String text) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
    }

    protected void copy(String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("riv", text));
        toast(LocaleController.getString(R.string.RivCopied));
    }

    protected interface Text {
        void got(String value);
    }

    protected interface Pick {
        void got(int index);
    }

    /** One text box. multiline lets the note keep several lines. */
    protected void ask(String title, String hint, String initial, boolean multiline, Text done) {
        if (getParentActivity() == null) return;
        EditText edit = new EditText(ctx);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        edit.setHint(hint);
        edit.setText(initial == null ? "" : initial);
        edit.setSelection(edit.getText().length());
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> done.got(edit.getText() == null ? "" : edit.getText().toString()));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    protected void confirm(String title, String message, String yes, Runnable run) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setMessage(message);
        b.setPositiveButton(yes, (d, w) -> run.run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    protected void choose(String title, CharSequence[] items, Pick pick) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setItems(items, (d, w) -> pick.got(w));
        showDialog(b.create());
    }

    protected void info(String title, String message) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setMessage(message);
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(b.create());
    }

    protected String chatTitle(long dialogId) {
        if (dialogId > 0) {
            org.telegram.tgnet.TLRPC.User u = getMessagesController().getUser(dialogId);
            if (u != null) return org.telegram.messenger.UserObject.getUserName(u);
        } else {
            org.telegram.tgnet.TLRPC.Chat c = getMessagesController().getChat(-dialogId);
            if (c != null) return c.title;
        }
        return String.valueOf(dialogId);
    }

    /** Opens a chat, optionally at one message. */
    protected void openChat(long dialogId, int messageId) {
        android.os.Bundle args = new android.os.Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else args.putLong("chat_id", -dialogId);
        if (messageId > 0) args.putInt("message_id", messageId);
        presentFragment(new ChatActivity(args));
    }

    protected static String when(long millis) {
        return new java.text.SimpleDateFormat("d MMM  HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(millis));
    }
}
