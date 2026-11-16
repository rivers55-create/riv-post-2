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
public class RivSettingsActivity extends RivFragment {

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
        TextSettingsCell reminders = new TextSettingsCell(context);
        reminders.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        reminders.setText(LocaleController.getString(R.string.RivReminders), true);
        reminders.setOnClickListener(v -> presentFragment(new RivRemindersActivity()));
        layout.addView(reminders);
        TextSettingsCell quickReplies = new TextSettingsCell(context);
        quickReplies.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        quickReplies.setText(LocaleController.getString(R.string.RivQuickReplies), true);
        quickReplies.setOnClickListener(v -> presentFragment(new RivQuickRepliesActivity()));
        layout.addView(quickReplies);
        TextSettingsCell exportChat = new TextSettingsCell(context);
        exportChat.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        exportChat.setText(LocaleController.getString(R.string.RivExportChat), false);
        exportChat.setOnClickListener(v -> presentFragment(new RivExportChatActivity()));
        layout.addView(exportChat);
        TextSettingsCell downloads = new TextSettingsCell(context);
        downloads.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        downloads.setText(LocaleController.getString(R.string.RivDownloads), false);
        downloads.setOnClickListener(v -> presentFragment(new RivDownloadsActivity()));
        layout.addView(downloads);
        layout.addView(info(context, LocaleController.getString(R.string.RivLibrariesInfo)));

        // ---- Video & music metadata (optional, user's own keys)
        layout.addView(header(context, LocaleController.getString(R.string.RivMetaHeader)));
        TextSettingsCell tmdbKey = new TextSettingsCell(context);
        tmdbKey.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tmdbKey.setTextAndValue(LocaleController.getString(R.string.RivTmdbKey), maskKey(RivPrefs.s(RivPrefs.TMDB_KEY, "")), true, true);
        tmdbKey.setOnClickListener(v -> askKey(context, RivPrefs.TMDB_KEY, LocaleController.getString(R.string.RivTmdbKey), tmdbKey));
        layout.addView(tmdbKey);
        layout.addView(check(context, LocaleController.getString(R.string.RivUseTmdb), null, RivPrefs.TMDB_ON, true));
        TextSettingsCell tvdbKey = new TextSettingsCell(context);
        tvdbKey.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tvdbKey.setTextAndValue(LocaleController.getString(R.string.RivTvdbKey), maskKey(RivPrefs.s(RivPrefs.TVDB_KEY, "")), true, true);
        tvdbKey.setOnClickListener(v -> askKey(context, RivPrefs.TVDB_KEY, LocaleController.getString(R.string.RivTvdbKey), tvdbKey));
        layout.addView(tvdbKey);
        TextSettingsCell tvdbPin = new TextSettingsCell(context);
        tvdbPin.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tvdbPin.setTextAndValue(LocaleController.getString(R.string.RivTvdbPin), maskKey(RivPrefs.s(RivPrefs.TVDB_PIN, "")), true, true);
        tvdbPin.setOnClickListener(v -> askKey(context, RivPrefs.TVDB_PIN, LocaleController.getString(R.string.RivTvdbPin), tvdbPin));
        layout.addView(tvdbPin);
        layout.addView(check(context, LocaleController.getString(R.string.RivUseTvdb), null, RivPrefs.TVDB_ON, true));
        layout.addView(showSourceCheck(context));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivCleanTitles), LocaleController.getString(R.string.RivCleanTitlesInfo), RivPrefs.CLEAN_TITLES));
        layout.addView(info(context, LocaleController.getString(R.string.RivMetaFooter)));

        // ---- Look
        layout.addView(header(context, LocaleController.getString(R.string.RivLookHeader)));
        layout.addView(themeCheck(context, LocaleController.getString(R.string.RivAmoled), LocaleController.getString(R.string.RivAmoledInfo), org.telegram.messenger.RivTheme.AMOLED, true));
        layout.addView(themeCheck(context, LocaleController.getString(R.string.RivGreen), LocaleController.getString(R.string.RivGreenInfo), org.telegram.messenger.RivTheme.GREEN, true));
        stickerCell = new TextSettingsCell(context);
        stickerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        updateStickerCell();
        stickerCell.setOnClickListener(v -> {
            int cur = RivPrefs.i(RivPrefs.STICKER_SIZE, 100);
            int next = cur >= 140 ? 60 : cur + 20;
            RivPrefs.set(RivPrefs.STICKER_SIZE, next);
            updateStickerCell();
        });
        layout.addView(stickerCell);
        layout.addView(check(context, LocaleController.getString(R.string.RivHideStories), LocaleController.getString(R.string.RivHideStoriesInfo), RivPrefs.HIDE_STORIES, true));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivHideAds), LocaleController.getString(R.string.RivHideAdsInfo), RivPrefs.HIDE_ADS));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivHideUpdateBanner), LocaleController.getString(R.string.RivHideUpdateBannerInfo), RivPrefs.HIDE_UPDATE_BANNER));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivNameFit), LocaleController.getString(R.string.RivNameFitInfo), RivPrefs.NAME_FIT));
        TextSettingsCell themeColors = new TextSettingsCell(context);
        themeColors.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        themeColors.setText(LocaleController.getString(R.string.RivThemeColors), true);
        themeColors.setOnClickListener(v -> presentFragment(new RivThemeActivity()));
        layout.addView(themeColors);
        TextSettingsCell looks = new TextSettingsCell(context);
        looks.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        looks.setText(LocaleController.getString(R.string.RivQuickLooks), false);
        looks.setOnClickListener(v -> presentFragment(new RivLooksActivity()));
        layout.addView(looks);
        layout.addView(info(context, LocaleController.getString(R.string.RivLookFooter)));

        // ---- Chats
        layout.addView(header(context, LocaleController.getString(R.string.RivChatsHeader)));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivGhostButton), LocaleController.getString(R.string.RivGhostButtonInfo), RivPrefs.GHOST_BUTTON));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivRangeSelect), LocaleController.getString(R.string.RivRangeSelectInfo), RivPrefs.RANGE_SELECT));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivEditHistory), LocaleController.getString(R.string.RivEditHistoryInfo), RivPrefs.EDIT_HISTORY));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivMarkDeleted), LocaleController.getString(R.string.RivMarkDeletedInfo), RivPrefs.MARK_DELETED));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivFastDownloads), LocaleController.getString(R.string.RivFastDownloadsInfo), RivPrefs.FAST_DOWNLOADS));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivExactLastSeen), LocaleController.getString(R.string.RivExactLastSeenInfo), RivPrefs.EXACT_LAST_SEEN));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivShowUserId), LocaleController.getString(R.string.RivShowUserIdInfo), RivPrefs.SHOW_USER_ID));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivQuickReplyInsert), LocaleController.getString(R.string.RivQuickReplyInsertInfo), RivPrefs.QUICK_REPLY_INSERT));
        layout.addView(check(context, LocaleController.getString(R.string.RivBadgeMuted), LocaleController.getString(R.string.RivBadgeMutedInfo), RivPrefs.BADGE_MUTED, true));
        quietCell = new TextSettingsCell(context);
        quietCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        updateQuietCell();
        quietCell.setOnClickListener(v -> pickQuietHours(context));
        layout.addView(quietCell);
        TextSettingsCell swipe = new TextSettingsCell(context);
        swipe.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        swipe.setText(LocaleController.getString(R.string.RivSwipeGesture), false);
        swipe.setOnClickListener(v -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)));
        layout.addView(swipe);
        TextSettingsCell scheduled = new TextSettingsCell(context);
        scheduled.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        scheduled.setText(LocaleController.getString(R.string.RivScheduledTitle), true);
        scheduled.setOnClickListener(v -> presentFragment(new RivScheduledActivity()));
        layout.addView(scheduled);
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivTranslateDefault), LocaleController.getString(R.string.RivTranslateDefaultInfo), RivPrefs.TRANSLATE_DEFAULT));
        TextCheckCell saver = new TextCheckCell(context);
        saver.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        saver.setTextAndValueAndCheck(LocaleController.getString(R.string.RivDataSaver), LocaleController.getString(R.string.RivDataSaverInfo), RivPrefs.b(RivPrefs.DATA_SAVER, false), true, false);
        saver.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(RivPrefs.DATA_SAVER, false);
            RivPrefs.setDataSaver(now);
            saver.setChecked(now);
        });
        layout.addView(saver);
        layout.addView(info(context, LocaleController.getString(R.string.RivChatsFooter)));

        // ---- Music & video players
        layout.addView(header(context, LocaleController.getString(R.string.RivPlayersHeader)));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivPlayerRtp), LocaleController.getString(R.string.RivPlayerRtpInfo), RivPrefs.PLAYER_RTP));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivLyricsSwitch), LocaleController.getString(R.string.RivLyricsSwitchInfo), RivPrefs.LYRICS));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivLyricsOnline), LocaleController.getString(R.string.RivLyricsOnlineInfo), RivPrefs.LYRICS_ONLINE));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivVideoPlayerRvp), LocaleController.getString(R.string.RivVideoPlayerRvpInfo), RivPrefs.VIDEO_PLAYER_RVP));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivSubtitlesSwitch), LocaleController.getString(R.string.RivSubtitlesSwitchInfo), RivPrefs.SUBTITLES));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivSleepFade), LocaleController.getString(R.string.RivSleepFadeInfo), RivPrefs.SLEEP_FADE));
        TextSettingsCell fadeCell = new TextSettingsCell(context);
        fadeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        Runnable showFade = () -> {
            int sec = RivPrefs.i(RivPrefs.CROSSFADE_SEC, 0);
            fadeCell.setTextAndValue(LocaleController.getString(R.string.RivCrossfade), sec == 0 ? LocaleController.getString(R.string.RivOff) : LocaleController.formatString(R.string.RivSecondsShort, sec), true);
        };
        showFade.run();
        fadeCell.setOnClickListener(v -> {
            if (getParentActivity() == null) return;
            int[] opts = {0, 2, 4, 6, 8, 10, 12};
            CharSequence[] labels = new CharSequence[opts.length];
            for (int i = 0; i < opts.length; i++) labels[i] = opts[i] == 0 ? LocaleController.getString(R.string.RivOff) : LocaleController.formatString(R.string.RivSecondsShort, opts[i]);
            org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.RivCrossfade));
            b.setItems(labels, (d, w) -> {
                RivPrefs.set(RivPrefs.CROSSFADE_SEC, opts[w]);
                showFade.run();
            });
            showDialog(b.create());
        });
        layout.addView(fadeCell);
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivAutoNext), LocaleController.getString(R.string.RivAutoNextInfo), RivPrefs.AUTO_NEXT_EPISODE));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivSeriesShelves), LocaleController.getString(R.string.RivSeriesShelvesInfo), RivPrefs.SERIES_SHELVES));
        layout.addView(check(context, LocaleController.getString(R.string.RivHideWatched), LocaleController.getString(R.string.RivHideWatchedInfo), RivPrefs.HIDE_WATCHED, false));
        layout.addView(info(context, LocaleController.getString(R.string.RivPlayersFooter)));

        // ---- In-chat music player
        layout.addView(header(context, LocaleController.getString(R.string.RivChatPlayerHeader)));
        layout.addView(check(context, LocaleController.getString(R.string.RivChatPlayerRtpStyle), LocaleController.getString(R.string.RivChatPlayerRtpStyleInfo), RivPrefs.IN_CHAT_PLAYER_RTP_STYLE, true));
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivChatPlayerOpensRtp), LocaleController.getString(R.string.RivChatPlayerOpensRtpInfo), RivPrefs.TAP_OPENS_RTP_MINI_PLAYER));
        layout.addView(check(context, LocaleController.getString(R.string.RivHideSourcePreviewButton), LocaleController.getString(R.string.RivHideSourcePreviewButtonInfo), RivPrefs.HIDE_SOURCE_PREVIEW_BUTTON, false));
        layout.addView(info(context, LocaleController.getString(R.string.RivChatPlayerFooter)));

        // ---- Notifications
        layout.addView(header(context, LocaleController.getString(R.string.RivNotifHeader)));
        layout.addView(keepAliveCheck(context));
        layout.addView(info(context, LocaleController.getString(R.string.RivKeepAliveFooter)));

        // ---- Privacy
        layout.addView(header(context, LocaleController.getString(R.string.RivPrivacyHeader)));
        TextSettingsCell vault = new TextSettingsCell(context);
        vault.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        vault.setText(LocaleController.getString(R.string.RivVault), true);
        vault.setOnClickListener(v -> presentFragment(new RivVaultActivity()));
        layout.addView(vault);
        layout.addView(defaultOnCheck(context, LocaleController.getString(R.string.RivVaultFingerprint), LocaleController.getString(R.string.RivVaultFingerprintInfo), RivPrefs.VAULT_FINGERPRINT));
        TextCheckCell ghostCell = new TextCheckCell(context);
        ghostCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        ghostCell.setTextAndValueAndCheck(LocaleController.getString(R.string.RivGhostMode), LocaleController.getString(R.string.RivGhostModeInfo), RivPrefs.ghost(), true, true);
        layout.addView(ghostCell);
        TextCheckCell typingCell = check(context, LocaleController.getString(R.string.RivHideTyping), LocaleController.getString(R.string.RivHideTypingInfo), RivPrefs.HIDE_TYPING, true);
        layout.addView(typingCell);
        TextCheckCell onlineCell = check(context, LocaleController.getString(R.string.RivHideOnline), LocaleController.getString(R.string.RivHideOnlineInfo), RivPrefs.HIDE_ONLINE, true);
        layout.addView(onlineCell);
        ghostCell.setOnClickListener(v -> {
            boolean now = !RivPrefs.ghost();
            RivPrefs.setGhost(now);
            ghostCell.setChecked(now);
            typingCell.setChecked(now);
            onlineCell.setChecked(now);
        });
        layout.addView(check(context, LocaleController.getString(R.string.RivKeepDeleted), LocaleController.getString(R.string.RivKeepDeletedInfo), RivPrefs.KEEP_DELETED, false));
        layout.addView(info(context, LocaleController.getString(R.string.RivGhostModeFooter)));

        TextSettingsCell vipContacts = new TextSettingsCell(context);
        vipContacts.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        vipContacts.setText(LocaleController.getString(R.string.RivVipContacts), false);
        vipContacts.setOnClickListener(v -> presentFragment(new RivVipContactsActivity()));
        layout.addView(vipContacts);
        layout.addView(info(context, LocaleController.getString(R.string.RivVipContactsFooter)));

        // ---- Backup
        layout.addView(header(context, LocaleController.getString(R.string.RivBackupHeader)));
        TextSettingsCell backup = new TextSettingsCell(context);
        backup.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        backup.setText(LocaleController.getString(R.string.RivBackupSave), true);
        backup.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(android.content.Intent.EXTRA_TITLE, "RivPost-settings.json");
            try {
                startActivityForResult(i, REQ_BACKUP);
            } catch (Throwable ignored) {
            }
        });
        layout.addView(backup);
        TextSettingsCell restore = new TextSettingsCell(context);
        restore.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        restore.setText(LocaleController.getString(R.string.RivBackupRestore), false);
        restore.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try {
                startActivityForResult(i, REQ_RESTORE);
            } catch (Throwable ignored) {
            }
        });
        layout.addView(restore);
        layout.addView(info(context, LocaleController.getString(R.string.RivBackupFooter)));

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

    private static final int REQ_BACKUP = 9301;
    private static final int REQ_RESTORE = 9302;
    private TextSettingsCell quietCell;

    private void updateQuietCell() {
        if (quietCell == null) return;
        quietCell.setTextAndValue(LocaleController.getString(R.string.RivQuietHours),
                org.telegram.messenger.RivQuiet.format(org.telegram.messenger.RivQuiet.from()) + " \u2013 " + org.telegram.messenger.RivQuiet.format(org.telegram.messenger.RivQuiet.to()), false, true);
    }

    private void pickQuietHours(Context context) {
        if (getParentActivity() == null) return;
        int from = org.telegram.messenger.RivQuiet.from();
        new android.app.TimePickerDialog(getParentActivity(), (v1, h1, m1) -> {
            RivPrefs.set(RivPrefs.QUIET_FROM, h1 * 60 + m1);
            int to = org.telegram.messenger.RivQuiet.to();
            new android.app.TimePickerDialog(getParentActivity(), (v2, h2, m2) -> {
                RivPrefs.set(RivPrefs.QUIET_TO, h2 * 60 + m2);
                updateQuietCell();
            }, to / 60, to % 60, true).show();
        }, from / 60, from % 60, true).show();
        android.widget.Toast.makeText(context, LocaleController.getString(R.string.RivQuietPickStart), android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null || getParentActivity() == null) return;
        android.net.Uri uri = data.getData();
        try {
            if (requestCode == REQ_BACKUP) {
                try (java.io.OutputStream out = getParentActivity().getContentResolver().openOutputStream(uri)) {
                    out.write(RivPrefs.exportAll().getBytes("UTF-8"));
                }
                android.widget.Toast.makeText(getParentActivity(), LocaleController.getString(R.string.RivBackupSaved), android.widget.Toast.LENGTH_LONG).show();
            } else if (requestCode == REQ_RESTORE) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = getParentActivity().getContentResolver().openInputStream(uri)) {
                    byte[] b = new byte[8192];
                    int r;
                    while ((r = in.read(b)) > 0) buf.write(b, 0, r);
                }
                int n = RivPrefs.importAll(buf.toString("UTF-8"));
                Theme.refreshThemeColors();
                android.widget.Toast.makeText(getParentActivity(), LocaleController.formatString(R.string.RivBackupRestored, n), android.widget.Toast.LENGTH_LONG).show();
                finishFragment();
            }
        } catch (Throwable t) {
            android.widget.Toast.makeText(getParentActivity(), LocaleController.getString(R.string.RivBackupFailed), android.widget.Toast.LENGTH_LONG).show();
        }
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

    private TextSettingsCell stickerCell;

    private void updateStickerCell() {
        if (stickerCell != null) {
            stickerCell.setTextAndValue(LocaleController.getString(R.string.RivStickerSize), RivPrefs.i(RivPrefs.STICKER_SIZE, 100) + "%", false, true);
        }
    }

    private TextCheckCell themeCheck(Context context, String title, String subtitle, String prefKey, boolean divider) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValueAndCheck(title, subtitle, RivPrefs.b(prefKey), true, divider);
        cell.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(prefKey);
            RivPrefs.set(prefKey, now);
            cell.setChecked(now);
            Theme.refreshThemeColors();
        });
        return cell;
    }

    private TextCheckCell keepAliveCheck(Context context) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValueAndCheck(LocaleController.getString(R.string.RivKeepAlive), LocaleController.getString(R.string.RivKeepAliveInfo), RivPrefs.b(RivPrefs.KEEP_ALIVE, true), true, false);
        cell.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(RivPrefs.KEEP_ALIVE, true);
            RivPrefs.set(RivPrefs.KEEP_ALIVE, now);
            cell.setChecked(now);
            org.telegram.messenger.MessagesController.getGlobalNotificationsSettings().edit().putBoolean("pushConnection", now).putBoolean("pushService", now).apply();
            org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).setPushConnectionEnabled(now);
            org.telegram.messenger.ApplicationLoader.startPushService();
        });
        return cell;
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return LocaleController.getString(R.string.RivNotSet);
        if (key.length() <= 6) return "••••••";
        return key.substring(0, 4) + "••••" + key.substring(key.length() - 2);
    }

    private void askKey(Context context, String prefKey, String title, TextSettingsCell cell) {
        if (getParentActivity() == null) {
            return;
        }
        android.widget.EditText edit = new android.widget.EditText(context);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setText(RivPrefs.s(prefKey, ""));
        edit.setHint(title);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(context);
        wrap.setPadding(org.telegram.messenger.AndroidUtilities.dp(22), 0, org.telegram.messenger.AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String val = edit.getText() == null ? "" : edit.getText().toString().trim();
            RivPrefs.set(prefKey, val);
            cell.setTextAndValue(title, maskKey(val), true, true);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private TextCheckCell defaultOnCheck(Context context, String title, String subtitle, String prefKey) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValueAndCheck(title, subtitle, RivPrefs.b(prefKey, true), true, false);
        cell.setOnClickListener(v -> {
            boolean now = !RivPrefs.b(prefKey, true);
            RivPrefs.set(prefKey, now);
            cell.setChecked(now);
        });
        return cell;
    }

    private TextCheckCell showSourceCheck(Context context) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        boolean tvdbPreferred = "tvdb".equals(RivPrefs.s(RivPrefs.SHOW_SOURCE, "tmdb"));
        cell.setTextAndValueAndCheck(LocaleController.getString(R.string.RivPreferTvdbShows), LocaleController.getString(R.string.RivPreferTvdbShowsInfo), tvdbPreferred, true, false);
        cell.setOnClickListener(v -> {
            boolean now = !"tvdb".equals(RivPrefs.s(RivPrefs.SHOW_SOURCE, "tmdb"));
            RivPrefs.set(RivPrefs.SHOW_SOURCE, now ? "tvdb" : "tmdb");
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
