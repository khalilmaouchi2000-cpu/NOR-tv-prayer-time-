package app.nor.prayertv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Fires at reminder / prayer time, and re-arms alarms after a reboot or time change. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Scheduler.ACTION_ALERT.equals(intent.getAction())) { Scheduler.scheduleNext(c); return; }
        long at = intent.getLongExtra("at", 0);
        boolean late = at > 0 && System.currentTimeMillis() - at > 3 * 60000L;
        // When NOR itself is on screen, the app shows the reminder and plays the Adhan by itself.
        if (!late && !MainActivity.visible) {
            Intent s = new Intent(c, AlertService.class).putExtras(intent);
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
            } catch (Exception e) {
                AlertService.playQuick(c);
            }
        }
        Scheduler.scheduleNext(c);
    }
}
