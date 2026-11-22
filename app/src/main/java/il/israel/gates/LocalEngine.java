package il.israel.gates;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * "חישוב בטלפון" – תשתית לאפשרות ג' (בלי שרת מרכזי), 17/11.
 * הטלפון מחליט לבד מתי לפתוח – אותם כללים כמו בשרת (בתוך הרדיוס, מתקרבים, מרווח בין פתיחות) –
 * ושולח לשרת רק "לפתוח את שער X". המיקום עצמו לא יוצא מהטלפון.
 * שלב הבא (כשיוחלט): את "לפתוח את X" לשלוח ישר לגוגל מהטלפון (Opener אחר), ואז אין צורך בשרת בכלל.
 * רץ על התהליכון של הרשת ב-LocService (לא על המסך).
 */
final class LocalEngine {
    private final Context c;
    private JSONObject st;           // מהשרת: שערים מותרים, ברירות מחדל, הגדרות אישיות (/st)
    private long stAt = 0, prevAt = 0;
    private final Map<String, Double> prev = new HashMap<>();
    private final Map<String, Long> lastOpen = new HashMap<>();
    String state = "";

    // יומן קצר בטלפון (18/11: "למה הניסיוני לא עבד?") – מוצג בהגדרות; בלי מיקומים, רק מרחקים והחלטות
    static final java.util.ArrayList<String> LOG = new java.util.ArrayList<>();
    static final java.util.ArrayList<String> PENDING = new java.util.ArrayList<>(); // שורות שעוד לא נשלחו לשרת (רק במצב מנהל)

    /** 📤 שליחת היומן לשרת – רק במצב מנהל (הבעלים, 18/11: "לחבר את 'מה קרה לאחרונה' לשרת, כרגע רק למנהל"). רץ על תהליכון הרשת. */
    static void upload(Context c) {
        if (!Prefs.isDash(Prefs.admin(c))) return;
        init(c);
        java.io.File f = new java.io.File(c.getFilesDir(), "pending.log");
        String body;
        synchronized (LOG) {
            try { body = f.exists() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim() : String.join("\n", PENDING); } catch (Exception e) { body = String.join("\n", PENDING); }
            if (body.isEmpty()) return;
            PENDING.clear(); f.delete();
        }
        String s = Prefs.url(c); if (!Prefs.valid(s)) return;
        Uri u = Uri.parse(s);
        String url = u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/gcfg?t=" + Uri.encode(u.getQueryParameter("t"));
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setRequestMethod("POST"); h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); h.setRequestProperty("User-Agent", "GatesApp/1");
            try (OutputStream o = h.getOutputStream()) { o.write(("applog=" + Uri.encode(body)).getBytes(StandardCharsets.UTF_8)); }
            h.getResponseCode();
        } catch (Exception e) { synchronized (LOG) { try (java.io.FileWriter w = new java.io.FileWriter(f, true)) { w.write(body + "\n"); } catch (Exception e2) { /* אין */ } } } // בלי רשת – יישלח בפעם הבאה
    }
    static Context app; // נקבע בהפעלה – כדי לשמור את היומן בקובץ גם כשהאפליקציה נסגרת (19/11: "אין רישום ביומן")
    static void log(String s) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        synchronized (LOG) { LOG.add(0, t + "  " + s); while (LOG.size() > 40) LOG.remove(LOG.size() - 1); PENDING.add(t + "  " + s); while (PENDING.size() > 200) PENDING.remove(0); }
        if (app != null) try (java.io.FileWriter w = new java.io.FileWriter(new java.io.File(app.getFilesDir(), "pending.log"), true)) { w.write(t + "  " + s + "\n"); } catch (Exception e) { /* בלי קובץ */ }
    }
    static void init(Context c) { if (app == null) app = c.getApplicationContext(); }

    LocalEngine(Context c) {
        this.c = c;
        // נתוני השערים נשמרים בטלפון – עובד גם כשהשרת / האינטרנט לא זמינים לרגע (רק הפתיחה עצמה צריכה את השרת)
        try { String j = c.getSharedPreferences("gates", Context.MODE_PRIVATE).getString("stCache", ""); if (!j.isEmpty()) { st = new JSONObject(j); log("נתוני שערים מהטלפון (שמורים)"); } } catch (Exception e) { /* אין */ }
    }

    private String url(String sub) {
        String s = Prefs.url(c);
        if (!Prefs.valid(s)) return null;
        Uri u = Uri.parse(s);
        return u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/" + sub + "?t=" + Uri.encode(u.getQueryParameter("t"));
    }

    private static double meters(double aLat, double aLon, double bLat, double bLon) {
        double r = Math.PI / 180, x = (bLon - aLon) * r * Math.cos(((aLat + bLat) / 2) * r), y = (bLat - aLat) * r;
        return Math.sqrt(x * x + y * y) * 6371000;
    }

    /** נקודה בתוך מצולע ([[lat,lon],...]) */
    private static boolean inZone(double lat, double lon, JSONArray z) {
        boolean c = false;
        for (int i = 0, j = z.length() - 1; i < z.length(); j = i++) {
            JSONArray a = z.optJSONArray(i), b = z.optJSONArray(j);
            if (a == null || b == null) return false;
            double yi = a.optDouble(0), xi = a.optDouble(1), yj = b.optDouble(0), xj = b.optDouble(1);
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) c = !c;
        }
        return c;
    }

    /** @return "open" אם נשלחה פקודת פתיחה, "expired" אם הקישור פג, אחרת "ok" */
    String onFix(double lat, double lon) throws Exception {
        long now = System.currentTimeMillis();
        // נתוני השערים נשמרים בטלפון לתמיד (הבעלים, 18/11: "השערים לא זזים כל יום"). מתעדכנים מהשרת רק כשצריך:
        // אין עדיין נתונים / המשתמש שינה הגדרה בדף השערים (stDirty) / עדכון גרסה של האפליקציה
        android.content.SharedPreferences sp = c.getSharedPreferences("gates", Context.MODE_PRIVATE);
        int ver = 0; try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { /* לא ידוע */ }
        boolean need = st == null || sp.getBoolean("stDirty", false) || sp.getInt("stVer", -1) != ver;
        if (need && now - stAt > 30000) { // לא יותר מניסיון אחד בחצי דקה
            String u = url("st");
            if (u == null) { state = "אין קישור אישי באפליקציה"; log(state); return "ok"; }
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setRequestProperty("User-Agent", "GatesApp/1"); h.setRequestProperty("X-Gates-Local", "1");
                if (h.getResponseCode() == 403) { log("הקישור פג"); return "expired"; }
                String body;
                try (InputStream in = h.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) { body = s.hasNext() ? s.next() : "{}"; }
                st = new JSONObject(body);
                sp.edit().putString("stCache", body).putBoolean("stDirty", false).putInt("stVer", ver).apply();
                JSONArray o = st.optJSONArray("on");
                log("נתוני שערים עודכנו מהשרת: " + (o == null ? 0 : o.length()) + " שערים לפתיחה אוטומטית");
            } catch (Exception e) { log("אין חיבור לשרת – משתמש בנתונים השמורים"); if (st == null) { state = "אין נתוני שערים עדיין"; return "ok"; } }
            stAt = now;
        }
        JSONArray on = st.optJSONArray("on");
        JSONObject defs = st.optJSONObject("defs"), cfg = st.optJSONObject("cfg");
        if (on == null || defs == null) { log("אין שערים לפתיחה אוטומטית"); return "ok"; }
        boolean fresh = now - prevAt < 60000;
        Map<String, Double> cur = new HashMap<>();
        String result = "ok";
        for (int i = 0; i < on.length(); i++) {
            String g = on.optString(i);
            JSONObject d = defs.optJSONObject(g), my = cfg == null ? null : cfg.optJSONObject(g);
            if (d == null) continue;
            double glat = my != null && my.has("lat") ? my.optDouble("lat") : d.optDouble("lat", Double.NaN);
            double glon = my != null && my.has("lon") ? my.optDouble("lon") : d.optDouble("lon", Double.NaN);
            if (Double.isNaN(glat) || Double.isNaN(glon)) continue;
            double radius = my != null && my.has("radius") ? my.optDouble("radius") : d.optDouble("radius", 200);
            double gap = my != null && my.has("gap") ? my.optDouble("gap") : d.optDouble("gap", 5);
            double dist = meters(lat, lon, glat, glon);
            cur.put(g, dist);
            Double was = fresh ? prev.get(g) : null;
            if (dist < 1500 && (was == null || Math.abs(was - dist) > 15)) log(g + ": " + Math.round(dist) + " מ׳" + (dist <= radius ? " (בתוך " + Math.round(radius) + ")" : ""));
            JSONArray zone = my != null && my.optJSONArray("zone") != null ? my.optJSONArray("zone") : d.optJSONArray("zone"); // 📐 אזור במקום עיגול (18/11)
            boolean outside = zone != null && zone.length() >= 3 ? !inZone(lat, lon, zone) : dist > radius;
            if (outside || was == null || dist > was - 3) continue;                 // מחוץ לרדיוס / לאזור, או לא מתקרבים
            Long lo = lastOpen.get(g);
            if (lo != null && now - lo < gap * 60000) continue;                           // נפתח לאחרונה
            lastOpen.put(g, now);
            log("➡️ שולח פתיחה: " + g + " (" + Math.round(dist) + " מ׳)");
            int code; String body = "";
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url("open")).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(20000); h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); h.setRequestProperty("User-Agent", "GatesApp/1");
                try (OutputStream o = h.getOutputStream()) { o.write(("gate=" + Uri.encode(g) + "&auto=1&cv=" + Uri.encode(st == null ? "" : st.optString("cv", ""))).getBytes(StandardCharsets.UTF_8)); } // A8: גרסת ההגדרות
                code = h.getResponseCode();
                try (InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) { if (s.hasNext()) body = s.next(); }
                catch (Exception e) { /* בלי גוף */ }
            } catch (Exception e) { lastOpen.put(g, now - (long) (gap * 60000) + 30000); state = "אין אינטרנט"; log("⚠️ אין אינטרנט – ניסיון חוזר בעוד חצי דקה"); continue; } // ניסיון חוזר אחרי חצי דקה
            JSONObject r = new JSONObject(body.isEmpty() ? "{}" : body);
            if (code == 403 && "expired".equals(r.optString("r"))) return "expired";
            if (code == 409) { sp.edit().putBoolean("stDirty", true).apply(); log("🔄 הגדרות השערים השתנו בשרת – מרענן ומנסה שוב"); lastOpen.put(g, now - (long) (gap * 60000) + 3000); continue; } // A8
            if (code == 403) { sp.edit().putBoolean("stDirty", true).apply(); state = r.optString("r", "אין הרשאה לפתוח"); lastOpen.put(g, now - (long) (gap * 60000) + 60000); continue; }
            log((r.optBoolean("ok") ? "✅ " : "⚠️ ") + r.optString("r", "code " + code));
            Notify.gate(c, g, r.optBoolean("ok"), r.optString("r", ""));
            if (!r.optBoolean("ok")) lastOpen.put(g, now - (long) (gap * 60000) + 30000); // לא נפתח – ניסיון חוזר אחרי חצי דקה
            else { result = "open"; state = "🚪 נפתח: " + g; }
        }
        prev.clear(); prev.putAll(cur); prevAt = now;
        return result;
    }
}
