package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Riv Post: a Downloads page. Everything Telegram is downloading, with progress, speed, and
 * pause / resume / cancel for each file, then the finished ones below.
 */
public class RivDownloadsActivity extends RivFragment {

    private LinearLayout list;
    private final HashMap<String, long[]> lastBytes = new HashMap<>(); // name -> {bytes, timeMs}
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            rebuild();
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivDownloads));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;
        ScrollView scroll = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        frame.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        rebuild();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.cancelRunOnUIThread(tick);
        AndroidUtilities.runOnUIThread(tick, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.cancelRunOnUIThread(tick);
    }

    private void rebuild() {
        if (list == null) return;
        Context context = list.getContext();
        list.removeAllViews();
        DownloadController dc = DownloadController.getInstance(currentAccount);
        ArrayList<MessageObject> active = new ArrayList<>(dc.downloadingFiles);
        ArrayList<MessageObject> done = new ArrayList<>(dc.recentDownloadingFiles);
        if (active.isEmpty() && done.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(LocaleController.getString(R.string.RivNoDownloads));
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(80), AndroidUtilities.dp(32), 0);
            list.addView(empty);
            return;
        }
        if (!active.isEmpty()) {
            list.addView(sectionTitle(context, LocaleController.getString(R.string.RivDownloading)));
            for (MessageObject m : active) list.addView(row(context, m, true));
        }
        if (!done.isEmpty()) {
            list.addView(sectionTitle(context, LocaleController.getString(R.string.RivDownloaded)));
            for (MessageObject m : done) list.addView(row(context, m, false));
        }
    }

    private TextView sectionTitle(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        t.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(6));
        return t;
    }

    private View row(Context context, MessageObject m, boolean active) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TLRPC.Document doc = m.getDocument();
        String name = doc != null ? FileLoader.getDocumentFileName(doc) : m.getFileName();
        TextView title = new TextView(context);
        title.setText(TextUtils.isEmpty(name) ? m.getFileName() : name);
        title.setTextSize(15);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        col.addView(title);

        TextView sub = new TextView(context);
        sub.setTextSize(13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        col.addView(sub);

        long total = doc != null ? doc.size : 0;
        String fileName = m.getFileName();
        boolean loading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
        File f = FileLoader.getInstance(currentAccount).getPathToMessage(m.messageOwner);
        boolean finished = f != null && f.exists() && (total <= 0 || f.length() >= total);

        if (finished || !active) {
            sub.setText(AndroidUtilities.formatFileSize(total) + (finished ? "" : " · " + LocaleController.getString(R.string.RivPaused)));
        } else {
            long[] sizes = ImageLoader.getInstance().getFileProgressSizes(fileName);
            long got = sizes != null && sizes.length > 0 ? sizes[0] : 0;
            if (sizes != null && sizes.length > 1 && sizes[1] > 0) total = sizes[1];
            int pct = total > 0 ? (int) (got * 100 / total) : 0;
            String speed = "";
            long now = System.currentTimeMillis();
            long[] prev = lastBytes.get(fileName);
            if (prev != null && now > prev[1] && got >= prev[0]) {
                long bps = (got - prev[0]) * 1000 / (now - prev[1]);
                if (bps > 0) speed = " · " + AndroidUtilities.formatFileSize(bps) + "/s";
            }
            lastBytes.put(fileName, new long[]{got, now});
            String state = loading ? "" : " · " + LocaleController.getString(R.string.RivPaused);
            sub.setText(AndroidUtilities.formatFileSize(got) + " / " + AndroidUtilities.formatFileSize(total) + " · " + pct + "%" + speed + state);
        }

        if (active && !finished && doc != null) {
            TextView pause = button(context, LocaleController.getString(loading ? R.string.RivPause : R.string.RivResume));
            pause.setOnClickListener(v -> {
                if (FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(doc);
                } else {
                    FileLoader.getInstance(currentAccount).loadFile(doc, m, FileLoader.PRIORITY_NORMAL, 0);
                }
                rebuild();
            });
            row.addView(pause, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 8, 0, 0, 0));
        }
        if (doc != null && active && !finished) {
            TextView remove = button(context, "✕");
            remove.setOnClickListener(v -> {
                ArrayList<MessageObject> one = new ArrayList<>();
                one.add(m);
                DownloadController.getInstance(currentAccount).deleteRecentFiles(one);
                rebuild();
            });
            row.addView(remove, LayoutHelper.createLinear(40, 36, 8, 0, 0, 0));
        }
        return row;
    }

    private TextView button(Context context, String text) {
        TextView b = new TextView(context);
        b.setText(text);
        b.setTextSize(13);
        b.setTypeface(AndroidUtilities.bold());
        b.setGravity(Gravity.CENTER);
        b.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        b.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bg.setCornerRadius(AndroidUtilities.dp(18));
        b.setBackground(bg);
        return b;
    }
}
