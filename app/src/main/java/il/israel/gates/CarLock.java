package il.israel.gates;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * 🔐 23/11: נעילה במסך הרכב (הבעלים: "לכל מי שיש גישה לרכב יש גישה לנתונים אישיים").
 * דף השערים פתוח לכולם; כל השאר (הדשבורד, ההגדרות, הקליפים) – תבנית החלקה, פעם אחת בכל הפעלה של הרכב:
 * הפתיחה נשמרת בזיכרון בלבד ומתבטלת כשהמסך נכבה (כיבוי הרכב) או כשהאפליקציה נסגרת.
 * התבנית נשמרת כ-SHA-256 עם מלח (לא התבנית עצמה). בטלפון – בלי שינוי.
 */
final class CarLock {
    private CarLock() {}
    static volatile boolean unlocked = false;
    private static boolean watching = false;

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("carlock", Context.MODE_PRIVATE); }
    static boolean active(Context c) { return Prefs.carScreen(c); }
    static boolean hasPattern(Context c) { return p(c).getString("h", null) != null; }
    static boolean needed(Context c) { watch(c); return active(c) && !unlocked; }

    /** כיבוי המסך (הרכב כבה) = נעול שוב */
    static synchronized void watch(Context c) {
        if (watching) return; watching = true;
        try {
            c.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent i) { unlocked = false; }
            }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        } catch (Exception e) { /* בלי */ }
    }

    static String hash(Context c, String pattern) {
        try {
            String salt = p(c).getString("s", null);
            if (salt == null) { byte[] b = new byte[16]; new java.security.SecureRandom().nextBytes(b); salt = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP); p(c).edit().putString("s", salt).apply(); }
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest((salt + ":" + pattern).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(d, android.util.Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }
    static void set(Context c, String pattern) { p(c).edit().putString("h", hash(c, pattern)).putInt("bad", 0).apply(); }
    static boolean check(Context c, String pattern) {
        String h = p(c).getString("h", null);
        boolean ok = h != null && h.equals(hash(c, pattern));
        p(c).edit().putInt("bad", ok ? 0 : p(c).getInt("bad", 0) + 1).putLong("badAt", ok ? 0 : System.currentTimeMillis()).apply();
        return ok;
    }
    /** אחרי 5 ניסיונות שגויים – המתנה של דקה */
    static long waitMs(Context c) { return p(c).getInt("bad", 0) >= 5 ? Math.max(0, p(c).getLong("badAt", 0) + 60000 - System.currentTimeMillis()) : 0; }
    /** "שכחתי": מוחקים את התבנית וגם את החיבור של מסך הרכב – מחברים מחדש בברקוד מהטלפון (רק מי שיש לו את הטלפון) */
    static void forget(Context c) { p(c).edit().clear().apply(); unlocked = false; Prefs.setUrl(c, ""); }

    /** האם הכתובת היא דף השערים האישי (פתוח בלי נעילה) */
    static boolean isGatesPage(Context c, String url) {
        try {
            Uri u = Uri.parse(url), g = Uri.parse(Prefs.url(c));
            if (g.getHost() == null || !g.getHost().equals(u.getHost())) return false;
            String gp = g.getPath() == null ? "" : g.getPath().replaceAll("/+$", ""), up = u.getPath() == null ? "" : u.getPath();
            return !gp.isEmpty() && (up.equals(gp) || up.startsWith(gp + "/")) && String.valueOf(g.getQueryParameter("t")).equals(String.valueOf(u.getQueryParameter("t")));
        } catch (Exception e) { return false; }
    }

    /** מסך נעול (הגדרות / קליפים): אם צריך – פותח את מסך התבנית, וחוזר לכאן רק אחרי פתיחה */
    static boolean guard(Activity a) {
        if (!needed(a)) return false;
        a.startActivity(new Intent(a, PatternActivity.class).putExtra("next", a.getClass().getName()));
        a.finish();
        return true;
    }
}
