package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Riv Post: "Pick chats and channels for your library" screen, shared by RivMusicActivity's
 * "Music sources" tab and RivVideosActivity's "Selected chats" tab. Search box + a checkbox
 * list of the user's own chats/channels (checking one adds it as a source, unchecking removes
 * it) + a floating round button that plays a sample item from the currently checked sources,
 * via a caller-supplied preview callback (what "preview" means differs for music vs video).
 */
public class RivChatSourcePicker extends RivFragment {

    public interface Delegate {
        void onSourcesChanged(ArrayList<Long> sources);

        /** Preview the picked sources right now (e.g. play a sample song/video). */
        void onPreview(ArrayList<Long> sources);
    }

    private final String title;
    private final String description;
    private final ArrayList<Long> sources;
    private Delegate delegate;

    private EditText searchField;
    private RecyclerListView listView;
    private RowAdapter adapter;
    private boolean showPreviewBtn;
    private LinearLayout column;
    private String query = "";
    private final List<TLRPC.Dialog> allDialogs = new ArrayList<>();

    public RivChatSourcePicker(String title, String description, ArrayList<Long> sources) {
        super();
        this.title = title;
        this.description = description;
        this.sources = new ArrayList<>(sources);
    }

    public void setDelegate(Delegate d) {
        delegate = d;
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    // Riv Post: tells the framework which RecyclerListView to inset for simpleActionBar, so the
    // header is attached. The framework only ever pads this one view though, and on this screen
    // "desc" and "searchBar" sit above listView as separate siblings in the same column, so they
    // never got pushed down -- only listView did, which is why the search box still showed up
    // partly under the title. onInsets() below pushes the whole column down instead, and cancels
    // out listView's own top padding so it isn't pushed down twice.
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        applyHeaderInset();
    }

    private void applyHeaderInset() {
        if (column == null) return;
        int headerHeight = rivHeaderHeight();
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) column.getLayoutParams();
        if (lp != null && lp.topMargin != headerHeight) {
            lp.topMargin = headerHeight;
            column.setLayoutParams(lp);
        }
        // Riv Post: the framework resets listView's own padding (top AND bottom) on every inset
        // change, which would silently wipe out the room reserved below for the floating preview
        // button -- so that room is recomputed here every time instead of just being preserved.
        if (listView != null) {
            int listBottomPad = showPreviewBtn ? AndroidUtilities.dp(84) : AndroidUtilities.dp(8);
            if (listView.getPaddingTop() != 0 || listView.getPaddingBottom() != listBottomPad) {
                listView.setPadding(listView.getPaddingLeft(), 0, listView.getPaddingRight(), listBottomPad);
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        allDialogs.clear();
        for (TLRPC.Dialog d : getMessagesController().getAllDialogs()) {
            if (d == null || d.isFolder || d.id == 0) continue;
            allDialogs.add(d);
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(title);
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        applyHeaderInset();

        TextView desc = new TextView(context);
        desc.setText(description);
        desc.setTextSize(13);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
        column.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchBg.setCornerRadius(AndroidUtilities.dp(22));
        searchBar.setBackground(searchBg);
        searchField = new EditText(context);
        searchField.setHint(LocaleController.getString(R.string.Search));
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
                if (adapter != null) adapter.rebuild();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        searchBar.addView(searchField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 18, 0, 10, 0));
        column.addView(searchBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 12, 8, 12, 8));

        FrameLayout listWrap = new FrameLayout(context);
        column.addView(listWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new RowAdapter(context));
        // Riv Post: reserve room at the bottom of the list so the floating preview button (added
        // below) never sits on top of the last row; clipToPadding(false) keeps that last row
        // scrollable all the way into view instead of stopping short.
        showPreviewBtn = !org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.HIDE_SOURCE_PREVIEW_BUTTON);
        applyHeaderInset();
        listView.setClipToPadding(false);
        listWrap.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (showPreviewBtn) {
            FrameLayout previewBtn = new FrameLayout(context);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setShape(GradientDrawable.OVAL);
            btnBg.setColor(0xFFE53935);
            previewBtn.setBackground(btnBg);
            RivIcon playIcon = new RivIcon(context);
            playIcon.setKind(RivIcon.PLAY);
            playIcon.setColor(Color.WHITE);
            previewBtn.addView(playIcon, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            previewBtn.setOnClickListener(v -> {
                if (delegate != null) delegate.onPreview(new ArrayList<>(sources));
            });
            listWrap.addView(previewBtn, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 16, 16));
        }

        return fragmentView;
    }

    private void toggle(long dialogId) {
        if (sources.contains(dialogId)) {
            sources.remove(dialogId);
        } else {
            sources.add(dialogId);
        }
        if (delegate != null) delegate.onSourcesChanged(new ArrayList<>(sources));
        if (adapter != null) adapter.rebuild();
    }

    private String titleOf(TLRPC.Dialog d) {
        if (d.id > 0) {
            TLRPC.User user = getMessagesController().getUser(d.id);
            if (user != null) return UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-d.id);
            if (chat != null) return chat.title;
        }
        return String.valueOf(d.id);
    }

    private class RowAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;
        final List<TLRPC.Dialog> visible = new ArrayList<>();

        RowAdapter(Context context) {
            this.context = context;
            rebuild();
        }

        void rebuild() {
            visible.clear();
            String q = query.trim().toLowerCase(Locale.getDefault());
            for (TLRPC.Dialog d : allDialogs) {
                if (!q.isEmpty() && !titleOf(d).toLowerCase(Locale.getDefault()).contains(q)) continue;
                visible.add(d);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new Row(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((Row) holder.itemView).bind(visible.get(position));
        }
    }

    private class Row extends LinearLayout {
        BackupImageView avatar;
        TextView name;
        FrameLayout checkBoxWrap;
        RivIcon checkIcon;
        long dialogId;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            avatar = new BackupImageView(context);
            avatar.getImageReceiver().setRoundRadius(AndroidUtilities.dp(22));
            addView(avatar, LayoutHelper.createLinear(44, 44));

            name = new TextView(context);
            name.setTextSize(15);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 8, 0));

            checkBoxWrap = new FrameLayout(context);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(AndroidUtilities.dp(1.6f), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            checkBoxWrap.setBackground(ring);
            checkIcon = new RivIcon(context);
            checkIcon.setKind(RivIcon.CHECK);
            checkIcon.setColor(Color.WHITE);
            checkBoxWrap.addView(checkIcon, LayoutHelper.createFrame(14, 14, Gravity.CENTER));
            addView(checkBoxWrap, LayoutHelper.createLinear(26, 26));
        }

        void bind(TLRPC.Dialog d) {
            dialogId = d.id;
            String t = titleOf(d);
            name.setText(t);

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (d.id > 0) {
                TLRPC.User user = getMessagesController().getUser(d.id);
                avatarDrawable.setInfo(currentAccount, user);
                avatar.setForUserOrChat(user, avatarDrawable);
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(-d.id);
                avatarDrawable.setInfo(currentAccount, chat);
                avatar.setForUserOrChat(chat, avatarDrawable);
            }

            boolean checked = sources.contains(dialogId);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (checked) {
                bg.setColor(0xFFE53935);
            } else {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(AndroidUtilities.dp(1.6f), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            }
            checkBoxWrap.setBackground(bg);
            checkIcon.setVisibility(checked ? VISIBLE : INVISIBLE);

            setOnClickListener(v -> toggle(dialogId));
        }
    }
}
