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

    static Set<String> cars(Context c) { return new HashSet<>(p(c).getStringSet("cars", new HashSet<>())); }
    static void setCars(Context c, Set<String> s) { p(c).edit().putStringSet("cars", new HashSet<>(s)).apply(); }

    static boolean auto(Context c) { return p(c).getBoolean("auto", true); }
    static void setAuto(Context c, boolean b) { p(c).edit().putBoolean("auto", b).apply(); }

    /** קישור אורח תקין: https://<שרת>/gate/<מפתח>?t=<טוקן> */
    static boolean valid(String s) {
        if (s == null) return false;
        Uri u = Uri.parse(s.trim());
        String t = u.getQueryParameter("t");
        return "https".equals(u.getScheme()) && u.getPath() != null && u.getPath().matches("/gate/[A-Za-z0-9_-]{20,64}/?")
                && t != null && t.matches("[A-Za-z0-9_-]{20,40}");
    }

    /** https://<שרת>/gate/<מפתח>?t=X  ->  https://<שרת>/gate/<מפתח>/loc?t=X */
    static String locUrl(Context c) {
        String s = url(c);
        if (!valid(s)) return null;
        Uri u = Uri.parse(s);
        return u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/loc?t=" + Uri.encode(u.getQueryParameter("t"));
    }
}
