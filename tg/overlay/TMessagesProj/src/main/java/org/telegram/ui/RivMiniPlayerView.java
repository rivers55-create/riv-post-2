package org.telegram.ui;

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
    private final ImageView playPauseIcon;
    private final View progressLine;
    private Runnable onOpenPlayer;

    public RivMiniPlayerView(Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        art = new BackupImageView(context);
        art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(8));
        addView(art, LayoutHelper.createFrame(50, 50, Gravity.CENTER_VERTICAL | Gravity.LEFT, 8, 0, 0, 0));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 66, 0, 120, 0));

        title = new TextView(context);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(14);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);

        artist = new TextView(context);
        artist.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        artist.setTextSize(12);
        artist.setMaxLines(1);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(artist);

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        addView(right, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 6, 0));

        RivIcon outputIcon = new RivIcon(context);
        outputIcon.setKind(RivIcon.SPEAKER);
        outputIcon.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        right.addView(outputIcon, LayoutHelper.createLinear(40, 40));

        favIcon = new RivIcon(context);
        favIcon.setKind(RivIcon.HEART_OUTLINE);
        right.addView(favIcon, LayoutHelper.createLinear(40, 40));
        favIcon.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m != null) {
                RivMeta.toggleFavoriteSong(RivMusicActivity.keyOf(m));
                updateFav(m);
            }
        });

        playPauseIcon = new ImageView(context);
        playPauseIcon.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        playPauseIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        right.addView(playPauseIcon, LayoutHelper.createLinear(40, 40));
        playPauseIcon.setOnClickListener(v -> {
            MessageObject m = MediaController.getInstance().getPlayingMessageObject();
            if (m == null) return;
            if (MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().playMessage(m);
            } else {
                MediaController.getInstance().pauseMessage(m);
            }
            updatePlayPause();
        });

        progressLine = new View(context);
        progressLine.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        addView(progressLine, LayoutHelper.createFrame(0, 2, Gravity.BOTTOM | Gravity.LEFT));

        setOnClickListener(v -> {
            if (onOpenPlayer != null) onOpenPlayer.run();
        });
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
            if (getWidth() > 0) {
                android.view.ViewGroup.LayoutParams lp = progressLine.getLayoutParams();
                lp.width = (int) (getWidth() * frac);
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
        playPauseIcon.setImageResource(playing ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
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
        android.widget.FrameLayout.LayoutParams lp = LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(64), Gravity.BOTTOM,
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
