package il.israel.gates;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * המסך הראשי: דף השערים מהשרת (מפה חיה, שערים, הגדרות אישיות, כניסה עם גוגל) בתוך WebView.
 * בפעם הראשונה – מדביקים את הקישור האישי (או פותחים אותו מוואטסאפ ובוחרים "שערים").
 */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        take(getIntent());
        show();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (take(i)) show();
    }

    /** קישור שנפתח מבחוץ: קישור אורח → נשמר; כל קישור אחר של השרת → לדפדפן. */
    private boolean take(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null) return false;
        if (Prefs.valid(d.toString())) { Prefs.setUrl(this, d.toString()); return true; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, d).addCategory(Intent.CATEGORY_BROWSABLE).setPackage("com.android.chrome")); }
        catch (Exception e) { Toast.makeText(this, "זה לא קישור של שערים. פתח אותו בדפדפן.", Toast.LENGTH_LONG).show(); }
        return false;
    }

    private void show() {
        if (!Prefs.valid(Prefs.url(this))) { onboarding(); return; }
        if (getSharedPreferences("gates", MODE_PRIVATE).getBoolean("firstSettings", true)) {
            getSharedPreferences("gates", MODE_PRIVATE).edit().putBoolean("firstSettings", false).apply();
            startActivity(new Intent(this, SettingsActivity.class));
        }
        openWeb();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void onboarding() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(24), dp(48), dp(24), dp(24));
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView t = new TextView(this);
        t.setText("🚪 שערים");
        t.setTextSize(28);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText("הדבק כאן את הקישור האישי שקיבלת בוואטסאפ.\nאפשר גם פשוט ללחוץ על הקישור בוואטסאפ ולבחור \"שערים\".");
        s.setTextSize(16);
        s.setPadding(0, dp(12), 0, dp(16));
        EditText e = new EditText(this);
        e.setHint("https://…");
        e.setSingleLine(true);
        Button go = new Button(this);
        go.setText("המשך");
        go.setOnClickListener(v -> {
            String u = e.getText().toString().trim();
            if (!Prefs.valid(u)) { Toast.makeText(this, "זה לא קישור תקין. העתק את כל הקישור שקיבלת.", Toast.LENGTH_LONG).show(); return; }
            Prefs.setUrl(this, u);
            show();
        });
        l.addView(t); l.addView(s); l.addView(e); l.addView(go);
        setContentView(l);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void openWeb() {
        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setUserAgentString(ws.getUserAgentString() + " GatesApp/1"); // הדף מזהה שזו האפליקציה (מסתיר שידור מהדפדפן, מציג "הגדרות")
        web.addJavascriptInterface(new Bridge(), "GatesApp");
        final String host = Uri.parse(Prefs.url(this)).getHost();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                // באפליקציה נשארים רק דפי השערים; כניסה עם גוגל (גוגל חוסמת בתוך אפליקציות), וואטסאפ, OwnTracks וכו' – בחוץ
                boolean inside = "https".equals(u.getScheme()) && host != null && host.equals(u.getHost()) && !u.getPath().endsWith("/google");
                if (inside) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { Toast.makeText(MainActivity.this, "אין אפליקציה שפותחת את זה", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
                cb.invoke(origin, true, false);
            }
        });
        web.setBackgroundColor(Color.parseColor("#EEF2F7"));
        setContentView(web);
        web.loadUrl(Prefs.url(this));
    }

    /** פונקציות שהדף יכול לקרוא: window.GatesApp.openSettings() וכו'. */
    class Bridge {
        @JavascriptInterface public void openSettings() { runOnUiThread(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class))); }
        @JavascriptInterface public boolean broadcasting() { return LocService.running; }
        @JavascriptInterface public String state() { return LocService.state; }
        @JavascriptInterface public void start(int minutes) { runOnUiThread(() -> LocService.start(MainActivity.this, "ידני", Math.max(5, Math.min(minutes, 240)))); }
        @JavascriptInterface public void stop() { runOnUiThread(() -> LocService.stop(MainActivity.this)); }
        @JavascriptInterface public String cars() { return String.valueOf(Prefs.cars(MainActivity.this).size()); }
        @JavascriptInterface public boolean auto() { return Prefs.auto(MainActivity.this); }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.appRefresh && appRefresh()", null);
    }
}
