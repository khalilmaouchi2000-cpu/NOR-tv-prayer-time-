package app.nor.prayertv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps exactly one alarm set: the next reminder (X min before) or the next prayer time. */
public final class Scheduler {
    static final String PREFS = "nor";
    static final String ACTION_ALERT = "app.nor.prayertv.ALERT";

    private Scheduler() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static void save(Context c, String json) {
        prefs(c).edit().putString("schedule", json).apply();
        scheduleNext(c);
    }

    static int immutable() { return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0; }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent base = new Intent(c, AlarmReceiver.class).setAction(ACTION_ALERT);
        PendingIntent old = PendingIntent.getBroadcast(c, 7, base, PendingIntent.FLAG_UPDATE_CURRENT | immutable());
        am.cancel(old);
        try {
            JSONObject s = new JSONObject(prefs(c).getString("schedule", "{}"));
            JSONArray ev = s.optJSONArray("events");
            if (ev == null) return;
            int remind = s.optInt("reminder", 10);
            long now = System.currentTimeMillis();
            long bestAt = Long.MAX_VALUE; String bestType = null; JSONObject best = null;
            for (int i = 0; i < ev.length(); i++) {
                JSONObject e = ev.getJSONObject(i);
                long t = e.getLong("t");
                if (remind > 0) {
                    long r = t - remind * 60000L;
                    if (r > now + 1000 && r < bestAt) { bestAt = r; bestType = "remind"; best = e; }
                }
                if (t > now + 1000 && t < bestAt) { bestAt = t; bestType = "prayer"; best = e; }
            }
            if (best == null) return;
            Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_ALERT)
                .putExtra("type", bestType)
                .putExtra("label", best.optString("l", "Prayer"))
                .putExtra("time", best.optString("h", ""))
                .putExtra("at", bestAt)
                .putExtra("minutes", remind)
                .putExtra("adhanOn", s.optBoolean("adhanOn", true))
                .putExtra("reciter", s.optString("reciter", "mishary"))
                .putExtra("city", s.optString("city", ""));
            PendingIntent pi = PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | immutable());
            setAlarm(am, bestAt, pi);
        } catch (Exception ignored) { }
    }

    private static void setAlarm(AlarmManager am, long at, PendingIntent pi) {
        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact && Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else if (exact) am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            else if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.set(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
