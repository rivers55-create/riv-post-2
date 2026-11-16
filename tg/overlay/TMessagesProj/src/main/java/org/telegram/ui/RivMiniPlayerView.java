package org.telegram.ui;

import android.graphics.drawable.GradientDrawable;

import android.graphics.Color;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.RivMeta;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Riv Post: the docked mini-player bar shown above the bottom tabs on the music screens.
 * Square art, title/artist, output/favorite/play-pause icons, a thin progress line along
 * the bottom edge. Tap opens the full "Now Playing" screen. Playback itself stays on
 * MediaController, which is Telegram's own working audio engine.
 */
public class RivMiniPlayerView extends FrameLayout {

    private final BackupImageView art;
    private final TextView title;
    private final TextView artist;
    private final RivIcon favIcon;
    private final RivIcon playPauseIcon;
    private final View progressLine;
    private Runnable onOpenPlayer;

    private final FrameLayout card;

    // Riv Post: redesigned as a rounded floating pill (it was a flat strip whose speaker icon was
    // cut off and whose edges ran into the list above). Speaker button replaced by "next",
    // play/pause sits in a filled circle, and the progress line is clipped to the pill's shape.
    public RivMiniPlayerView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        int rivMini = org.telegram.messenger.RivPrefs.i(org.telegram.messenger.RivTheme.MINI_COLOR, 0);
        int cardColor = rivMini != 0 ? rivMini : Theme.getColor(Theme.key_windowBackgroundWhite);

        card = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(AndroidUtilities.dp(16));
        cardBg.setStroke(Math.max(1, AndroidUtilities.dp(0.7f)), 0x22FFFFFF);
        card.setBackground(cardBg);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            card.setClipToOutline(true);
            card.setElevation(AndroidUtilities.dp(3));
        }
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 8, 4, 8, 4));

        art = new BackupImageView(context);
        art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(10));
        card.addView(art, LayoutHelper.createFrame(42, 42, Gravity.CENTER_VERTICAL | Gravity.LEFT, 7, 0, 0, 0));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 59, 0, 132, 0));

        title = new TextView(context);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(14);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);

        artist = new TextView(context);
        artist.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        artist.setTextSize(12);
        artist.setMaxLines(1);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(artist, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(right, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 6, 0));

        favIcon = new RivIcon(context);
        favIcon.setKind(RivIcon.HEART_OUTLINE);
        FrameLayout favWrap = wrap(context, favIcon);
        right.addView(favWrap, LayoutHelper.createLinear(40, 40));
        favWrap.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m != null) {
                RivMeta.toggleFavoriteSong(RivMusicActivity.keyOf(m));
                updateFav(m);
            }
        });

        playPauseIcon = new RivIcon(context);
        playPauseIcon.setColor(Color.WHITE);
        FrameLayout playWrap = new FrameLayout(context);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setShape(GradientDrawable.OVAL);
        playBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        playWrap.setBackground(playBg);
        playWrap.addView(playPauseIcon, LayoutHelper.createFrame(18, 18, Gravity.CENTER));
        right.addView(playWrap, LayoutHelper.createLinear(38, 38, 2, 0, 2, 0));
        playWrap.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m == null) return;
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(m);
            } else {
                MediaController.getInstance().pauseMessage(m);
            }
            updatePlayPause();
        });

        RivIcon nextIcon = new RivIcon(context);
        nextIcon.setKind(RivIcon.NEXT_FILLED);
        nextIcon.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        FrameLayout nextWrap = wrap(context, nextIcon);
        right.addView(nextWrap, LayoutHelper.createLinear(40, 40));
        nextWrap.setOnClickListener(v -> {
            MediaController.getInstance().playNextMessage();
            refresh();
        });

        progressLine = new View(context);
        progressLine.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        card.addView(progressLine, LayoutHelper.createFrame(0, 2, Gravity.BOTTOM | Gravity.LEFT));

        card.setOnClickListener(v -> {
            if (onOpenPlayer != null) onOpenPlayer.run();
        });
    }

    private static FrameLayout wrap(Context context, RivIcon icon) {
        FrameLayout f = new FrameLayout(context);
        f.addView(icon, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        return f;
    }

    // Riv Post: keeps itself current (song, play/pause, progress, shown/hidden) on every page,
    // not only on the pages that remembered to call refresh().
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AndroidUtilities.cancelRunOnUIThread(tick);
        AndroidUtilities.runOnUIThread(tick, 300);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(tick);
    }

    public void setOnOpenPlayer(Runnable r) {
        onOpenPlayer = r;
    }

    /** Refreshes art/title/artist/icons/progress from MediaController's current state. Call on a short tick while visible. */
    public void refresh() {
        MessageObject m = MediaController.getInstance().getPlayingMessageObject();
        if (m == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        TLRPC.Document doc = m.getDocument();
        TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 150) : null;
        if (thumb != null) {
            art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), "50_50", new ColorDrawable(0xFF2A2236), m);
        } else {
            art.setImageDrawable(new ColorDrawable(0xFF2A2236));
        }
        String t = m.getMusicTitle();
        title.setText(t == null || t.isEmpty() ? m.getFileName() : t);
        String a = m.getMusicAuthor();
        artist.setText(a == null ? "" : a);
        updateFav(m);
        updatePlayPause();
        long dur = (long) MediaController.getInstance().getDuration();
        long pos = MediaController.getInstance().getProgressMs(m);
        float frac = dur > 0 ? Math.max(0f, Math.min(1f, (float) pos / dur)) : 0f;
        post(() -> {
            if (card.getWidth() > 0) {
                android.view.ViewGroup.LayoutParams lp = progressLine.getLayoutParams();
                lp.width = (int) (card.getWidth() * frac);
                progressLine.setLayoutParams(lp);
            }
        });
    }

    private void updateFav(MessageObject m) {
        boolean fav = RivMeta.isFavoriteSong(RivMusicActivity.keyOf(m));
        favIcon.setKind(fav ? RivIcon.HEART : RivIcon.HEART_OUTLINE);
        favIcon.setColor(fav ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    private void updatePlayPause() {
        boolean playing = !MediaController.getInstance().isMessagePaused();
        playPauseIcon.setKind(playing ? RivIcon.PAUSE : RivIcon.PLAY);
    }

    // ---- Floating overlay shown over a chat (not docked above RivBottomBar) ----
    // Riv Post: tapping Telegram's own in-chat music bar normally opens Telegram's
    // AudioPlayerAlert; when RivPrefs.TAP_OPENS_RTP_MINI_PLAYER is on, FragmentContextView's
    // click handler calls showFloating() instead, which adds one of these bars just above the
    // bottom of the fragment's root view, over everything else, dismissible by tapping it again
    // or by tapping its own "open full player" row.
    private static RivMiniPlayerView floating;

    public static void showFloating(android.app.Activity activity, org.telegram.ui.ActionBar.BaseFragment fragment) {
        if (activity == null) return;
        android.view.ViewGroup decor = (android.view.ViewGroup) activity.getWindow().getDecorView();
        hideFloating();
        RivMiniPlayerView v = new RivMiniPlayerView(activity);
        v.setOnOpenPlayer(() -> {
            hideFloating();
            if (fragment != null) fragment.presentFragment(new RivMusicPlayerActivity());
        });
        // Riv Post: AndroidUtilities.navigationBarHeight is never actually set anywhere in real
        // Telegram, so it's always 0 -- getViewInset() reads the real, live nav-bar height off
        // the window instead.
        int navInset = AndroidUtilities.getViewInset(decor);
        android.widget.FrameLayout.LayoutParams lp = LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM,
                0, 0, 0, navInset);
        decor.addView(v, lp);
        v.refresh();
        floating = v;
    }

    public static void hideFloating() {
        if (floating != null) {
            AndroidUtilities.removeFromParent(floating);
            floating = null;
        }
    }
}
