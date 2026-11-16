package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Riv Post: every message you've scheduled, across chats, on one page (soonest first).
 *
 * Telegram has no "all scheduled messages" request, so this asks each of your 80 most recent
 * chats for its scheduled messages, one chat after another (so Telegram's speed limits aren't
 * hit). Tap a row to open that chat's scheduled messages, where they can be edited or sent now.
 */
public class RivScheduledActivity extends RivFragment {

    private static final int MAX_CHATS = 80;

    private static class Row {
        long dialogId;
        TLRPC.Message msg;
    }

    private final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<Long> queue = new ArrayList<>();
    private int asked;
    private boolean done;
    private LinearLayout listBox;
    private TextView status;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        for (TLRPC.Dialog d : getMessagesController().getAllDialogs()) {
            if (d == null || d.isFolder || d.id == 0) continue;
            queue.add(d.id);
            if (queue.size() >= MAX_CHATS) break;
        }
        askNext();
        return super.onFragmentCreate();
    }

    private boolean destroyed;

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }

    private void askNext() {
        if (destroyed) return;
        if (asked >= queue.size()) {
            done = true;
            AndroidUtilities.runOnUIThread(this::refresh);
            return;
        }
        long dialogId = queue.get(asked++);
        TLRPC.TL_messages_getScheduledHistory req = new TLRPC.TL_messages_getScheduledHistory();
        req.peer = getMessagesController().getInputPeer(dialogId);
        if (req.peer == null || req.peer instanceof TLRPC.TL_inputPeerEmpty) {
            askNext();
            return;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                for (TLRPC.Message m : res.messages) {
                    if (m == null || m instanceof TLRPC.TL_messageEmpty) continue;
                    Row r = new Row();
                    r.dialogId = dialogId;
                    r.msg = m;
                    rows.add(r);
                }
            }
            if (asked % 8 == 0) refresh();
            askNext();
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivScheduledTitle));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));

        status = new TextView(context);
        status.setTextSize(13);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        col.addView(status);

        listBox = new LinearLayout(context);
        listBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(listBox);

        refresh();
        return fragmentView;
    }

    private void refresh() {
        if (listBox == null) return;
        Context context = listBox.getContext();
        Collections.sort(rows, (a, b) -> Integer.compare(a.msg.date, b.msg.date));
        if (!done) {
            status.setText(LocaleController.formatString(R.string.RivScheduledChecking, asked, queue.size()));
        } else if (rows.isEmpty()) {
            status.setText(LocaleController.getString(R.string.RivScheduledNone));
        } else {
            status.setText(LocaleController.formatString(R.string.RivScheduledCount, rows.size()));
        }
        listBox.removeAllViews();
        for (Row r : rows) listBox.addView(rowView(context, r));
    }

    private View rowView(Context context, Row r) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        BackupImageView avatar = new BackupImageView(context);
        avatar.getImageReceiver().setRoundRadius(AndroidUtilities.dp(22));
        AvatarDrawable ad = new AvatarDrawable();
        String name;
        if (r.dialogId > 0) {
            TLRPC.User u = getMessagesController().getUser(r.dialogId);
            ad.setInfo(currentAccount, u);
            avatar.setForUserOrChat(u, ad);
            name = u != null ? UserObject.getUserName(u) : String.valueOf(r.dialogId);
        } else {
            TLRPC.Chat c = getMessagesController().getChat(-r.dialogId);
            ad.setInfo(currentAccount, c);
            avatar.setForUserOrChat(c, ad);
            name = c != null ? c.title : String.valueOf(r.dialogId);
        }
        row.addView(avatar, LayoutHelper.createLinear(44, 44));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));

        TextView top = new TextView(context);
        top.setText(name + "  ·  " + LocaleController.formatDateTime(r.msg.date, true));
        top.setTextSize(14);
        top.setMaxLines(1);
        top.setEllipsize(TextUtils.TruncateAt.END);
        top.setTypeface(AndroidUtilities.bold());
        top.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        texts.addView(top);

        TextView body = new TextView(context);
        String text = r.msg.message;
        if (TextUtils.isEmpty(text)) text = LocaleController.getString(R.string.RivScheduledMedia);
        body.setText(text);
        body.setTextSize(13);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        texts.addView(body);

        row.setOnClickListener(v -> {
            Bundle args = new Bundle();
            if (r.dialogId > 0) args.putLong("user_id", r.dialogId);
            else args.putLong("chat_id", -r.dialogId);
            args.putInt("chat_mode", ChatActivity.MODE_SCHEDULED);
            presentFragment(new ChatActivity(args));
        });
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        View gap = new View(context);
        wrap.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));
        return wrap;
    }
}
