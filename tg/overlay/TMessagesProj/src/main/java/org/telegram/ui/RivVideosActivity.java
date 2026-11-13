package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.RivMeta;
import org.telegram.messenger.RivPrefs;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Riv Post: a Netflix-style video library built from chats and channels the user picks.
 * Hero banner + "Continue watching" + "My List" + one shelf per source chat.
 * Actual playback stays on Telegram's own player (PhotoViewer); this screen is only
 * about browsing and the details screen.
 */
public class RivVideosActivity extends BaseFragment {

    private static final String SOURCES_KEY = "videoSources";

    private RecyclerListView listView;
    private RowAdapter adapter;

    private final ArrayList<Long> sources = new ArrayList<>();
    private final ArrayList<MessageObject> allVideos = new ArrayList<>();
    private final HashSet<String> videoKeys = new HashSet<>();
    private int loading = 0;

    // rows: 0 = hero, then shelves (Continue watching, My List, one per chat)
    private final List<Shelf> shelves = new ArrayList<>();

    private static class Shelf {
        String title;
        List<MessageObject> items;

        Shelf(String title, List<MessageObject> items) {
            this.title = title;
            this.items = items;
        }
    }

    static String keyOf(MessageObject m) {
        return org.telegram.messenger.UserConfig.selectedAccount + "_" + m.getDialogId() + "_" + m.getId();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        loadSources();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivVideos));
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

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new RowAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        rebuildShelves();
        reloadVideos();
        return fragmentView;
    }

    private void loadSources() {
        sources.clear();
        String s = RivPrefs.s(SOURCES_KEY, "");
        if (s != null && !s.isEmpty()) {
            for (String part : s.split(",")) {
                try {
                    sources.add(Long.parseLong(part));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void saveSources() {
        StringBuilder sb = new StringBuilder();
        for (Long id : sources) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        RivPrefs.set(SOURCES_KEY, sb.toString());
    }

    private void pickSource() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            for (MessagesStorage.TopicKey key : dids) {
                if (!sources.contains(key.dialogId)) {
                    sources.add(key.dialogId);
                }
            }
            saveSources();
            fragment1.finishFragment();
            reloadVideos();
            return true;
        });
        presentFragment(fragment);
    }

    private void askRemoveSource(long dialogId) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(titleOf(dialogId));
        builder.setMessage(LocaleController.getString(R.string.RivVideosRemoveSource));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            sources.remove(dialogId);
            saveSources();
            reloadVideos();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String titleOf(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) return org.telegram.messenger.UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null) return chat.title;
        }
        return String.valueOf(dialogId);
    }

    private void reloadVideos() {
        allVideos.clear();
        videoKeys.clear();
        loading = sources.size();
        rebuildShelves();
        if (loading == 0) return;
        for (Long dialogId : sources) {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = getMessagesController().getInputPeer(dialogId);
            req.q = "";
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
            req.limit = 100;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading--;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    getMessagesController().putUsers(res.users, false);
                    getMessagesController().putChats(res.chats, false);
                    for (TLRPC.Message msg : res.messages) {
                        String key = MessageObject.getDialogId(msg) + "_" + msg.id;
                        if (videoKeys.contains(key)) continue;
                        MessageObject obj = new MessageObject(currentAccount, msg, false, true);
                        if (obj.isVideo()) {
                            videoKeys.add(key);
                            allVideos.add(obj);
                        }
                    }
                    Collections.sort(allVideos, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));
                }
                rebuildShelves();
            }));
        }
    }

    private void rebuildShelves() {
        shelves.clear();

        List<MessageObject> continueWatching = new ArrayList<>();
        for (MessageObject m : allVideos) {
            if (RivMeta.position(ApplicationLoaderContext(), keyOf(m)) > 5000) continueWatching.add(m);
        }
        if (!continueWatching.isEmpty()) {
            shelves.add(new Shelf(LocaleController.getString(R.string.RivContinueWatching), continueWatching));
        }

        List<MessageObject> myList = new ArrayList<>();
        for (MessageObject m : allVideos) {
            if (RivMeta.inMyList(keyOf(m))) myList.add(m);
        }
        if (!myList.isEmpty()) {
            shelves.add(new Shelf(LocaleController.getString(R.string.RivMyList), myList));
        }

        Map<Long, List<MessageObject>> byChat = new LinkedHashMap<>();
        for (Long id : sources) byChat.put(id, new ArrayList<>());
        for (MessageObject m : allVideos) {
            long d = m.getDialogId();
            List<MessageObject> l = byChat.get(d);
            if (l != null) l.add(m);
        }
        for (Map.Entry<Long, List<MessageObject>> e : byChat.entrySet()) {
            if (!e.getValue().isEmpty()) {
                shelves.add(new Shelf(titleOf(e.getKey()), e.getValue()));
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private Context ApplicationLoaderContext() {
        return org.telegram.messenger.ApplicationLoader.applicationContext;
    }

    private void openDetails(MessageObject m) {
        presentFragment(new RivVideoDetailsActivity(m, new ArrayList<>(shelves.isEmpty() ? allVideos : shelves.get(0).items)));
    }

    private void play(MessageObject m, List<MessageObject> playlist) {
        PhotoViewer.getInstance().setParentActivity(this);
        int index = playlist.indexOf(m);
        if (index < 0) index = 0;
        PhotoViewer.getInstance().openPhoto(new ArrayList<>(playlist), index, m.getDialogId(), 0, 0, new PhotoViewer.EmptyPhotoViewerProvider());
    }

    // ---------------------------------------------------------------- rows

    private static final int TYPE_HERO = 0;
    private static final int TYPE_SHELF = 1;
    private static final int TYPE_EMPTY = 2;

    private class RowAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;

        RowAdapter(Context context) {
            this.context = context;
        }

        private MessageObject heroItem() {
            if (!shelves.isEmpty() && !shelves.get(0).items.isEmpty()) return shelves.get(0).items.get(0);
            if (!allVideos.isEmpty()) return allVideos.get(0);
            return null;
        }

        @Override
        public int getItemCount() {
            if (allVideos.isEmpty() && sources.isEmpty()) return 1;
            int n = heroItem() != null ? 1 : 0;
            return n + shelves.size() + (allVideos.isEmpty() ? 1 : 0);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemViewType(int position) {
            if (allVideos.isEmpty() && sources.isEmpty()) return TYPE_EMPTY;
            int offset = heroItem() != null ? 1 : 0;
            if (offset == 1 && position == 0) return TYPE_HERO;
            if (allVideos.isEmpty()) return TYPE_EMPTY;
            return TYPE_SHELF;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HERO:
                    view = new HeroView(context);
                    break;
                case TYPE_EMPTY:
                    view = emptyView(context);
                    break;
                default:
                    view = new ShelfView(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = holder.getItemViewType();
            int offset = heroItem() != null ? 1 : 0;
            if (type == TYPE_HERO) {
                ((HeroView) holder.itemView).bind(heroItem());
            } else if (type == TYPE_SHELF) {
                int shelfIndex = position - offset;
                if (shelfIndex >= 0 && shelfIndex < shelves.size()) {
                    ((ShelfView) holder.itemView).bind(shelves.get(shelfIndex));
                }
            }
        }
    }

    private View emptyView(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(80), AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        TextView title = new TextView(context);
        title.setText(sources.isEmpty() ? LocaleController.getString(R.string.RivVideosEmptyTitle) : (loading > 0 ? LocaleController.getString(R.string.RivVideosLoading) : LocaleController.getString(R.string.RivVideosEmpty)));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(LocaleController.getString(R.string.RivVideosEmptyHint));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16));
        box.addView(subtitle);

        TextView button = new TextView(context);
        button.setText(LocaleController.getString(R.string.RivVideosAddSource));
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        button.setTextSize(16);
        button.setOnClickListener(v -> pickSource());
        box.addView(button);

        return box;
    }

    // ---------------------------------------------------------------- hero

    private class HeroView extends FrameLayout {
        BackupImageView art;
        TextView titleView;
        TextView subtitleView;
        TextView playBtn;
        TextView listBtn;
        MessageObject current;

        HeroView(Context context) {
            super(context);
            int h = AndroidUtilities.dp(230);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

            art = new BackupImageView(context);
            art.setAspectFit(false);
            addView(art, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            View shade = new View(context);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xE6000000}));
            addView(shade, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            addView(info, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 16, 0, 16, 14));

            titleView = new TextView(context);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(21);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(titleView);

            subtitleView = new TextView(context);
            subtitleView.setTextColor(0xCCFFFFFF);
            subtitleView.setTextSize(13);
            subtitleView.setMaxLines(1);
            subtitleView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(10));
            info.addView(subtitleView);

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            info.addView(buttons);

            playBtn = pillButton(context, LocaleController.getString(R.string.RivPlay), Color.WHITE, Color.BLACK);
            buttons.addView(playBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 10, 0));

            listBtn = pillButton(context, LocaleController.getString(R.string.RivMyListAdd), 0x33FFFFFF, Color.WHITE);
            buttons.addView(listBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));

            TextView infoBtn = new TextView(context);
            infoBtn.setText("ⓘ");
            infoBtn.setTextColor(Color.WHITE);
            infoBtn.setTextSize(18);
            infoBtn.setGravity(Gravity.CENTER);
            infoBtn.setBackground(roundBg(0x33FFFFFF, 20));
            buttons.addView(infoBtn, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
            infoBtn.setOnClickListener(v -> {
                if (current != null) openDetails(current);
            });

            TextView sourcesBtn = new TextView(context);
            sourcesBtn.setText("⚙");
            sourcesBtn.setTextColor(Color.WHITE);
            sourcesBtn.setTextSize(16);
            sourcesBtn.setGravity(Gravity.CENTER);
            sourcesBtn.setBackground(roundBg(0x66000000, 16));
            addView(sourcesBtn, LayoutHelper.createFrame(34, 34, Gravity.TOP | Gravity.RIGHT, 0, 12, 12, 0));
            sourcesBtn.setOnClickListener(v -> pickSource());
        }

        void bind(MessageObject m) {
            current = m;
            if (m == null) return;
            TLRPC.Document doc = m.getDocument();
            TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 1000) : null;
            if (thumb != null) {
                art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), null, new ColorDrawable(0xFF2A2236), m);
            } else {
                art.setImageDrawable(new ColorDrawable(0xFF2A2236));
            }
            titleView.setText(RivMeta.title(toSource(m)));
            String chat = titleOf(m.getDialogId());
            subtitleView.setText(chat);
            playBtn.setOnClickListener(v -> play(m, shelves.isEmpty() ? new ArrayList<>(allVideos) : new ArrayList<>(shelves.get(0).items)));
            boolean inList = RivMeta.inMyList(keyOf(m));
            listBtn.setText((inList ? "✓ " : "+ ") + LocaleController.getString(inList ? R.string.RivMyList : R.string.RivMyListAdd));
            listBtn.setOnClickListener(v -> {
                RivMeta.toggleMyList(keyOf(m));
                rebuildShelves();
            });
        }
    }

    private TextView pillButton(Context context, String text, int bg, int fg) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(fg);
        tv.setTextSize(14);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        tv.setBackground(roundBg(bg, 20));
        return tv;
    }

    private Drawable roundBg(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        return d;
    }

    // ---------------------------------------------------------------- shelf (horizontal row)

    private class ShelfView extends LinearLayout {
        TextView title;
        RecyclerListView row;
        CardAdapter cardAdapter;

        ShelfView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(0, AndroidUtilities.dp(10), 0, 0);

            title = new TextView(context);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(16);
            title.setTypeface(AndroidUtilities.bold());
            title.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            row = new RecyclerListView(context);
            row.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            row.setAdapter(cardAdapter = new CardAdapter(context));
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 190));
        }

        void bind(Shelf shelf) {
            title.setText(shelf.title);
            cardAdapter.items = shelf.items;
            cardAdapter.notifyDataSetChanged();
        }
    }

    private class CardAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;
        List<MessageObject> items = new ArrayList<>();

        CardAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PosterCard card = new PosterCard(context);
            card.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(150), ViewGroup.LayoutParams.MATCH_PARENT));
            return new RecyclerListView.Holder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MessageObject m = items.get(position);
            PosterCard card = (PosterCard) holder.itemView;
            card.bind(m);
            card.setOnClickListener(v -> play(m, items));
            card.setOnLongClickListener(v -> {
                openDetails(m);
                return true;
            });
        }
    }

    private class PosterCard extends LinearLayout {
        FrameLayout artWrap;
        BackupImageView art;
        TextView badge;
        View progressBar;
        TextView title;
        MessageObject current;

        PosterCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);

            artWrap = new FrameLayout(context);
            addView(artWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 94));

            art = new BackupImageView(context);
            art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(8));
            artWrap.addView(art, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            badge = new TextView(context);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(10);
            badge.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
            badge.setBackground(roundBg(0x99000000, 4));
            artWrap.addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 5, 5));

            progressBar = new View(context);
            progressBar.setBackgroundColor(0xFFE53935);
            artWrap.addView(progressBar, LayoutHelper.createFrame(0, 3, Gravity.BOTTOM | Gravity.LEFT));

            title = new TextView(context);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(12);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        void bind(MessageObject m) {
            current = m;
            TLRPC.Document doc = m.getDocument();
            TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 320) : null;
            if (thumb != null) {
                art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), "94_150", new ColorDrawable(0xFF2A2236), m);
            } else {
                art.setImageDrawable(new ColorDrawable(0xFF2A2236));
            }
            int dur = (int) m.getDuration();
            if (dur > 0) {
                badge.setVisibility(VISIBLE);
                badge.setText(AndroidUtilities.formatShortDuration(dur));
            } else {
                badge.setVisibility(GONE);
            }
            float p = RivMeta.progress(ApplicationLoaderContext(), keyOf(m));
            ViewGroup.LayoutParams lp = progressBar.getLayoutParams();
            lp.width = p > 0.01f ? Math.max(1, (int) (AndroidUtilities.dp(140) * p)) : 0;
            progressBar.setLayoutParams(lp);
            title.setText(RivMeta.title(toSource(m)));
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
