package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivLog;
import org.telegram.messenger.RivPrefs;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** Riv Post: the extra features, each with its own switch. Official behaviour is the default. */
public class RivSettingsActivity extends BaseFragment {

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivSettings));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        layout.addView(space(context, ActionBar.getCurrentActionBarHeight()));

        // ---- Features
        TextSettingsCell music = new TextSettingsCell(context);
        music.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        music.setText(LocaleController.getString(R.string.RivMusicOpen), true);
        music.setOnClickListener(v -> presentFragment(new RivMusicActivity()));
        layout.addView(music);
        TextSettingsCell videos = new TextSettingsCell(context);
        videos.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        videos.setText(LocaleController.getString(R.string.RivVideosOpen), false);
        videos.setOnClickListener(v -> presentFragment(new RivVideosActivity()));
        layout.addView(videos);
        layout.addView(info(context, LocaleController.getString(R.string.RivLibrariesInfo)));

        // ---- Privacy
        layout.addView(header(context, LocaleController.getString(R.string.RivPrivacyHeader)));
        layout.addView(check(context, LocaleController.getString(R.string.RivGhostMode), LocaleController.getString(R.string.RivGhostModeInfo), RivPrefs.GHOST, true));
        layout.addView(check(context, LocaleController.getString(R.string.RivHideTyping), LocaleController.getString(R.string.RivHideTypingInfo), RivPrefs.HIDE_TYPING, true));
        layout.addView(check(context, LocaleController.getString(R.string.RivHideOnline), LocaleController.getString(R.string.RivHideOnlineInfo), RivPrefs.HIDE_ONLINE, true));
        layout.addView(check(context, LocaleController.getString(R.string.RivKeepDeleted), LocaleController.getString(R.string.RivKeepDeletedInfo), RivPrefs.KEEP_DELETED, false));
        layout.addView(info(context, LocaleController.getString(R.string.RivGhostModeFooter)));

        // ---- Help
        layout.addView(header(context, LocaleController.getString(R.string.RivHelpHeader)));
        TextSettingsCell log = new TextSettingsCell(context);
        log.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        log.setText(LocaleController.getString(R.string.RivSendLog), false);
        log.setOnClickListener(v -> {
            if (getParentActivity() != null) {
                RivLog.send(getParentActivity());
            }
        });
        layout.addView(log);
        layout.addView(info(context, LocaleController.getString(R.string.RivComingFooter)));

        return fragmentView;
    }

    private View space(Context context, int height) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, height));
        return v;
    }

    private HeaderCell header(Context context, String text) {
        HeaderCell cell = new HeaderCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setText(text);
        return cell;
    }

    private TextCheckCell check(Context context, String title, String subtitle, String prefKey, boolean divider) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (subtitle != null) {
            cell.setTextAndValueAndCheck(title, subtitle, RivPrefs.b(prefKey), true, divider);
        } else {
            cell.setTextAndCheck(title, RivPrefs.b(prefKey), divider);
        }
        cell.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(prefKey);
            RivPrefs.set(prefKey, now);
            cell.setChecked(now);
        });
        return cell;
    }

    private TextInfoPrivacyCell info(Context context, String text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
        cell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        cell.setText(text);
        return cell;
    }
}
