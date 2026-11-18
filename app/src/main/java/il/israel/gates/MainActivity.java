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
    private GeolocationPermissions.Callback geoCb; // מחכים לתשובת המשתמש להרשאת המיקום (18/11)
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        take(getIntent());
        show();
        Updater.check(this, true); // פעם ביום: חלון רק אם יש גרסה חדשה
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (take(i)) show();
    }

    /** קישור שנפתח מבחוץ: קישור אורח (מלא או קצר) → נשמר; כל קישור אחר של השרת → לדפדפן. */
    private boolean take(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null) return false;
        if (Prefs.valid(d.toString())) { Prefs.setUrl(this, d.toString()); return true; }
        if (Prefs.isShort(d.toString())) { useLink(d.toString()); return false; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, d).addCategory(Intent.CATEGORY_BROWSABLE).setPackage("com.android.chrome")); }
        catch (Exception e) { Toast.makeText(this, "זה לא קישור של שערים. פתח אותו בדפדפן.", Toast.LENGTH_LONG).show(); }
        return false;
    }

    /** קישור מלא או קצר (מוואטסאפ / הדבקה): קצר → פונים לשרת ומקבלים את המלא. */
    private void useLink(String u) {
        if (Prefs.valid(u)) { Prefs.setUrl(this, u); show(); return; }
        if (!Prefs.isShort(u)) { Toast.makeText(this, "זה לא קישור תקין. העתק את כל הקישור שקיבלת.", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, "בודק את הקישור…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String full = Prefs.resolveShort(u);
            runOnUiThread(() -> {
                if (full == null) { Toast.makeText(this, "הקישור פג או לא תקין. בקש קישור חדש.", Toast.LENGTH_LONG).show(); return; }
                Prefs.setUrl(this, full);
                show();
            });
        }).start();
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
        getWindow().setStatusBarColor(Color.parseColor("#1E3A8A"));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        root.addView(Ui.hero(this, "🚪 שערים", "השערים נפתחים לבד כשמתקרבים"));
        LinearLayout k = Ui.card(this, root, "ברוך הבא");
        Ui.note(this, k, "כדי להתחיל צריך קישור אישי. מקבלים אותו בוואטסאפ ממי שנותן לך גישה לשערים.");
        Ui.step(this, k, 1, false, "לוחצים על הקישור בוואטסאפ", "ובוחרים לפתוח עם \"שערים\"", null, null);
        Ui.step(this, k, 2, false, "או מדביקים אותו כאן", null, null, null);
        EditText e = new EditText(this);
        e.setHint("https://isrnb.duckdns.org/g/…");
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        e.setBackground(Ui.round(Ui.SOFT, Ui.dp(this, 12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        k.addView(e, lp);
        Ui.button(this, k, "📋 הדבק מהלוח", false, v -> {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) e.setText(String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(this)).trim());
        });
        Ui.button(this, k, "המשך", true, v -> {
            useLink(e.getText().toString().trim());
        });
        setContentView(sv);
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
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { cb.invoke(origin, true, false); return; }
                geoCb = cb; geoOrigin = origin;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
            }
        });
        web.setBackgroundColor(Color.parseColor("#EEF2F7"));
        getWindow().setStatusBarColor(Color.parseColor("#1E3A8A"));
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
        @JavascriptInterface public void checkUpdate() { runOnUiThread(() -> Updater.check(MainActivity.this, false)); }
        @JavascriptInterface public String version() { return Updater.currentName(MainActivity.this); }
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

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 7 && geoCb != null) {
            geoCb.invoke(geoOrigin, res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED, false);
            geoCb = null;
        }
    }
}
