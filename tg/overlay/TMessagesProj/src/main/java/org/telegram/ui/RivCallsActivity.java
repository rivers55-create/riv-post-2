package org.telegram.ui;

import android.content.Context;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Riv Post: a simple "Calls" screen that shows a Talk Time summary (this week / this month,
 * plus the three people talked to most this month) above a plain call history list. Reads its
 * data the same way Telegram's own CallLogActivity does -- TL_messages_search with
 * TL_inputMessagesFilterPhoneCalls -- but keeps its own small, independent list instead of
 * reusing CallLogActivity's internals (those are private to that class), so this stays a
 * read-only summary screen. Tapping a row opens the chat; there's no swipe-to-delete or
 * multi-select here, unlike Telegram's own Calls screen.
 */
public class RivCallsActivity extends BaseFragment {

    private static final int TYPE_IN = 0;
    private static final int TYPE_OUT = 1;
    private static final int TYPE_MISSED_IN = 2;
    private static final int TYPE_MISSED_OUT = 3;

    private static class CallRow {
        long userId;
        TLRPC.User user;
        int date;
        int duration;
        int type;
        boolean video;
    }

    private final ArrayList<CallRow> rows = new ArrayList<>();
    private boolean loading;
    private boolean endReached;
    private int oldestMessageId;

    private RecyclerListView listView;
    private RowAdapter adapter;
    private TextView statsLine;
    private TextView topLine;
    private LinearLayout statsBlock;
    private View emptyLabel;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    // Riv Post: without this, the header is created but never wired to this screen's list, so
    // the stats block and the call list start drawing under the status bar and title instead
    // of below them.
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean onFragmentCreate() {
        loadCalls(0);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivCalls));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        statsBlock = new LinearLayout(context);
        statsBlock.setOrientation(LinearLayout.VERTICAL);
        statsBlock.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        statsBlock.setVisibility(View.GONE);

        statsLine = new TextView(context);
        statsLine.setTextSize(14);
        statsLine.setTypeface(AndroidUtilities.bold());
        statsLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statsBlock.addView(statsLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        topLine = new TextView(context);
        topLine.setTextSize(13);
        topLine.setMaxLines(2);
        topLine.setEllipsize(TextUtils.TruncateAt.END);
        topLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statsBlock.addView(topLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0, 0));

        column.addView(statsBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new RowAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) return;
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("user_id", rows.get(position).userId);
            presentFragment(new org.telegram.ui.ChatActivity(args));
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!loading && !endReached) {
                    LinearLayoutManager lm = (LinearLayoutManager) listView.getLayoutManager();
                    if (lm != null && lm.findLastVisibleItemPosition() >= rows.size() - 4) {
                        loadCalls(oldestMessageId);
                    }
                }
            }
        });
        column.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1f));

        int bottomBarSpace = AndroidUtilities.dp(RivBottomBar.HEIGHT_DP) + AndroidUtilities.navigationBarHeight;
        listView.setPadding(0, 0, 0, bottomBarSpace);
        listView.setClipToPadding(false);

        TextView empty = new TextView(context);
        empty.setText(LocaleController.getString(R.string.RivNoCallsYet));
        empty.setTextSize(14);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        empty.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(40), AndroidUtilities.dp(32), AndroidUtilities.dp(40));
        empty.setVisibility(View.GONE);
        emptyLabel = empty;
        column.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        RivBottomBar.attach(this, root, RivBottomBar.TAB_CALLS, AndroidUtilities.navigationBarHeight);

        return fragmentView;
    }

    // Riv Post: same request CallLogActivity itself uses (TL_messages_search with
    // TL_inputMessagesFilterPhoneCalls), paged by offset_id. One flat row per call here --
    // no grouping of consecutive calls from the same person into one row, unlike Telegram's
    // own screen -- since this list only needs to be read, not managed.
    private void loadCalls(int maxId) {
        if (loading) return;
        loading = true;

        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 50;
        req.peer = new TLRPC.TL_inputPeerEmpty();
        req.filter = new TLRPC.TL_inputMessagesFilterPhoneCalls();
        req.q = "";
        req.offset_id = maxId;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                endReached = true;
                refreshStats();
                return;
            }
            TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) response;
            getMessagesController().putUsers(msgs.users, false);
            endReached = msgs.messages.isEmpty();
            for (TLRPC.Message msg : msgs.messages) {
                if (msg.action == null) continue;
                oldestMessageId = msg.id;
                long fromId = MessageObject.getFromChatId(msg);
                boolean out = fromId == getUserConfig().getClientUserId();
                long userId = out ? msg.peer_id.user_id : fromId;

                int duration = msg.action.duration;
                boolean video = msg.action.video;
                boolean missed = false;
                if (msg.action instanceof TLRPC.TL_messageActionPhoneCall) {
                    TLRPC.PhoneCallDiscardReason reason = msg.action.reason;
                    missed = reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed
                            || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy;
                } else if (msg.action instanceof TLRPC.TL_messageActionConferenceCall) {
                    missed = ((TLRPC.TL_messageActionConferenceCall) msg.action).missed;
                } else {
                    continue;
                }

                CallRow row = new CallRow();
                row.userId = userId;
                row.user = getMessagesController().getUser(userId);
                row.date = msg.date;
                row.duration = duration;
                row.video = video;
                if (missed) {
                    row.type = out ? TYPE_MISSED_OUT : TYPE_MISSED_IN;
                } else {
                    row.type = out ? TYPE_OUT : TYPE_IN;
                }
                rows.add(row);
            }
            refreshStats();
            if (adapter != null) adapter.notifyDataSetChanged();
            if (emptyLabel != null) emptyLabel.setVisibility(rows.isEmpty() && endReached ? View.VISIBLE : View.GONE);
        }));
    }

    // Riv Post: same math as the old Riv Post app's Talk Time header -- sum call durations
    // within the last 7 / 30 days, and the top 3 people talked to most within 30 days.
    private void refreshStats() {
        if (statsBlock == null) return;
        if (rows.isEmpty()) {
            statsBlock.setVisibility(View.GONE);
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        int week = 0, month = 0;
        Map<Long, Integer> perContact = new LinkedHashMap<>();
        for (CallRow row : rows) {
            long age = now - row.date;
            if (age < 7L * 86400L) week += row.duration;
            if (age < 30L * 86400L) {
                month += row.duration;
                if (row.duration > 0) {
                    perContact.merge(row.userId, row.duration, Integer::sum);
                }
            }
        }
        statsLine.setText(LocaleController.getString(R.string.RivTalkTime) + ": " + LocaleController.getString(R.string.RivThisWeek)
                + " " + fmtDuration(week) + "  ·  " + LocaleController.getString(R.string.RivThisMonth) + " " + fmtDuration(month));

        List<Map.Entry<Long, Integer>> top = new ArrayList<>(perContact.entrySet());
        Collections.sort(top, (a, b) -> b.getValue() - a.getValue());
        if (top.size() > 3) top = top.subList(0, 3);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Integer> e : top) {
            TLRPC.User u = getMessagesController().getUser(e.getKey());
            String name = u != null ? org.telegram.messenger.UserObject.getUserName(u) : "";
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(name).append(" ").append(fmtDuration(e.getValue()));
        }
        topLine.setText(sb.toString());
        topLine.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);
        statsBlock.setVisibility(View.VISIBLE);
    }

    // Riv Post: matches the old Riv Post app's Fmt.duration format (H:MM:SS, or M:SS under an hour).
    private static String fmtDuration(int totalSeconds) {
        int s = Math.max(totalSeconds, 0);
        if (s >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private class RowAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;

        RowAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new Row(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((Row) holder.itemView).bind(rows.get(position));
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private class Row extends LinearLayout {
        final BackupImageView avatar;
        final TextView name;
        final TextView subtitle;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            avatar = new BackupImageView(context);
            avatar.getImageReceiver().setRoundRadius(AndroidUtilities.dp(22));
            addView(avatar, LayoutHelper.createLinear(44, 44));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            addView(texts, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));

            name = new TextView(context);
            name.setTextSize(16);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitle = new TextView(context);
            subtitle.setTextSize(13);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0, 0));
        }

        void bind(CallRow row) {
            boolean missed = row.type == TYPE_MISSED_IN || row.type == TYPE_MISSED_OUT;
            String displayName = row.user != null ? org.telegram.messenger.UserObject.getUserName(row.user) : "";
            name.setText(displayName);
            name.setTextColor(missed ? 0xFFE53935 : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(currentAccount, row.user);
            avatar.setForUserOrChat(row.user, avatarDrawable);

            String dir = row.type == TYPE_OUT || row.type == TYPE_MISSED_OUT
                    ? LocaleController.getString(R.string.RivOutgoingCall)
                    : LocaleController.getString(R.string.RivIncomingCall);
            if (missed) dir = LocaleController.getString(R.string.RivMissedCall);
            StringBuilder sub = new StringBuilder(dir).append(" · ").append(LocaleController.formatDateCallLog(row.date));
            if (row.duration > 0) sub.append(" · ").append(fmtDuration(row.duration));
            if (row.video) sub.append(" · ").append(LocaleController.getString(R.string.RivVideoCallTag));
            subtitle.setText(sub.toString());
        }
    }
}
