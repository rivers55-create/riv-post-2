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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Riv Post: picker for the chats and channels the Music and Videos libraries read from.
 *
 * Layout, top to bottom:
 *  - a strip of the picked chats as small chips (tap a chip's x to drop it),
 *  - a search box,
 *  - type filters: (In library) / All / Channels / Groups / People / Bots / Picked,
 *  - "Select all shown" and "Clear" shortcuts,
 *  - the list (picked chats always first), each row showing what kind of chat it is,
 *  - a bottom bar with the count, a Preview button and Done.
 *
 * Changes are applied once, when leaving the screen (Done or Back), instead of after every tap,
 * so the library is not re-searched over and over while picking.
 */
public class RivChatSourcePicker extends RivFragment {

    public interface Delegate {
        void onSourcesChanged(ArrayList<Long> sources);

        /** Preview the picked sources right now (e.g. play a sample song/video). */
        void onPreview(ArrayList<Long> sources);
    }

    private static final int F_LIBRARY = 0, F_ALL = 1, F_CHANNELS = 2, F_GROUPS = 3, F_PEOPLE = 4, F_BOTS = 5, F_PICKED = 6;

    private final String title;
    private final String description;
    private final ArrayList<Long> original;
    private final ArrayList<Long> sources;
    private ArrayList<Long> libraryIds;
    private Delegate delegate;

    private int filter = F_ALL;
    private String query = "";
    private final List<TLRPC.Dialog> allDialogs = new ArrayList<>();

    private RowAdapter adapter;
    private LinearLayout chipsRow;
    private HorizontalScrollView chipsScroll;
    private LinearLayout filterRow;
    private TextView countLabel;
    private TextView descView;

    public RivChatSourcePicker(String title, String description, ArrayList<Long> sources) {
        super();
        this.title = title;
        this.description = description;
        this.original = new ArrayList<>(sources);
        this.sources = new ArrayList<>(sources);
    }

    public void setDelegate(Delegate d) {
        delegate = d;
    }

    /** Optional: the chats already in the library; adds an "In library" filter and starts on it. */
    public void setLibraryIds(ArrayList<Long> ids) {
        libraryIds = ids == null ? null : new ArrayList<>(ids);
        if (libraryIds != null && !libraryIds.isEmpty()) filter = F_LIBRARY;
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
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
    public void onFragmentDestroy() {
        applyChanges();
        super.onFragmentDestroy();
    }

    private boolean applied;

    private void applyChanges() {
        if (applied) return;
        applied = true;
        boolean same = original.size() == sources.size() && original.containsAll(sources);
        if (!same && delegate != null) delegate.onSourcesChanged(new ArrayList<>(sources));
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

        // Plain mode: RivFragment adds the header and keeps this column clear of the status bar,
        // the header and the phone's own buttons.
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        descView = new TextView(context);
        descView.setText(description);
        if (TextUtils.isEmpty(description)) descView.setVisibility(View.GONE);
        descView.setTextSize(13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(2));
        column.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // picked chips
        chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chipsScroll.addView(chipsRow, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0));
        column.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 6, 0, 0));

        // search
        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(round(Theme.getColor(Theme.key_windowBackgroundWhite), 22));
        android.widget.ImageView searchIcon = new android.widget.ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), android.graphics.PorterDuff.Mode.SRC_IN));
        searchIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        searchBar.addView(searchIcon, LayoutHelper.createLinear(22, 22, 12, 0, 0, 0));
        EditText searchField = new EditText(context);
        searchField.setHint(LocaleController.getString(R.string.Search));
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
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
        searchBar.addView(searchField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 8, 0, 10, 0));
        column.addView(searchBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 12, 6, 12, 6));

        // type filters
        HorizontalScrollView filterScroll = new HorizontalScrollView(context);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterRow = new LinearLayout(context);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        filterScroll.addView(filterRow, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0));
        column.addView(filterScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));

        // select all / clear
        LinearLayout quick = new LinearLayout(context);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        TextView selectAll = linkText(context, LocaleController.getString(R.string.RivPickSelectAll));
        selectAll.setOnClickListener(v -> {
            for (TLRPC.Dialog d : adapter.visible) {
                if (!sources.contains(d.id)) sources.add(d.id);
            }
            refreshAll();
        });
        quick.addView(selectAll);
        View spacer = new View(context);
        quick.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));
        TextView clear = linkText(context, LocaleController.getString(R.string.RivPickClear));
        clear.setOnClickListener(v -> {
            sources.clear();
            refreshAll();
        });
        quick.addView(clear);
        column.addView(quick, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34));

        // list
        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new RowAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= adapter.visible.size()) return;
            toggle(adapter.visible.get(position).id);
        });
        column.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // bottom bar: count, preview, done
        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        bottom.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        countLabel = new TextView(context);
        countLabel.setTextSize(14);
        countLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        countLabel.setMaxLines(2);
        bottom.addView(countLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (!org.telegram.messenger.RivPrefs.b(org.telegram.messenger.RivPrefs.HIDE_SOURCE_PREVIEW_BUTTON)) {
            TextView preview = pillButton(context, "▶  " + LocaleController.getString(R.string.RivPickPreview), 0x33FFFFFF, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            preview.setOnClickListener(v -> {
                if (delegate != null) delegate.onPreview(new ArrayList<>(sources));
            });
            bottom.addView(preview, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 8, 0));
        }
        TextView done = pillButton(context, LocaleController.getString(R.string.RivPickDone), 0xFFE53935, Color.WHITE);
        done.setOnClickListener(v -> finishFragment());
        bottom.addView(done, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        column.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshAll();
        return fragmentView;
    }

    // ---------------------------------------------------------------- state

    private void toggle(long dialogId) {
        if (sources.contains(dialogId)) {
            sources.remove(dialogId);
        } else {
            sources.add(dialogId);
        }
        refreshAll();
    }

    private void refreshAll() {
        buildFilters();
        buildChips();
        if (countLabel != null) {
            countLabel.setText(sources.isEmpty()
                    ? LocaleController.getString(R.string.RivPickNone)
                    : LocaleController.formatString(R.string.RivPickCount, sources.size()));
        }
        if (adapter != null) adapter.rebuild();
    }

    private void buildFilters() {
        if (filterRow == null) return;
        Context context = filterRow.getContext();
        filterRow.removeAllViews();
        if (libraryIds != null && !libraryIds.isEmpty()) addFilter(context, F_LIBRARY, R.string.RivPickInLibrary);
        addFilter(context, F_ALL, R.string.RivPickAll);
        addFilter(context, F_CHANNELS, R.string.RivPickChannels);
        addFilter(context, F_GROUPS, R.string.RivPickGroups);
        addFilter(context, F_PEOPLE, R.string.RivPickPeople);
        addFilter(context, F_BOTS, R.string.RivPickBots);
        addFilter(context, F_PICKED, R.string.RivPickPicked);
    }

    private void addFilter(Context context, int which, int labelRes) {
        boolean on = filter == which;
        String label = LocaleController.getString(labelRes);
        if (which == F_PICKED && !sources.isEmpty()) label += "  " + sources.size();
        TextView pill = new TextView(context);
        pill.setText(label);
        pill.setTextSize(13);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        pill.setTextColor(on ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        pill.setBackground(round(on ? 0xFFE53935 : Theme.getColor(Theme.key_windowBackgroundWhite), 16));
        pill.setOnClickListener(v -> {
            filter = which;
            refreshAll();
        });
        filterRow.addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 4, 6, 4));
    }

    private void buildChips() {
        if (chipsRow == null) return;
        Context context = chipsRow.getContext();
        chipsRow.removeAllViews();
        chipsScroll.setVisibility(sources.isEmpty() ? View.GONE : View.VISIBLE);
        for (Long id : sources) {
            LinearLayout chip = new LinearLayout(context);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(8), 0);
            chip.setBackground(round(Theme.getColor(Theme.key_windowBackgroundWhite), 17));
            BackupImageView av = new BackupImageView(context);
            av.getImageReceiver().setRoundRadius(AndroidUtilities.dp(14));
            setAvatar(av, id);
            chip.addView(av, LayoutHelper.createLinear(28, 28));
            TextView name = new TextView(context);
            name.setText(titleOf(id));
            name.setTextSize(13);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxWidth(AndroidUtilities.dp(120));
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            chip.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 0));
            TextView x = new TextView(context);
            x.setText("✕");
            x.setTextSize(12);
            x.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            chip.addView(x);
            final long cid = id;
            chip.setOnClickListener(v -> toggle(cid));
            chipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 34, 0, 5, 6, 5));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(AndroidUtilities.dp(radiusDp));
        return g;
    }

    private static TextView linkText(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(0xFFE53935);
        t.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        return t;
    }

    private static TextView pillButton(Context context, String text, int bg, int fg) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(AndroidUtilities.bold());
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTextColor(fg);
        t.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        t.setBackground(round(bg, 20));
        return t;
    }

    private String titleOf(long id) {
        if (id > 0) {
            TLRPC.User user = getMessagesController().getUser(id);
            if (user != null) return UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            if (chat != null) return chat.title;
        }
        return String.valueOf(id);
    }

    private void setAvatar(BackupImageView view, long id) {
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (id > 0) {
            TLRPC.User user = getMessagesController().getUser(id);
            avatarDrawable.setInfo(currentAccount, user);
            view.setForUserOrChat(user, avatarDrawable);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            avatarDrawable.setInfo(currentAccount, chat);
            view.setForUserOrChat(chat, avatarDrawable);
        }
    }

    /** 2 = channel, 1 = group, 3 = bot, 0 = person. */
    private int kindOf(long id) {
        if (id > 0) {
            TLRPC.User user = getMessagesController().getUser(id);
            return user != null && user.bot ? 3 : 0;
        }
        TLRPC.Chat chat = getMessagesController().getChat(-id);
        return chat != null && ChatObject.isChannelAndNotMegaGroup(chat) ? 2 : 1;
    }

    private String subtitleOf(long id) {
        int kind = kindOf(id);
        if (kind == 2 || kind == 1) {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            String type = LocaleController.getString(kind == 2 ? R.string.RivPickChannel : R.string.RivPickGroup);
            if (chat != null && chat.participants_count > 0) {
                return type + " · " + LocaleController.formatShortNumber(chat.participants_count, null);
            }
            return type;
        }
        return LocaleController.getString(kind == 3 ? R.string.RivPickBot : R.string.RivPickPerson);
    }

    private boolean passesFilter(long id) {
        switch (filter) {
            case F_LIBRARY:
                return libraryIds != null && libraryIds.contains(id);
            case F_CHANNELS:
                return kindOf(id) == 2;
            case F_GROUPS:
                return kindOf(id) == 1;
            case F_PEOPLE:
                return kindOf(id) == 0;
            case F_BOTS:
                return kindOf(id) == 3;
            case F_PICKED:
                return sources.contains(id);
            default:
                return true;
        }
    }

    // ---------------------------------------------------------------- list

    private class RowAdapter extends RecyclerListView.SelectionAdapter {
        final Context context;
        final List<TLRPC.Dialog> visible = new ArrayList<>();

        RowAdapter(Context context) {
            this.context = context;
        }

        void rebuild() {
            visible.clear();
            String q = query.trim().toLowerCase(Locale.getDefault());
            List<TLRPC.Dialog> picked = new ArrayList<>();
            List<TLRPC.Dialog> rest = new ArrayList<>();
            for (TLRPC.Dialog d : allDialogs) {
                if (!passesFilter(d.id)) continue;
                if (!q.isEmpty() && !titleOf(d.id).toLowerCase(Locale.getDefault()).contains(q)) continue;
                if (sources.contains(d.id)) picked.add(d);
                else rest.add(d);
            }
            visible.addAll(picked);
            visible.addAll(rest);
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
            ((Row) holder.itemView).bind(visible.get(position).id);
        }
    }

    private class Row extends LinearLayout {
        final BackupImageView avatar;
        final TextView name;
        final TextView sub;
        final FrameLayout checkBoxWrap;
        final RivIcon checkIcon;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(16), AndroidUtilities.dp(7));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            avatar = new BackupImageView(context);
            avatar.getImageReceiver().setRoundRadius(AndroidUtilities.dp(22));
            addView(avatar, LayoutHelper.createLinear(44, 44));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 10, 0));

            name = new TextView(context);
            name.setTextSize(15);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
            name.setTextDirection(TEXT_DIRECTION_LOCALE);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            sub = new TextView(context);
            sub.setTextSize(12);
            sub.setMaxLines(1);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            checkBoxWrap = new FrameLayout(context);
            checkIcon = new RivIcon(context);
            checkIcon.setKind(RivIcon.CHECK);
            checkIcon.setColor(Color.WHITE);
            checkBoxWrap.addView(checkIcon, LayoutHelper.createFrame(14, 14, Gravity.CENTER));
            addView(checkBoxWrap, LayoutHelper.createLinear(24, 24));
        }

        void bind(long id) {
            name.setText(titleOf(id));
            sub.setText(subtitleOf(id));
            setAvatar(avatar, id);
            boolean checked = sources.contains(id);
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
        }
    }
}
