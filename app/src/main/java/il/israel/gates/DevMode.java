package il.israel.gates;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.provider.Settings;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 🔧 מצב מפתחים (25/11, הבעלים; כמו OverDrive): הרכב פתוח לניפוי באגים אלחוטי (פורט 5555, נראה בדוח הרכב).
 * האפליקציה מתחברת לרכב עצמו (127.0.0.1) ומדליקה לעצמה מה שמסכי ההגדרות ברכב חוסמים:
 * נגישות (WakeAccess – המערכת מעירה את האפליקציה בכל התנעה), פטור סוללה ופעולה ברקע.
 * בפעם הראשונה הרכב שואל על המסך "לאפשר ניפוי באגים?" – לסמן "תמיד מהמחשב הזה" ולאשר.
 * רק במסך הרכב; שוב אחרי כל עדכון, ובכל פעם שהנגישות כבויה (לכל היותר פעם ב-10 דקות). התוצאה ביומן: "🔧 מצב מפתחים".
 */
final class DevMode {
    static final String PKG = "il.israel.gates";
    static final String A11Y = PKG + "/" + PKG + ".WakeAccess";
    static volatile boolean busy = false;

    static void ensure(Context c) {
        if (!Prefs.carScreen(c) || busy) return;
        SharedPreferences sp = c.getSharedPreferences("gates", Context.MODE_PRIVATE);
        int ver = Updater.current(c);
        long ago = System.currentTimeMillis() - sp.getLong("devAt", 0);
        boolean sameVer = sp.getInt("devVer", 0) == ver;
        if (sameVer && (a11yOn(c) ? ago < 6 * 3600000L : ago < 10 * 60000L)) return;
        busy = true;
        sp.edit().putLong("devAt", System.currentTimeMillis()).putInt("devVer", ver).apply();
        final Context a = c.getApplicationContext();
        new Thread(() -> {
            try { run(a); }
            catch (Throwable t) { LocalEngine.log("🔧 מצב מפתחים: נכשל – " + t.getClass().getSimpleName() + (t.getMessage() != null ? " " + t.getMessage() : "")); }
            finally { busy = false; }
        }).start();
    }

    static boolean a11yOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(PKG + "/");
    }

    static void run(Context c) throws Exception {
        File d = c.getFilesDir(), pr = new File(d, "adbkey"), pu = new File(d, "adbkey.pub");
        if (!pr.exists() || !pu.exists()) dadb.AdbKeyPair.Companion.generate(pr, pu);
        final dadb.AdbKeyPair kp = dadb.AdbKeyPair.Companion.read(pr, pu);
        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<String> f = ex.submit(() -> {
            try (dadb.Dadb db = dadb.Dadb.Companion.create("127.0.0.1", 5555, kp)) {
                String cur = sh(db, "settings get secure enabled_accessibility_services").trim();
                if (cur.isEmpty() || cur.equals("null")) cur = "";
                if (!cur.contains(PKG + "/")) sh(db, "settings put secure enabled_accessibility_services '" + (cur.isEmpty() ? A11Y : cur + ":" + A11Y) + "'");
                sh(db, "settings put secure accessibility_enabled 1");
                sh(db, "dumpsys deviceidle whitelist +" + PKG);
                sh(db, "cmd appops set " + PKG + " RUN_ANY_IN_BACKGROUND allow");
                sh(db, "cmd appops set " + PKG + " RUN_IN_BACKGROUND allow");
                sh(db, "am set-standby-bucket " + PKG + " active");
                return "ok";
            }
        });
        try {
            f.get(90, TimeUnit.SECONDS);
            PowerManager pm = c.getSystemService(PowerManager.class);
            LocalEngine.log("🔧 מצב מפתחים: הצליח – נגישות " + (a11yOn(c) ? "דלוקה" : "לא נדלקה") + " · פטור סוללה " + (pm != null && pm.isIgnoringBatteryOptimizations(PKG) ? "כן" : "לא"));
        } catch (TimeoutException e) {
            f.cancel(true);
            LocalEngine.log("🔧 מצב מפתחים: מחכה לאישור על מסך הרכב (\"לאפשר ניפוי באגים?\" – לסמן \"תמיד\" ולאשר)");
        } finally { ex.shutdownNow(); }
    }

    static String sh(dadb.Dadb db, String cmd) throws Exception {
        return db.shell(cmd).getAllOutput();
    }
}
