package org.telegram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivReminders;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Calendar;

/** Riv Post: reminders that repeat. They arrive in Saved Messages. */
public class RivRemindersActivity extends RivFragment {

    private LinearLayout layout;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RivReminders));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        ScrollView scroll = new ScrollView(context);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));
        frame.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        rebuild(context);
        return fragmentView;
    }

    private void rebuild(Context context) {
        layout.removeAllViews();

        TextSettingsCell add = new TextSettingsCell(context);
        add.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setText(LocaleController.getString(R.string.RivReminderAdd), true);
        add.setOnClickListener(v -> askText(context));
        layout.addView(add);

        ArrayList<RivReminders.Item> items = RivReminders.load();
        if (!items.isEmpty()) {
            HeaderCell header = new HeaderCell(context);
            header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            header.setText(LocaleController.getString(R.string.RivReminders));
            layout.addView(header);
            for (int i = 0; i < items.size(); i++) {
                RivReminders.Item it = items.get(i);
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                String when = LocaleController.formatDateTime(it.time / 1000, true) + repeatLabel(it.repeat);
                cell.setTextAndValue(it.text, when, i != items.size() - 1);
                cell.setOnClickListener(v -> {
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle(it.text);
                    b.setMessage(LocaleController.getString(R.string.RivReminderDelete));
                    b.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                        RivReminders.remove(it.id);
                        rebuild(context);
                    });
                    b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    showDialog(b.create());
                });
                layout.addView(cell);
            }
        }

        TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        info.setText(LocaleController.getString(R.string.RivRemindersInfo));
        layout.addView(info);
    }

    private String repeatLabel(int repeat) {
        switch (repeat) {
            case RivReminders.REPEAT_DAILY:
                return " · " + LocaleController.getString(R.string.RivRepeatDaily);
            case RivReminders.REPEAT_WEEKLY:
                return " · " + LocaleController.getString(R.string.RivRepeatWeekly);
            case RivReminders.REPEAT_MONTHLY:
                return " · " + LocaleController.getString(R.string.RivRepeatMonthly);
            default:
                return "";
        }
    }

    private void askText(Context context) {
        if (getParentActivity() == null) {
            return;
        }
        EditText edit = new EditText(context);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setHint(LocaleController.getString(R.string.RivReminderHint));
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivReminderAdd));
        b.setView(wrap);
        b.setPositiveButton(LocaleController.getString(R.string.Next), (d, w) -> {
            String text = edit.getText() == null ? "" : edit.getText().toString().trim();
            if (!text.isEmpty()) {
                askDate(context, text);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void askDate(Context context, String text) {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(getParentActivity(), (view, year, month, day) -> {
            TimePickerDialog tp = new TimePickerDialog(getParentActivity(), (v2, hour, minute) -> {
                Calendar cal = Calendar.getInstance();
                cal.set(year, month, day, hour, minute, 0);
                askRepeat(context, text, cal.getTimeInMillis());
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true);
            tp.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    private void askRepeat(Context context, String text, long time) {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.RivRepeat));
        CharSequence[] options = {
                LocaleController.getString(R.string.RivRepeatNever),
                LocaleController.getString(R.string.RivRepeatDaily),
                LocaleController.getString(R.string.RivRepeatWeekly),
                LocaleController.getString(R.string.RivRepeatMonthly),
        };
        b.setItems(options, (d, which) -> {
            RivReminders.add(text, time, which);
            rebuild(context);
        });
        showDialog(b.create());
    }
}
