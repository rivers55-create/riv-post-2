package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileStreamLoadOperation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.RivMeta;
import org.telegram.messenger.RivPrefs;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Riv Post: a video player laid out like Rivers Video Player (RVP): see-through round "glass"
 * buttons over the picture, swipe left side for brightness, right side for volume, sideways to
 * seek, double-tap a side to jump 10 seconds, lock, speed, picture size, rotate, and next/previous
 * through the shelf it was opened from. Remembers where you stopped (feeds "Continue watching").
 * Uses Telegram's own VideoPlayer, so it streams while downloading, .mkv included.
 */
public class RivVideoPlayerActivity extends RivFragment {

    private static final int GLASS = 0x33FFFFFF;
    private static final int ACCENT = 0xFFE53935;

    private final ArrayList<MessageObject> playlist = new ArrayList<>();
    private int index;

    private FrameLayout root;
    private FrameLayout videoFrame;
    private TextureView textureView;
    private VideoPlayer player;
    private int videoW, videoH;
    private int aspectMode; // 0 fit, 1 fill, 2 stretch

    private FrameLayout controls;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LinearLayout centerRow;
    private TextView titleView;
    private TextView posLabel, durLabel;
    private SeekBar seekBar;
    private FrameLayout playBtn;
    private RivIcon playIcon;
    private FrameLayout lockBtn;
    private TextView speedBtn;
    private TextView hint;
    private boolean controlsVisible = true;
    private boolean locked;
    private boolean userSeeking;
    private float speed = 1f;
    private int previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean landscape;
    private boolean enteringPip;
    private TextView subtitleView;
    private final ArrayList<long[]> subTimes = new ArrayList<>();
    private final ArrayList<String> subTexts = new ArrayList<>();
    private boolean subtitlesOn = true;
    private int subsForIndex = -1;
    private long subDelayMs = 0;
    private boolean embeddedOn = false;
    private String embeddedText = "";
    private long abA = -1, abB = -1;

    // gesture state
    private int gestureMode; // 0 none, 1 seek, 2 brightness, 3 volume
    private long gestureSeekTarget;
    private float gestureStartValue;

    private final Runnable hideControls = () -> setControlsVisible(false);
    private final Runnable tick = this::onTick;
    private final Runnable hideHint = () -> {
        if (hint != null) hint.setVisibility(View.GONE);
    };

    public RivVideoPlayerActivity(List<MessageObject> list, int startIndex) {
        if (list != null) playlist.addAll(list);
        index = Math.max(0, Math.min(startIndex, playlist.size() - 1));
    }

    /** Opens a video in this player, or in Telegram's own viewer if the Settings switch is off. */
    public static void open(org.telegram.ui.ActionBar.BaseFragment from, MessageObject m, List<MessageObject> list) {
        List<MessageObject> l = list == null || list.isEmpty() ? java.util.Collections.singletonList(m) : list;
        int i = l.indexOf(m);
        if (i < 0) {
            // Riv Post: the tapped video wasn't in the list it came with (e.g. a shelf that was
            // rebuilt meanwhile); play just that video instead of the first one in the list.
            l = java.util.Collections.singletonList(m);
            i = 0;
        }
        if (RivPrefs.b(RivPrefs.VIDEO_PLAYER_RVP, true)) {
            from.presentFragment(new RivVideoPlayerActivity(l, Math.max(0, i)));
        } else {
            PhotoViewer.getInstance().setParentActivity(from);
            PhotoViewer.getInstance().openPhoto(new ArrayList<>(l), Math.max(0, i), m.getDialogId(), 0, 0, new PhotoViewer.EmptyPhotoViewerProvider());
        }
    }

    @Override
    protected boolean useSimpleActionBar() {
        return false;
    }

    @Override
    protected void applyRivInsets() {
        // the picture fills the whole screen; only the controls keep clear of the phone's bars
        if (controls != null) {
            int top = Math.max(mSystemInsets.top, AndroidUtilities.statusBarHeight);
            int bottom = rivNavBarHeight();
            if (controls.getPaddingTop() != top || controls.getPaddingBottom() != bottom) {
                controls.setPadding(mSystemInsets.left, top, mSystemInsets.right, bottom);
            }
        }
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        if (actionBar != null) actionBar.setAddToContainer(false);
        if (playlist.isEmpty()) {
            finishFragment();
            return new FrameLayout(context);
        }
        // stop any song so the two don't play over each other
        MessageObject song = MediaController.getInstance().getPlayingMessageObject();
        if (song != null && !MediaController.getInstance().isMessagePaused()) {
            MediaController.getInstance().pauseMessage(song);
        }

        root = new FrameLayout(context);
        root.setBackgroundColor(0xFF000000);
        fragmentView = root;

        videoFrame = new FrameLayout(context);
        root.addView(videoFrame, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        textureView = new TextureView(context);
        videoFrame.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        videoFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) applyAspect();
        });

        buildControls(context);

        hint = new TextView(context);
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(18);
        hint.setTypeface(AndroidUtilities.bold());
        hint.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setColor(0x99000000);
        hintBg.setCornerRadius(AndroidUtilities.dp(20));
        hint.setBackground(hintBg);
        hint.setVisibility(View.GONE);
        root.addView(hint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(0xFFFFFFFF);
        subtitleView.setTextSize(18);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0xFF000000);
        subtitleView.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        subtitleView.setVisibility(View.GONE);
        root.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 56));

        GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                gestureMode = 0;
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                setControlsVisible(!controlsVisible);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (locked) return true;
                boolean forward = e.getX() > root.getWidth() / 2f;
                seekBy(forward ? 10_000 : -10_000);
                showHint(forward ? "+10s" : "−10s");
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (locked || e1 == null || player == null) return false;
                float totalX = e2.getX() - e1.getX();
                float totalY = e2.getY() - e1.getY();
                if (gestureMode == 0) {
                    if (Math.abs(totalX) > Math.abs(totalY)) {
                        gestureMode = 1;
                        gestureStartValue = player.getCurrentPosition();
                    } else {
                        gestureMode = e1.getX() < root.getWidth() / 2f ? 2 : 3;
                        gestureStartValue = gestureMode == 2 ? currentBrightness() : currentVolumeFraction();
                    }
                }
                if (gestureMode == 1) {
                    long dur = Math.max(1, player.getDuration());
                    long delta = (long) (totalX / root.getWidth() * Math.min(dur, 180_000));
                    gestureSeekTarget = Math.max(0, Math.min(dur, (long) gestureStartValue + delta));
                    showHint(format(gestureSeekTarget) + " / " + format(dur));
                } else {
                    float value = Math.max(0f, Math.min(1f, gestureStartValue - totalY / (root.getHeight() * 0.7f)));
                    if (gestureMode == 2) {
                        setBrightness(value);
                        showHint("☀ " + Math.round(value * 100) + "%");
                    } else {
                        setVolumeFraction(value);
                        showHint("🔊 " + Math.round(value * 100) + "%");
                    }
                }
                return true;
            }
        });
        root.setOnTouchListener((v, e) -> {
            boolean handled = detector.onTouchEvent(e);
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                if (gestureMode == 1 && player != null) {
                    player.seekTo(gestureSeekTarget);
                }
                if (gestureMode != 0) AndroidUtilities.runOnUIThread(hideHint, 600);
                gestureMode = 0;
            }
            return handled || true;
        });

        startCurrent(true);
        AndroidUtilities.runOnUIThread(tick, 500);
        scheduleHide();
        return fragmentView;
    }

    // ------------------------------------------------------------------ controls

    private FrameLayout glassButton(Context context, int kind, int sizeDp, int iconDp) {
        FrameLayout b = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(GLASS);
        bg.setStroke(AndroidUtilities.dp(1), 0x40FFFFFF);
        b.setBackground(bg);
        RivIcon icon = new RivIcon(context);
        icon.setKind(kind);
        icon.setColor(0xFFFFFFFF);
        b.addView(icon, LayoutHelper.createFrame(iconDp, iconDp, Gravity.CENTER));
        b.setTag(icon);
        return b;
    }

    private void buildControls(Context context) {
        controls = new FrameLayout(context);
        root.addView(controls, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // top: back, title, speed
        topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(24));
        topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xAA000000, 0x00000000}));
        controls.addView(topBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        FrameLayout back = glassButton(context, RivIcon.BACK, 44, 22);
        topBar.addView(back, LayoutHelper.createLinear(44, 44));
        back.setOnClickListener(v -> finishFragment());

        titleView = new TextView(context);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        topBar.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 12, 0));

        speedBtn = new TextView(context);
        speedBtn.setTextColor(0xFFFFFFFF);
        speedBtn.setTextSize(14);
        speedBtn.setTypeface(AndroidUtilities.bold());
        speedBtn.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(GLASS);
        sbg.setCornerRadius(AndroidUtilities.dp(22));
        speedBtn.setBackground(sbg);
        speedBtn.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        speedBtn.setText("1×");
        topBar.addView(speedBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44));
        speedBtn.setOnClickListener(v -> cycleSpeed());

        TextView tracksBtn = new TextView(context);
        tracksBtn.setTextColor(0xFFFFFFFF);
        tracksBtn.setTextSize(13);
        tracksBtn.setTypeface(AndroidUtilities.bold());
        tracksBtn.setGravity(Gravity.CENTER);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(GLASS);
        tbg.setCornerRadius(AndroidUtilities.dp(22));
        tracksBtn.setBackground(tbg);
        tracksBtn.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        tracksBtn.setText("CC \u266B");
        topBar.addView(tracksBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, 8, 0, 0, 0));
        tracksBtn.setOnClickListener(v -> showTracksMenu());

        // centre: previous, -10s, play/pause, +10s, next
        centerRow = new LinearLayout(context);
        centerRow.setOrientation(LinearLayout.HORIZONTAL);
        centerRow.setGravity(Gravity.CENTER);
        controls.addView(centerRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        FrameLayout prev = glassButton(context, RivIcon.PREV_FILLED, 52, 24);
        centerRow.addView(prev, LayoutHelper.createLinear(52, 52, 0, 0, 14, 0));
        prev.setOnClickListener(v -> playIndex(index - 1));
        FrameLayout rew = glassButton(context, RivIcon.REWIND, 56, 30);
        centerRow.addView(rew, LayoutHelper.createLinear(56, 56, 0, 0, 14, 0));
        rew.setOnClickListener(v -> {
            seekBy(-10_000);
            scheduleHide();
        });
        playBtn = glassButton(context, RivIcon.PAUSE, 76, 34);
        playIcon = (RivIcon) playBtn.getTag();
        centerRow.addView(playBtn, LayoutHelper.createLinear(76, 76));
        playBtn.setOnClickListener(v -> {
            togglePlay();
            scheduleHide();
        });
        FrameLayout fwd = glassButton(context, RivIcon.FORWARD, 56, 30);
        centerRow.addView(fwd, LayoutHelper.createLinear(56, 56, 14, 0, 0, 0));
        fwd.setOnClickListener(v -> {
            seekBy(10_000);
            scheduleHide();
        });
        FrameLayout next = glassButton(context, RivIcon.NEXT_FILLED, 52, 24);
        centerRow.addView(next, LayoutHelper.createLinear(52, 52, 14, 0, 0, 0));
        next.setOnClickListener(v -> playIndex(index + 1));
        prev.setVisibility(playlist.size() > 1 ? View.VISIBLE : View.GONE);
        next.setVisibility(playlist.size() > 1 ? View.VISIBLE : View.GONE);

        // bottom: times + bar, then lock / size / rotate
        bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(28), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        bottomBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xAA000000, 0x00000000}));
        controls.addView(bottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        LinearLayout seekRow = new LinearLayout(context);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.addView(seekRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
        posLabel = new TextView(context);
        posLabel.setTextColor(0xFFFFFFFF);
        posLabel.setTextSize(12);
        seekRow.addView(posLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        seekRow.addView(seekBar, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        durLabel = new TextView(context);
        durLabel.setTextColor(0xFFFFFFFF);
        durLabel.setTextSize(12);
        seekRow.addView(durLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    posLabel.setText(format(player.getDuration() * progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
                AndroidUtilities.cancelRunOnUIThread(hideControls);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (player != null) player.seekTo(player.getDuration() * s.getProgress() / 1000);
                scheduleHide();
            }
        });

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        lockBtn = glassButton(context, RivIcon.LOCK, 44, 20);
        buttons.addView(lockBtn, LayoutHelper.createLinear(44, 44));
        lockBtn.setOnClickListener(v -> setLocked(!locked));
        // when locked, the lock button sits on its own so you can unlock
        View spacer = new View(context);
        buttons.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));
        FrameLayout aspect = glassButton(context, RivIcon.ASPECT, 44, 22);
        buttons.addView(aspect, LayoutHelper.createLinear(44, 44, 0, 0, 12, 0));
        aspect.setOnClickListener(v -> {
            aspectMode = (aspectMode + 1) % 3;
            applyAspect();
            showHint(LocaleController.getString(aspectMode == 0 ? R.string.RivAspectFit : aspectMode == 1 ? R.string.RivAspectFill : R.string.RivAspectStretch));
            AndroidUtilities.runOnUIThread(hideHint, 900);
            scheduleHide();
        });
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            TextView pip = new TextView(context);
            pip.setText("PiP");
            pip.setTextColor(0xFFFFFFFF);
            pip.setTextSize(12);
            pip.setTypeface(AndroidUtilities.bold());
            pip.setGravity(Gravity.CENTER);
            GradientDrawable pbg = new GradientDrawable();
            pbg.setShape(GradientDrawable.OVAL);
            pbg.setColor(GLASS);
            pbg.setStroke(AndroidUtilities.dp(1), 0x40FFFFFF);
            pip.setBackground(pbg);
            buttons.addView(pip, LayoutHelper.createLinear(44, 44, 0, 0, 12, 0));
            pip.setOnClickListener(v -> enterPip());
        }
        FrameLayout rotate = glassButton(context, RivIcon.ROTATE, 44, 22);
        buttons.addView(rotate, LayoutHelper.createLinear(44, 44));
        rotate.setOnClickListener(v -> {
            toggleRotation();
            scheduleHide();
        });
    }

    private void setLocked(boolean value) {
        locked = value;
        int vis = locked ? View.GONE : View.VISIBLE;
        topBar.setVisibility(vis);
        centerRow.setVisibility(vis);
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            View c = bottomBar.getChildAt(i);
            if (c instanceof LinearLayout && ((LinearLayout) c).indexOfChild(lockBtn) >= 0) {
                LinearLayout row = (LinearLayout) c;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View b = row.getChildAt(j);
                    if (b != lockBtn) b.setVisibility(vis);
                }
            } else {
                c.setVisibility(vis);
            }
        }
        bottomBar.setBackground(locked ? null : new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xAA000000, 0x00000000}));
        ((RivIcon) lockBtn.getTag()).setColor(locked ? ACCENT : 0xFFFFFFFF);
        showHint(LocaleController.getString(locked ? R.string.RivLocked : R.string.RivUnlocked));
        AndroidUtilities.runOnUIThread(hideHint, 900);
        setControlsVisible(true);
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (controls == null) return;
        controls.animate().alpha(visible ? 1f : 0f).setDuration(180).start();
        for (int i = 0; i < controls.getChildCount(); i++) {
            controls.getChildAt(i).setEnabled(visible);
        }
        if (visible) scheduleHide();
    }

    private void scheduleHide() {
        AndroidUtilities.cancelRunOnUIThread(hideControls);
        AndroidUtilities.runOnUIThread(hideControls, 3500);
    }

    private void showHint(String text) {
        if (hint == null) return;
        AndroidUtilities.cancelRunOnUIThread(hideHint);
        hint.setText(text);
        hint.setVisibility(View.VISIBLE);
        if (gestureMode == 0) AndroidUtilities.runOnUIThread(hideHint, 700);
    }

    // ------------------------------------------------------------------ playback

    private void startCurrent(boolean resume) {
        MessageObject m = playlist.get(index);
        String t = m.getFileName();
        RivMeta.CleanTitle ct = RivMeta.clean(t);
        titleView.setText(ct != null && ct.title != null && !ct.title.isEmpty() ? ct.label() : t);
        if (player != null) {
            savePosition();
            player.releasePlayer(true);
        }
        TLRPC.Document doc = m.getDocument();
        if (doc == null) {
            finishFragment();
            return;
        }
        Uri uri = FileStreamLoadOperation.prepareUri(getCurrentAccount(), doc, m);
        if (uri == null) {
            finishFragment();
            return;
        }
        videoW = videoH = 0;
        player = new VideoPlayer();
        player.setTextureView(textureView);
        player.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == 4) { // ended
                    savePosition();
                    if (index + 1 < playlist.size()) {
                        if (RivPrefs.b(RivPrefs.AUTO_NEXT_EPISODE, true)) {
                            showNextCountdown();
                        } else {
                            setControlsVisible(true);
                        }
                    } else {
                        setControlsVisible(true);
                    }
                }
                updatePlayIcon();
            }

            @Override
            public void onError(VideoPlayer p, Exception e) {
                showHint(LocaleController.getString(R.string.RivVideoError));
            }

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                    int tmp = width;
                    width = height;
                    height = tmp;
                }
                videoW = (int) (width * (pixelWidthHeightRatio > 0 ? pixelWidthHeightRatio : 1f));
                videoH = height;
                applyAspect();
            }

            @Override
            public void onRenderedFirstFrame() {
            }
        });
        player.preparePlayer(uri, "other");
        subDelayMs = 0;
        embeddedOn = false;
        embeddedText = "";
        abA = abB = -1;
        try {
            player.player.addListener(new androidx.media3.common.Player.Listener() {
                @Override
                public void onCues(androidx.media3.common.text.CueGroup cueGroup) {
                    StringBuilder sb = new StringBuilder();
                    for (androidx.media3.common.text.Cue c : cueGroup.cues) {
                        if (c.text == null) continue;
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(c.text.toString());
                    }
                    embeddedText = sb.toString();
                    // a subtitle track the file marks as default/forced starts on by itself; show it
                    if (!embeddedText.isEmpty() && !embeddedOn && subTimes.isEmpty()) embeddedOn = true;
                }
            });
        } catch (Throwable ignored) {
        }
        player.setPlaybackSpeed(speed);
        long saved = resume ? RivMeta.position(getParentActivity(), RivVideosActivity.keyOf(m)) : 0;
        if (saved > 5000) {
            player.seekTo(saved);
            showHint(LocaleController.formatString(R.string.RivResumedAt, format(saved)));
            AndroidUtilities.runOnUIThread(hideHint, 1500);
        }
        player.setPlayWhenReady(true);
        keepScreenOn(true);
        loadSubtitles(m);
    }

    // ------------------------------------------------------------------ next-episode countdown

    private LinearLayout nextCard;
    private TextView nextLabel;
    private Runnable nextTick;
    private int nextLeft;

    /** Riv Post: at the end of a video, a 10-second countdown to the next one, with Play now / Cancel. */
    private void showNextCountdown() {
        if (root == null || index + 1 >= playlist.size()) return;
        cancelNextCountdown();
        Context context = root.getContext();
        MessageObject nm = playlist.get(index + 1);
        String nt = nm.getFileName();
        RivMeta.CleanTitle nct = RivMeta.clean(nt);
        final String nextTitle = nct != null && nct.title != null && !nct.title.isEmpty() ? nct.label() : nt;

        nextCard = new LinearLayout(context);
        nextCard.setOrientation(LinearLayout.VERTICAL);
        nextCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xE6202020);
        bg.setCornerRadius(AndroidUtilities.dp(16));
        nextCard.setBackground(bg);
        nextCard.setClickable(true);

        nextLabel = new TextView(context);
        nextLabel.setTextColor(0xFFFFFFFF);
        nextLabel.setTextSize(14);
        nextLabel.setMaxLines(3);
        nextCard.addView(nextLabel);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView playNow = new TextView(context);
        playNow.setText(LocaleController.getString(R.string.RivNextPlayNow));
        playNow.setTextColor(0xFF000000);
        playNow.setTypeface(AndroidUtilities.bold());
        playNow.setGravity(Gravity.CENTER);
        playNow.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        android.graphics.drawable.GradientDrawable pb = new android.graphics.drawable.GradientDrawable();
        pb.setColor(0xFFFFFFFF);
        pb.setCornerRadius(AndroidUtilities.dp(18));
        playNow.setBackground(pb);
        playNow.setOnClickListener(v -> {
            cancelNextCountdown();
            playIndex(index + 1);
        });
        row.addView(playNow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));
        TextView cancel = new TextView(context);
        cancel.setText(LocaleController.getString(R.string.Cancel));
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        cancel.setOnClickListener(v -> {
            cancelNextCountdown();
            setControlsVisible(true);
        });
        row.addView(cancel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 8, 0, 0, 0));
        nextCard.addView(row, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        root.addView(nextCard, LayoutHelper.createFrame(300, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 20, 90));
        nextLeft = 10;
        nextTick = new Runnable() {
            @Override
            public void run() {
                if (nextLabel == null) return;
                if (nextLeft <= 0) {
                    cancelNextCountdown();
                    playIndex(index + 1);
                    return;
                }
                nextLabel.setText(LocaleController.formatString(R.string.RivNextIn, nextTitle, nextLeft));
                nextLeft--;
                AndroidUtilities.runOnUIThread(this, 1000);
            }
        };
        nextTick.run();
    }

    private void cancelNextCountdown() {
        if (nextTick != null) AndroidUtilities.cancelRunOnUIThread(nextTick);
        nextTick = null;
        if (nextCard != null && nextCard.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) nextCard.getParent()).removeView(nextCard);
        nextCard = null;
        nextLabel = null;
    }

    private void playIndex(int i) {
        cancelNextCountdown();
        if (i < 0 || i >= playlist.size()) return;
        savePosition();
        index = i;
        startCurrent(true);
        setControlsVisible(true);
    }

    private void togglePlay() {
        if (player == null) return;
        boolean playing = player.isPlaying() || player.getPlayWhenReady();
        if (playing) {
            player.pause();
            savePosition();
        } else {
            player.play();
        }
        keepScreenOn(!playing);
        updatePlayIcon();
    }

    private void updatePlayIcon() {
        if (playIcon == null || player == null) return;
        playIcon.setKind(player.getPlayWhenReady() ? RivIcon.PAUSE : RivIcon.PLAY);
    }

    private void seekBy(long delta) {
        if (player == null) return;
        long dur = player.getDuration();
        long pos = Math.max(0, player.getCurrentPosition() + delta);
        if (dur > 0) pos = Math.min(dur - 500, pos);
        player.seekTo(pos);
    }

    private void cycleSpeed() {
        float[] speeds = {1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f};
        int i = 0;
        for (int k = 0; k < speeds.length; k++) {
            if (Math.abs(speeds[k] - speed) < 0.01f) {
                i = (k + 1) % speeds.length;
                break;
            }
        }
        speed = speeds[i];
        if (player != null) player.setPlaybackSpeed(speed);
        speedBtn.setText(new java.text.DecimalFormat("0.##").format(speed) + "×");
        scheduleHide();
    }

    private void onTick() {
        if (player != null && !userSeeking) {
            long dur = player.getDuration();
            long pos = player.getCurrentPosition();
            if (dur > 0) {
                seekBar.setProgress((int) (pos * 1000 / dur));
                seekBar.setSecondaryProgress((int) (player.getBufferedPosition() * 1000 / dur));
                durLabel.setText(format(dur));
            }
            posLabel.setText(format(pos));
            updatePlayIcon();
            updateSubtitle(pos);
            if (abA >= 0 && abB > abA && pos >= abB) player.seekTo(abA);
            Activity pa = getParentActivity();
            if (android.os.Build.VERSION.SDK_INT >= 24 && pa != null && pa.isInPictureInPictureMode() && controls.getAlpha() > 0f) {
                controls.setAlpha(0f);
                hint.setVisibility(View.GONE);
            }
            if (System.currentTimeMillis() % 5000 < 250) savePosition();
        }
        AndroidUtilities.runOnUIThread(tick, 250);
    }

    private void savePosition() {
        if (player == null || index < 0 || index >= playlist.size()) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur <= 0 || pos <= 0) return;
        RivMeta.savePosition(getParentActivity(), RivVideosActivity.keyOf(playlist.get(index)), pos, dur);
    }

    private void applyAspect() {
        if (textureView == null || videoFrame == null || videoW <= 0 || videoH <= 0) return;
        int fw = videoFrame.getWidth(), fh = videoFrame.getHeight();
        if (fw <= 0 || fh <= 0) return;
        int w, h;
        if (aspectMode == 2) {
            w = fw;
            h = fh;
        } else {
            float scale = aspectMode == 1 ? Math.max((float) fw / videoW, (float) fh / videoH) : Math.min((float) fw / videoW, (float) fh / videoH);
            w = Math.round(videoW * scale);
            h = Math.round(videoH * scale);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) textureView.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            lp.gravity = Gravity.CENTER;
            textureView.setLayoutParams(lp);
        }
    }

    private void toggleRotation() {
        Activity a = getParentActivity();
        if (a == null) return;
        landscape = !landscape;
        a.setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void keepScreenOn(boolean on) {
        Activity a = getParentActivity();
        if (a == null) return;
        if (on) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private float currentBrightness() {
        Activity a = getParentActivity();
        if (a == null) return 0.5f;
        float b = a.getWindow().getAttributes().screenBrightness;
        return b < 0 ? 0.5f : b;
    }

    private void setBrightness(float value) {
        Activity a = getParentActivity();
        if (a == null) return;
        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.02f, value);
        a.getWindow().setAttributes(lp);
    }

    private float currentVolumeFraction() {
        AudioManager am = (AudioManager) root.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return 0.5f;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0.5f : (float) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max;
    }

    private void setVolumeFraction(float value) {
        AudioManager am = (AudioManager) root.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    @Override
    public void onPause() {
        super.onPause();
        Activity pa = getParentActivity();
        if (enteringPip || (android.os.Build.VERSION.SDK_INT >= 24 && pa != null && pa.isInPictureInPictureMode())) {
            return;
        }
        if (player != null) {
            player.pause();
            savePosition();
            updatePlayIcon();
        }
        keepScreenOn(false);
    }

    @Override
    public void onFragmentDestroy() {
        if (nextTick != null) AndroidUtilities.cancelRunOnUIThread(nextTick);
        AndroidUtilities.cancelRunOnUIThread(tick);
        AndroidUtilities.cancelRunOnUIThread(hideControls);
        AndroidUtilities.cancelRunOnUIThread(hideHint);
        if (player != null) {
            savePosition();
            player.releasePlayer(true);
            player = null;
        }
        Activity a = getParentActivity();
        if (a != null) {
            keepScreenOn(false);
            a.setRequestedOrientation(previousOrientation);
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            a.getWindow().setAttributes(lp);
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean onFragmentCreate() {
        Activity a = getParentActivity();
        if (a != null) previousOrientation = a.getRequestedOrientation();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        enteringPip = false;
        if (controls != null && controlsVisible) controls.setAlpha(1f);
    }

    // ------------------------------------------------------------------ picture-in-picture

    private void enterPip() {
        Activity a = getParentActivity();
        if (a == null || android.os.Build.VERSION.SDK_INT < 26) return;
        try {
            android.app.PictureInPictureParams.Builder b = new android.app.PictureInPictureParams.Builder();
            if (videoW > 0 && videoH > 0) {
                float r = Math.max(1f / 2.3f, Math.min(2.3f, (float) videoW / videoH));
                b.setAspectRatio(new android.util.Rational(Math.round(r * 1000), 1000));
            }
            enteringPip = true;
            setControlsVisible(false);
            a.enterPictureInPictureMode(b.build());
        } catch (Throwable t) {
            enteringPip = false;
            showHint(LocaleController.getString(R.string.RivPipUnavailable));
        }
    }

    // ------------------------------------------------------------------ audio tracks & subtitles

    private void showTracksMenu() {
        Activity a = getParentActivity();
        if (a == null || player == null || player.player == null) return;
        final ArrayList<String> names = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();
        try {
            androidx.media3.common.Tracks tracks = player.player.getCurrentTracks();
            int n = 1;
            for (androidx.media3.common.Tracks.Group g : tracks.getGroups()) {
                if (g.getType() != androidx.media3.common.C.TRACK_TYPE_AUDIO) continue;
                for (int i = 0; i < g.length; i++) {
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String name = f.label;
                    if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(f.language) && !"und".equals(f.language)) {
                        name = new Locale(f.language).getDisplayLanguage();
                    }
                    if (TextUtils.isEmpty(name)) name = LocaleController.getString(R.string.RivAudioTrack) + " " + n;
                    n++;
                    final androidx.media3.common.Tracks.Group group = g;
                    final int index = i;
                    names.add("\u266B  " + name + (g.isTrackSelected(i) ? "  \u2713" : ""));
                    actions.add(() -> player.player.setTrackSelectionParameters(player.player.getTrackSelectionParameters().buildUpon()
                            .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(), index)).build()));
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            androidx.media3.common.Tracks tr = player.player.getCurrentTracks();
            int k = 1;
            for (androidx.media3.common.Tracks.Group g : tr.getGroups()) {
                if (g.getType() != androidx.media3.common.C.TRACK_TYPE_TEXT) continue;
                for (int i = 0; i < g.length; i++) {
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String name = f.label;
                    if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(f.language) && !"und".equals(f.language)) {
                        name = new Locale(f.language).getDisplayLanguage();
                    }
                    if (TextUtils.isEmpty(name)) name = LocaleController.getString(R.string.RivSubtitleTrack) + " " + k;
                    k++;
                    final androidx.media3.common.Tracks.Group group = g;
                    final int ti = i;
                    names.add("CC  " + name + (embeddedOn && g.isTrackSelected(i) ? "  \u2713" : ""));
                    actions.add(() -> {
                        player.player.setTrackSelectionParameters(player.player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(), ti)).build());
                        embeddedOn = true;
                        subtitlesOn = true;
                    });
                }
            }
            if (embeddedOn) {
                names.add("CC  " + LocaleController.getString(R.string.RivEmbeddedSubsOff));
                actions.add(() -> {
                    player.player.setTrackSelectionParameters(player.player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true).build());
                    embeddedOn = false;
                    embeddedText = "";
                });
            }
        } catch (Throwable ignored) {
        }
        if (!subTimes.isEmpty() || embeddedOn) {
            final String dl = (subDelayMs >= 0 ? "+" : "") + new java.text.DecimalFormat("0.0").format(subDelayMs / 1000f) + "s";
            names.add("CC  " + LocaleController.formatString(R.string.RivSubDelayEarlier, dl));
            actions.add(() -> {
                subDelayMs -= 500;
                showHint(LocaleController.formatString(R.string.RivSubDelayNow, (subDelayMs >= 0 ? "+" : "") + new java.text.DecimalFormat("0.0").format(subDelayMs / 1000f) + "s"));
            });
            names.add("CC  " + LocaleController.formatString(R.string.RivSubDelayLater, dl));
            actions.add(() -> {
                subDelayMs += 500;
                showHint(LocaleController.formatString(R.string.RivSubDelayNow, (subDelayMs >= 0 ? "+" : "") + new java.text.DecimalFormat("0.0").format(subDelayMs / 1000f) + "s"));
            });
        }
        if (abA < 0) {
            names.add("A-B  " + LocaleController.getString(R.string.RivAbSetA));
            actions.add(() -> {
                abA = player.getCurrentPosition();
                abB = -1;
                showHint("A  " + format(abA));
            });
        } else if (abB < 0) {
            names.add("A-B  " + LocaleController.getString(R.string.RivAbSetB));
            actions.add(() -> {
                long p = player.getCurrentPosition();
                if (p > abA + 500) {
                    abB = p;
                    showHint("A " + format(abA) + "  B " + format(abB));
                }
            });
        } else {
            names.add("A-B  " + LocaleController.getString(R.string.RivAbClear));
            actions.add(() -> {
                abA = abB = -1;
            });
        }
        if (!subTimes.isEmpty()) {
            names.add("CC  " + LocaleController.getString(subtitlesOn ? R.string.RivSubtitlesOff : R.string.RivSubtitlesOn));
            actions.add(() -> {
                subtitlesOn = !subtitlesOn;
                if (!subtitlesOn) subtitleView.setVisibility(View.GONE);
            });
        } else {
            names.add("CC  " + LocaleController.getString(R.string.RivNoSubtitles));
            actions.add(() -> {
            });
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
        b.setTitle(LocaleController.getString(R.string.RivAudioAndSubtitles));
        b.setItems(names.toArray(new CharSequence[0]), (d, w) -> actions.get(w).run());
        showDialog(b.create());
    }

    private static String baseName(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        String b = dot > 0 ? fileName.substring(0, dot) : fileName;
        return b.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    /** Looks in the video's chat for an .srt with the same name, downloads it and reads it. */
    private void loadSubtitles(MessageObject video) {
        subTimes.clear();
        subTexts.clear();
        if (subtitleView != null) subtitleView.setVisibility(View.GONE);
        subsForIndex = index;
        if (!RivPrefs.b(RivPrefs.SUBTITLES, true) || video == null || video.getDocument() == null) return;
        final int forIndex = index;
        final int account = getCurrentAccount();
        String videoName = org.telegram.messenger.FileLoader.getDocumentFileName(video.getDocument());
        final String base = baseName(videoName);
        if (base.isEmpty()) return;
        int dot = videoName.lastIndexOf('.');
        String query = dot > 0 ? videoName.substring(0, dot) : videoName;
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = getMessagesController().getInputPeer(video.getDialogId());
        req.q = query.length() > 60 ? query.substring(0, 60) : query;
        req.filter = new TLRPC.TL_inputMessagesFilterDocument();
        req.limit = 30;
        org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (!(response instanceof TLRPC.messages_Messages)) return;
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            for (TLRPC.Message msg : res.messages) {
                MessageObject mo = new MessageObject(account, msg, false, false);
                TLRPC.Document d = mo.getDocument();
                if (d == null) continue;
                String n = org.telegram.messenger.FileLoader.getDocumentFileName(d);
                if (n == null || !n.toLowerCase(Locale.US).endsWith(".srt")) continue;
                if (!baseName(n).equals(base)) continue;
                downloadAndParse(account, mo, d, forIndex);
                return;
            }
        });
    }

    private void downloadAndParse(int account, MessageObject mo, TLRPC.Document d, int forIndex) {
        AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.FileLoader.getInstance(account).loadFile(d, mo, org.telegram.messenger.FileLoader.PRIORITY_HIGH, 0));
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            java.io.File f = null;
            for (int i = 0; i < 120; i++) {
                f = org.telegram.messenger.FileLoader.getInstance(account).getPathToAttach(d);
                if (f != null && f.exists() && f.length() > 0) break;
                f = org.telegram.messenger.FileLoader.getInstance(account).getPathToMessage(mo.messageOwner);
                if (f != null && f.exists() && f.length() > 0) break;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
            if (f == null || !f.exists()) return;
            ArrayList<long[]> times = new ArrayList<>();
            ArrayList<String> texts = new ArrayList<>();
            try {
                parseSrt(new String(readAll(f), "UTF-8"), times, texts);
            } catch (Throwable ignored) {
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (forIndex != index || times.isEmpty()) return;
                subTimes.clear();
                subTimes.addAll(times);
                subTexts.clear();
                subTexts.addAll(texts);
                showHint(LocaleController.getString(R.string.RivSubtitlesLoaded));
            });
        });
    }

    private static byte[] readAll(java.io.File f) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private static final java.util.regex.Pattern SRT_TIME = java.util.regex.Pattern.compile("(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,.](\\d+)");

    static void parseSrt(String text, ArrayList<long[]> times, ArrayList<String> texts) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] blocks = text.replace("\r", "").split("\n\\s*\n");
        for (String block : blocks) {
            String[] lines = block.trim().split("\n");
            for (int i = 0; i < lines.length; i++) {
                java.util.regex.Matcher m = SRT_TIME.matcher(lines[i]);
                if (!m.find()) continue;
                long start = Long.parseLong(m.group(1)) * 3600000 + Long.parseLong(m.group(2)) * 60000 + Long.parseLong(m.group(3)) * 1000 + Long.parseLong(m.group(4));
                long end = Long.parseLong(m.group(5)) * 3600000 + Long.parseLong(m.group(6)) * 60000 + Long.parseLong(m.group(7)) * 1000 + Long.parseLong(m.group(8));
                StringBuilder sb = new StringBuilder();
                for (int k = i + 1; k < lines.length; k++) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(lines[k].replaceAll("<[^>]+>", ""));
                }
                times.add(new long[]{start, end});
                texts.add(sb.toString());
                break;
            }
        }
    }

    private void updateSubtitle(long posMs) {
        if (subtitleView == null) return;
        if (subtitlesOn && embeddedOn) {
            if (TextUtils.isEmpty(embeddedText)) {
                subtitleView.setVisibility(View.GONE);
            } else {
                if (!embeddedText.contentEquals(subtitleView.getText())) subtitleView.setText(embeddedText);
                subtitleView.setVisibility(View.VISIBLE);
                subtitleView.setTranslationY(controlsVisible ? -AndroidUtilities.dp(70) : 0);
            }
            return;
        }
        posMs -= subDelayMs;
        if (!subtitlesOn || subTimes.isEmpty()) {
            if (subtitleView.getVisibility() != View.GONE) subtitleView.setVisibility(View.GONE);
            return;
        }
        String show = null;
        for (int i = 0; i < subTimes.size(); i++) {
            long[] t = subTimes.get(i);
            if (posMs >= t[0] && posMs <= t[1]) {
                show = subTexts.get(i);
                break;
            }
        }
        if (show == null) {
            subtitleView.setVisibility(View.GONE);
        } else {
            if (!show.contentEquals(subtitleView.getText())) subtitleView.setText(show);
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setTranslationY(controlsVisible ? -AndroidUtilities.dp(70) : 0);
        }
    }
}
