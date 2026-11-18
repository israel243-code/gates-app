package il.israel.gates;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * בדיקת עדכונים (כל עוד האפליקציה לא בחנות): הגרסה האחרונה ב-GitHub מול הגרסה המותקנת.
 * יש חדשה → חלון "להוריד?" → הורדה בדפדפן והתקנה מעל הקיימת (ההגדרות נשמרות).
 */
final class Updater {
    private Updater() {}

    static final String APK = "https://github.com/israel243-code/gates-app/releases/download/latest/gates.apk";
    private static final String API = "https://api.github.com/repos/israel243-code/gates-app/releases/tags/latest";

    static int current(Context c) {
        try { return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode(); }
        catch (Exception e) { return 0; }
    }

    /** עד ההשקה הרשמית: "1 (עדכון N)" */
    /** הספירה למשתמש התחילה מחדש ב-18/11: עדכון 1 = בנייה 12. המספר הפנימי (versionCode) ממשיך לעלות – אנדרואיד לא מתקין מספר נמוך יותר */
    static final int BASE = 11;
    static String currentName(Context c) { return "1 (עדכון " + Math.max(1, current(c) - BASE) + ")"; }

    /**
     * הורדה בתוך האפליקציה (DownloadManager) ופתיחת מסך ההתקנה של אנדרואיד – בלי דפדפן (17/11: העדכון דרך הדפדפן לא עבד טוב).
     * בפעם הראשונה אנדרואיד מבקש לאשר "התקנה ממקור זה" לאפליקציה – פותחים את המסך הזה ומבקשים ללחוץ שוב.
     */
    static void install(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a).setTitle("אישור חד-פעמי")
                    .setMessage("כדי שהאפליקציה תוכל לעדכן את עצמה, אנדרואיד מבקש לאשר \"התקנה ממקור זה\".\nבמסך שנפתח – מדליקים את המתג, חוזרים, ולוחצים שוב \"בדוק עדכונים\".")
                    .setPositiveButton("פתח", (d, w) -> a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("ביטול", null).show();
            return;
        }
        try {
            final DownloadManager dm = a.getSystemService(DownloadManager.class);
            final long id = dm.enqueue(new DownloadManager.Request(Uri.parse(APK))
                    .setTitle("עדכון לאפליקציית השערים").setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, "gates-" + System.currentTimeMillis() + ".apk"));
            Toast.makeText(a, "⬇️ מוריד את העדכון…", Toast.LENGTH_LONG).show();
            final Context app = a.getApplicationContext();
            BroadcastReceiver done = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { app.unregisterReceiver(this); } catch (Exception e) { /* כבר בוטל */ }
                    Uri u = dm.getUriForDownloadedFile(id);
                    if (u == null) { Toast.makeText(app, "ההורדה נכשלה. נסה שוב.", Toast.LENGTH_LONG).show(); return; }
                    app.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            };
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(done, f, Context.RECEIVER_EXPORTED); else app.registerReceiver(done, f);
        } catch (Exception e) {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK))); // גיבוי: דרך הדפדפן
        }
    }

    /** silent = בדיקה אוטומטית (פעם ביום, בפתיחה): מציג חלון רק כשיש עדכון */
    static void check(Activity a, boolean silent) {
        if (silent) {
            long last = a.getSharedPreferences("gates", Context.MODE_PRIVATE).getLong("updCheck", 0);
            if (System.currentTimeMillis() - last < 20 * 3600 * 1000L) return;
        }
        a.getSharedPreferences("gates", Context.MODE_PRIVATE).edit().putLong("updCheck", System.currentTimeMillis()).apply();
        if (!silent) Toast.makeText(a, "בודק עדכונים…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int latest = 0;
            String notes = "";
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "GatesApp");
                try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                    JSONObject j = new JSONObject(s.hasNext() ? s.next() : "{}");
                    String name = j.optString("name", "");
                    Matcher m = Pattern.compile("b1\\.(\\d+)").matcher(name); // המספר הפנימי; "עדכון #N" רק לתצוגה
                    if (!m.find()) { m = Pattern.compile("עדכון (\\d+)").matcher(name); if (!m.find()) m = null; }
                    if (m != null) latest = Integer.parseInt(m.group(1));
                    notes = j.optString("body", "");
                }
            } catch (Exception e) {
                if (!silent) a.runOnUiThread(() -> Toast.makeText(a, "לא הצלחתי לבדוק עכשיו. נסה שוב בעוד רגע.", Toast.LENGTH_LONG).show());
                return;
            }
            final int lv = latest;
            final String nt = notes;
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (lv > current(a)) {
                    new AlertDialog.Builder(a)
                            .setTitle("🆕 יש עדכון חדש לאפליקציה")
                            .setMessage("הגרסה שלך: " + currentName(a) + " · העדכון החדש: עדכון " + Math.max(1, lv - BASE) + "\n" + (nt.isEmpty() || nt.equals("בנייה אוטומטית") ? "" : nt + "\n")
                                    + "\nההורדה נעשית בתוך האפליקציה, ובסוף נפתח מסך ההתקנה – לוחצים \"עדכן\". ההגדרות שלך נשמרות.")
                            .setPositiveButton("⬇️ עדכן עכשיו", (d, w) -> install(a))
                            .setNegativeButton("אחר כך", null)
                            .show();
                } else if (!silent) {
                    Toast.makeText(a, "✅ יש לך את הגרסה האחרונה (" + currentName(a) + ")", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
