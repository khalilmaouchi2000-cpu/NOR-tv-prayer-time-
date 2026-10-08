package app.nor.prayertv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** NOR Prayer Times TV: full-screen wrapper around the offline HTML app. */
public class MainActivity extends Activity {
    private WebView web;
    static volatile boolean visible = false;

    /** Called from the web app with the next 7 days of prayer times. */
    public class Bridge {
        @JavascriptInterface public void sync(String json) { Scheduler.save(getApplicationContext(), json); }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // TV never sleeps
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(10, 12, 40));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                   // saves your settings
        s.setMediaPlaybackRequiresUserGesture(false);   // Adhan can play by itself
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "NORNative");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        hideBars();
        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
        Scheduler.scheduleNext(this);
        askPermissions();
    }

    /** Notifications (Android 13+) and "Display over other apps" for the reminder banner. */
    private void askPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 5); } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) return;
        if (Scheduler.prefs(this).getBoolean("askedOverlay", false)) return;
        Scheduler.prefs(this).edit().putBoolean("askedOverlay", true).apply();
        new AlertDialog.Builder(this)
            .setTitle("Prayer reminders over other apps")
            .setMessage("To see the NOR reminder and the Adhan banner while you watch other apps, allow NOR to \"Display over other apps\" on the next screen.")
            .setPositiveButton("Allow", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { openOverlaySettings(); } })
            .setNegativeButton("Later", null)
            .show();
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
            catch (Exception e2) {
                Toast.makeText(this, "Open TV Settings > Apps > Special app access > Display over other apps > NOR", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    /** Remote BACK: go back inside the app; on the Home screen, close the app. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){if(!document.body||document.body.dataset.view==='home')return 'exit';"
            + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',keyCode:27,bubbles:true}));return 'ok';})()",
            value -> { if (value != null && value.contains("exit")) finish(); });
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onResume() { super.onResume(); visible = true; web.onResume(); web.evaluateJavascript("window.NOR_BG=false", null); hideBars(); }
    @Override protected void onPause() { visible = false; web.evaluateJavascript("window.NOR_BG=true;try{Adhan.playing&&Adhan.stop()}catch(e){}", null); super.onPause(); }
    @Override protected void onStop() { visible = false; super.onStop(); }
}
