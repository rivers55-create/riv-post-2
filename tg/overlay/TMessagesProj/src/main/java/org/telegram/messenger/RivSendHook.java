package org.telegram.messenger;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

/**
 * Riv Post: runs before every message is sent. It (1) turns off the link preview for chats where
 * you switched previews off, and (2) with "Undo send" on, holds a plain text message for five
 * seconds with an Undo button.
 */
public final class RivSendHook {

    public static final String UNDO_SEND = "undoSend";
    private static final int SECONDS = 5;

    /** Set while our own code sends, so the hook lets it straight through. */
    public static volatile boolean skip = false;

    private static Pending current;

    private RivSendHook() {
    }

    private static final class Pending {
        SendMessagesHelper helper;
        SendMessagesHelper.SendMessageParams params;
        FrameLayout host;
        View bar;
        TextView label;
        int left = SECONDS;
        boolean done;
    }

    /** Returns true when the message was taken over (the caller must not send it now). */
    public static boolean intercept(SendMessagesHelper helper, SendMessagesHelper.SendMessageParams p) {
        try {
            if (p == null || skip) return false;
            if (p.peer != 0 && RivChatFlags.noPreview(p.peer)) p.searchLinks = false;
            if (!RivPrefs.b(UNDO_SEND, false)) return false;
            if (p.message == null || p.message.trim().isEmpty()) return false;
            if (p.caption != null || p.location != null || p.photo != null || p.document != null || p.user != null
                    || p.game != null || p.poll != null || p.todo != null || p.invoice != null || p.mediaWebPage != null
                    || p.path != null || p.retryMessageObject != null || p.scheduleDate != 0 || p.videoEditedInfo != null
                    || p.sendingStory != null || (p.params != null && !p.params.isEmpty()) || p.replyMarkup != null) {
                return false;
            }
            Activity activity = LaunchActivity.instance;
            if (activity == null) return false;
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout)) return false;
            if (current != null) finish(current, true); // keep the order: the earlier one goes now
            final Pending pending = new Pending();
            pending.helper = helper;
            pending.params = p;
            pending.host = (FrameLayout) content;
            show(pending, activity);
            current = pending;
            tick(pending);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private static void show(Pending pending, Activity activity) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2262626);
        bg.setCornerRadius(AndroidUtilities.dp(22));
        bar.setBackground(bg);
        bar.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(6), 0);
        bar.setElevation(AndroidUtilities.dp(8));
        TextView label = new TextView(activity);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        bar.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView undo = new TextView(activity);
        undo.setText(RivT.t("UNDO", "تراجع"));
        undo.setTextColor(0xFF6AB3F3);
        undo.setTypeface(AndroidUtilities.bold());
        undo.setTextSize(14);
        undo.setGravity(Gravity.CENTER);
        undo.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        undo.setOnClickListener(v -> finish(pending, false));
        bar.addView(undo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(44)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = AndroidUtilities.dp(130);
        pending.host.addView(bar, lp);
        pending.bar = bar;
        pending.label = label;
        update(pending);
    }

    private static void update(Pending pending) {
        if (pending.label != null) {
            pending.label.setText(RivT.t("Sending in ", "الإرسال بعد ") + pending.left + RivT.t(" s", " ث"));
        }
    }

    private static void tick(Pending pending) {
        AndroidUtilities.runOnUIThread(() -> {
            if (pending.done) return;
            pending.left--;
            if (pending.left <= 0) {
                finish(pending, true);
            } else {
                update(pending);
                tick(pending);
            }
        }, 1000);
    }

    private static void finish(Pending pending, boolean send) {
        if (pending.done) return;
        pending.done = true;
        if (current == pending) current = null;
        try {
            if (pending.bar != null && pending.host != null) pending.host.removeView(pending.bar);
        } catch (Throwable ignored) {
        }
        if (send) {
            skip = true;
            try {
                pending.helper.sendMessage(pending.params);
            } finally {
                skip = false;
            }
        } else {
            giveTextBack(pending.params);
        }
    }

    private static void giveTextBack(SendMessagesHelper.SendMessageParams p) {
        try {
            org.telegram.ui.ActionBar.BaseFragment f = LaunchActivity.getSafeLastFragment();
            if (f instanceof ChatActivity && ((ChatActivity) f).getDialogId() == p.peer) {
                org.telegram.ui.Components.ChatActivityEnterView v = ((ChatActivity) f).getChatActivityEnterViewForStickers();
                if (v != null) {
                    v.setFieldText(p.message);
                    return;
                }
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("riv", p.message));
            Toast.makeText(ApplicationLoader.applicationContext, RivT.t("Not sent. The text is on your clipboard.", "لم تُرسل. النص في الحافظة."), Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
