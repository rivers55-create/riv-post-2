package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Riv Post: a music library built from chats and channels the user picks.
 * Playback goes through Telegram's own player, so the bar in chats,
 * the notification and the lock screen all work as usual.
 */
public class RivMusicActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final String SOURCES_KEY = "musicSources";

    private RecyclerListView listView;
    private ListAdapter adapter;

    private final ArrayList<Long> sources = new ArrayList<>();
    private final ArrayList<MessageObject> songs = new ArrayList<>();
    private final HashSet<String> songKeys = new HashSet<>();
    private int loading = 0;

    // list positions
    private int rowCount;
    private int sourcesHeaderRow;
    private int addSourceRow;
    private int firstSourceRow, lastSourceRow;
    private int sourcesInfoRow;
    private int songsHeaderRow;
    private int firstSongRow, lastSongRow;
    private int songsInfoRow;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        loadSources();
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivMusic));
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
        listView.setAdapter(adapter = new ListAdapter());
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position == addSourceRow) {
                pickSource();
            } else if (position >= firstSourceRow && position <= lastSourceRow) {
                askRemoveSource(sources.get(position - firstSourceRow));
            } else if (position >= firstSongRow && position <= lastSongRow && view instanceof SharedAudioCell) {
                ((SharedAudioCell) view).didPressedButton();
            }
        });

        updateRows();
        reloadSongs();
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
            if (sb.length() > 0) {
                sb.append(',');
            }
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
            updateRows();
            reloadSongs();
            return true;
        });
        presentFragment(fragment);
    }

    private void askRemoveSource(long dialogId) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(titleOf(dialogId));
        builder.setMessage(LocaleController.getString(R.string.RivMusicRemoveSource));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            sources.remove(dialogId);
            saveSources();
            updateRows();
            reloadSongs();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String titleOf(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) {
                return org.telegram.messenger.UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return String.valueOf(dialogId);
    }

    private void reloadSongs() {
        songs.clear();
        songKeys.clear();
        loading = sources.size();
        if (loading == 0) {
            updateRows();
            return;
        }
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
                        if (songKeys.contains(key)) {
                            continue;
                        }
                        MessageObject obj = new MessageObject(currentAccount, msg, false, true);
                        if (obj.isMusic()) {
                            songKeys.add(key);
                            songs.add(obj);
                        }
                    }
                    java.util.Collections.sort(songs, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));
                }
                updateRows();
            }));
        }
        updateRows();
    }

    private void updateRows() {
        rowCount = 0;
        sourcesHeaderRow = rowCount++;
        addSourceRow = rowCount++;
        if (sources.isEmpty()) {
            firstSourceRow = lastSourceRow = -1;
        } else {
            firstSourceRow = rowCount;
            rowCount += sources.size();
            lastSourceRow = rowCount - 1;
        }
        sourcesInfoRow = rowCount++;
        if (songs.isEmpty()) {
            songsHeaderRow = -1;
            firstSongRow = lastSongRow = -1;
            songsInfoRow = sources.isEmpty() || loading > 0 ? -1 : rowCount++;
        } else {
            songsHeaderRow = rowCount++;
            firstSongRow = rowCount;
            rowCount += songs.size();
            lastSongRow = rowCount - 1;
            songsInfoRow = -1;
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            if (listView != null) {
                for (int a = 0, n = listView.getChildCount(); a < n; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof SharedAudioCell) {
                        ((SharedAudioCell) child).updateButtonState(false, true);
                    }
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 1 || type == 2 || type == 4;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == sourcesHeaderRow || position == songsHeaderRow) {
                return 0;
            }
            if (position == addSourceRow) {
                return 1;
            }
            if (position >= firstSourceRow && position <= lastSourceRow && firstSourceRow != -1) {
                return 2;
            }
            if (position == sourcesInfoRow || position == songsInfoRow) {
                return 3;
            }
            return 4;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
            switch (viewType) {
                case 0:
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                case 2:
                    view = new TextSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextInfoPrivacyCell(context);
                    view.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 4:
                default:
                    view = new SharedAudioCell(context) {
                        @Override
                        protected boolean needPlayMessage(MessageObject messageObject) {
                            return MediaController.getInstance().setPlaylist(new ArrayList<>(songs), messageObject, 0, false, null);
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == sourcesHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.RivMusicSources));
                    } else {
                        cell.setText(LocaleController.formatPluralString("MusicFiles", songs.size()));
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText(LocaleController.getString(R.string.RivMusicAddSource), firstSourceRow != -1);
                    break;
                }
                case 2: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    cell.setText(titleOf(sources.get(position - firstSourceRow)), position != lastSourceRow);
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == sourcesInfoRow) {
                        cell.setText(LocaleController.getString(loading > 0 ? R.string.RivMusicLoading : R.string.RivMusicSourcesInfo));
                    } else {
                        cell.setText(LocaleController.getString(R.string.RivMusicEmpty));
                    }
                    break;
                }
                case 4: {
                    SharedAudioCell cell = (SharedAudioCell) holder.itemView;
                    int index = position - firstSongRow;
                    if (index >= 0 && index < songs.size()) {
                        cell.setMessageObject(songs.get(index), position != lastSongRow);
                    }
                    break;
                }
            }
        }
    }
}
