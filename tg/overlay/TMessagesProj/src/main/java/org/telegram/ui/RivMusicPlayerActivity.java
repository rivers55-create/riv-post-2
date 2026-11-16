package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RivEq;
import org.telegram.messenger.RivLyrics;
import org.telegram.messenger.RivMeta;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivSleepTimer;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

/**
 * Riv Post: the "Now Playing" screen, laid out like Riv Tunes Player (RTP): round buttons and a
 * Playing / Lyrics / Queue switcher on top, big rounded cover, title + artist + format line with
 * a heart, the wavy seekbar in the accent colour, -10s / previous / big play / next / +10s,
 * the output device, and a pill of extra controls at the bottom. With "RTP-style player" on, the
 * background and accent take their colour from the song's cover, like RTP's "Color by current
 * track". Playback itself stays on Telegram's own MediaController.
 */
public class RivMusicPlayerActivity extends RivFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int DEFAULT_ACCENT = 0xFFE53935;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0x99FFFFFF;
    private static final int CONTROL_BG = 0x1FFFFFFF;

    private FrameLayout root;
    private BackupImageView cover;
    private TextView titleView;
    private TextView artistView;
    private TextView formatView;
    private RivIcon favIcon;
    private RivWavySeekBar seekBar;
    private float draggedFraction = -1f;
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

    private int accent = DEFAULT_ACCENT;
    private int bgTop = 0xFF121212;
    private String colorForKey;
    private String lyricsForKey;
    private RivLyrics.Result lyrics;
    private LinearLayout lyricsList;
    private ScrollView lyricsScroll;
    private int lyricsCurrent = -1;

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
        if (id == NotificationCenter.messagePlayingDidStart && page == 1 && fragmentView != null) {
            showPage(fragmentView.getContext());
        }
        refresh();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        if (actionBar != null) {
            actionBar.setAddToContainer(false);
        }
        MessageObject current = MediaController.getInstance().getPlayingMessageObject();
        if (current == null) {
            finishFragment();
            return new FrameLayout(context);
        }

        root = new FrameLayout(context);
        fragmentView = root;
        applyBackground();

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // header: round chevron, Playing / Lyrics / Queue switcher, round "more"
        FrameLayout header = new FrameLayout(context);
        column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 0, 12, 8, 12, 0));

        FrameLayout down = roundButton(context, RivIcon.CHEVRON_DOWN);
        header.addView(down, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL | Gravity.LEFT));
        down.setOnClickListener(v -> finishFragment());

        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER);
        chipsRow.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        GradientDrawable chipsBg = new GradientDrawable();
        chipsBg.setColor(CONTROL_BG);
        chipsBg.setCornerRadius(AndroidUtilities.dp(22));
        chipsRow.setBackground(chipsBg);
        header.addView(chipsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER));
        String[] labels = {LocaleController.getString(R.string.RivPlaying), LocaleController.getString(R.string.RivLyrics), LocaleController.getString(R.string.RivQueue)};
        chips = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            TextView chip = new TextView(context);
            chip.setText(labels[i]);
            chip.setTextSize(14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            chip.setOnClickListener(v -> {
                page = idx;
                updateChips();
                showPage(context);
            });
            chipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));
            chips[i] = chip;
        }

        FrameLayout more = roundButton(context, RivIcon.MORE);
        header.addView(more, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        more.setOnClickListener(v -> showMoreMenu());
        updateChips();

        pageHolder = new FrameLayout(context);
        column.addView(pageHolder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        showPage(context);

        buildControls(context, column);

        applyColors();
        refresh();
        AndroidUtilities.runOnUIThread(tick, 250);
        return fragmentView;
    }

    private FrameLayout roundButton(Context context, int kind) {
        FrameLayout b = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(CONTROL_BG);
        b.setBackground(bg);
        RivIcon icon = new RivIcon(context);
        icon.setKind(kind);
        icon.setColor(TEXT);
        b.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        return b;
    }

    private void applyBackground() {
        if (root == null) return;
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, 0xFF0B0B0B});
        root.setBackground(bg);
    }

    private void updateChips() {
        if (chips == null) return;
        for (int i = 0; i < chips.length; i++) {
            boolean sel = i == page;
            chips[i].setTextColor(sel ? TEXT : TEXT_DIM);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? accent : 0x00000000);
            bg.setCornerRadius(AndroidUtilities.dp(18));
            chips[i].setBackground(bg);
        }
    }

    private void showPage(Context context) {
        pageHolder.removeAllViews();
        lyricsList = null;
        lyricsScroll = null;
        switch (page) {
            case 1:
                pageHolder.addView(lyricsPage(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            case 2:
                pageHolder.addView(queuePage(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            default:
                pageHolder.addView(coverPage(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
        }
    }

    private View coverPage(Context context) {
        FrameLayout wrap = new FrameLayout(context);
        cover = new BackupImageView(context);
        cover.getImageReceiver().setRoundRadius(AndroidUtilities.dp(28));
        int size = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40), AndroidUtilities.dp(400));
        wrap.addView(cover, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 20, 12, 20, 12));
        cover.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        bindCover(MediaController.getInstance().getPlayingMessageObject());
        return wrap;
    }

    private String boundCoverKey;

    /**
     * Riv Post: the cover used to come from Telegram's tiny preview picture (often only 90-320 px),
     * which looked blurry at full width. Now, like Telegram's own player, it uses the full picture
     * stored inside the song file once it's known, then the full-size online artwork, and only
     * falls back to the small preview while those load.
     */
    private void bindCover(MessageObject m) {
        if (cover == null || m == null) return;
        org.telegram.messenger.audioinfo.AudioInfo info = MediaController.getInstance().getAudioInfo();
        Bitmap full = null;
        if (info != null && MediaController.getInstance().isPlayingMessage(m)) {
            try {
                full = info.getCover();
            } catch (Throwable ignored) {
            }
        }
        String key = m.getDialogId() + "_" + m.getId() + (full != null ? "_full" : "_net");
        if (key.equals(boundCoverKey)) return;
        boundCoverKey = key;
        colorForKey = null; // pick the accent again from the sharper picture
        if (full != null) {
            cover.setImageBitmap(full);
            return;
        }
        TLRPC.Document doc = m.getDocument();
        TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 800) : null;
        org.telegram.messenger.ImageLocation thumbLoc = thumb != null ? org.telegram.messenger.ImageLocation.getForDocument(thumb, doc) : null;
        String artworkUrl = m.getArtworkUrl(false);
        if (!TextUtils.isEmpty(artworkUrl)) {
            cover.setImage(org.telegram.messenger.ImageLocation.getForPath(artworkUrl), null, thumbLoc, null, new ColorDrawable(0xFF2A2236), null, 0, 1, m);
        } else if (thumbLoc != null) {
            cover.setImage(thumbLoc, null, new ColorDrawable(0xFF2A2236), m);
        } else {
            cover.setImageDrawable(new ColorDrawable(0xFF2A2236));
        }
    }

    // ------------------------------------------------------------------ lyrics

    private View lyricsPage(Context context) {
        FrameLayout wrap = new FrameLayout(context);
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        if (!RivPrefs.b(RivPrefs.LYRICS, true) || m == null) {
            wrap.addView(centeredText(context, LocaleController.getString(R.string.RivNoLyricsFound)), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            return wrap;
        }
        String key = RivMusicActivity.keyOf(m);
        if (lyrics != null && key.equals(lyricsForKey)) {
            fillLyrics(context, wrap);
            return wrap;
        }
        TextView loading = centeredText(context, LocaleController.getString(R.string.RivLyricsLoading));
        wrap.addView(loading, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        RivLyrics.load(m, result -> {
            lyrics = result;
            lyricsForKey = key;
            if (page == 1 && wrap.getParent() != null) {
                wrap.removeAllViews();
                fillLyrics(context, wrap);
            }
        });
        return wrap;
    }

    private TextView centeredText(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(TEXT_DIM);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        return tv;
    }

    private void fillLyrics(Context context, FrameLayout wrap) {
        if (lyrics == null || (lyrics.lines.isEmpty() && TextUtils.isEmpty(lyrics.plain))) {
            wrap.addView(centeredText(context, LocaleController.getString(R.string.RivNoLyricsFound)), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            return;
        }
        lyricsScroll = new ScrollView(context);
        lyricsScroll.setVerticalScrollBarEnabled(false);
        lyricsList = new LinearLayout(context);
        lyricsList.setOrientation(LinearLayout.VERTICAL);
        lyricsList.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(80), AndroidUtilities.dp(24), AndroidUtilities.dp(160));
        lyricsScroll.addView(lyricsList);
        wrap.addView(lyricsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        lyricsCurrent = -1;
        if (lyrics.synced) {
            for (RivLyrics.Line line : lyrics.lines) {
                TextView tv = new TextView(context);
                tv.setText(TextUtils.isEmpty(line.text) ? "♪" : line.text);
                tv.setTextSize(22);
                tv.setTypeface(AndroidUtilities.bold());
                tv.setTextColor(TEXT_DIM);
                tv.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                tv.setOnClickListener(v -> seekToMs(line.timeMs));
                lyricsList.addView(tv);
            }
        } else {
            TextView tv = new TextView(context);
            tv.setText(lyrics.plain);
            tv.setTextSize(18);
            tv.setLineSpacing(AndroidUtilities.dp(6), 1f);
            tv.setTextColor(TEXT);
            lyricsList.addView(tv);
        }
    }

    private void updateLyricsHighlight(long posMs) {
        if (lyrics == null || !lyrics.synced || lyricsList == null || lyricsScroll == null) return;
        int idx = -1;
        for (int i = 0; i < lyrics.lines.size(); i++) {
            if (lyrics.lines.get(i).timeMs <= posMs) idx = i;
            else break;
        }
        if (idx == lyricsCurrent || idx >= lyricsList.getChildCount()) return;
        if (lyricsCurrent >= 0 && lyricsCurrent < lyricsList.getChildCount()) {
            ((TextView) lyricsList.getChildAt(lyricsCurrent)).setTextColor(TEXT_DIM);
        }
        lyricsCurrent = idx;
        if (idx >= 0) {
            TextView tv = (TextView) lyricsList.getChildAt(idx);
            tv.setTextColor(TEXT);
            int y = tv.getTop() - lyricsScroll.getHeight() / 3;
            lyricsScroll.smoothScrollTo(0, Math.max(0, y));
        }
    }

    // ------------------------------------------------------------------ queue

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
                row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
                BackupImageView art = new BackupImageView(context);
                art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(8));
                row.addView(art, LayoutHelper.createLinear(46, 46));
                LinearLayout col = new LinearLayout(context);
                col.setOrientation(LinearLayout.VERTICAL);
                row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
                TextView t = new TextView(context);
                t.setTextSize(15);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(t);
                TextView a = new TextView(context);
                a.setTextSize(13);
                a.setSingleLine(true);
                a.setEllipsize(TextUtils.TruncateAt.END);
                a.setTextColor(TEXT_DIM);
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
                if (thumb != null) art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), "46_46", new ColorDrawable(0xFF2A2236), m);
                else art.setImageDrawable(new ColorDrawable(0xFF2A2236));
                t.setText(m.getMusicTitle());
                t.setTextColor(position == cur ? accent : TEXT);
                a.setText(m.getMusicAuthor());
                holder.itemView.setOnClickListener(v -> {
                    MediaController.getInstance().playMessage(m);
                    refresh();
                });
            }
        });
        if (cur > 2) list.scrollToPosition(cur - 2);
        return list;
    }

    // ------------------------------------------------------------------ controls

    private void buildControls(Context context, LinearLayout column) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(14));
        column.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTextSize(22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setSelected(true);
        titleView.setTextColor(TEXT);
        titleCol.addView(titleView);

        artistView = new TextView(context);
        artistView.setTextSize(16);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(artistView);

        formatView = new TextView(context);
        formatView.setTextSize(12);
        formatView.setTypeface(AndroidUtilities.bold());
        formatView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        titleCol.addView(formatView);

        favIcon = new RivIcon(context);
        favIcon.setKind(RivIcon.HEART_OUTLINE);
        favIcon.setColor(TEXT);
        titleRow.addView(favIcon, LayoutHelper.createLinear(44, 44, 8, 0, 0, 0));
        favIcon.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m != null) {
                RivMeta.toggleFavoriteSong(RivMusicActivity.keyOf(m));
                refresh();
            }
        });

        seekBar = new RivWavySeekBar(context);
        box.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 0, 10, 0, 0));
        seekBar.setListener(new RivWavySeekBar.Listener() {
            @Override
            public void onSeek(float fraction) {
                draggedFraction = fraction;
                posLabel.setText(AndroidUtilities.formatShortDuration((int) (fraction * MediaController.getInstance().getDuration() / 1000)));
            }

            @Override
            public void onSeekEnd() {
                MessageObject m = MediaController.getInstance().getPlayingMessageObject();
                if (m != null && draggedFraction >= 0) MediaController.getInstance().seekToProgress(m, draggedFraction);
                draggedFraction = -1f;
            }
        });

        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(timeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        posLabel = new TextView(context);
        posLabel.setTextSize(12);
        posLabel.setTextColor(TEXT_DIM);
        timeRow.addView(posLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        durLabel = new TextView(context);
        durLabel.setTextSize(12);
        durLabel.setTextColor(TEXT_DIM);
        timeRow.addView(durLabel);

        // -10s, previous, big play/pause, next, +10s
        LinearLayout transport = new LinearLayout(context);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(transport, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 80, 0, 6, 0, 0));

        RivIcon rewind = transportIcon(context, transport, RivIcon.REWIND, 34);
        rewind.setOnClickListener(v -> seekBy(-10_000));
        RivIcon prev = transportIcon(context, transport, RivIcon.PREV_FILLED, 34);
        prev.setOnClickListener(v -> {
            MediaController.getInstance().playPreviousMessage();
            refresh();
        });

        playPauseBg = new FrameLayout(context);
        transport.addView(playPauseBg, LayoutHelper.createLinear(76, 76, 10, 0, 10, 0));
        playPauseIcon = new RivIcon(context);
        playPauseIcon.setColor(TEXT);
        playPauseBg.addView(playPauseIcon, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
        playPauseBg.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m == null) return;
            if (MediaController.getInstance().isMessagePaused()) MediaController.getInstance().playMessage(m);
            else MediaController.getInstance().pauseMessage(m);
            refresh();
        });

        RivIcon next = transportIcon(context, transport, RivIcon.NEXT_FILLED, 34);
        next.setOnClickListener(v -> {
            MediaController.getInstance().playNextMessage();
            refresh();
        });
        RivIcon forward = transportIcon(context, transport, RivIcon.FORWARD, 34);
        forward.setOnClickListener(v -> seekBy(10_000));

        // output device
        LinearLayout outputRow = new LinearLayout(context);
        outputRow.setOrientation(LinearLayout.HORIZONTAL);
        outputRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(outputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34, 0, 4, 0, 0));
        outputIcon = new RivIcon(context);
        outputIcon.setKind(RivIcon.PHONE);
        outputRow.addView(outputIcon, LayoutHelper.createLinear(22, 22, 0, 0, 8, 0));
        outputChip = new TextView(context);
        outputChip.setTextSize(14);
        outputChip.setSingleLine(true);
        outputRow.addView(outputChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // bottom pill: shuffle / repeat / sleep / speed / EQ / lyrics / queue
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(CONTROL_BG);
        pillBg.setCornerRadius(AndroidUtilities.dp(26));
        pill.setBackground(pillBg);
        box.addView(pill, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 10, 0, 0));

        shuffleIcon = addPillIcon(context, pill, RivIcon.SHUFFLE, v -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            refresh();
        });
        repeatIcon = addPillIcon(context, pill, RivIcon.REPEAT, v -> {
            SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
            refresh();
        });
        addPillIcon(context, pill, RivIcon.MOON, v -> showSleepDialog());
        addPillIcon(context, pill, RivIcon.SPEED, v -> showSpeedDialog());
        addPillIcon(context, pill, RivIcon.EQ, v -> showEqDialog());
        addPillIcon(context, pill, RivIcon.LYRICS, v -> {
            page = 1;
            updateChips();
            showPage(context);
        });
        addPillIcon(context, pill, RivIcon.QUEUE, v -> {
            page = 2;
            updateChips();
            showPage(context);
        });
    }

    private RivIcon transportIcon(Context context, LinearLayout row, int kind, int sizeDp) {
        FrameLayout cell = new FrameLayout(context);
        row.addView(cell, LayoutHelper.createLinear(0, 56, 1f));
        RivIcon icon = new RivIcon(context);
        icon.setKind(kind);
        icon.setColor(TEXT);
        cell.addView(icon, LayoutHelper.createFrame(sizeDp, sizeDp, Gravity.CENTER));
        cell.setOnClickListener(v -> icon.performClick());
        return icon;
    }

    private RivIcon addPillIcon(Context context, LinearLayout pill, int kind, View.OnClickListener l) {
        FrameLayout cell = new FrameLayout(context);
        pill.addView(cell, LayoutHelper.createLinear(0, 52, 1f));
        RivIcon icon = new RivIcon(context);
        icon.setKind(kind);
        icon.setColor(TEXT);
        icon.setOnClickListener(l);
        cell.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        cell.setOnClickListener(l);
        return icon;
    }

    private void seekBy(long deltaMs) {
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        long dur = (long) MediaController.getInstance().getDuration();
        if (m == null || dur <= 0) return;
        long pos = MediaController.getInstance().getProgressMs(m) + deltaMs;
        seekToMs(Math.max(0, Math.min(dur - 500, pos)));
    }

    private void seekToMs(long ms) {
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        long dur = (long) MediaController.getInstance().getDuration();
        if (m == null || dur <= 0) return;
        MediaController.getInstance().seekToProgress(m, Math.max(0f, Math.min(1f, (float) ms / dur)));
        refresh();
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

    // ------------------------------------------------------------------ colours from the cover

    /** Picks the cover's strongest colour once it has loaded, like RTP's "Color by current track". */
    private void updateColorsFromCover(MessageObject m) {
        String key = RivMusicActivity.keyOf(m);
        boolean fromCover = RivPrefs.b(RivPrefs.PLAYER_RTP, true) && RivPrefs.b(RivPrefs.ACCENT_FROM_COVER, true);
        int custom = RivPrefs.i(RivPrefs.ACCENT, 0);
        if (!fromCover) {
            int a = custom != 0 ? custom : DEFAULT_ACCENT;
            if (a != accent || colorForKey == null) {
                accent = a;
                bgTop = RivPrefs.b(RivPrefs.PLAYER_RTP, true) ? darken(a, 0.22f) : 0xFF121212;
                colorForKey = key;
                applyColors();
            }
            return;
        }
        if (key.equals(colorForKey) || cover == null) return;
        Bitmap bmp = cover.getImageReceiver().getBitmap();
        if (bmp == null || bmp.isRecycled()) return;
        int picked = dominantColor(bmp);
        colorForKey = key;
        if (picked == 0) {
            accent = custom != 0 ? custom : DEFAULT_ACCENT;
        } else {
            float[] hsv = new float[3];
            Color.colorToHSV(picked, hsv);
            hsv[1] = Math.max(0.45f, Math.min(0.8f, hsv[1]));
            hsv[2] = 0.88f;
            accent = Color.HSVToColor(hsv);
        }
        bgTop = darken(accent, 0.24f);
        applyColors();
    }

    private static int darken(int color, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1], 0.6f);
        hsv[2] = value;
        return Color.HSVToColor(hsv);
    }

    private static int dominantColor(Bitmap bmp) {
        try {
            Bitmap small = Bitmap.createScaledBitmap(bmp, 24, 24, true);
            float bestScore = 0f;
            int best = 0;
            float[] hsv = new float[3];
            for (int x = 0; x < 24; x++) {
                for (int y = 0; y < 24; y++) {
                    int c = small.getPixel(x, y);
                    Color.colorToHSV(c, hsv);
                    float score = hsv[1] * (0.3f + hsv[2]);
                    if (hsv[2] < 0.15f) score = 0f;
                    if (score > bestScore) {
                        bestScore = score;
                        best = c;
                    }
                }
            }
            if (small != bmp) small.recycle();
            return bestScore < 0.12f ? 0 : (best | 0xFF000000);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void applyColors() {
        applyBackground();
        updateChips();
        if (artistView != null) artistView.setTextColor(accent);
        if (formatView != null) formatView.setTextColor(accent);
        if (seekBar != null) seekBar.setColors(accent, 0x40FFFFFF);
        if (playPauseBg != null) {
            GradientDrawable ppBg = new GradientDrawable();
            ppBg.setColor(accent);
            ppBg.setCornerRadius(AndroidUtilities.dp(26));
            playPauseBg.setBackground(ppBg);
        }
        if (outputIcon != null) outputIcon.setColor(accent);
        if (outputChip != null) outputChip.setTextColor(accent);
    }

    // ------------------------------------------------------------------ refresh

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
            String fmt = formatLine(m);
            formatView.setText(fmt);
            formatView.setVisibility(fmt.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (favIcon != null) {
            boolean fav = RivMeta.isFavoriteSong(RivMusicActivity.keyOf(m));
            favIcon.setKind(fav ? RivIcon.HEART : RivIcon.HEART_OUTLINE);
            favIcon.setColor(fav ? accent : TEXT);
        }
        bindCover(m);
        updateColorsFromCover(m);
        boolean playing = !MediaController.getInstance().isMessagePaused();
        if (playPauseIcon != null) {
            playPauseIcon.setKind(playing ? RivIcon.PAUSE : RivIcon.PLAY);
        }
        long pos = MediaController.getInstance().getProgressMs(m);
        if (seekBar != null) {
            seekBar.setPlaying(playing);
            if (draggedFraction < 0) seekBar.setProgress(seekBarProgress());
            long dur = (long) MediaController.getInstance().getDuration();
            if (draggedFraction < 0) posLabel.setText(AndroidUtilities.formatShortDuration((int) (pos / 1000)));
            durLabel.setText(AndroidUtilities.formatShortDuration((int) (dur / 1000)));
        }
        if (shuffleIcon != null) {
            shuffleIcon.setColor(SharedConfig.shuffleMusic ? accent : TEXT);
        }
        if (repeatIcon != null) {
            repeatIcon.setKind(SharedConfig.repeatMode == 1 ? RivIcon.REPEAT_ONE : RivIcon.REPEAT);
            repeatIcon.setColor(SharedConfig.repeatMode != 0 ? accent : TEXT);
        }
        updateLyricsHighlight(pos);
        updateOutputChip();
    }

    /** "MP3 · 320 kbps" from the file type and its size over its length. */
    private static String formatLine(MessageObject m) {
        TLRPC.Document doc = m.getDocument();
        if (doc == null) return "";
        String ext = "";
        String name = FileLoader.getDocumentFileName(doc);
        if (name != null && name.lastIndexOf('.') > 0) {
            ext = name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.US);
        } else if (doc.mime_type != null && doc.mime_type.contains("/")) {
            ext = doc.mime_type.substring(doc.mime_type.indexOf('/') + 1).toUpperCase(Locale.US);
        }
        if ("MPEG".equals(ext)) ext = "MP3";
        double dur = m.getDuration();
        String rate = "";
        if (dur > 0 && doc.size > 0) {
            long kbps = Math.round(doc.size * 8 / dur / 1000);
            if (kbps > 0 && kbps < 20000) rate = kbps + " kbps";
        }
        if (ext.isEmpty()) return rate;
        return rate.isEmpty() ? ext : ext + " · " + rate;
    }

    private void updateOutputChip() {
        if (outputChip == null) return;
        String name = LocaleController.getString(R.string.RivPhoneSpeaker);
        boolean external = false;
        try {
            AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo d : devices) {
                        int type = d.getType();
                        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            name = d.getProductName() != null ? d.getProductName().toString() : LocaleController.getString(R.string.RivBluetooth);
                            external = true;
                            break;
                        } else if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                            name = LocaleController.getString(R.string.RivWiredHeadphones);
                            external = true;
                            break;
                        }
                    }
                } else if (am.isBluetoothA2dpOn() || am.isBluetoothScoOn()) {
                    name = LocaleController.getString(R.string.RivBluetooth);
                    external = true;
                } else if (am.isWiredHeadsetOn()) {
                    name = LocaleController.getString(R.string.RivWiredHeadphones);
                    external = true;
                }
            }
        } catch (Throwable ignored) {
        }
        outputChip.setText(name);
        if (outputIcon != null) outputIcon.setKind(external ? RivIcon.SPEAKER : RivIcon.PHONE);
    }

    // ------------------------------------------------------------------ dialogs

    private void showMoreMenu() {
        if (getParentActivity() == null) return;
        MessageObject current = MediaController.getInstance().getPlayingMessageObject();
        if (current == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        CharSequence[] items = {
                LocaleController.getString(R.string.RivShowInChat),
                LocaleController.getString(R.string.RivEqualizer),
                LocaleController.getString(R.string.RivSleepTimer),
        };
        b.setItems(items, (d, w) -> {
            if (w == 0) {
                Bundle args = new Bundle();
                if (current.getDialogId() >= 0) args.putLong("user_id", current.getDialogId());
                else args.putLong("chat_id", -current.getDialogId());
                args.putInt("message_id", current.getId());
                presentFragment(new ChatActivity(args));
            } else if (w == 1) {
                showEqDialog();
            } else {
                showSleepDialog();
            }
        });
        showDialog(b.create());
    }

    private void showSleepDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivSleepTimer));
        int[] minutes = {10, 15, 30, 45, 60, 90};
        // Riv Post: also "end of this song" and "after N songs"
        int[] songs = {1, 2, 3, 5};
        CharSequence[] items = new CharSequence[songs.length + minutes.length];
        items[0] = LocaleController.getString(R.string.RivSleepEndOfSong);
        for (int i = 1; i < songs.length; i++) items[i] = LocaleController.formatString(R.string.RivSleepAfterSongs, songs[i]);
        for (int i = 0; i < minutes.length; i++) items[songs.length + i] = minutes[i] + " " + LocaleController.getString(R.string.RivMinutesWord);
        b.setItems(items, (d, w) -> {
            if (w < songs.length) {
                org.telegram.messenger.RivPlayback.stopAfterSongs(songs[w]);
                return;
            }
            long atMs = System.currentTimeMillis() + minutes[w - songs.length] * 60_000L;
            RivSleepTimer.schedule(atMs);
        });
        b.setNegativeButton(LocaleController.getString(R.string.RivTurnOff), (d, w) -> {
            RivSleepTimer.cancel();
            org.telegram.messenger.RivPlayback.stopAfterSongs(0);
        });
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

    /** Real equalizer: on/off, the phone's presets, and a slider per band. */
    private void showEqDialog() {
        if (getParentActivity() == null) return;
        Context context = getParentActivity();
        Equalizer eq = RivEq.current();
        if (eq == null) {
            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle(LocaleController.getString(R.string.RivEqualizer));
            b.setMessage(LocaleController.getString(R.string.RivEqualizerUnavailable));
            b.setPositiveButton(LocaleController.getString(R.string.OK), null);
            showDialog(b.create());
            return;
        }
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(6), AndroidUtilities.dp(22), AndroidUtilities.dp(6));

        Switch onOff = new Switch(context);
        onOff.setText(LocaleController.getString(R.string.RivEqualizerOn));
        onOff.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        onOff.setChecked(RivPrefs.b(RivPrefs.EQ_ON));
        col.addView(onOff, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        short[] range;
        short bands;
        try {
            range = eq.getBandLevelRange();
            bands = eq.getNumberOfBands();
        } catch (Throwable t) {
            return;
        }
        final short[] fRange = range;
        SeekBar[] sliders = new SeekBar[bands];
        for (short bnd = 0; bnd < bands; bnd++) {
            final short band = bnd;
            TextView label = new TextView(context);
            int hz = 0;
            try {
                hz = eq.getCenterFreq(band) / 1000;
            } catch (Throwable ignored) {
            }
            label.setText(hz >= 1000 ? String.format(Locale.US, "%.1f kHz", hz / 1000f) : hz + " Hz");
            label.setTextSize(13);
            label.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextGray));
            col.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            SeekBar s = new SeekBar(context);
            s.setMax(fRange[1] - fRange[0]);
            try {
                s.setProgress(eq.getBandLevel(band) - fRange[0]);
            } catch (Throwable ignored) {
            }
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        if (!RivPrefs.b(RivPrefs.EQ_ON)) {
                            RivEq.setEnabled(true);
                            onOff.setChecked(true);
                        }
                        RivEq.setBand(band, progress + fRange[0]);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            sliders[band] = s;
            col.addView(s, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
        }
        onOff.setOnCheckedChangeListener((v, on) -> RivEq.setEnabled(on));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(col);
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(R.string.RivEqualizer));
        b.setView(scroll);
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        b.setNeutralButton(LocaleController.getString(R.string.RivEqPresets), (d, w) -> showPresetDialog());
        showDialog(b.create());
    }

    private void showPresetDialog() {
        Equalizer eq = RivEq.current();
        if (eq == null || getParentActivity() == null) return;
        try {
            short n = eq.getNumberOfPresets();
            CharSequence[] names = new CharSequence[n];
            for (short i = 0; i < n; i++) names[i] = eq.getPresetName(i);
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(LocaleController.getString(R.string.RivEqPresets));
            b.setItems(names, (d, w) -> {
                RivEq.setEnabled(true);
                RivEq.usePreset((short) w);
                showEqDialog();
            });
            showDialog(b.create());
        } catch (Throwable ignored) {
        }
    }
}
