package il.israel.bydprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    // כתובת הקליטה בשרת "העוזר של ישראל". שליחה רק אחרי קוד שמגיע לבעלים בוואטסאפ
    static final String SERVER = "https://isrnb.duckdns.org/byd";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status, report;
    private Button permBtn, runBtn, sendBtn, upd;
    private String last;
    private boolean newer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(16, 22, 34));
        root.setPadding(dp(24), dp(20), dp(24), dp(12));

        TextView title = new TextView(this);
        title.setText("🔍 בדיקת רכב");
        title.setTextSize(30); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("בודקת מה המערכת ברכב חושפת: מצלמות, חיישנים, שירותים, חבילות והרשאות. רק קוראת – לא משנה כלום ברכב.");
        sub.setTextSize(17); sub.setTextColor(Color.rgb(170, 185, 210)); sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        permBtn = btn("🔑 אשר הרשאות", Color.rgb(217, 119, 6));
        runBtn = btn("▶ הרץ בדיקה", Color.rgb(37, 99, 235));
        sendBtn = btn("📤 שלח לשרת", Color.rgb(22, 163, 74));
        sendBtn.setEnabled(false); sendBtn.setAlpha(0.4f);
        row.addView(permBtn, new LinearLayout.LayoutParams(0, dp(72), 1));
        View gap0 = new View(this); row.addView(gap0, new LinearLayout.LayoutParams(dp(16), 1));
        row.addView(runBtn, new LinearLayout.LayoutParams(0, dp(72), 1));
        View gap = new View(this); row.addView(gap, new LinearLayout.LayoutParams(dp(16), 1));
        row.addView(sendBtn, new LinearLayout.LayoutParams(0, dp(72), 1));
        root.addView(row);

        status = new TextView(this);
        status.setTextSize(18); status.setTextColor(Color.rgb(250, 204, 21)); status.setPadding(0, dp(12), 0, dp(8));
        status.setText("1. לחץ \"אשר הרשאות\" ואשר את כל החלונות.  2. \"הרץ בדיקה\".  3. \"שלח לשרת\".");
        root.addView(status);

        ScrollView sv = new ScrollView(this);
        report = new TextView(this);
        report.setTextSize(13); report.setTextColor(Color.rgb(220, 228, 240)); report.setTypeface(Typeface.MONOSPACE);
        report.setTextIsSelectable(true);
        report.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        sv.addView(report);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        upd = btn("🔄 בדוק עדכונים", Color.rgb(71, 85, 105));
        upd.setOnClickListener(v -> {
            if (newer) { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://isrnb.duckdns.org/car"))); return; }
            upd.setText("⏳ בודק…"); checkUpdate(true);
        });
        root.addView(upd, 2, new LinearLayout.LayoutParams(-1, dp(64)));

        setContentView(root);
        checkUpdate(false);
        runBtn.setOnClickListener(v -> start());
        permBtn.setOnClickListener(v -> askPerms());
        sendBtn.setOnClickListener(v -> askCode());

    }

    // כל ההרשאות שהאפליקציה מבקשת ועוד לא אושרו (כולל BYD וקבצים)
    private void askPerms() {
        try {
            String[] all = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            java.util.List<String> need = new java.util.ArrayList<>();
            for (String s : all) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED && !s.equals(Manifest.permission.INTERNET)) need.add(s);
            if (need.isEmpty()) { status.setText("✅ כל ההרשאות כבר מאושרות. לחץ \"הרץ בדיקה\"."); return; }
            requestPermissions(need.toArray(new String[0]), 2);
        } catch (Throwable e) { status.setText("⚠️ " + e); }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        int ok = 0;
        for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) ok++;
        status.setText("🔑 אושרו " + ok + " מתוך " + res.length + " הרשאות. עכשיו לחץ \"הרץ בדיקה\".");
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button btn(String t, int color) {
        Button b = new Button(this);
        b.setText(t); b.setTextSize(22); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setBackground(round(color));
        return b;
    }

    private GradientDrawable round(int color) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14)); return g; }

    private void say(String s) { ui.post(() -> status.setText(s)); }

    private void start() {
        runBtn.setEnabled(false); runBtn.setAlpha(0.4f);
        report.setText("");
        new Thread(() -> {
            String r = new Probe(this, what -> say("⏳ " + what)).runLive();
            last = r;
            String where = save(r);
            ui.post(() -> {
                report.setText(r.length() > 200000 ? r.substring(0, 200000) + "\n...(המשך בקובץ)" : r);
                status.setText("✅ הבדיקה הסתיימה. " + where + "\nעכשיו לחץ \"שלח לשרת\".");
                runBtn.setEnabled(true); runBtn.setAlpha(1f);
                sendBtn.setEnabled(true); sendBtn.setAlpha(1f);
            });
        }).start();
    }

    private String save(String r) {
        String name = "byd-probe-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt";
        String where = "";
        try {
            File d = getExternalFilesDir(null);
            if (d != null) { write(new File(d, name), r); where = "נשמר באפליקציה."; }
        } catch (Throwable ignored) {}
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            dl.mkdirs();
            write(new File(dl, name), r);
            where = "נשמר בתיקיית ההורדות: " + name;
        } catch (Throwable ignored) {}
        return where;
    }

    private static void write(File f, String s) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
    }

    // בדיקת עדכון: מספר הבנייה בכותרת השחרור ב-GitHub ("בנייה N") מול הגרסה המותקנת
    private void checkUpdate(boolean manual) {
        new Thread(() -> {
            String res = "⚠️ אין תקשורת – נסה שוב";
            try {
                HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/israel243-code/gates-app/releases/tags/byd-latest").openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(10000);
                InputStream is = h.getInputStream();
                byte[] buf = new byte[8192]; int n; StringBuilder sb = new StringBuilder();
                while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("בנייה (\\d+)").matcher(sb);
                if (m.find()) {
                    int latest = Integer.parseInt(m.group(1));
                    int mine = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    newer = latest > mine;
                    res = newer ? "⬇️ יש גרסה חדשה – לחץ לעדכון" : "✅ יש לך את הגרסה האחרונה (" + mine + ")";
                }
            } catch (Throwable ignored) {}
            final String r = res;
            ui.post(() -> {
                if (newer) { upd.setText(r); upd.setBackground(round(Color.rgb(124, 58, 237))); }
                else if (manual) { upd.setText(r); ui.postDelayed(() -> upd.setText("🔄 בדוק עדכונים"), 5000); }
            });
        }).start();
    }

    // ---------- שליחה לשרת: קוד חד-פעמי מגיע לבעלים בוואטסאפ ----------
    private void askCode() {
        if (last == null) return;
        sendBtn.setEnabled(false); sendBtn.setAlpha(0.4f);
        say("⏳ מבקש קוד…");
        new Thread(() -> {
            String r = post("code", null, null);
            ui.post(() -> {
                sendBtn.setEnabled(true); sendBtn.setAlpha(1f);
                if (r == null || !r.contains("\"ok\":true")) { status.setText("⚠️ " + msg(r, "אין תקשורת עם השרת")); return; }
                status.setText("📲 נשלח קוד לוואטסאפ שלך. הקלד אותו כאן.");
                codeDialog();
            });
        }).start();
    }

    private void codeDialog() {
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER); in.setTextSize(28); in.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this)
                .setTitle("קוד מוואטסאפ")
                .setView(in)
                .setPositiveButton("שלח", (d, w) -> {
                    String code = in.getText().toString().trim();
                    say("⏳ שולח…");
                    new Thread(() -> {
                        String r = post("send", code, last);
                        say(r != null && r.contains("\"ok\":true") ? "✅ נשלח לשרת. אפשר לסגור." : "⚠️ " + msg(r, "השליחה נכשלה"));
                    }).start();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private static String msg(String json, String def) {
        if (json == null) return def;
        int i = json.indexOf("\"r\":\"");
        if (i < 0) return def;
        int j = json.indexOf('"', i + 5);
        return j > i ? json.substring(i + 5, j) : def;
    }

    private static String post(String step, String code, String body) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(SERVER + "?step=" + step).openConnection();
            h.setRequestMethod("POST"); h.setDoOutput(true);
            h.setConnectTimeout(15000); h.setReadTimeout(60000);
            h.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            if (code != null) h.setRequestProperty("X-Probe-Code", code);
            byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            h.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = h.getOutputStream()) { o.write(b); }
            int st = h.getResponseCode();
            InputStream is = st < 400 ? h.getInputStream() : h.getErrorStream();
            if (is == null) return null;
            byte[] buf = new byte[4096]; int n; StringBuilder sb = new StringBuilder();
            while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            return sb.toString();
        } catch (Throwable e) { return null; }
    }
}
