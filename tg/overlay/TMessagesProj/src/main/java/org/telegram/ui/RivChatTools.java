package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.RivEdits;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;

/** Riv Post: chat-screen extras that don't need to live inside Telegram's own ChatActivity. */
public class RivChatTools {

    /**
     * Downloads every selected message's file (photos, videos, music, documents) inside
     * Telegram, and with {@code saveToPhone} also copies each one to the phone's own folders
     * (Music for songs, Downloads for other files, Pictures for photos).
     */
    public static void downloadAll(BaseFragment fragment, ArrayList<MessageObject> picked, boolean saveToPhone) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null || picked == null || picked.isEmpty()) return;
        int account = fragment.getCurrentAccount();
        ArrayList<MessageObject> songs = new ArrayList<>();
        ArrayList<MessageObject> files = new ArrayList<>();
        ArrayList<MessageObject> photos = new ArrayList<>();
        for (MessageObject m : picked) {
            if (m == null || m.messageOwner == null) continue;
            TLRPC.Document doc = m.getDocument();
            if (doc != null) {
                if (m.isMusic()) songs.add(m); else files.add(m);
            } else if (m.photoThumbs != null && !m.photoThumbs.isEmpty()) {
                photos.add(m);
            }
        }
        int total = songs.size() + files.size() + photos.size();
        if (total == 0) {
            Toast.makeText(activity, LocaleController.getString(R.string.RivNothingToDownload), Toast.LENGTH_SHORT).show();
            return;
        }

        if (saveToPhone) {
            // Telegram's own saver downloads anything missing first, then copies it out.
            if (!songs.isEmpty()) MediaController.saveFilesFromMessages(activity, fragment.getAccountInstance(), songs, null);
            if (!files.isEmpty()) MediaController.saveFilesFromMessages(activity, fragment.getAccountInstance(), files, null);
        } else {
            for (MessageObject m : songs) loadDocument(account, m);
            for (MessageObject m : files) loadDocument(account, m);
        }
        for (MessageObject m : photos) {
            loadPhoto(account, m);
        }
        if (saveToPhone && !photos.isEmpty()) {
            Context appContext = activity.getApplicationContext();
            new Thread(() -> {
                for (MessageObject m : photos) {
                    try {
                        File f = FileLoader.getInstance(account).getPathToMessage(m.messageOwner);
                        // wait for the photo to finish downloading (up to 2 minutes each)
                        for (int i = 0; i < 240 && (f == null || !f.exists()); i++) {
                            Thread.sleep(500);
                            f = FileLoader.getInstance(account).getPathToMessage(m.messageOwner);
                        }
                        if (f != null && f.exists()) {
                            final String path = f.getAbsolutePath();
                            AndroidUtilities.runOnUIThread(() -> MediaController.saveFile(path, appContext, 0, null, null));
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }).start();
        }
        Toast.makeText(activity, LocaleController.formatString(saveToPhone ? R.string.RivSavingCount : R.string.RivDownloadingCount, total), Toast.LENGTH_LONG).show();
    }

    private static void loadDocument(int account, MessageObject m) {
        try {
            TLRPC.Document doc = m.getDocument();
            if (doc == null) return;
            DownloadController.getInstance(account).startDownloadFile(doc, m);
            FileLoader.getInstance(account).loadFile(doc, m, FileLoader.PRIORITY_NORMAL, 0);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void loadPhoto(int account, MessageObject m) {
        try {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(m.photoThumbs, AndroidUtilities.getPhotoSize());
            if (size == null) return;
            FileLoader.getInstance(account).loadFile(ImageLocation.getForObject(size, m.photoThumbsObject), m, "jpg", FileLoader.PRIORITY_NORMAL, 0);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Shows earlier versions of an edited message, oldest first, then the current text. */
    public static void showEditHistory(BaseFragment fragment, MessageObject m) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null || m == null) return;
        ArrayList<RivEdits.Version> versions = RivEdits.get(m);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22), AndroidUtilities.dp(8));
        for (RivEdits.Version v : versions) {
            addVersion(activity, list, LocaleController.formatDateAudio(v.date, true), v.text);
        }
        String now = m.messageOwner != null ? m.messageOwner.message : null;
        if (!TextUtils.isEmpty(now)) {
            addVersion(activity, list, LocaleController.getString(R.string.RivEditNow), now);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.RivEditHistory));
        b.setView(scroll);
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(b.create());
    }

    private static void addVersion(Context context, LinearLayout list, String when, String text) {
        TextView whenView = new TextView(context);
        whenView.setText(when);
        whenView.setTextSize(12);
        whenView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        whenView.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(2));
        list.addView(whenView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(15);
        textView.setTextIsSelectable(true);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        list.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
