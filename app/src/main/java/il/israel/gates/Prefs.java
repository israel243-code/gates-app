package il.israel.gates;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.HashSet;
import java.util.Set;

/** הגדרות האפליקציה: הקישור האישי, הרכבים (כתובות בלוטות'), ומצב אוטומטי. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("gates", Context.MODE_PRIVATE); }

    static String url(Context c) { return p(c).getString("url", ""); }
    static void setUrl(Context c, String u) { p(c).edit().putString("url", u == null ? "" : u.trim()).apply(); }

    /** נעילה: 0 = בלי, 1 = לפני כל פתיחה בכפתור, 2 = בכל כניסה לאפליקציה (18/11) */
    static int lock(Context c) { return p(c).getInt("lock", 0); }
    static void setLock(Context c, int v) { p(c).edit().putInt("lock", v).apply(); }

    /** מצב מנהל: קישור הדשבורד של הבעלים (נכנסים אליו עם קוד מוואטסאפ, כמו בדפדפן) */
    static String admin(Context c) { return p(c).getString("admin", ""); }
    static void setAdmin(Context c, String u) { p(c).edit().putString("admin", u == null ? "" : u.trim()).apply(); }
    static boolean adminView(Context c) { return p(c).getBoolean("adminView", true); }
    static void setAdminView(Context c, boolean b) { p(c).edit().putBoolean("adminView", b).apply(); }
    static boolean isDash(String s) {
        if (s == null) return false;
        Uri u = Uri.parse(s.trim());
        return "https".equals(u.getScheme()) && allowedHost(u) && u.getQuery() == null
                && u.getPath() != null && u.getPath().matches("/gate/[A-Za-z0-9_-]{20,64}/?");
    }

    static Set<String> cars(Context c) { return new HashSet<>(p(c).getStringSet("cars", new HashSet<>())); }
    static void setCars(Context c, Set<String> s) { p(c).edit().putStringSet("cars", new HashSet<>(s)).apply(); }

    static boolean auto(Context c) { return p(c).getBoolean("auto", true); }

    /** "חישוב בטלפון" (ניסיוני): המיקום לא נשלח לשרת – רק "לפתוח את X" */
    static boolean local(Context c) { return p(c).getBoolean("local", false); }
    static void setLocal(Context c, boolean b) { p(c).edit().putBoolean("local", b).apply(); }
    static void setAuto(Context c, boolean b) { p(c).edit().putBoolean("auto", b).apply(); }

    /** קישור אורח תקין: https://<שרת>/gate/<מפתח>?t=<טוקן> */
    /** רק השרת של ישראל – קישור לשרת אחר לא נשמר באפליקציה (בדיקת קוד, 18/11) */
    static boolean allowedHost(Uri u) {
        String h = u == null ? null : u.getHost();
        if (h == null) return false;
        h = h.toLowerCase();
        return h.equals("isrnb.duckdns.org") || h.equals("89-167-17-60.sslip.io");
    }

    static boolean valid(String s) {
        if (s == null) return false;
        Uri u = Uri.parse(s.trim());
        String t = u.getQueryParameter("t");
        return "https".equals(u.getScheme()) && allowedHost(u) && u.getPath() != null && u.getPath().matches("/gate/[A-Za-z0-9_-]{20,64}/?")
                && t != null && t.matches("[A-Za-z0-9_-]{20,40}");
    }

    /** קישור קצר: https://<שרת>/g/<קוד> */
    static boolean isShort(String s) {
        if (s == null) return false;
        Uri u = Uri.parse(s.trim());
        return "https".equals(u.getScheme()) && allowedHost(u) && u.getPath() != null && u.getPath().matches("/g/[A-Za-z0-9]{5,12}/?");
    }

    /** קישור קצר → הקישור המלא (בקשה לשרת בלי לעקוב אחרי ההפניה). רק מחוץ ל-UI thread. null אם לא תקין / פג. */
    static String resolveShort(String s) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(s.trim()).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            String loc = c.getHeaderField("Location");
            c.disconnect();
            return valid(loc) ? loc : null;
        } catch (Exception e) { return null; }
    }

    /** https://<שרת>/gate/<מפתח>?t=X  ->  https://<שרת>/gate/<מפתח>/loc?t=X */
    static String locUrl(Context c) {
        String s = url(c);
        if (!valid(s)) return null;
        Uri u = Uri.parse(s);
        return u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/loc?t=" + Uri.encode(u.getQueryParameter("t"));
    }
}
