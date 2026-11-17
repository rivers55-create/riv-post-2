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

    // ------------------------------------------------------------------ quick replies

    /** Pick a saved quick reply and drop it into the message box where the cursor is. */
    public static void showQuickReplies(BaseFragment fragment, android.widget.EditText field) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || field == null) return;
        ArrayList<String> items = org.telegram.messenger.RivQuickReplies.load();
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.RivQuickReplies));
        if (items.isEmpty()) {
            b.setMessage(LocaleController.getString(R.string.RivNoQuickReplies));
            b.setPositiveButton(LocaleController.getString(R.string.RivAddQuickReply), (d, w) -> fragment.presentFragment(new RivQuickRepliesActivity()));
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        } else {
            b.setItems(items.toArray(new CharSequence[0]), (d, w) -> {
                String text = items.get(w);
                android.text.Editable e = field.getText();
                if (e == null) return;
                int at = Math.max(0, field.getSelectionStart());
                e.insert(Math.min(at, e.length()), text);
                field.requestFocus();
                AndroidUtilities.showKeyboard(field);
            });
            b.setNeutralButton(LocaleController.getString(R.string.RivManage), (d, w) -> fragment.presentFragment(new RivQuickRepliesActivity()));
        }
        fragment.showDialog(b.create());
    }

    // ------------------------------------------------------------------ chat options

    private static final long[] EXPIRE_STEPS = {0, 3600_000L, 6 * 3600_000L, 24 * 3600_000L, 7 * 24 * 3600_000L, 30 * 24 * 3600_000L};
    private static final int[] FONT_STEPS = {0, 12, 14, 16, 18, 20, 24};

    private static String expireLabel(long ms) {
        String v = ms == 0 ? org.telegram.messenger.RivT.t("off", "متوقف")
                : ms < 24 * 3600_000L ? (ms / 3600_000L) + org.telegram.messenger.RivT.t(" h", " س")
                : (ms / (24 * 3600_000L)) + org.telegram.messenger.RivT.t(" days", " يوم");
        return org.telegram.messenger.RivT.t("Auto-clean on this phone after: ", "تنظيف تلقائي من هذا الهاتف بعد: ") + v;
    }

    private static String fontLabel(int size) {
        return org.telegram.messenger.RivT.t("Text size in this chat: ", "حجم النص في هذه المحادثة: ")
                + (size == 0 ? org.telegram.messenger.RivT.t("default", "الافتراضي") : String.valueOf(size));
    }

    /** Per-chat switches: no auto-download, quiet hours, lock, block screenshots, link previews, pin on top, auto-clean, text size. */
    public static void showChatOptions(BaseFragment fragment, long dialogId) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        String quietLine = LocaleController.formatString(R.string.RivQuietHoursFor, org.telegram.messenger.RivQuiet.format(org.telegram.messenger.RivQuiet.from()), org.telegram.messenger.RivQuiet.format(org.telegram.messenger.RivQuiet.to()));
        CharSequence[] labels = {
                LocaleController.getString(R.string.RivNoAutoDownloadHere),
                quietLine,
                LocaleController.getString(R.string.RivLockThisChat),
                org.telegram.messenger.RivT.t("Block screenshots and screen recording", "منع لقطات الشاشة وتسجيلها"),
                org.telegram.messenger.RivT.t("Hide link previews in this chat", "إخفاء معاينة الروابط في هذه المحادثة"),
                org.telegram.messenger.RivT.t("Pin on top (no limit, this phone only)", "تثبيت في الأعلى (بلا حد، على هذا الهاتف فقط)"),
        };
        boolean[] checked = {
                org.telegram.messenger.RivPrefs.inList(org.telegram.messenger.RivPrefs.NO_AUTODL_CHATS, dialogId),
                org.telegram.messenger.RivPrefs.inList(org.telegram.messenger.RivPrefs.QUIET_CHATS, dialogId),
                org.telegram.messenger.RivPrefs.inList(org.telegram.messenger.RivPrefs.LOCKED_CHATS, dialogId),
                org.telegram.messenger.RivChatFlags.secure(dialogId),
                org.telegram.messenger.RivChatFlags.noPreview(dialogId),
                org.telegram.messenger.RivChatFlags.localPinRank(dialogId) >= 0,
        };
        final long[] expire = {org.telegram.messenger.RivChatFlags.expireMs(dialogId)};
        final int[] font = {org.telegram.messenger.RivChatFlags.fontSize(dialogId)};
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), 0);
        for (int i = 0; i < labels.length; i++) {
            org.telegram.ui.Cells.CheckBoxCell cell = new org.telegram.ui.Cells.CheckBoxCell(activity, 1);
            cell.setText(labels[i], "", checked[i], false);
            final int idx = i;
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx], true);
            });
            col.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }
        // two rows that step through a few choices each time you tap them
        org.telegram.ui.Cells.CheckBoxCell expireCell = new org.telegram.ui.Cells.CheckBoxCell(activity, 1);
        expireCell.setText(expireLabel(expire[0]), "", expire[0] != 0, false);
        expireCell.setOnClickListener(v -> {
            int at = 0;
            for (int k = 0; k < EXPIRE_STEPS.length; k++) if (EXPIRE_STEPS[k] == expire[0]) at = k;
            expire[0] = EXPIRE_STEPS[(at + 1) % EXPIRE_STEPS.length];
            expireCell.setText(expireLabel(expire[0]), "", expire[0] != 0, false);
        });
        col.addView(expireCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        org.telegram.ui.Cells.CheckBoxCell fontCell = new org.telegram.ui.Cells.CheckBoxCell(activity, 1);
        fontCell.setText(fontLabel(font[0]), "", font[0] != 0, false);
        fontCell.setOnClickListener(v -> {
            int at = 0;
            for (int k = 0; k < FONT_STEPS.length; k++) if (FONT_STEPS[k] == font[0]) at = k;
            font[0] = FONT_STEPS[(at + 1) % FONT_STEPS.length];
            fontCell.setText(fontLabel(font[0]), "", font[0] != 0, false);
        });
        col.addView(fontCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(col);
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.RivChatOptions));
        b.setView(scroll);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            setInList(org.telegram.messenger.RivPrefs.NO_AUTODL_CHATS, dialogId, checked[0]);
            setInList(org.telegram.messenger.RivPrefs.QUIET_CHATS, dialogId, checked[1]);
            if (checked[2]) RivChatLock.markUnlocked(dialogId); // don't ask right after locking it
            setInList(org.telegram.messenger.RivPrefs.LOCKED_CHATS, dialogId, checked[2]);
            org.telegram.messenger.RivChatFlags.set(org.telegram.messenger.RivChatFlags.SECURE, dialogId, checked[3]);
            org.telegram.messenger.RivChatFlags.set(org.telegram.messenger.RivChatFlags.NO_PREVIEW, dialogId, checked[4]);
            boolean wasPinned = org.telegram.messenger.RivChatFlags.localPinRank(dialogId) >= 0;
            if (wasPinned != checked[5]) {
                org.telegram.messenger.RivChatFlags.setLocalPin(dialogId, checked[5]);
                try {
                    fragment.getMessagesController().sortDialogs(null);
                    fragment.getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
                } catch (Throwable ignored) {
                }
            }
            org.telegram.messenger.RivChatFlags.setValue(org.telegram.messenger.RivChatFlags.EXPIRE, dialogId, expire[0]);
            org.telegram.messenger.RivChatFlags.setValue(org.telegram.messenger.RivChatFlags.FONT, dialogId, font[0]);
            if (expire[0] != 0) org.telegram.messenger.RivLocalExpire.sweep();
            if (fragment instanceof ChatActivity) {
                RivChatLock.guard((ChatActivity) fragment); // block-screenshots flag and text size apply right away
                if (font[0] != 0 || org.telegram.messenger.RivChatFlags.fontSize(dialogId) != 0) {
                    Toast.makeText(activity, org.telegram.messenger.RivT.t("Text size applies when you reopen the chat.", "يُطبَّق حجم النص عند إعادة فتح المحادثة."), Toast.LENGTH_SHORT).show();
                }
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.setNeutralButton(org.telegram.messenger.RivT.t("More…", "المزيد…"), (d, w) -> {
            ArrayList<CharSequence> items = new ArrayList<>();
            items.add(org.telegram.messenger.RivT.t("Tags…", "الوسوم…"));
            items.add(org.telegram.messenger.RivT.t("Notes for this chat", "ملاحظات هذه المحادثة"));
            if (fragment instanceof ChatActivity) {
                items.add(org.telegram.messenger.RivT.t("Quick poll…", "استطلاع سريع…"));
                items.add(org.telegram.messenger.RivT.t("Quick checklist…", "قائمة مهام سريعة…"));
            }
            AlertDialog.Builder c = new AlertDialog.Builder(activity);
            c.setTitle(org.telegram.messenger.RivT.t("More", "المزيد"));
            c.setItems(items.toArray(new CharSequence[0]), (dd, which) -> {
                if (which == 0) RivTagsActivity.showTagDialog(fragment, dialogId, null);
                else if (which == 1) fragment.presentFragment(new RivNotesActivity(dialogId));
                else if (which == 2) quickCompose((ChatActivity) fragment, true);
                else quickCompose((ChatActivity) fragment, false);
            });
            fragment.showDialog(c.create());
        });
        fragment.showDialog(b.create());
    }

    // ------------------------------------------------------------------ quick poll / checklist

    /** Type a title and one line per option, then send it as a poll, or as a checklist. */
    private static void quickCompose(ChatActivity chat, boolean poll) {
        Activity activity = chat.getParentActivity();
        if (activity == null) return;
        android.widget.EditText title = new android.widget.EditText(activity);
        title.setHint(poll ? org.telegram.messenger.RivT.t("Question", "السؤال") : org.telegram.messenger.RivT.t("Title", "العنوان"));
        title.setSingleLine(false);
        android.widget.EditText lines = new android.widget.EditText(activity);
        lines.setHint(poll ? org.telegram.messenger.RivT.t("One option per line (2 to 10)", "خيار في كل سطر (من 2 إلى 10)") : org.telegram.messenger.RivT.t("One item per line", "عنصر في كل سطر"));
        lines.setMinLines(3);
        lines.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        col.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        col.addView(lines, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(poll ? org.telegram.messenger.RivT.t("Quick poll", "استطلاع سريع") : org.telegram.messenger.RivT.t("Quick checklist", "قائمة مهام سريعة"));
        b.setView(col);
        b.setPositiveButton(org.telegram.messenger.RivT.t("Send", "إرسال"), (d, w) -> {
            String t = title.getText().toString().trim();
            ArrayList<String> items = new ArrayList<>();
            for (String line : lines.getText().toString().split("\n")) {
                line = line.trim();
                if (!line.isEmpty()) items.add(line);
            }
            if (t.isEmpty() || items.isEmpty() || (poll && (items.size() < 2 || items.size() > 10))) {
                Toast.makeText(activity, poll ? org.telegram.messenger.RivT.t("Write a question and 2 to 10 options.", "اكتب سؤالاً و2 إلى 10 خيارات.") : org.telegram.messenger.RivT.t("Write a title and at least one item.", "اكتب عنواناً وعنصراً واحداً على الأقل."), Toast.LENGTH_LONG).show();
                return;
            }
            try {
                if (poll) sendPoll(chat, t, items);
                else sendChecklist(chat, t, items);
            } catch (Throwable e) {
                FileLog.e(e);
                Toast.makeText(activity, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        chat.showDialog(b.create());
    }

    private static void sendPoll(ChatActivity chat, String question, ArrayList<String> options) {
        TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
        poll.poll = new TLRPC.TL_poll();
        poll.poll.creator = true;
        poll.poll.question = new TLRPC.TL_textWithEntities();
        poll.poll.question.text = question;
        poll.poll.question.entities = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            TLRPC.PollAnswer answer = new TLRPC.TL_pollAnswer();
            answer.text = new TLRPC.TL_textWithEntities();
            answer.text.text = options.get(i);
            answer.text.entities = new ArrayList<>();
            answer.option = new byte[]{(byte) (48 + i)};
            poll.poll.answers.add(answer);
        }
        poll.results = new TLRPC.TL_pollResults();
        chat.sendPoll(poll, null, new org.telegram.ui.Components.poll.PollAttachedMediaPack(), new ArrayList<Integer>(), true, 0, 0);
    }

    private static void sendChecklist(ChatActivity chat, String title, ArrayList<String> items) {
        if (chat.getUserConfig().isPremium()) {
            TLRPC.TL_messageMediaToDo todo = new TLRPC.TL_messageMediaToDo();
            todo.todo = new TLRPC.TodoList();
            todo.todo.others_can_append = false;
            todo.todo.others_can_complete = true;
            todo.todo.title = new TLRPC.TL_textWithEntities();
            todo.todo.title.text = title;
            todo.todo.title.entities = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                TLRPC.TodoItem item = new TLRPC.TodoItem();
                item.title = new TLRPC.TL_textWithEntities();
                item.title.text = items.get(i);
                item.title.entities = new ArrayList<>();
                item.id = 1 + todo.todo.list.size();
                todo.todo.list.add(item);
            }
            chat.sendTodo(todo, true, 0, 0);
            return;
        }
        // Telegram's own checklists need Premium; without it, send the list as ordinary text
        StringBuilder sb = new StringBuilder(title);
        for (String it : items) sb.append("\n\u2610 ").append(it);
        chat.getSendMessagesHelper().sendMessage(org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(sb.toString(), chat.getDialogId()));
    }

    private static void setInList(String key, long id, boolean on) {
        if (org.telegram.messenger.RivPrefs.inList(key, id) != on) org.telegram.messenger.RivPrefs.toggleInList(key, id);
    }

    // ------------------------------------------------------------------ chat stats

    private static final String[] STAT_KEYS = {"all", "photos", "videos", "music", "files", "voice", "links"};

    /** Counts what's in a chat (messages, photos, videos, songs, files, voice, links) from Telegram's servers. */
    public static void showChatStats(BaseFragment fragment, long dialogId) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        int account = fragment.getCurrentAccount();
        TLRPC.InputPeer peer = fragment.getMessagesController().getInputPeer(dialogId);
        int[] counts = new int[STAT_KEYS.length];
        int[] done = {0};
        AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        fragment.showDialog(progress);
        for (int i = 0; i < STAT_KEYS.length; i++) {
            final int idx = i;
            org.telegram.tgnet.TLObject req;
            if (i == 0) {
                TLRPC.TL_messages_getHistory h = new TLRPC.TL_messages_getHistory();
                h.peer = peer;
                h.limit = 1;
                req = h;
            } else {
                TLRPC.TL_messages_search q = new TLRPC.TL_messages_search();
                q.peer = peer;
                q.q = "";
                q.limit = 1;
                switch (i) {
                    case 1: q.filter = new TLRPC.TL_inputMessagesFilterPhotos(); break;
                    case 2: q.filter = new TLRPC.TL_inputMessagesFilterVideo(); break;
                    case 3: q.filter = new TLRPC.TL_inputMessagesFilterMusic(); break;
                    case 4: q.filter = new TLRPC.TL_inputMessagesFilterDocument(); break;
                    case 5: q.filter = new TLRPC.TL_inputMessagesFilterVoice(); break;
                    default: q.filter = new TLRPC.TL_inputMessagesFilterUrl(); break;
                }
                req = q;
            }
            org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages r = (TLRPC.messages_Messages) response;
                    counts[idx] = Math.max(r.count, r.messages.size());
                } else {
                    counts[idx] = -1;
                }
                done[0]++;
                if (done[0] == STAT_KEYS.length) {
                    try {
                        progress.dismiss();
                    } catch (Throwable ignored) {
                    }
                    StringBuilder sb = new StringBuilder();
                    int[] labels = {R.string.RivStatMessages, R.string.RivStatPhotos, R.string.RivStatVideos, R.string.RivStatMusic, R.string.RivStatFiles, R.string.RivStatVoice, R.string.RivStatLinks};
                    for (int k = 0; k < labels.length; k++) {
                        sb.append(LocaleController.getString(labels[k])).append(": ").append(counts[k] < 0 ? "\u2014" : String.format(java.util.Locale.US, "%,d", counts[k])).append('\n');
                    }
                    AlertDialog.Builder b = new AlertDialog.Builder(activity);
                    b.setTitle(LocaleController.getString(R.string.RivChatStats));
                    b.setMessage(sb.toString().trim());
                    b.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    fragment.showDialog(b.create());
                }
            }));
        }
    }
}
