package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RivMeta;
import org.telegram.messenger.RivSleepTimer;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

/**
 * Riv Post: the full "Now Playing" screen, matching the old app's "Riv Tunes Player" (RTP).
 * Swipe-down header with Playing/Lyrics/Queue chips, big cover art, the wavy seekbar,
 * transport controls, output chip, and a bottom pill row of secondary controls.
 * Playback itself stays on MediaController; this screen is only the chrome around it.
 */
public class RivMusicPlayerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView cover;
    private TextView titleView;
    private TextView artistView;
    private TextView formatView;
    private RivWavySeekBar seekBar;
    private TextView posLabel;
    private TextView durLabel;
    private RivIcon shuffleIcon;
    private RivIcon repeatIcon;
    private RivIcon playPauseIcon;
    private FrameLayout playPauseBg;
    private TextView outputChip;
    private RivIcon outputIcon;
    private int page = 0; // 0 = playing, 1 = lyrics, 2 = queue
    private TextView[] chips;
    private FrameLayout pageHolder;
    private final Runnable tick = this::onTick;

    @Override
    protected boolean useSimpleActionBar() {
        return false;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        AndroidUtilities.cancelRunOnUIThread(tick);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidReset) {
            finishFragment();
            return;
        }
        refresh();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        MessageObject current = MediaController.getInstance().getPlayingMessageObject();
        if (current == null) {
            finishFragment();
            return new FrameLayout(context);
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // header: chevron-down, page chips, more
        FrameLayout header = new FrameLayout(context);
        column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 8, 10, 8, 0));

        RivIcon down = new RivIcon(context);
        down.setKind(RivIcon.CHEVRON_DOWN);
        down.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        header.addView(down, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT));
        down.setOnClickListener(v -> finishFragment());

        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER);
        header.addView(chipsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        String[] labels = {LocaleController.getString(R.string.RivPlaying), LocaleController.getString(R.string.RivLyrics), LocaleController.getString(R.string.RivQueue)};
        chips = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            TextView chip = new TextView(context);
            chip.setText(labels[i]);
            chip.setTextSize(14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            chip.setOnClickListener(v -> {
                page = idx;
                updateChips();
                showPage(context, current);
            });
            chipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));
            chips[i] = chip;
        }

        RivIcon moreIcon = new RivIcon(context);
        moreIcon.setKind(RivIcon.INFO);
        moreIcon.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        header.addView(moreIcon, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        moreIcon.setOnClickListener(v -> {
            Bundle args = new Bundle();
            if (current.getDialogId() >= 0) args.putLong("user_id", current.getDialogId());
            else args.putLong("chat_id", -current.getDialogId());
            args.putInt("message_id", current.getId());
            presentFragment(new ChatActivity(args));
        });
        updateChips();

        pageHolder = new FrameLayout(context);
        column.addView(pageHolder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        showPage(context, current);

        buildControls(context, column);

        refresh();
        AndroidUtilities.runOnUIThread(tick, 250);
        return fragmentView;
    }

    private void updateChips() {
        if (chips == null) return;
        for (int i = 0; i < chips.length; i++) {
            boolean sel = i == page;
            chips[i].setTextColor(sel ? Theme.getColor(Theme.key_windowBackgroundWhite) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : 0x00000000);
            bg.setCornerRadius(AndroidUtilities.dp(18));
            chips[i].setBackground(bg);
        }
    }

    private void showPage(Context context, MessageObject current) {
        pageHolder.removeAllViews();
        switch (page) {
            case 1:
                pageHolder.addView(lyricsPage(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            case 2:
                pageHolder.addView(queuePage(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            default:
                pageHolder.addView(coverPage(context, current), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
        }
    }

    private View coverPage(Context context, MessageObject current) {
        FrameLayout wrap = new FrameLayout(context);
        cover = new BackupImageView(context);
        cover.getImageReceiver().setRoundRadius(AndroidUtilities.dp(24));
        int size = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(56), AndroidUtilities.dp(380));
        wrap.addView(cover, LayoutHelper.createFrame(size, size, Gravity.CENTER));
        bindCover(current);
        return wrap;
    }

    private void bindCover(MessageObject m) {
        if (cover == null || m == null) return;
        TLRPC.Document doc = m.getDocument();
        TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 800) : null;
        if (thumb != null) {
            cover.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), null, new ColorDrawable(0xFF2A2236), m);
        } else {
            cover.setImageDrawable(new ColorDrawable(0xFF2A2236));
        }
    }

    private View lyricsPage(Context context) {
        FrameLayout wrap = new FrameLayout(context);
        TextView tv = new TextView(context);
        tv.setText(LocaleController.getString(R.string.RivNoLyricsFound));
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setTextSize(15);
        wrap.addView(tv, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        return wrap;
    }

    private View queuePage(Context context) {
        java.util.List<MessageObject> q = MediaController.getInstance().getPlaylist();
        int cur = MediaController.getInstance().getPlayingMessageObjectNum();
        androidx.recyclerview.widget.RecyclerView list = new org.telegram.ui.Components.RecyclerListView(context);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context));
        list.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override
            public int getItemCount() {
                return q == null ? 0 : q.size();
            }

            @androidx.annotation.NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
                BackupImageView art = new BackupImageView(context);
                art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(8));
                row.addView(art, LayoutHelper.createLinear(44, 44));
                LinearLayout col = new LinearLayout(context);
                col.setOrientation(LinearLayout.VERTICAL);
                row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 10, 0, 0, 0));
                TextView t = new TextView(context);
                t.setTextSize(14);
                t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                col.addView(t);
                TextView a = new TextView(context);
                a.setTextSize(12);
                a.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                col.addView(a);
                row.setTag(new Object[]{art, t, a});
                row.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                MessageObject m = q.get(position);
                Object[] tag = (Object[]) holder.itemView.getTag();
                BackupImageView art = (BackupImageView) tag[0];
                TextView t = (TextView) tag[1];
                TextView a = (TextView) tag[2];
                TLRPC.Document doc = m.getDocument();
                TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 150) : null;
                if (thumb != null) art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), "44_44", new ColorDrawable(0xFF2A2236), m);
                else art.setImageDrawable(new ColorDrawable(0xFF2A2236));
                t.setText(m.getMusicTitle());
                t.setTextColor(position == cur ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                a.setText(m.getMusicAuthor());
                holder.itemView.setOnClickListener(v -> {
                    MediaController.getInstance().playMessage(m);
                    refresh();
                });
            }
        });
        return list;
    }

    private void buildControls(Context context, LinearLayout column) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        column.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleRow.addView(titleCol, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTextSize(22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleCol.addView(titleView);

        artistView = new TextView(context);
        artistView.setTextSize(16);
        artistView.setMaxLines(1);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        titleCol.addView(artistView);

        formatView = new TextView(context);
        formatView.setTextSize(12);
        formatView.setTypeface(AndroidUtilities.bold());
        formatView.setTextColor(0xFFFFC14D);
        titleCol.addView(formatView);

        RivIcon fav = new RivIcon(context);
        fav.setKind(RivIcon.HEART_OUTLINE);
        titleRow.addView(fav, LayoutHelper.createLinear(40, 40));
        fav.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m != null) {
                RivMeta.toggleFavoriteSong(RivMusicActivity.keyOf(m));
                refresh();
            }
        });

        seekBar = new RivWavySeekBar(context);
        seekBar.setColors(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) & 0x55FFFFFF | 0x33000000);
        box.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 0, 6, 0, 0));
        seekBar.setListener(new RivWavySeekBar.Listener() {
            @Override
            public void onSeek(float fraction) {
                posLabel.setText(AndroidUtilities.formatShortDuration((int) (fraction * MediaController.getInstance().getDuration() / 1000)));
            }

            @Override
            public void onSeekEnd() {
                MessageObject m = MediaController.getInstance().getPlayingMessageObject();
                if (m != null) MediaController.getInstance().seekToProgress(m, seekBarProgress());
            }
        });

        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(timeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        posLabel = new TextView(context);
        posLabel.setTextSize(12);
        posLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        timeRow.addView(posLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));
        durLabel = new TextView(context);
        durLabel.setTextSize(12);
        durLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        timeRow.addView(durLabel);

        LinearLayout transport = new LinearLayout(context);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(transport, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70, 0, 8, 0, 0));

        shuffleIcon = new RivIcon(context);
        shuffleIcon.setKind(RivIcon.SHUFFLE);
        transport.addView(shuffleIcon, LayoutHelper.createLinear(0, 40, 1f));
        shuffleIcon.setOnClickListener(v -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            refresh();
        });

        RivIcon prev = new RivIcon(context);
        prev.setKind(RivIcon.SKIP_PREV);
        prev.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        transport.addView(prev, LayoutHelper.createLinear(0, 50, 1f));
        prev.setOnClickListener(v -> {
            MediaController.getInstance().playPreviousMessage();
            refresh();
        });

        playPauseBg = new FrameLayout(context);
        GradientDrawable ppBg = new GradientDrawable();
        ppBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        ppBg.setCornerRadius(AndroidUtilities.dp(22));
        playPauseBg.setBackground(ppBg);
        transport.addView(playPauseBg, LayoutHelper.createLinear(0, 64, 1.2f));
        playPauseIcon = new RivIcon(context);
        playPauseIcon.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        playPauseBg.addView(playPauseIcon, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        playPauseBg.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m == null) return;
            if (MediaController.getInstance().isMessagePaused()) MediaController.getInstance().playMessage(m);
            else MediaController.getInstance().pauseMessage(m);
            refresh();
        });

        RivIcon next = new RivIcon(context);
        next.setKind(RivIcon.SKIP_NEXT);
        next.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        transport.addView(next, LayoutHelper.createLinear(0, 50, 1f));
        next.setOnClickListener(v -> {
            MediaController.getInstance().playNextMessage();
            refresh();
        });

        repeatIcon = new RivIcon(context);
        repeatIcon.setKind(RivIcon.REPEAT);
        transport.addView(repeatIcon, LayoutHelper.createLinear(0, 40, 1f));
        repeatIcon.setOnClickListener(v -> {
            SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
            refresh();
        });

        LinearLayout outputRow = new LinearLayout(context);
        outputRow.setOrientation(LinearLayout.HORIZONTAL);
        outputRow.setGravity(Gravity.CENTER);
        box.addView(outputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        chipBg.setCornerRadius(AndroidUtilities.dp(18));
        outputRow.setBackground(chipBg);
        outputIcon = new RivIcon(context);
        outputIcon.setKind(RivIcon.SPEAKER);
        outputIcon.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        outputRow.addView(outputIcon, LayoutHelper.createLinear(28, 28, 0, 8, 6, 2, 6));
        outputChip = new TextView(context);
        outputChip.setTextSize(14);
        outputChip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        outputChip.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        outputRow.addView(outputChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0, 0));

        // bottom pill row: sleep / speed / eq / lyrics / queue / A-B
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        pillBg.setCornerRadius(AndroidUtilities.dp(24));
        pill.setBackground(pillBg);
        box.addView(pill, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 10, 10, 0, 0));

        addPillIcon(context, pill, RivIcon.MOON, v -> showSleepDialog());
        addPillIcon(context, pill, RivIcon.SPEED, v -> showSpeedDialog());
        addPillIcon(context, pill, RivIcon.EQ, v -> showEqDialog());
        addPillIcon(context, pill, RivIcon.LYRICS, v -> {
            page = 1;
            updateChips();
            showPage(context, MediaController.getInstance().getPlayingMessageObject());
        });
        addPillIcon(context, pill, RivIcon.QUEUE, v -> {
            page = 2;
            updateChips();
            showPage(context, MediaController.getInstance().getPlayingMessageObject());
        });
    }

    private void addPillIcon(Context context, LinearLayout pill, int kind, View.OnClickListener l) {
        RivIcon icon = new RivIcon(context);
        icon.setKind(kind);
        icon.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        icon.setOnClickListener(l);
        pill.addView(icon, LayoutHelper.createLinear(0, 52, 1f));
    }

    private float seekBarProgress() {
        long dur = (long) MediaController.getInstance().getDuration();
        if (dur <= 0) return 0f;
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        long pos = m != null ? MediaController.getInstance().getProgressMs(m) : 0;
        return Math.max(0f, Math.min(1f, (float) pos / dur));
    }

    private void onTick() {
        refresh();
        AndroidUtilities.runOnUIThread(tick, 400);
    }

    private void refresh() {
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        if (m == null) {
            finishFragment();
            return;
        }
        if (titleView != null) {
            String t = m.getMusicTitle();
            titleView.setText(t == null || t.isEmpty() ? m.getFileName() : t);
            artistView.setText(m.getMusicAuthor());
            TLRPC.Document doc = m.getDocument();
            String ext = "";
            if (doc != null && doc.mime_type != null && doc.mime_type.contains("/")) {
                ext = doc.mime_type.substring(doc.mime_type.indexOf('/') + 1).toUpperCase(Locale.US);
            }
            formatView.setText(ext);
            formatView.setVisibility(ext.isEmpty() ? View.GONE : View.VISIBLE);
        }
        bindCover(m);
        boolean playing = !MediaController.getInstance().isMessagePaused();
        if (playPauseIcon != null) {
            playPauseIcon.setKind(playing ? RivIcon.PAUSE : RivIcon.PLAY);
        }
        if (seekBar != null) {
            seekBar.setPlaying(playing);
            seekBar.setProgress(seekBarProgress());
            long dur = (long) MediaController.getInstance().getDuration();
            long pos = MediaController.getInstance().getProgressMs(m);
            posLabel.setText(AndroidUtilities.formatShortDuration((int) (pos / 1000)));
            durLabel.setText(AndroidUtilities.formatShortDuration((int) (dur / 1000)));
        }
        if (shuffleIcon != null) {
            shuffleIcon.setColor(SharedConfig.shuffleMusic ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        if (repeatIcon != null) {
            repeatIcon.setKind(SharedConfig.repeatMode == 1 ? RivIcon.REPEAT_ONE : RivIcon.REPEAT);
            repeatIcon.setColor(SharedConfig.repeatMode != 0 ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        updateOutputChip();
    }

    private void updateOutputChip() {
        if (outputChip == null) return;
        String name = LocaleController.getString(R.string.RivPhoneSpeaker);
        try {
            AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo d : devices) {
                        int type = d.getType();
                        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            name = d.getProductName() != null ? d.getProductName().toString() : LocaleController.getString(R.string.RivBluetooth);
                            break;
                        } else if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                            name = LocaleController.getString(R.string.RivWiredHeadphones);
                            break;
                        }
                    }
                } else if (am.isBluetoothA2dpOn() || am.isBluetoothScoOn()) {
                    name = LocaleController.getString(R.string.RivBluetooth);
                } else if (am.isWiredHeadsetOn()) {
                    name = LocaleController.getString(R.string.RivWiredHeadphones);
                }
            }
        } catch (Throwable ignored) {
        }
        outputChip.setText(name);
    }

    private void showSleepDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivSleepTimer));
        int[] minutes = {10, 15, 30, 45, 60, 90};
        CharSequence[] items = new CharSequence[minutes.length];
        for (int i = 0; i < minutes.length; i++) items[i] = minutes[i] + " " + LocaleController.getString(R.string.RivMinutesWord);
        b.setItems(items, (d, w) -> {
            long atMs = System.currentTimeMillis() + minutes[w] * 60_000L;
            RivSleepTimer.schedule(atMs);
        });
        b.setNegativeButton(LocaleController.getString(R.string.RivTurnOff), (d, w) -> RivSleepTimer.cancel());
        showDialog(b.create());
    }

    private void showSpeedDialog() {
        if (getParentActivity() == null) return;
        float current = MediaController.getInstance().getPlaybackSpeed(true);
        float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        CharSequence[] items = new CharSequence[speeds.length];
        for (int i = 0; i < speeds.length; i++) items[i] = String.format(Locale.US, "%.2f×", speeds[i]) + (Math.abs(speeds[i] - current) < 0.01f ? "  ✓" : "");
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivSpeed));
        b.setItems(items, (d, w) -> MediaController.getInstance().setPlaybackSpeed(true, speeds[w]));
        showDialog(b.create());
    }

    private void showEqDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivEqualizer));
        b.setMessage(LocaleController.getString(R.string.RivEqualizerInfo));
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(b.create());
    }
}
