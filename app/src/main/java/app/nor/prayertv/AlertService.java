package app.nor.prayertv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shows the NOR reminder / prayer banner over other apps and plays the chime or the Adhan. */
public class AlertService extends Service {
    static final String CHANNEL = "nor_alerts";
    static final String ACTION_STOP = "app.nor.prayertv.STOP";
    private MediaPlayer player;
    private View overlay;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int change) { }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) { stopAll(); return START_NOT_STICKY; }
        boolean prayer = "prayer".equals(intent.getStringExtra("type"));
        String label = intent.getStringExtra("label"); if (label == null) label = "Prayer";
        String time = intent.getStringExtra("time"); if (time == null) time = "";
        int minutes = intent.getIntExtra("minutes", 10);
        boolean adhanOn = intent.getBooleanExtra("adhanOn", true);
        String reciter = intent.getStringExtra("reciter");

        String title = prayer ? "It's time for " + label + " prayer" : label + " in " + minutes + " minutes";
        String sub = prayer ? (time + (adhanOn ? "  \u00b7  Adhan playing" : "")) : "Prayer at " + time + "  \u00b7  Get ready for wudu";

        Notification n = buildNotification(title, sub);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(42, n);
        } catch (Exception e) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(42, n);
        }

        stopSound(); removeOverlay(); ui.removeCallbacksAndMessages(null);
        int res = R.raw.chime;
        if (prayer && adhanOn) {
            if ("nufais".equals(reciter)) res = R.raw.adhan_nufais;
            else if ("ozcan".equals(reciter)) res = R.raw.adhan_ozcan;
            else res = R.raw.adhan_mishary;
        }
        boolean longSound = res != R.raw.chime;
        play(res, longSound);
        showOverlay(title, sub, longSound);
        if (!longSound) ui.postDelayed(new Runnable() { @Override public void run() { stopAll(); } }, 15000);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String title, String sub) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Prayer alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | Scheduler.immutable());
        PendingIntent stopPi = PendingIntent.getService(this, 2, new Intent(this, AlertService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | Scheduler.immutable());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
         .setContentTitle(title).setContentText(sub)
         .setContentIntent(openPi).setAutoCancel(true)
         .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= 21) b.setCategory(Notification.CATEGORY_ALARM);
        return b.build();
    }

    @SuppressWarnings("deprecation")
    private void play(int res, boolean longSound) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC,
                longSound ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            player = MediaPlayer.create(this, res);
            if (player == null) return;
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    if (longSound) stopAll();
                }
            });
            player.start();
        } catch (Exception ignored) { }
    }

    /** Fallback when the system refuses to start the service: just play the chime. */
    static void playQuick(Context c) {
        try {
            final MediaPlayer mp = MediaPlayer.create(c, R.raw.chime);
            if (mp == null) return;
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer p) { p.release(); }
            });
            mp.start();
        } catch (Exception ignored) { }
    }

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private void showOverlay(String title, String sub, final boolean prayer) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        try {
            final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            LinearLayout card = new LinearLayout(this) {
                @Override public boolean dispatchKeyEvent(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) { stopAll(); return true; }
                    return super.dispatchKeyEvent(e);
                }
            };
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(26), dp(20), dp(26), dp(20));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { Color.argb(245, 44, 22, 92), Color.argb(245, 16, 14, 52) });
            bg.setCornerRadius(dp(22)); bg.setStroke(dp(2), Color.argb(200, 160, 123, 255));
            card.setBackground(bg);

            TextView brand = new TextView(this);
            brand.setText(prayer ? "\uD83D\uDD4C  NOR  \u00b7  ADHAN" : "\uD83D\uDD14  NOR  \u00b7  PRAYER REMINDER");
            brand.setTextColor(Color.rgb(201, 184, 255)); brand.setTextSize(13); brand.setLetterSpacing(0.12f);
            TextView t = new TextView(this);
            t.setText(title); t.setTextColor(Color.WHITE); t.setTextSize(prayer ? 30 : 24);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); t.setPadding(0, dp(6), 0, dp(2));
            TextView s = new TextView(this);
            s.setText(sub); s.setTextColor(Color.rgb(214, 205, 245)); s.setTextSize(16);
            card.addView(brand); card.addView(t); card.addView(s);

            WindowManager.LayoutParams lp;
            int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                                   : WindowManager.LayoutParams.TYPE_PHONE;
            if (prayer) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, dp(14), 0, 0);
                Button stop = makeButton("Stop Adhan", true);
                stop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopAll(); } });
                Button open = makeButton("Open NOR", false);
                open.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                    stopAll();
                    startActivity(new Intent(AlertService.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } });
                row.addView(stop); LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(12), 1); row.addView(new View(this), gap); row.addView(open);
                card.addView(row);
                lp = new WindowManager.LayoutParams(dp(560), WindowManager.LayoutParams.WRAP_CONTENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.CENTER;
                stop.requestFocus();
            } else {
                lp = new WindowManager.LayoutParams(dp(460), WindowManager.LayoutParams.WRAP_CONTENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.END; lp.x = dp(32); lp.y = dp(32);
            }
            wm.addView(card, lp);
            overlay = card;
            if (prayer) card.getChildAt(3).requestFocus();
        } catch (Exception ignored) { overlay = null; }
    }

    private Button makeButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(Color.WHITE);
        b.setFocusable(true); b.setFocusableInTouchMode(true);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(primary ? Color.rgb(139, 92, 246) : Color.argb(60, 255, 255, 255));
        d.setStroke(dp(2), Color.argb(primary ? 255 : 90, 201, 184, 255));
        b.setBackground(d);
        b.setPadding(dp(22), dp(10), dp(22), dp(10));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean has) {
            v.setScaleX(has ? 1.08f : 1f); v.setScaleY(has ? 1.08f : 1f);
        } });
        return b;
    }

    private void removeOverlay() {
        if (overlay == null) return;
        try { ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(overlay); } catch (Exception ignored) { }
        overlay = null;
    }

    @SuppressWarnings("deprecation")
    private void stopSound() {
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.abandonAudioFocus(focus);
    }

    private void stopAll() {
        ui.removeCallbacksAndMessages(null);
        stopSound(); removeOverlay();
        try { stopForeground(true); } catch (Exception ignored) { }
        stopSelf();
    }

    @Override public void onDestroy() { ui.removeCallbacksAndMessages(null); stopSound(); removeOverlay(); super.onDestroy(); }
}
