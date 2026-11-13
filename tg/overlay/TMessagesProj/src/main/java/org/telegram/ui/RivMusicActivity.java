package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RivMeta;
import org.telegram.messenger.RivMusicStats;
import org.telegram.messenger.RivPlaylists;
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
import java.util.Locale;

/**
 * Riv Post: a Spotify-like music library (search bar + pill tabs + song rows), built from
 * chats and channels the user picks. Playback stays on MediaController, Telegram's own
 * working audio engine; this screen is only about browsing, playlists and the mini-player.
 */
public class RivMusicActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final String SOURCES_KEY = "musicSources";
    private static final String[] TAB_KEYS = {"all", "playlists", "albums", "artists", "favorites", "selected"};

    private final ArrayList<Long> sources = new ArrayList<>();
    private final ArrayList<Long> selectedChats = new ArrayList<>();
    private final ArrayList<MessageObject> allSongs = new ArrayList<>();
    private final HashSet<String> songKeys = new HashSet<>();
    private int loading = 0;
    private int currentTab = 0;
    private String query = "";

    private FrameLayout root;
    private LinearLayout tabsRow;
    private FrameLayout listContainer;
    private RivMiniPlayerView miniPlayer;
    private EditText searchField;

    public static String keyOf(MessageObject m) {
        return org.telegram.messenger.UserConfig.selectedAccount + "_" + m.getDialogId() + "_" + m.getId();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        loadSources();
        loadSelectedChats();
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
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (miniPlayer != null) miniPlayer.refresh();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivMusic));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        // Riv Post: AndroidUtilities.navigationBarHeight is never actually set anywhere in real
        // Telegram, so it's always 0 -- getViewInset() reads the real, live nav-bar height off
        // the window instead, so the list and bottom bar don't end up under the phone's own nav bar.
        int bottomBarSpace = AndroidUtilities.dp(RivBottomBar.HEIGHT_DP) + AndroidUtilities.getViewInset(root);
        root.addView(column, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, bottomBarSpace));

        // search bar
        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchBg.setCornerRadius(AndroidUtilities.dp(22));
        searchBar.setBackground(searchBg);
        searchField = new EditText(context);
        searchField.setHint(LocaleController.getString(R.string.RivSearchSongs));
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setBackground(null);
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString();
                refreshList();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        searchBar.addView(searchField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 18, 0, 10, 0));
        column.addView(searchBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 12, 8, 12, 4));

        // pill tabs
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        scroller.addView(tabsRow, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0));
        column.addView(scroller, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        buildTabs(context);

        listContainer = new FrameLayout(context);
        column.addView(listContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        RivBottomBar.Docked docked = RivBottomBar.attach(this, root, RivBottomBar.TAB_MUSIC, AndroidUtilities.navigationBarHeight);
        miniPlayer = docked.miniPlayer;

        refreshList();
        reloadSongs();
        miniPlayer.refresh();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (miniPlayer != null) miniPlayer.refresh();
    }

    private void buildTabs(Context context) {
        tabsRow.removeAllViews();
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int idx = i;
            TextView pill = new TextView(context);
            pill.setText(tabLabel(TAB_KEYS[i]));
            pill.setTextSize(14);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            boolean sel = currentTab == idx;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhite));
            bg.setCornerRadius(AndroidUtilities.dp(18));
            pill.setBackground(bg);
            pill.setTextColor(sel ? Theme.getColor(Theme.key_windowBackgroundWhite) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            pill.setOnClickListener(v -> {
                currentTab = idx;
                buildTabs(context);
                refreshList();
            });
            tabsRow.addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 0, 8, 0));
        }
    }

    private String tabLabel(String k) {
        switch (k) {
            case "playlists":
                return LocaleController.getString(R.string.RivTabPlaylists);
            case "albums":
                return LocaleController.getString(R.string.RivTabAlbums);
            case "artists":
                return LocaleController.getString(R.string.RivTabArtists);
            case "favorites":
                return LocaleController.getString(R.string.RivTabFavorites);
            case "selected":
                return LocaleController.getString(R.string.RivTabSelectedChats);
            default:
                return LocaleController.getString(R.string.RivTabAll);
        }
    }

    // ---------------------------------------------------------------- data

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

    private void loadSelectedChats() {
        selectedChats.clear();
        String s = RivPrefs.s(RivPrefs.MUSIC_SELECTED_CHATS, "");
        if (s != null && !s.isEmpty()) {
            for (String part : s.split(",")) {
                try {
                    selectedChats.add(Long.parseLong(part));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void saveSelectedChats() {
        StringBuilder sb = new StringBuilder();
        for (Long id : selectedChats) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        RivPrefs.set(RivPrefs.MUSIC_SELECTED_CHATS, sb.toString());
    }

    /** "Music sources": the full picker, checking a chat adds it to sources (and to selected chats). */
    private void pickSource() {
        RivChatSourcePicker picker = new RivChatSourcePicker(
                LocaleController.getString(R.string.RivMusicSources),
                LocaleController.getString(R.string.RivMusicSourcesDesc),
                sources);
        picker.setDelegate(new RivChatSourcePicker.Delegate() {
            @Override
            public void onSourcesChanged(ArrayList<Long> newSources) {
                sources.clear();
                sources.addAll(newSources);
                saveSources();
                reloadSongs();
            }

            @Override
            public void onPreview(ArrayList<Long> previewSources) {
                previewFirstSong(previewSources);
            }
        });
        presentFragment(picker);
    }

    /** "Selected chats" tab: pick a subset of the already-added sources to narrow the library to. */
    private void pickSelectedChats() {
        RivChatSourcePicker picker = new RivChatSourcePicker(
                LocaleController.getString(R.string.RivTabSelectedChats),
                LocaleController.getString(R.string.RivMusicSourcesDesc),
                selectedChats);
        picker.setDelegate(new RivChatSourcePicker.Delegate() {
            @Override
            public void onSourcesChanged(ArrayList<Long> newSelected) {
                selectedChats.clear();
                selectedChats.addAll(newSelected);
                saveSelectedChats();
                refreshList();
            }

            @Override
            public void onPreview(ArrayList<Long> previewSources) {
                previewFirstSong(previewSources);
            }
        });
        presentFragment(picker);
    }

    private void previewFirstSong(List<Long> fromChats) {
        for (MessageObject m : allSongs) {
            if (fromChats.isEmpty() || fromChats.contains(m.getDialogId())) {
                playFrom(new ArrayList<>(allSongs), m);
                return;
            }
        }
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

    private void reloadSongs() {
        allSongs.clear();
        songKeys.clear();
        loading = sources.size();
        refreshList();
        if (loading == 0) return;
        for (Long dialogId : sources) {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = getMessagesController().getInputPeer(dialogId);
            req.q = "";
            req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            req.limit = 100;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading--;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    getMessagesController().putUsers(res.users, false);
                    getMessagesController().putChats(res.chats, false);
                    for (TLRPC.Message msg : res.messages) {
                        String key = MessageObject.getDialogId(msg) + "_" + msg.id;
                        if (songKeys.contains(key)) continue;
                        MessageObject obj = new MessageObject(currentAccount, msg, false, true);
                        if (obj.isMusic()) {
                            songKeys.add(key);
                            allSongs.add(obj);
                        }
                    }
                    Collections.sort(allSongs, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));
                }
                refreshList();
            }));
        }
    }

    private List<MessageObject> visibleSongs(List<MessageObject> src) {
        List<MessageObject> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.getDefault());
        for (MessageObject m : src) {
            if (RivMeta.isHiddenSong(keyOf(m))) continue;
            if (!q.isEmpty()) {
                String title = m.getMusicTitle();
                String artist = m.getMusicAuthor();
                boolean match = (title != null && title.toLowerCase(Locale.getDefault()).contains(q))
                        || (artist != null && artist.toLowerCase(Locale.getDefault()).contains(q));
                if (!match) continue;
            }
            out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- list building

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        Context context = listContainer.getContext();

        if (allSongs.isEmpty() && (sources.isEmpty() || loading > 0)) {
            listContainer.addView(emptyView(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return;
        }

        String tab = TAB_KEYS[currentTab];
        switch (tab) {
            case "playlists":
                listContainer.addView(playlistsView(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            case "albums":
                listContainer.addView(groupListView(context, groupByChat(), true), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            case "artists":
                listContainer.addView(groupListView(context, groupByArtist(), false), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            case "favorites": {
                List<MessageObject> favs = new ArrayList<>();
                for (MessageObject m : visibleSongs(allSongs)) if (RivMeta.isFavoriteSong(keyOf(m))) favs.add(m);
                listContainer.addView(songListView(context, favs, null), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            }
            case "selected": {
                listContainer.addView(selectedChatsView(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
            }
            default:
                listContainer.addView(songListView(context, visibleSongs(allSongs), null), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
        }
    }

    private LinkedHashMap<String, List<MessageObject>> groupByChat() {
        LinkedHashMap<Long, List<MessageObject>> byId = new LinkedHashMap<>();
        for (MessageObject m : visibleSongs(allSongs)) {
            long id = m.getDialogId();
            List<MessageObject> l = byId.get(id);
            if (l == null) {
                l = new ArrayList<>();
                byId.put(id, l);
            }
            l.add(m);
        }
        LinkedHashMap<String, List<MessageObject>> out = new LinkedHashMap<>();
        for (java.util.Map.Entry<Long, List<MessageObject>> e : byId.entrySet()) {
            out.put(titleOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private LinkedHashMap<String, List<MessageObject>> groupByArtist() {
        LinkedHashMap<String, List<MessageObject>> out = new LinkedHashMap<>();
        for (MessageObject m : visibleSongs(allSongs)) {
            String artist = m.getMusicAuthor();
            if (artist == null || artist.isEmpty()) artist = LocaleController.getString(R.string.RivUnknownArtist);
            List<MessageObject> l = out.get(artist);
            if (l == null) {
                l = new ArrayList<>();
                out.put(artist, l);
            }
            l.add(m);
        }
        return out;
    }

    private View emptyView(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(60), AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(17);
        title.setText(sources.isEmpty() ? LocaleController.getString(R.string.RivMusicEmptyTitle) : (loading > 0 ? LocaleController.getString(R.string.RivMusicLoading) : LocaleController.getString(R.string.RivMusicEmpty)));
        box.addView(title);
        TextView button = new TextView(context);
        button.setText(LocaleController.getString(R.string.RivMusicAddSource));
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        button.setTextSize(16);
        button.setPadding(0, AndroidUtilities.dp(14), 0, 0);
        button.setOnClickListener(v -> pickSource());
        box.addView(button);
        return box;
    }

    private View selectedChatsView(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        org.telegram.ui.Cells.TextSettingsCell editCell = new org.telegram.ui.Cells.TextSettingsCell(context);
        editCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        editCell.setText(LocaleController.getString(R.string.RivTabSelectedChats), true);
        editCell.setOnClickListener(v -> pickSelectedChats());
        column.addView(editCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        List<MessageObject> filtered = new ArrayList<>();
        for (MessageObject m : visibleSongs(allSongs)) {
            if (selectedChats.contains(m.getDialogId())) filtered.add(m);
        }
        column.addView(songListView(context, filtered, null), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        return column;
    }

    private View playlistsView(Context context) {
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        LinkedHashMap<String, ArrayList<String>> playlists = RivPlaylists.load();
        List<String> names = new ArrayList<>(playlists.keySet());
        list.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public int getItemCount() {
                return names.size() + 1;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                org.telegram.ui.Cells.TextSettingsCell cell = new org.telegram.ui.Cells.TextSettingsCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                org.telegram.ui.Cells.TextSettingsCell cell = (org.telegram.ui.Cells.TextSettingsCell) holder.itemView;
                if (position == 0) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText(LocaleController.getString(R.string.RivNewPlaylist), !names.isEmpty());
                    cell.setOnClickListener(v -> askPlaylistName());
                } else {
                    String name = names.get(position - 1);
                    int count = playlists.get(name) != null ? playlists.get(name).size() : 0;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    cell.setTextAndValue(name, String.valueOf(count), position != names.size());
                    cell.setOnClickListener(v -> openPlaylist(name));
                }
            }
        });
        return list;
    }

    private void askPlaylistName() {
        if (getParentActivity() == null) return;
        EditText edit = new EditText(getParentActivity());
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setHint(LocaleController.getString(R.string.RivNewPlaylist));
        FrameLayout wrap = new FrameLayout(getParentActivity());
        wrap.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivNewPlaylist));
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String name = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (!name.isEmpty()) {
                RivPlaylists.create(name);
                refreshList();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void openPlaylist(String name) {
        LinkedHashMap<String, ArrayList<String>> playlists = RivPlaylists.load();
        ArrayList<String> keys = playlists.get(name);
        List<MessageObject> list = new ArrayList<>();
        if (keys != null) {
            for (MessageObject m : allSongs) {
                if (keys.contains(keyOf(m))) list.add(m);
            }
        }
        Context context = listContainer.getContext();
        listContainer.removeAllViews();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));
        TextView back = new TextView(context);
        back.setText(LocaleController.getString(R.string.Back));
        back.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        back.setOnClickListener(v -> refreshList());
        header.addView(back);
        TextView delete = new TextView(context);
        delete.setText(LocaleController.getString(R.string.Delete));
        delete.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        delete.setPadding(AndroidUtilities.dp(14), 0, 0, 0);
        delete.setOnClickListener(v -> {
            RivPlaylists.delete(name);
            refreshList();
        });
        header.addView(delete);
        column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        column.addView(songListView(context, list, name), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        listContainer.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private View groupListView(Context context, LinkedHashMap<String, List<MessageObject>> groups, boolean openAsChat) {
        List<String> names = new ArrayList<>(groups.keySet());
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        list.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public int getItemCount() {
                return names.size();
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new GroupRow(context));
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                String name = names.get(position);
                List<MessageObject> items = groups.get(name);
                ((GroupRow) holder.itemView).bind(name, items, openAsChat);
            }
        });
        return list;
    }

    private class GroupRow extends LinearLayout {
        BackupImageView art;
        TextView title;
        TextView subtitle;

        GroupRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            art = new BackupImageView(context);
            art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(10));
            addView(art, LayoutHelper.createLinear(54, 54));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));

            title = new TextView(context);
            title.setTextSize(15);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            col.addView(title);

            subtitle = new TextView(context);
            subtitle.setTextSize(13);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            col.addView(subtitle);
        }

        void bind(String name, List<MessageObject> items, boolean openAsChat) {
            title.setText(name);
            subtitle.setText(items.size() + " " + LocaleController.getString(R.string.RivSongsCount));
            MessageObject first = items.isEmpty() ? null : items.get(0);
            if (first != null) {
                TLRPC.Document doc = first.getDocument();
                TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 150) : null;
                if (thumb != null) {
                    art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), "54_54", new ColorDrawable(0xFF2A2236), first);
                } else {
                    art.setImageDrawable(new ColorDrawable(0xFF2A2236));
                }
            }
            setOnClickListener(v -> {
                Context context = listContainer.getContext();
                listContainer.removeAllViews();
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                LinearLayout header = new LinearLayout(context);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);
                header.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
                TextView t = new TextView(context);
                t.setText(name);
                t.setTextSize(18);
                t.setTypeface(AndroidUtilities.bold());
                t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                header.addView(t, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));
                TextView back = new TextView(context);
                back.setText(LocaleController.getString(R.string.Back));
                back.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                back.setOnClickListener(v2 -> refreshList());
                header.addView(back);
                column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                column.addView(songListView(context, items, null), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
                listContainer.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            });
        }
    }

    private View songListView(Context context, List<MessageObject> songs, String currentPlaylist) {
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        list.setAdapter(new SongAdapter(context, songs, currentPlaylist));
        return list;
    }

    private void playFrom(List<MessageObject> list, MessageObject m) {
        MediaController.getInstance().setPlaylist(new ArrayList<>(list), m, 0, false, null);
        MediaController.getInstance().playMessage(m);
        RivMusicStats.recordPlay(keyOf(m));
        if (miniPlayer != null) miniPlayer.refresh();
    }

    private class SongAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;
        final List<MessageObject> songs;
        final String currentPlaylist;

        SongAdapter(Context context, List<MessageObject> songs, String currentPlaylist) {
            this.context = context;
            this.songs = songs;
            this.currentPlaylist = currentPlaylist;
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new SongRow(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((SongRow) holder.itemView).bind(songs.get(position), songs, currentPlaylist);
        }
    }

    private class SongRow extends LinearLayout {
        BackupImageView art;
        TextView title;
        TextView subtitle;
        RivIcon favIcon;
        RivIcon moreIcon;

        SongRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(10), AndroidUtilities.dp(7));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            art = new BackupImageView(context);
            art.getImageReceiver().setRoundRadius(AndroidUtilities.dp(10));
            addView(art, LayoutHelper.createLinear(50, 50));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));

            title = new TextView(context);
            title.setTextSize(15);
            title.setTypeface(AndroidUtilities.bold());
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            col.addView(title);

            subtitle = new TextView(context);
            subtitle.setTextSize(13);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            col.addView(subtitle);

            favIcon = new RivIcon(context);
            addView(favIcon, LayoutHelper.createLinear(32, 32));

            moreIcon = new RivIcon(context);
            moreIcon.setKind(RivIcon.QUEUE);
            moreIcon.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            addView(moreIcon, LayoutHelper.createLinear(32, 32, 0, 4, 0, 0, 0));
        }

        void bind(MessageObject m, List<MessageObject> playlist, String currentPlaylist) {
            title.setText(m.getMusicTitle());
            String artist = m.getMusicAuthor();
            int dur = (int) m.getDuration();
            String ext = "";
            TLRPC.Document doc = m.getDocument();
            if (doc != null && doc.mime_type != null && doc.mime_type.contains("/")) {
                ext = doc.mime_type.substring(doc.mime_type.indexOf('/') + 1).toUpperCase(Locale.US);
            }
            String sub = (artist == null ? "" : artist) + (dur > 0 ? " · " + AndroidUtilities.formatShortDuration(dur) : "") + (!ext.isEmpty() ? " · " + ext : "");
            subtitle.setText(sub);

            TLRPC.PhotoSize thumb = doc != null ? FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 150) : null;
            if (thumb != null) {
                art.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumb, doc), "50_50", new ColorDrawable(0xFF2A2236), m);
            } else {
                art.setImageDrawable(new ColorDrawable(0xFF2A2236));
            }

            boolean fav = RivMeta.isFavoriteSong(keyOf(m));
            favIcon.setKind(fav ? RivIcon.HEART : RivIcon.HEART_OUTLINE);
            favIcon.setColor(fav ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            favIcon.setOnClickListener(v -> {
                RivMeta.toggleFavoriteSong(keyOf(m));
                bind(m, playlist, currentPlaylist);
            });

            setOnClickListener(v -> playFrom(playlist, m));
            moreIcon.setOnClickListener(v -> showSongMenu(m, currentPlaylist));
        }
    }

    private void showSongMenu(MessageObject m, String currentPlaylist) {
        if (getParentActivity() == null) return;
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        items.add(LocaleController.getString(R.string.RivPlayNext));
        actions.add(() -> {
            ArrayList<MessageObject> q = MediaController.getInstance().getPlaylist();
            if (q != null) {
                int cur = MediaController.getInstance().getPlayingMessageObjectNum();
                q.remove(m);
                q.add(Math.min(cur + 1, q.size()), m);
            }
        });

        items.add(LocaleController.getString(R.string.RivAddToQueue));
        actions.add(() -> {
            ArrayList<MessageObject> q = MediaController.getInstance().getPlaylist();
            if (q != null) {
                q.remove(m);
                q.add(m);
            }
        });

        items.add(LocaleController.getString(R.string.RivAddToPlaylist));
        actions.add(() -> showAddToPlaylist(m));

        if (currentPlaylist != null) {
            items.add(LocaleController.getString(R.string.RivRemoveFromPlaylist));
            actions.add(() -> {
                RivPlaylists.removeFrom(currentPlaylist, keyOf(m));
                refreshList();
            });
        }

        items.add(LocaleController.getString(R.string.RivHideSong));
        actions.add(() -> {
            RivMeta.hideSong(keyOf(m));
            refreshList();
        });

        items.add(LocaleController.getString(R.string.RivGoToMessage));
        actions.add(() -> {
            Bundle args = new Bundle();
            if (m.getDialogId() >= 0) args.putLong("user_id", m.getDialogId());
            else args.putLong("chat_id", -m.getDialogId());
            args.putInt("message_id", m.getId());
            presentFragment(new ChatActivity(args));
        });

        CharSequence[] arr = items.toArray(new CharSequence[0]);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setItems(arr, (dialog, which) -> actions.get(which).run());
        showDialog(b.create());
    }

    private void showAddToPlaylist(MessageObject m) {
        if (getParentActivity() == null) return;
        LinkedHashMap<String, ArrayList<String>> playlists = RivPlaylists.load();
        List<String> names = new ArrayList<>(playlists.keySet());
        names.add(LocaleController.getString(R.string.RivNewPlaylist));
        CharSequence[] arr = names.toArray(new CharSequence[0]);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setItems(arr, (dialog, which) -> {
            if (which == names.size() - 1) {
                askPlaylistNameThenAdd(m);
            } else {
                RivPlaylists.addTo(names.get(which), keyOf(m));
            }
        });
        showDialog(b.create());
    }

    private void askPlaylistNameThenAdd(MessageObject m) {
        if (getParentActivity() == null) return;
        EditText edit = new EditText(getParentActivity());
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setHint(LocaleController.getString(R.string.RivNewPlaylist));
        FrameLayout wrap = new FrameLayout(getParentActivity());
        wrap.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivNewPlaylist));
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String name = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (!name.isEmpty()) {
                RivPlaylists.create(name);
                RivPlaylists.addTo(name, keyOf(m));
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }
}
