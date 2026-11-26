package il.israel.gates;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;

/**
 * 🔐 מעבר לחתימה חדשה (26/11, דוח האבטחה: מפתח החתימה הישן היה במאגר ציבורי). אנדרואיד לא מקבל חתימה חדשה כ"עדכון",
 * ולכן צריך להסיר ולהתקין מחדש. ברכב זה קורה לבד דרך מצב המפתחים (ניפוי הבאגים של הרכב עצמו):
 * 1. מורידים את שתי האפליקציות החדשות ובודקים שהן חתומות בדיוק במפתח החדש (טביעת אצבע קבועה כאן) – אחרת עוצרים.
 * 2. מעבירים אותן לרכב, ומריצים שם תסריט שממשיך גם אחרי שהאפליקציה הזו נסגרת: מסיר ומתקין את "הרכב שלי" ואת "שערים",
 *    מחזיר נגישות ופטור סוללה, ופותח את "שערים" החדשה עם ההגדרות של הישנה (restore). ההפעלה האוטומטית נדלקת לבד (AutoStart).
 * בטלפון: האפליקציה החדשה מקבלת את ההגדרות בקישור שחזור (restore) – רק בהתקנה נקייה.
 */
final class Migrate {
    private Migrate() {}
    static final String GATES_APK = "https://github.com/israel243-code/gates-app/releases/download/latest2/gates2.apk";
    static final String PROBE_APK = "https://github.com/israel243-code/gates-app/releases/download/byd-latest2/byd-probe2.apk";
    static final String GATES_FP = "9176ee83d2e0b6df76bf617724f2227f319b24f9db9fafca10e5cbeb5347160d";
    static final String PROBE_FP = "0852c6aad0bb1c48e32da901a8fc483d9e9011a6aa63cc2194046b584353a62f";
    static volatile boolean busy = false;

    /** האם האפליקציה הזו כבר חתומה במפתח החדש (אז אין מה להעביר) */
    static boolean onNewKey(Context c) {
        try { return GATES_FP.equals(certFp(c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES))); } catch (Exception e) { return false; }
    }

    static String certFp(PackageInfo pi) throws Exception {
        Signature[] s = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners() : pi.signingInfo.getSigningCertificateHistory();
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s[s.length - 1].toByteArray());
        StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x)); return b.toString();
    }

    static File download(Context c, String url, String name) throws Exception {
        File f = new File(c.getCacheDir(), name);
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection(); h.setConnectTimeout(20000); h.setReadTimeout(60000); h.setInstanceFollowRedirects(true);
        try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(f)) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n); }
        return f;
    }

    static void check(Context c, File apk, String fp, String what) throws Exception {
        PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (pi == null || pi.signingInfo == null) throw new Exception(what + ": הקובץ לא תקין");
        if (!fp.equals(certFp(pi))) throw new Exception(what + ": החתימה לא מתאימה למפתח החדש – עצרתי");
    }

    /** כל ההגדרות של האפליקציה (קובץ gates) כטקסט – להעברה לאפליקציה החדשה */
    static String export(Context c) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences("gates", Context.MODE_PRIVATE).getAll().entrySet()) {
                Object v = e.getValue(); JSONObject t = new JSONObject();
                if (v instanceof Boolean) t.put("b", v); else if (v instanceof Integer) t.put("i", v); else if (v instanceof Long) t.put("l", v); else if (v instanceof Float) t.put("f", ((Float) v).doubleValue()); else if (v instanceof String) t.put("s", v); else continue;
                o.put(e.getKey(), t);
            }
            return android.util.Base64.encodeToString(o.toString().getBytes("UTF-8"), android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        } catch (Exception e) { return ""; }
    }

    /** קליטת ההגדרות – רק בהתקנה נקייה (אין קישור שמור), ורק קישורים של השרת שלנו */
    static boolean importIfFresh(Context c, String b64) {
        if (b64 == null || b64.isEmpty() || Prefs.valid(Prefs.url(c)) || Prefs.isDash(Prefs.admin(c))) return false;
        try {
            JSONObject o = new JSONObject(new String(android.util.Base64.decode(b64, android.util.Base64.URL_SAFE), "UTF-8"));
            SharedPreferences.Editor ed = c.getSharedPreferences("gates", Context.MODE_PRIVATE).edit();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next(); JSONObject t = o.getJSONObject(k);
                if (t.has("b")) ed.putBoolean(k, t.getBoolean("b")); else if (t.has("i")) ed.putInt(k, t.getInt("i")); else if (t.has("l")) ed.putLong(k, t.getLong("l")); else if (t.has("f")) ed.putFloat(k, (float) t.getDouble("f")); else if (t.has("s")) ed.putString(k, t.getString("s"));
            }
            ed.remove("asVer").remove("devVer").remove("devAt").commit(); // בהתקנה החדשה – להדליק שוב הפעלה אוטומטית ומצב מפתחים
            if (!Prefs.valid(Prefs.url(c))) Prefs.setUrl(c, "");
            if (!Prefs.isDash(Prefs.admin(c))) c.getSharedPreferences("gates", Context.MODE_PRIVATE).edit().putString("admin", "").commit();
            LocalEngine.log("🔐 ההגדרות הועברו מהאפליקציה הקודמת (חתימה חדשה)");
            return true;
        } catch (Exception e) { LocalEngine.log("🔐 העברת ההגדרות נכשלה: " + e.getClass().getSimpleName()); return false; }
    }

    /** ברכב: כל המעבר לבד, דרך ניפוי הבאגים של הרכב. מחזיר הודעה למסך */
    static void runCar(final Context c, final java.util.function.Consumer<String> say) {
        if (busy) { say.accept("⏳ המעבר כבר רץ"); return; }
        busy = true;
        new Thread(() -> {
            try {
                say.accept("⏳ מוריד את שתי האפליקציות החדשות…");
                File g = download(c, GATES_APK, "g2.apk"), p = download(c, PROBE_APK, "p2.apk");
                check(c, g, GATES_FP, "שערים"); check(c, p, PROBE_FP, "הרכב שלי");
                LocalEngine.log("🔐 מעבר לחתימה חדשה: שתי האפליקציות הורדו ונבדקו (חתימה נכונה)");
                File key = new File(c.getFilesDir(), "adbkey"), pub = new File(c.getFilesDir(), "adbkey.pub");
                if (!key.exists() || !pub.exists()) dadb.AdbKeyPair.Companion.generate(key, pub);
                dadb.AdbKeyPair kp = dadb.AdbKeyPair.Companion.read(key, pub);
                String restore = export(c), pkg = c.getPackageName();
                String sh = "sleep 3\n"
                    + "cur=$(settings get secure enabled_accessibility_services)\n"
                    + "pm uninstall il.israel.bydprobe\npm install -r -g /data/local/tmp/p2.apk\n"
                    + "pm uninstall " + pkg + "\npm install -r -g /data/local/tmp/g2.apk\n"
                    + "case \"$cur\" in *" + pkg + "/*) ;; *) cur=\"${cur:+$cur:}" + pkg + "/" + pkg + ".WakeAccess\";; esac\n"
                    + "settings put secure enabled_accessibility_services \"$cur\"\nsettings put secure accessibility_enabled 1\n"
                    + "dumpsys deviceidle whitelist +" + pkg + "\ncmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow\n"
                    + "am start -n " + pkg + "/.MainActivity --es restore '" + restore + "'\n"
                    + "rm -f /data/local/tmp/g2.apk /data/local/tmp/p2.apk\necho done\n";
                File script = new File(c.getCacheDir(), "mig.sh"); try (FileOutputStream o = new FileOutputStream(script)) { o.write(sh.getBytes("UTF-8")); }
                say.accept("⏳ מעביר לרכב…");
                try (dadb.Dadb db = dadb.Dadb.Companion.create("127.0.0.1", 5555, kp)) {
                    db.push(g, "/data/local/tmp/g2.apk", 420, System.currentTimeMillis());
                    db.push(p, "/data/local/tmp/p2.apk", 420, System.currentTimeMillis());
                    db.push(script, "/data/local/tmp/mig.sh", 420, System.currentTimeMillis());
                    LocalEngine.log("🔐 מעבר לחתימה חדשה: מתחיל התקנה ברכב (האפליקציה תיסגר ותיפתח מחדש)");
                    LocalEngine.upload(c);
                    db.shell("(setsid sh /data/local/tmp/mig.sh > /data/local/tmp/mig.log 2>&1 &) ; echo ok");
                }
                say.accept("✅ ההתקנה התחילה – האפליקציה תיסגר ותיפתח מחדש בעוד כחצי דקה");
            } catch (Throwable t) {
                LocalEngine.log("🔐 מעבר לחתימה חדשה נכשל: " + t.getMessage());
                say.accept("⚠️ " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            } finally { busy = false; }
        }).start();
    }

    // 📱 בטלפון (אי אפשר להסיר ולהתקין לבד): מורידים את החדשה לתיקיית ההורדות ובודקים חתימה, שומרים את ההגדרות בלוח ההעתקה
    // (קוד שחזור), ופותחים את מסך ההסרה. אחרי ההתקנה – האפליקציה החדשה קולטת את ההגדרות מהלוח בפתיחה הראשונה.
    static final String CLIP = "isrnb-restore:";
    static void runPhone(final android.app.Activity a, final java.util.function.Consumer<String> say) {
        if (busy) { say.accept("⏳ כבר רץ"); return; }
        busy = true;
        new Thread(() -> {
            try {
                say.accept("⏳ מוריד את האפליקציה החדשה…");
                File g = download(a, GATES_APK, "g2.apk"); check(a, g, GATES_FP, "שערים");
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "שערים-חדשה.apk");
                cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
                android.net.Uri u = a.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                try (java.io.OutputStream o = a.getContentResolver().openOutputStream(u); java.io.FileInputStream in = new java.io.FileInputStream(g)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n); }
                final String code = CLIP + export(a);
                LocalEngine.log("🔐 מעבר לחתימה חדשה (טלפון): הקובץ בהורדות, קוד השחזור בלוח");
                a.runOnUiThread(() -> {
                    android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("שחזור שערים", code));
                    Ui.dialog(a).setMessage("✅ האפליקציה החדשה נשמרה בהורדות, וההגדרות שלך שמורות.\n\n1. לחץ \"הסר\" במסך הבא.\n2. פתח את \"הורדות\" ← \"שערים-חדשה\" ← התקן.\n3. פתח את \"שערים\" – ההגדרות יחזרו לבד.")
                        .setPositiveButton("המשך להסרה", (d, w) -> a.startActivity(new android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:" + a.getPackageName()))))
                        .setNegativeButton("לא עכשיו", null).show();
                });
                say.accept("✅ מוכן");
            } catch (Throwable t) {
                LocalEngine.log("🔐 מעבר בטלפון נכשל: " + t.getMessage());
                say.accept("⚠️ " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            } finally { busy = false; }
        }).start();
    }

    /** בפתיחה הראשונה של האפליקציה החדשה בטלפון: קוד שחזור בלוח ההעתקה → ההגדרות חוזרות, והלוח מתנקה */
    static boolean importFromClipboard(android.app.Activity a) {
        try {
            if (Prefs.valid(Prefs.url(a)) || Prefs.isDash(Prefs.admin(a))) return false;
            android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return false;
            CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
            if (t == null || !t.toString().startsWith(CLIP)) return false;
            boolean ok = importIfFresh(a, t.toString().substring(CLIP.length()));
            if (ok) cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""));
            return ok;
        } catch (Exception e) { return false; }
    }
}
