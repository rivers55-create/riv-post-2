package org.telegram.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivVault;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashMap;

/**
 * Riv Post: lock single chats with your fingerprint (or the phone's screen lock).
 *
 * Turn it on per chat in the chat's "Riv options". Opening a locked chat covers it until you
 * unlock; backing out of the prompt closes the chat. After unlocking, the chat stays open for
 * a minute (so viewing a photo and coming back doesn't ask again).
 */
public final class RivChatLock {

    private static final long GRACE_MS = 60_000;
    private static final HashMap<Long, Long> unlockedAt = new HashMap<>();
    private static final String TAG = "rivChatLock";

    private RivChatLock() {
    }

    public static boolean isLocked(long dialogId) {
        return dialogId != 0 && RivPrefs.inList(RivPrefs.LOCKED_CHATS, dialogId);
    }

    public static void markUnlocked(long dialogId) {
        unlockedAt.put(dialogId, System.currentTimeMillis());
    }

    /** Called from ChatActivity.onPause: the minute of grace starts when you leave the chat. */
    public static void onLeave(long dialogId) {
        if (unlockedAt.containsKey(dialogId)) unlockedAt.put(dialogId, System.currentTimeMillis());
        org.telegram.messenger.RivLocalExpire.sweep();
    }

    /** Block screenshots and screen recording in chats that asked for it. */
    private static void applySecure(ChatActivity chat) {
        try {
            Activity a = chat.getParentActivity();
            if (a == null) return;
            if (org.telegram.messenger.RivChatFlags.secure(chat.getDialogId())) {
                a.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
                secureOn = true;
            } else if (secureOn) {
                a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                secureOn = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean secureOn;

    /** Called when the chat closes for good. */
    public static void onClose(ChatActivity chat) {
        try {
            if (secureOn && chat != null && chat.getParentActivity() != null) {
                chat.getParentActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            }
            secureOn = false;
        } catch (Throwable ignored) {
        }
        org.telegram.messenger.RivFontSize.restore();
    }

    /** Called from ChatActivity.onResume. */
    public static void guard(ChatActivity chat) {
        if (chat == null) return;
        long dialogId = chat.getDialogId();
        applySecure(chat);
        org.telegram.messenger.RivFontSize.applyFor(dialogId);
        if (!isLocked(dialogId)) return;
        Long at = unlockedAt.get(dialogId);
        if (at != null && System.currentTimeMillis() - at < GRACE_MS) return;
        Activity activity = chat.getParentActivity();
        View fv = chat.getFragmentView();
        if (activity == null || !(fv instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) fv;
        if (parent.findViewWithTag(TAG) == null) {
            parent.addView(cover(activity, chat), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        ask(chat);
    }

    private static View cover(Activity activity, ChatActivity chat) {
        FrameLayout f = new FrameLayout(activity);
        f.setTag(TAG);
        f.setBackgroundColor(Color.BLACK);
        f.setClickable(true);
        f.setFocusable(true);
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        RivIcon lock = new RivIcon(activity);
        lock.setKind(RivIcon.LOCK);
        lock.setColor(Color.WHITE);
        col.addView(lock, LayoutHelper.createLinear(56, 56));
        TextView t = new TextView(activity);
        t.setText(LocaleController.getString(R.string.RivChatLocked));
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        col.addView(t, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        TextView unlock = new TextView(activity);
        unlock.setText(LocaleController.getString(R.string.RivChatUnlock));
        unlock.setTextColor(Color.BLACK);
        unlock.setTypeface(AndroidUtilities.bold());
        unlock.setGravity(Gravity.CENTER);
        unlock.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(AndroidUtilities.dp(20));
        unlock.setBackground(bg);
        unlock.setOnClickListener(v -> ask(chat));
        col.addView(unlock, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 20, 0, 0));
        f.addView(col, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        return f;
    }

    private static void unlocked(ChatActivity chat) {
        markUnlocked(chat.getDialogId());
        View fv = chat.getFragmentView();
        if (fv instanceof ViewGroup) {
            View c = fv.findViewWithTag(TAG);
            if (c != null) ((ViewGroup) fv).removeView(c);
        }
    }

    private static void ask(ChatActivity chat) {
        Activity activity = chat.getParentActivity();
        if (activity == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.hardware.biometrics.BiometricPrompt prompt = new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.RivChatLocked))
                        .setDeviceCredentialAllowed(true)
                        .build();
                prompt.authenticate(new android.os.CancellationSignal(), activity.getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        unlocked(chat);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                || errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                            chat.finishFragment();
                        }
                        // other errors (e.g. no screen lock set up) leave the cover with its Unlock button
                    }
                });
                return;
            } catch (Throwable ignored) {
            }
        }
        // older phones: use the Vault PIN if one is set, otherwise nothing to check against
        if (!RivVault.hasPin()) {
            unlocked(chat);
            return;
        }
        android.widget.EditText edit = new android.widget.EditText(activity);
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.RivVaultEnterPin));
        b.setView(edit);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            if (RivVault.checkPin(edit.getText() == null ? "" : edit.getText().toString().trim())) unlocked(chat);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> chat.finishFragment());
        chat.showDialog(b.create());
    }
}
