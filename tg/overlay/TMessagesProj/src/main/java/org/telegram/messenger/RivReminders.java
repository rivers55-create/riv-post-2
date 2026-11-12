package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Riv Post: reminders that can repeat (daily, weekly, monthly).
 * At the chosen time the text is sent to Saved Messages, so it rings
 * like any message and is kept there. Repeats reschedule themselves.
 */
public class RivReminders {

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_DAILY = 1;
    public static final int REPEAT_WEEKLY = 2;
    public static final int REPEAT_MONTHLY = 3;

    public static class Item {
        public long id;
        public String text;
        public long time;
        public int repeat;
    }

    public static ArrayList<Item> load() {
        ArrayList<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(RivPrefs.s("reminders", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.getLong("id");
                it.text = o.getString("text");
                it.time = o.getLong("time");
                it.repeat = o.optInt("repeat", REPEAT_NONE);
                out.add(it);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return out;
    }

    public static void save(ArrayList<Item> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("text", it.text);
                o.put("time", it.time);
                o.put("repeat", it.repeat);
                arr.put(o);
            }
            RivPrefs.set("reminders", arr.toString());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void add(String text, long time, int repeat) {
        ArrayList<Item> items = load();
        Item it = new Item();
        it.id = System.currentTimeMillis();
        it.text = text;
        it.time = time;
        it.repeat = repeat;
        items.add(it);
        save(items);
        scheduleNext();
    }

    public static void remove(long id) {
        ArrayList<Item> items = load();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                items.remove(i);
                break;
            }
        }
        save(items);
        scheduleNext();
    }

    /** Points one alarm at the earliest reminder. */
    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleNext() {
        try {
            Context c = ApplicationLoader.applicationContext;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 7310,
                    new Intent(c, RivReminderReceiver.class).setAction("org.rivers.REMINDER"),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Item next = null;
            for (Item it : load()) {
                if (next == null || it.time < next.time) {
                    next = it;
                }
            }
            am.cancel(pi);
            if (next == null) {
                return;
            }
            long at = Math.max(next.time, System.currentTimeMillis() + 2000);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Fires every due reminder: sends it to Saved Messages and reschedules repeats. */
    public static void fire() {
        try {
            long now = System.currentTimeMillis() + 1000;
            ArrayList<Item> items = load();
            boolean changed = false;
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (it.time > now) {
                    continue;
                }
                sendToSaved(it.text);
                if (it.repeat == REPEAT_NONE) {
                    items.remove(i);
                    i--;
                } else {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(it.time);
                    while (cal.getTimeInMillis() <= now) {
                        if (it.repeat == REPEAT_DAILY) {
                            cal.add(Calendar.DAY_OF_YEAR, 1);
                        } else if (it.repeat == REPEAT_WEEKLY) {
                            cal.add(Calendar.WEEK_OF_YEAR, 1);
                        } else {
                            cal.add(Calendar.MONTH, 1);
                        }
                    }
                    it.time = cal.getTimeInMillis();
                }
                changed = true;
            }
            if (changed) {
                save(items);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            scheduleNext();
        }
    }

    private static void sendToSaved(String text) {
        try {
            int account = UserConfig.selectedAccount;
            UserConfig config = UserConfig.getInstance(account);
            if (!config.isClientActivated()) {
                return;
            }
            long self = config.getClientUserId();
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of("⏰ " + text, self);
            SendMessagesHelper.getInstance(account).sendMessage(params);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static class RivReminderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ApplicationLoader.postInitApplication();
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                scheduleNext();
            } else {
                fire();
            }
        }
    }
}
