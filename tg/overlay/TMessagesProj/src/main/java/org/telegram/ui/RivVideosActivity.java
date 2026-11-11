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
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Riv Post: a video library built from chats and channels the user picks.
 * Videos open in Telegram's own player.
 */
public class RivVideosActivity extends BaseFragment {

    private static final String SOURCES_KEY = "videoSources";

    private RecyclerListView listView;
    private ListAdapter adapter;

    private final ArrayList<Long> sources = new ArrayList<>();
    private final ArrayList<MessageObject> videos = new ArrayList<>();
    private final HashSet<String> videoKeys = new HashSet<>();
    private int loading = 0;

    // list positions
    private int rowCount;
    private int sourcesHeaderRow;
    private int addSourceRow;
    private int firstSourceRow, lastSourceRow;
    private int sourcesInfoRow;
    private int videosHeaderRow;
    private int firstVideoRow, lastVideoRow;
    private int videosInfoRow;

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
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
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
        listView.setAdapter(adapter = new ListAdapter());
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position == addSourceRow) {
                pickSource();
            } else if (position >= firstSourceRow && position <= lastSourceRow) {
                askRemoveSource(sources.get(position - firstSourceRow));
            } else if (position >= firstVideoRow && position <= lastVideoRow) {
                int index = position - firstVideoRow;
                if (index >= 0 && index < videos.size()) {
                    PhotoViewer.getInstance().setParentActivity(RivVideosActivity.this);
                    MessageObject current = videos.get(index);
                    PhotoViewer.getInstance().openPhoto(videos, index, current.getDialogId(), 0, 0, new PhotoViewer.EmptyPhotoViewerProvider());
                }
            }
        });

        updateRows();
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
            reloadVideos();
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
        builder.setMessage(LocaleController.getString(R.string.RivVideosRemoveSource));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            sources.remove(dialogId);
            saveSources();
            updateRows();
            reloadVideos();
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

    private void reloadVideos() {
        videos.clear();
        videoKeys.clear();
        loading = sources.size();
        if (loading == 0) {
            updateRows();
            return;
        }
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
                        if (videoKeys.contains(key)) {
                            continue;
                        }
                        MessageObject obj = new MessageObject(currentAccount, msg, false, true);
                        if (obj.isVideo()) {
                            videoKeys.add(key);
                            videos.add(obj);
                        }
                    }
                    java.util.Collections.sort(videos, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));
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
        if (videos.isEmpty()) {
            videosHeaderRow = -1;
            firstVideoRow = lastVideoRow = -1;
            videosInfoRow = sources.isEmpty() || loading > 0 ? -1 : rowCount++;
        } else {
            videosHeaderRow = rowCount++;
            firstVideoRow = rowCount;
            rowCount += videos.size();
            lastVideoRow = rowCount - 1;
            videosInfoRow = -1;
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
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
            if (position == sourcesHeaderRow || position == videosHeaderRow) {
                return 0;
            }
            if (position == addSourceRow) {
                return 1;
            }
            if (position >= firstSourceRow && position <= lastSourceRow && firstSourceRow != -1) {
                return 2;
            }
            if (position == sourcesInfoRow || position == videosInfoRow) {
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
                    view = new SharedDocumentCell(context);
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
                        cell.setText(LocaleController.getString(R.string.RivVideosSources));
                    } else {
                        cell.setText(LocaleController.formatPluralString("Videos", videos.size()));
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    cell.setText(LocaleController.getString(R.string.RivVideosAddSource), firstSourceRow != -1);
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
                        cell.setText(LocaleController.getString(loading > 0 ? R.string.RivVideosLoading : R.string.RivVideosSourcesInfo));
                    } else {
                        cell.setText(LocaleController.getString(R.string.RivVideosEmpty));
                    }
                    break;
                }
                case 4: {
                    SharedDocumentCell cell = (SharedDocumentCell) holder.itemView;
                    int index = position - firstVideoRow;
                    if (index >= 0 && index < videos.size()) {
                        cell.setDocument(videos.get(index), position != lastVideoRow);
                    }
                    break;
                }
            }
        }
    }
}
