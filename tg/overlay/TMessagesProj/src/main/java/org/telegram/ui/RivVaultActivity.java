package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RivVault;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Riv Post Vault: hide chosen chats behind a PIN. */
public class RivVaultActivity extends RivFragment {

    private LinearLayout layout;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivVault));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
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

        TextSettingsCell pin = new TextSettingsCell(context);
        pin.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        pin.setText(LocaleController.getString(RivVault.hasPin() ? R.string.RivVaultChangePin : R.string.RivVaultSetPin), true);
        pin.setOnClickListener(v -> askNewPin(context));
        layout.addView(pin);

        TextCheckCell reveal = new TextCheckCell(context);
        reveal.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        reveal.setTextAndCheck(LocaleController.getString(R.string.RivVaultShow), RivVault.revealed, true);
        reveal.setOnClickListener(v -> {
            if (RivVault.revealed) {
                RivVault.revealed = false;
                reveal.setChecked(false);
                reloadDialogs();
                rebuild(context);
            } else {
                askPin(context, () -> {
                    RivVault.revealed = true;
                    reveal.setChecked(true);
                    reloadDialogs();
                    rebuild(context);
                });
            }
        });
        layout.addView(reveal);

        TextSettingsCell add = new TextSettingsCell(context);
        add.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setText(LocaleController.getString(R.string.RivVaultAdd), false);
        add.setOnClickListener(v -> askPin(context, () -> pickChat()));
        layout.addView(add);

        if (RivVault.revealed && !RivVault.ids().isEmpty()) {
            HeaderCell header = new HeaderCell(context);
            header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            header.setText(LocaleController.getString(R.string.RivVaultHidden));
            layout.addView(header);
            ArrayList<Long> ids = new ArrayList<>(RivVault.ids());
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                cell.setText(titleOf(id), i != ids.size() - 1);
                cell.setOnClickListener(v -> {
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle(titleOf(id));
                    b.setMessage(LocaleController.getString(R.string.RivVaultUnhide));
                    b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
                        RivVault.unhide(currentAccount, id);
                        reloadDialogs();
                        rebuild(context);
                    });
                    b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    showDialog(b.create());
                });
                layout.addView(cell);
            }
        }

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivVaultInfo));
        layout.addView(info);
    }

    private String titleOf(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) {
                return org.telegram.messenger.UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return String.valueOf(dialogId);
    }

    private void reloadDialogs() {
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    private void pickChat() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (MessagesStorage.TopicKey key : dids) {
                RivVault.hide(currentAccount, key.dialogId);
            }
            fragment1.finishFragment();
            reloadDialogs();
            rebuild(getParentActivity());
            return true;
        });
        presentFragment(fragment);
    }

    private EditText pinField(Context context, FrameLayout wrap) {
        EditText edit = new EditText(context);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        edit.setHint(LocaleController.getString(R.string.RivVaultPinHint));
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        wrap.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return edit;
    }

    private void askNewPin(Context context) {
        Runnable set = () -> {
            FrameLayout wrap = new FrameLayout(context);
            EditText edit = pinField(context, wrap);
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.RivVaultSetPin));
            b.setView(wrap);
            b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
                String pin = edit.getText() == null ? "" : edit.getText().toString().trim();
                if (pin.length() >= 4) {
                    RivVault.setPin(pin);
                    rebuild(context);
                }
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        };
        if (RivVault.hasPin()) {
            askPin(context, set);
        } else {
            set.run();
        }
    }

    private void askPin(Context context, Runnable onOk) {
        if (!RivVault.hasPin()) {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.RivVault));
            b.setMessage(LocaleController.getString(R.string.RivVaultNeedPin));
            b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> askNewPin(context));
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
            return;
        }
        if (RivVault.revealed) {
            onOk.run();
            return;
        }
        if (org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.VAULT_FINGERPRINT, true) && android.os.Build.VERSION.SDK_INT >= 28 && getParentActivity() != null) {
            // Riv Post: fingerprint first; "Use PIN" (or any failure) falls back to the PIN box
            final boolean[] fellBack = {false};
            final Runnable toPin = () -> {
                if (fellBack[0]) return;
                fellBack[0] = true;
                askPinBox(context, onOk);
            };
            try {
                android.hardware.biometrics.BiometricPrompt prompt = new android.hardware.biometrics.BiometricPrompt.Builder(getParentActivity())
                        .setTitle(LocaleController.getString(R.string.RivVault))
                        .setNegativeButton(LocaleController.getString(R.string.RivUsePin), getParentActivity().getMainExecutor(), (d, w) -> toPin.run())
                        .build();
                prompt.authenticate(new android.os.CancellationSignal(), getParentActivity().getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        onOk.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                && errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                            toPin.run();
                        }
                    }
                });
                return;
            } catch (Throwable ignored) {
            }
        }
        askPinBox(context, onOk);
    }

    private void askPinBox(Context context, Runnable onOk) {
        if (getParentActivity() == null) return;
        FrameLayout wrap = new FrameLayout(context);
        EditText edit = pinField(context, wrap);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivVaultEnterPin));
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String pin = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (RivVault.checkPin(pin)) {
                onOk.run();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }
}
