package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Riv Post: picks a chat and dumps its messages to a local .txt file the user can share.
 * Pages backward through the chat with TL_messages_search (empty filter), the same request
 * type RivVideosActivity/RivMusicActivity already use, just without a media filter.
 */
public class RivExportChatActivity extends BaseFragment {

    private long dialogId = 0;
    private TextSettingsCell pickCell;
    private AlertDialog progressDialog;
    private final ArrayList<TLRPC.Message> collected = new ArrayList<>();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivExportChat));
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
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        frame.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        pickCell = new TextSettingsCell(context);
        pickCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        pickCell.setText(LocaleController.getString(R.string.RivPickChatToExport), true);
        pickCell.setOnClickListener(v -> pickChat());
        layout.addView(pickCell);

        TextSettingsCell exportCell = new TextSettingsCell(context);
        exportCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        exportCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        exportCell.setText(LocaleController.getString(R.string.RivStartExport), false);
        exportCell.setOnClickListener(v -> startExport());
        layout.addView(exportCell);

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivExportChatInfo));
        layout.addView(info);

        return fragmentView;
    }

    private void pickChat() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (MessagesStorage.TopicKey key : dids) {
                dialogId = key.dialogId;
                break;
            }
            fragment1.finishFragment();
            pickCell.setText(titleOf(dialogId), true);
            return true;
        });
        presentFragment(fragment);
    }

    private String titleOf(long id) {
        if (id == 0) return LocaleController.getString(R.string.RivPickChatToExport);
        if (id > 0) {
            TLRPC.User user = getMessagesController().getUser(id);
            if (user != null) return UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            if (chat != null) return chat.title;
        }
        return String.valueOf(id);
    }

    private void startExport() {
        if (dialogId == 0 || getParentActivity() == null) return;
        collected.clear();
        progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(true);
        showDialog(progressDialog);
        fetchPage(0);
    }

    private void fetchPage(int offsetId) {
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = getMessagesController().getInputPeer(dialogId);
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = 100;
        req.offset_id = offsetId;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                int minId = Integer.MAX_VALUE;
                for (TLRPC.Message msg : res.messages) {
                    collected.add(msg);
                    if (msg.id < minId) minId = msg.id;
                }
                boolean more = !res.messages.isEmpty() && collected.size() < 20000 && minId != Integer.MAX_VALUE;
                if (more) {
                    fetchPage(minId);
                } else {
                    finishExport();
                }
            } else {
                finishExport();
            }
        }));
    }

    private void finishExport() {
        try {
            if (progressDialog != null) progressDialog.dismiss();
        } catch (Throwable ignored) {
        }
        try {
            java.util.Collections.sort(collected, (a, b) -> Integer.compare(a.date, b.date));
            StringBuilder sb = new StringBuilder();
            sb.append(titleOf(dialogId)).append('\n').append('\n');
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            for (TLRPC.Message msg : collected) {
                String from = fromLabel(msg);
                String text = !TextUtils.isEmpty(msg.message) ? msg.message : "[" + LocaleController.getString(R.string.RivMediaMessage) + "]";
                sb.append('[').append(fmt.format(new Date(msg.date * 1000L))).append("] ").append(from).append(": ").append(text).append('\n');
            }
            File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "riv_export");
            dir.mkdirs();
            File out = new File(dir, "chat_export_" + System.currentTimeMillis() + ".txt");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
            shareFile(out);
        } catch (Throwable t) {
            if (getParentActivity() != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setMessage(LocaleController.getString(R.string.RivExportFailed));
                b.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showDialog(b.create());
            }
        }
    }

    private String fromLabel(TLRPC.Message msg) {
        long fromId = MessageObject.getFromChatId(msg);
        if (fromId > 0) {
            TLRPC.User u = getMessagesController().getUser(fromId);
            if (u != null) return UserObject.getUserName(u);
        } else if (fromId < 0) {
            TLRPC.Chat c = getMessagesController().getChat(-fromId);
            if (c != null) return c.title;
        }
        return String.valueOf(fromId);
    }

    private void shareFile(File file) {
        if (getParentActivity() == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(Intent.createChooser(intent, LocaleController.getString(R.string.RivExportChat)));
        } catch (Throwable ignored) {
        }
    }
}
