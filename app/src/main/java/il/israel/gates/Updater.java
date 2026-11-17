package il.israel.gates;

import android.app.Activity;
import android.app.AlertDialog;
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
    static String currentName(Context c) { return "1 (עדכון " + current(c) + ")"; }

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
                    Matcher m = Pattern.compile("עדכון (\\d+)").matcher(name);
                    if (!m.find()) { m = Pattern.compile("1\\.(\\d+)").matcher(name); if (!m.find()) m = null; }
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
                            .setMessage("הגרסה שלך: " + currentName(a) + " · העדכון החדש: " + lv + "\n" + (nt.isEmpty() || nt.equals("בנייה אוטומטית") ? "" : nt + "\n")
                                    + "\nההורדה נפתחת בדפדפן. אחרי שהיא מסתיימת – לוחצים על הקובץ ומאשרים התקנה. ההגדרות שלך נשמרות.")
                            .setPositiveButton("⬇️ הורד ועדכן", (d, w) -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK))))
                            .setNegativeButton("אחר כך", null)
                            .show();
                } else if (!silent) {
                    Toast.makeText(a, "✅ יש לך את הגרסה האחרונה (" + currentName(a) + ")", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
