package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.RivMeta;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Riv Post: full details for one video. Backdrop, title/year/rating/genres/runtime,
 * Play + My List buttons, overview, "This episode" block, cast row.
 * Metadata comes from RivMeta (TMDb/TheTVDB), looked up asynchronously.
 */
public class RivVideoDetailsActivity extends RivFragment {

    private final MessageObject video;
    private final List<MessageObject> playlist;
    private RivMeta.OnlineInfo info;

    private TextView titleView;
    private TextView metaLine;
    private TextView genresLine;
    private TextView overviewView;
    private TextView myListBtn;
    private LinearLayout episodeBlock;
    private LinearLayout castSection;
    private BackupImageView backdrop;
    private TextView sourceLine;

    public RivVideoDetailsActivity(MessageObject video, List<MessageObject> playlist) {
        this.video = video;
        this.playlist = playlist != null ? playlist : new ArrayList<>();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle("");
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        ScrollView scroll = new ScrollView(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // backdrop
        FrameLayout backdropWrap = new FrameLayout(context);
        column.addView(backdropWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 210));
        backdrop = new BackupImageView(context);
        backdropWrap.addView(backdrop, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, Theme.getColor(Theme.key_windowBackgroundWhite) & 0x00FFFFFF | 0xE6000000}));
        backdropWrap.addView(shade, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TLRPC.Document doc = video.getDocument();
        TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 1000) : null;
        if (thumb != null) {
            backdrop.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), null, new ColorDrawable(0xFF2A2236), video);
        } else {
            backdrop.setImageDrawable(new ColorDrawable(0xFF2A2236));
        }

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(4), AndroidUtilities.dp(18), AndroidUtilities.dp(24));
        column.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(23);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(RivMeta.title(toSource(video)));
        body.addView(titleView);

        metaLine = new TextView(context);
        metaLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        metaLine.setTextSize(14);
        metaLine.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        body.addView(metaLine);

        genresLine = new TextView(context);
        genresLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        genresLine.setTextSize(14);
        body.addView(genresLine);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, AndroidUtilities.dp(14), 0, 0);
        body.addView(buttons);

        TextView playBtn = new TextView(context);
        playBtn.setText(LocaleController.getString(R.string.RivPlay));
        playBtn.setTextColor(Color.WHITE);
        playBtn.setTypeface(AndroidUtilities.bold());
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        playBg.setCornerRadius(AndroidUtilities.dp(20));
        playBtn.setBackground(playBg);
        playBtn.setOnClickListener(v -> play());
        buttons.addView(playBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 10, 0));

        myListBtn = new TextView(context);
        myListBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        myListBtn.setGravity(Gravity.CENTER);
        myListBtn.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        GradientDrawable listBg = new GradientDrawable();
        listBg.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        listBg.setCornerRadius(AndroidUtilities.dp(20));
        myListBtn.setBackground(listBg);
        updateMyListButton();
        myListBtn.setOnClickListener(v -> {
            RivMeta.toggleMyList(RivVideosActivity.keyOf(video));
            updateMyListButton();
        });
        buttons.addView(myListBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));

        overviewView = new TextView(context);
        overviewView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        overviewView.setTextSize(15);
        overviewView.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        overviewView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        body.addView(overviewView);

        episodeBlock = new LinearLayout(context);
        episodeBlock.setOrientation(LinearLayout.VERTICAL);
        episodeBlock.setVisibility(View.GONE);
        body.addView(episodeBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        castSection = new LinearLayout(context);
        castSection.setOrientation(LinearLayout.VERTICAL);
        castSection.setVisibility(View.GONE);
        column.addView(castSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        sourceLine = new TextView(context);
        sourceLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sourceLine.setTextSize(12);
        sourceLine.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
        sourceLine.setVisibility(View.GONE);
        column.addView(sourceLine);

        // tidy placeholder meta line (chat name) until/unless online info arrives
        metaLine.setText(chatTitle());

        boolean arabic = LocaleController.getInstance().isRTL;
        RivMeta.lookup(context, toSource(video), arabic, result -> {
            info = result;
            bindInfo();
        });

        return fragmentView;
    }

    private String chatTitle() {
        long dialogId = video.getDialogId();
        if (dialogId > 0) {
            TLRPC.User u = getMessagesController().getUser(dialogId);
            if (u != null) return org.telegram.messenger.UserObject.getUserName(u);
        } else {
            TLRPC.Chat c = getMessagesController().getChat(-dialogId);
            if (c != null) return c.title;
        }
        return "";
    }

    private void updateMyListButton() {
        boolean inList = RivMeta.inMyList(RivVideosActivity.keyOf(video));
        myListBtn.setText((inList ? "✓ " : "+ ") + LocaleController.getString(inList ? R.string.RivMyList : R.string.RivMyListAdd));
    }

    private void play() {
        RivVideoPlayerActivity.open(this, video, playlist);
    }

    private void bindInfo() {
        Context context = fragmentView.getContext();
        if (info == null) {
            overviewView.setText(LocaleController.getString(R.string.RivNoOnlineInfo));
            return;
        }
        if (!TextUtils.isEmpty(info.title)) titleView.setText(info.title);
        if (!info.backdrop.isEmpty() || !info.poster.isEmpty()) {
            String url = !info.backdrop.isEmpty() ? info.backdrop : info.poster;
            backdrop.setImage(org.telegram.messenger.ImageLocation.getForPath(url), null, new ColorDrawable(0xFF2A2236), null);
        }
        List<String> metaParts = new ArrayList<>();
        if (!info.year.isEmpty()) metaParts.add(info.year);
        if (info.rating > 0) metaParts.add(String.format(Locale.US, "★ %.1f", info.rating));
        if (info.runtime > 0) metaParts.add(info.runtime + " " + LocaleController.getString(R.string.RivMinutesShort));
        metaLine.setText(TextUtils.join("  ·  ", metaParts));
        genresLine.setText(TextUtils.join(", ", info.genres));
        genresLine.setVisibility(info.genres.isEmpty() ? View.GONE : View.VISIBLE);

        if (!info.overview.isEmpty()) {
            overviewView.setText(info.overview);
        } else {
            overviewView.setText(RivMeta.description(toSource(video)));
        }

        if (!info.epTitle.isEmpty() || info.season > 0) {
            episodeBlock.removeAllViews();
            episodeBlock.setVisibility(View.VISIBLE);

            TextView header = new TextView(context);
            header.setText(LocaleController.getString(R.string.RivThisEpisode));
            header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            header.setTextSize(17);
            header.setTypeface(AndroidUtilities.bold());
            header.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            episodeBlock.addView(header);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            episodeBlock.addView(row);

            String pic = !info.epStill.isEmpty() ? info.epStill : info.seasonPoster;
            if (!pic.isEmpty()) {
                BackupImageView still = new BackupImageView(context);
                still.getImageReceiver().setRoundRadius(AndroidUtilities.dp(10));
                still.setImage(org.telegram.messenger.ImageLocation.getForPath(pic), null, new ColorDrawable(0xFF2A2236), null);
                row.addView(still, LayoutHelper.createLinear(130, 74, 0, 0, 0, 12, 0));
            }

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

            TextView seasonEp = new TextView(context);
            String se = LocaleController.getString(R.string.RivSeason) + " " + info.season;
            if (info.episode > 0) se += " · " + LocaleController.getString(R.string.RivEpisode) + " " + info.episode;
            seasonEp.setText(se);
            seasonEp.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            seasonEp.setTextSize(13);
            col.addView(seasonEp);

            if (!info.epTitle.isEmpty()) {
                TextView epTitle = new TextView(context);
                epTitle.setText(info.epTitle);
                epTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                epTitle.setTypeface(AndroidUtilities.bold());
                epTitle.setTextSize(15);
                col.addView(epTitle);
            }
            if (!info.epOverview.isEmpty()) {
                TextView epOverview = new TextView(context);
                epOverview.setText(info.epOverview);
                epOverview.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                epOverview.setTextSize(13);
                epOverview.setMaxLines(6);
                epOverview.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(epOverview);
            }
        }

        if (!info.cast.isEmpty()) {
            castSection.removeAllViews();
            castSection.setVisibility(View.VISIBLE);

            TextView header = new TextView(context);
            header.setText(LocaleController.getString(R.string.RivCast));
            header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            header.setTextSize(17);
            header.setTypeface(AndroidUtilities.bold());
            header.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), AndroidUtilities.dp(8));
            castSection.addView(header);

            RecyclerListView castRow = new RecyclerListView(context);
            castRow.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            castRow.setAdapter(new CastAdapter(context, info.cast));
            castSection.addView(castRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 118));
        }

        sourceLine.setVisibility(View.VISIBLE);
        sourceLine.setText(LocaleController.getString(R.string.RivInfoFrom) + " " + info.source);
    }

    private static class CastAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;
        final List<RivMeta.CastMember> cast;

        CastAdapter(Context context, List<RivMeta.CastMember> cast) {
            this.context = context;
            this.cast = cast;
        }

        @Override
        public int getItemCount() {
            return cast.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);

            BackupImageView photo = new BackupImageView(context);
            photo.getImageReceiver().setRoundRadius(AndroidUtilities.dp(38));
            col.addView(photo, LayoutHelper.createLinear(76, 76));

            TextView name = new TextView(context);
            name.setTextSize(12);
            name.setMaxLines(2);
            name.setGravity(Gravity.CENTER);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            col.addView(name, LayoutHelper.createLinear(86, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            TextView role = new TextView(context);
            role.setTextSize(11);
            role.setMaxLines(1);
            role.setGravity(Gravity.CENTER);
            role.setEllipsize(TextUtils.TruncateAt.END);
            role.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            col.addView(role, LayoutHelper.createLinear(86, LayoutHelper.WRAP_CONTENT));

            col.setLayoutParams(new RecyclerView.LayoutParams(86, ViewGroup.LayoutParams.MATCH_PARENT));
            col.setTag(new Object[]{photo, name, role});
            return new RecyclerListView.Holder(col);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RivMeta.CastMember m = cast.get(position);
            Object[] tag = (Object[]) holder.itemView.getTag();
            BackupImageView photo = (BackupImageView) tag[0];
            TextView name = (TextView) tag[1];
            TextView role = (TextView) tag[2];
            if (!m.photo.isEmpty()) {
                photo.setImage(org.telegram.messenger.ImageLocation.getForPath(m.photo), null, new ColorDrawable(0xFF3A3246), null);
            } else {
                photo.setImageDrawable(new ColorDrawable(0xFF3A3246));
            }
            name.setText(m.name);
            role.setText(m.role);
        }
    }

    private static RivMeta.Source toSource(MessageObject m) {
        return new RivMeta.Source() {
            @Override
            public String fileName() {
                String f = m.getFileName();
                return f == null ? "" : f;
            }

            @Override
            public String caption() {
                return m.messageOwner != null ? m.messageOwner.message : "";
            }

            @Override
            public String rawTitle() {
                String f = fileName();
                return f.isEmpty() ? LocaleController.getString(R.string.RivVideos) : f;
            }
        };
    }
}
