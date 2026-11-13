package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.RivVipContacts;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.LinkedHashSet;

/**
 * Riv Post: mark contacts as VIP. This flag is inert until Quiet Hours exists in this app
 * (see the TODO in apply.sh and the note in RivVipContacts.java) -- for now this screen
 * just keeps the list so it's ready the moment Quiet Hours is built.
 */
public class RivVipContactsActivity extends BaseFragment {

    private LinearLayout layout;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivVipContacts));
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
        add.setText(LocaleController.getString(R.string.RivAddVipContact), true);
        add.setOnClickListener(v -> pickContact());
        layout.addView(add);

        LinkedHashSet<Long> vips = RivVipContacts.load();
        int i = 0;
        for (Long id : vips) {
            TLRPC.User user = getMessagesController().getUser(id);
            String name = user != null ? UserObject.getUserName(user) : String.valueOf(id);
            TextSettingsCell cell = new TextSettingsCell(context);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            cell.setText(name, i != vips.size() - 1);
            cell.setOnClickListener(v -> {
                RivVipContacts.toggle(id);
                rebuild(context);
            });
            layout.addView(cell);
            i++;
        }

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivVipContactsInfo));
        layout.addView(info);
    }

    private void pickContact() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (MessagesStorage.TopicKey key : dids) {
                if (key.dialogId > 0) {
                    RivVipContacts.toggle(key.dialogId);
                }
            }
            fragment1.finishFragment();
            rebuild(layout.getContext());
            return true;
        });
        presentFragment(fragment);
    }
}
