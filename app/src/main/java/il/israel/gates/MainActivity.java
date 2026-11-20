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
    private long unlockedAt = 0;
    private boolean authing = false;
    private GeolocationPermissions.Callback geoCb; // מחכים לתשובת המשתמש להרשאת המיקום (18/11)
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.theme(this);
        LocalEngine.init(this);
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
        if (Prefs.isDash(d.toString())) { Prefs.setAdmin(this, d.toString()); Prefs.setAdminView(this, true); Toast.makeText(this, "🛡️ מצב מנהל הופעל", Toast.LENGTH_SHORT).show(); return true; }
        if (Prefs.isShort(d.toString())) { useLink(d.toString()); return false; }
        if ("/pair".equals(d.getPath()) && d.getQueryParameter("c") != null) { approvePair(d.getQueryParameter("c")); return false; } // 🚗 ברקוד ממסך הרכב
        try { startActivity(new Intent(Intent.ACTION_VIEW, d).addCategory(Intent.CATEGORY_BROWSABLE).setPackage("com.android.chrome")); }
        catch (Exception e) { Toast.makeText(this, "זה לא קישור של שערים. פתח אותו בדפדפן.", Toast.LENGTH_LONG).show(); }
        return false;
    }

    /** קישור מלא או קצר (מוואטסאפ / הדבקה): קצר → פונים לשרת ומקבלים את המלא. */
    private void useLink(String u) {
        if (Prefs.valid(u)) { Prefs.setUrl(this, u); show(); return; }
        if (Prefs.isDash(u)) { Prefs.setAdmin(this, u); Prefs.setAdminView(this, true); show(); return; }
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

    /** מה פותחים: הדשבורד (מצב מנהל) או דף השערים האישי */
    private String startUrl() {
        boolean g = Prefs.valid(Prefs.url(this)), a = Prefs.isDash(Prefs.admin(this));
        if (a && (Prefs.adminView(this) || !g)) return Prefs.admin(this);
        return g ? Prefs.url(this) : null;
    }

    private void show() {
        if (startUrl() == null) { onboarding(); return; }
        if (Prefs.valid(Prefs.url(this)) && getSharedPreferences("gates", MODE_PRIVATE).getBoolean("firstSettings", true)) {
            getSharedPreferences("gates", MODE_PRIVATE).edit().putBoolean("firstSettings", false).apply();
            startActivity(new Intent(this, SettingsActivity.class));
        }
        openWeb();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void onboarding() { if (Prefs.isCarUnit() && !manualLogin) pairScreen(); else onboardingManual(); }
    private boolean manualLogin = false;

    // ---------- 🚗 כניסה במסך הרכב: ברקוד שסורקים עם הטלפון (19/11) ----------
    private String pairId, pairSecret;

    private void pairScreen() {
        getWindow().setStatusBarColor(Color.parseColor("#1E3A8A"));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        root.addView(Ui.hero(this, "🚗 חיבור מסך הרכב", "סורקים את הברקוד עם הטלפון – וזהו"));
        LinearLayout k = Ui.card(this, root, "📷 סרוק עם הטלפון");
        android.widget.ImageView img = new android.widget.ImageView(this);
        img.setAdjustViewBounds(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(300), dp(300));
        ip.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        k.addView(img, ip);
        TextView st = new TextView(this);
        st.setTextSize(17); st.setTextColor(Ui.INK); st.setGravity(android.view.Gravity.CENTER); st.setPadding(0, dp(10), 0, dp(6));
        k.addView(st);
        Ui.step(this, k, 1, false, "בטלפון: אפליקציית השערים ← ⚙️ הגדרות ← \"חיבור מסך רכב\"", "\"סרוק את הברקוד\" או \"הקלד את הקוד\". אפשר גם עם המצלמה הרגילה", null, null);
        Ui.step(this, k, 2, false, "לוחצים \"חבר\" בטלפון", "הרכב מתחבר לבד תוך כמה שניות", null, null);
        Ui.button(this, k, "🔄 ברקוד חדש", false, v -> newPair(img, st));
        LinearLayout k2 = Ui.card(this, root, "כניסה אחרת");
        Ui.button(this, k2, "📱 עם מספר טלפון או קישור", false, v -> { manualLogin = true; pairId = null; onboardingManual(); });
        setContentView(sv);
        newPair(img, st);
    }

    private void newPair(android.widget.ImageView img, TextView st) {
        st.setText("⏳ יוצר ברקוד…");
        pairId = null;
        new Thread(() -> {
            String r = http("https://isrnb.duckdns.org/pair?step=new", "");
            runOnUiThread(() -> {
                try {
                    org.json.JSONObject j = new org.json.JSONObject(r);
                    if (!j.optBoolean("ok")) { st.setText("⚠️ " + j.optString("r", "נסה שוב")); return; }
                    org.json.JSONArray q = j.getJSONArray("qr");
                    int n = q.length(), pad = 4, sz = n + pad * 2;
                    android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888);
                    bm.eraseColor(Color.WHITE);
                    for (int y = 0; y < n; y++) { String row = q.getString(y); for (int x = 0; x < n; x++) if (row.charAt(x) == '1') bm.setPixel(x + pad, y + pad, Color.BLACK); }
                    android.graphics.Bitmap big = android.graphics.Bitmap.createScaledBitmap(bm, sz * 10, sz * 10, false);
                    img.setImageBitmap(big);
                    pairId = j.getString("id"); pairSecret = j.getString("secret");
                    String cd = j.optString("code", "");
                    st.setText((cd.length() == 6 ? "או הקלד בטלפון את הקוד:  " + cd.substring(0, 3) + " " + cd.substring(3) + "\n" : "") + "מחכה… (תקף ל-5 דקות)");
                    pollPair(pairId, img, st);
                } catch (Exception e) { st.setText("⚠️ אין חיבור לאינטרנט. לחץ \"ברקוד חדש\""); }
            });
        }).start();
    }

    private void pollPair(String id, android.widget.ImageView img, TextView st) {
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (!id.equals(pairId)) return; // ברקוד חדש / יצאנו מהמסך
            new Thread(() -> {
                String r = http("https://isrnb.duckdns.org/pair?step=poll", "id=" + id + "&s=" + pairSecret);
                runOnUiThread(() -> {
                    if (!id.equals(pairId)) return;
                    try {
                        org.json.JSONObject j = new org.json.JSONObject(r);
                        String s = j.optString("state");
                        if ("ok".equals(s) && Prefs.valid(j.optString("link"))) {
                            pairId = null;
                            Prefs.setUrl(this, j.getString("link"));
                            Toast.makeText(this, "✅ מסך הרכב מחובר", Toast.LENGTH_LONG).show();
                            show();
                            CarCheck.run(this, "אחרי חיבור בברקוד");
                            return;
                        }
                        if ("expired".equals(s)) { st.setText("⌛ הברקוד פג. לחץ \"ברקוד חדש\""); img.setAlpha(0.25f); pairId = null; return; }
                    } catch (Exception e) { /* תקלה רגעית – ממשיכים */ }
                    pollPair(id, img, st);
                });
            }).start();
        }, 2000);
    }

    /** בטלפון: נסרק ברקוד ממסך הרכב (מצלמה רגילה) → אישור */
    private void approvePair(String c) { Pair.approve(this, c, false); }

    static String http(String url, String body) {
        try {
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            h.setRequestMethod("POST"); h.setDoOutput(true); h.setConnectTimeout(12000); h.setReadTimeout(15000);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream o = h.getOutputStream()) { o.write(b); }
            java.io.InputStream is = h.getResponseCode() < 400 ? h.getInputStream() : h.getErrorStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (Exception e) { return ""; }
    }

    private void onboardingManual() {
        getWindow().setStatusBarColor(Color.parseColor("#1E3A8A"));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        root.addView(Ui.hero(this, "🚪 שערים", "השערים נפתחים לבד כשמתקרבים"));
        LinearLayout k0 = Ui.card(this, root, "📱 כניסה עם מספר טלפון");
        Ui.note(this, k0, "הוזמנת עם מספר הטלפון שלך? מקבלים קוד בוואטסאפ ונכנסים – בלי קישור.");
        Ui.button(this, k0, "📱 כניסה עם מספר טלפון", true, v -> openWeb("https://isrnb.duckdns.org/join"));
        LinearLayout k = Ui.card(this, root, "או עם קישור");
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
    private void showOffline() {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        root.addView(Ui.hero(this, "🚪 שערים", "אין חיבור לאינטרנט"));
        LinearLayout k = Ui.card(this, root, "⚠️ אין חיבור");
        Ui.note(this, k, "הדף לא נטען. בדוק קליטה או אינטרנט ונסה שוב. אם הרכב מחובר, השידור ברקע ממשיך לנסות לבד.");
        Ui.button(this, k, "🔄 נסה שוב", true, v -> openWeb());
        Ui.button(this, k, "⚙️ הגדרות: רכבים והרשאות", false, v -> startActivity(new Intent(this, SettingsActivity.class)));
        setContentView(sv);
    }

    private void openWeb() { openWeb(startUrl()); }

    private void openWeb(String start) {
        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setUserAgentString(ws.getUserAgentString() + " GatesApp/1"); // הדף מזהה שזו האפליקציה (מסתיר שידור מהדפדפן, מציג "הגדרות")
        web.addJavascriptInterface(new Bridge(), "GatesApp");
        final String host = Uri.parse(start).getHost();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) showOffline(); // בלי קליטה: מסך בעברית, בלי להציג את הקישור הסודי (18/11)
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                // באפליקציה נשארים רק דפי השערים; כניסה עם גוגל (גוגל חוסמת בתוך אפליקציות), וואטסאפ, OwnTracks וכו' – בחוץ
                boolean inside = "https".equals(u.getScheme()) && host != null && host.equals(u.getHost()) && !u.getPath().endsWith("/google");
                if (inside) return false;
                if ("mail.google.com".equals(u.getHost())) { // 📧 מייל: אפליקציית Gmail (המייל עצמו, ואם לא – פתיחת Gmail), אחרת הדפדפן (18/11)
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u).setPackage("com.google.android.gm")); return true; } catch (Exception e) { /* Gmail לא מקבל את הקישור */ }
                    Intent g = getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
                    if (g != null) { startActivity(g); return true; }
                }
                if ("intent".equals(u.getScheme())) { // קישור לאפליקציה (Gmail / וואטסאפ): פותחים אותה; אין – הקישור הרגיל (18/11)
                    String fb = null;
                    try {
                        Intent in = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                        fb = in.getStringExtra("browser_fallback_url");
                        if ("com.google.android.gm".equals(in.getPackage()) && fb != null) in = new Intent(Intent.ACTION_VIEW, Uri.parse(fb)).setPackage("com.google.android.gm"); // Gmail: הקישור המלא – פותח את המייל עצמו
                        in.addCategory(Intent.CATEGORY_BROWSABLE); in.setComponent(null); in.setSelector(null);
                        startActivity(in); return true;
                    } catch (Exception e) {
                        if (fb != null) try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fb))); return true; } catch (Exception e2) { /* אין */ }
                    }
                    Toast.makeText(MainActivity.this, "אין אפליקציה שפותחת את זה", Toast.LENGTH_SHORT).show();
                    return true;
                }
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
        web.loadUrl(start);
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
        @JavascriptInterface public String lastFix() { long a = (System.currentTimeMillis() - LocService.lastAt) / 1000; return LocService.lastAt == 0 || !LocService.running ? "" : "{\"lat\":" + LocService.lastLat + ",\"lon\":" + LocService.lastLon + ",\"age\":" + a + "}"; }
        @JavascriptInterface public boolean carScreen() { return Prefs.carScreen(MainActivity.this); }
        @JavascriptInterface public void pairScan() { runOnUiThread(() -> Pair.scan(MainActivity.this)); }
        @JavascriptInterface public void checkUpdate() { runOnUiThread(() -> Updater.check(MainActivity.this, false)); }
        @JavascriptInterface public String version() { return Updater.currentName(MainActivity.this); }
        /** דף הכניסה עם מספר טלפון (/join) מחזיר את הקישור האישי – נשמר כמו קישור שנלחץ בוואטסאפ (18/11) */
        @JavascriptInterface public void saveLink(String u) { if (Prefs.valid(u)) runOnUiThread(() -> { Prefs.setUrl(MainActivity.this, u); show(); }); }
        /** דף השערים: המשתמש שינה הגדרה (מיקום / מרחק / שערים) – הטלפון יעדכן את הנתונים השמורים בנסיעה הבאה */
        @JavascriptInterface public void gatesChanged() { getSharedPreferences("gates", MODE_PRIVATE).edit().putBoolean("stDirty", true).apply(); }
        /** 📧 קישור מייל מהדף: ישר לאפליקציית Gmail (בלי לעבור דרך הדפדפן הפנימי) – 18/11 */
        @JavascriptInterface public void openMail(String url) {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.gm")); return; } catch (Exception e) { /* Gmail לא מקבל את הקישור */ }
                Intent g = getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
                if (g != null) { startActivity(g); return; }
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { Toast.makeText(MainActivity.this, "אין אפליקציה שפותחת את זה", Toast.LENGTH_SHORT).show(); }
            });
        }
        /** 🔔 עדכון לאפליקציה – לפעמון בדשבורד: peek = בדיקה ברקע; updateAvail = "עדכון N" אם יש חדש, אחרת ריק; installUpdate = הורדה והתקנה */
        /** 📤 "שלח עכשיו לבדיקה" בדשבורד: היומן שעוד לא נשלח – לשרת מיד */
        @JavascriptInterface public void uploadLog() { LocalEngine.log("📤 נשלח לבדיקה מהדשבורד"); final android.content.Context x = getApplicationContext(); new Thread(() -> LocalEngine.upload(x)).start(); }
        /** מצב קצר לשורת המצב בראש הדף: האם משדר, למה לא, ומה קרה אחרון */
        @JavascriptInterface public String lastLog() { synchronized (LocalEngine.LOG) { return LocalEngine.LOG.isEmpty() ? "" : LocalEngine.LOG.get(0); } }
        @JavascriptInterface public boolean local() { return Prefs.local(MainActivity.this); }
        @JavascriptInterface public void peekUpdate() { Updater.peek(); }
        @JavascriptInterface public String updateAvail() { int k = Updater.known; return k > Updater.current(MainActivity.this) ? "עדכון " + Math.max(1, k - Updater.BASE) : ""; }
        @JavascriptInterface public void installUpdate() { runOnUiThread(() -> Updater.install(MainActivity.this)); }
        @JavascriptInterface public boolean lockOpen() { return Prefs.lock(MainActivity.this) == 1; }
        @JavascriptInterface public void auth(String id) {
            final String i = String.valueOf(id).replaceAll("[^A-Za-z0-9]", "");
            runOnUiThread(() -> Lock.auth(MainActivity.this, "פתיחת שער",
                    () -> { if (web != null) web.evaluateJavascript("window.__gaAuth && __gaAuth('" + i + "', true)", null); },
                    () -> { if (web != null) web.evaluateJavascript("window.__gaAuth && __gaAuth('" + i + "', false)", null); }));
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CarCheck.run(this, "בפתיחת האפליקציה"); // רכב מחובר אבל לא משדר – מתחילים
        // נעילה בכל כניסה (אם נבחרה): אחרי דקה מחוץ לאפליקציה – שוב אימות
        if (web != null && Prefs.lock(this) == 2 && !authing && System.currentTimeMillis() - unlockedAt > 60000) {
            authing = true; web.setVisibility(View.INVISIBLE);
            Lock.auth(this, "כניסה לשערים",
                    () -> { authing = false; unlockedAt = System.currentTimeMillis(); web.setVisibility(View.VISIBLE); },
                    () -> { authing = false; finish(); });
        }
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
