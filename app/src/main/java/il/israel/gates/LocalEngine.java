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
    static void log(String s) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        synchronized (LOG) { LOG.add(0, t + "  " + s); while (LOG.size() > 40) LOG.remove(LOG.size() - 1); }
    }

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

    /** @return "open" אם נשלחה פקודת פתיחה, "expired" אם הקישור פג, אחרת "ok" */
    String onFix(double lat, double lon) throws Exception {
        long now = System.currentTimeMillis();
        if (st == null || now - stAt > 10 * 60000) { // ההגדרות מתעדכנות מהשרת כל 10 דקות; אם אין חיבור – ממשיכים עם השמורות בטלפון
            String u = url("st");
            if (u == null) { state = "אין קישור אישי באפליקציה"; log(state); return "ok"; }
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setRequestProperty("User-Agent", "GatesApp/1"); h.setRequestProperty("X-Gates-Local", "1");
                if (h.getResponseCode() == 403) { log("הקישור פג"); return "expired"; }
                String body;
                try (InputStream in = h.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) { body = s.hasNext() ? s.next() : "{}"; }
                st = new JSONObject(body);
                c.getSharedPreferences("gates", Context.MODE_PRIVATE).edit().putString("stCache", body).apply();
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
            if (dist > radius || was == null || dist > was - 3) continue;                 // מחוץ לרדיוס / לא מתקרבים
            Long lo = lastOpen.get(g);
            if (lo != null && now - lo < gap * 60000) continue;                           // נפתח לאחרונה
            lastOpen.put(g, now);
            log("➡️ שולח פתיחה: " + g + " (" + Math.round(dist) + " מ׳)");
            int code; String body = "";
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url("open")).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(20000); h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); h.setRequestProperty("User-Agent", "GatesApp/1");
                try (OutputStream o = h.getOutputStream()) { o.write(("gate=" + Uri.encode(g) + "&auto=1").getBytes(StandardCharsets.UTF_8)); }
                code = h.getResponseCode();
                try (InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) { if (s.hasNext()) body = s.next(); }
                catch (Exception e) { /* בלי גוף */ }
            } catch (Exception e) { lastOpen.put(g, now - (long) (gap * 60000) + 30000); state = "אין אינטרנט"; log("⚠️ אין אינטרנט – ניסיון חוזר בעוד חצי דקה"); continue; } // ניסיון חוזר אחרי חצי דקה
            JSONObject r = new JSONObject(body.isEmpty() ? "{}" : body);
            if (code == 403 && "expired".equals(r.optString("r"))) return "expired";
            if (code == 403) { state = r.optString("r", "אין הרשאה לפתוח"); lastOpen.put(g, now - (long) (gap * 60000) + 60000); continue; }
            log((r.optBoolean("ok") ? "✅ " : "⚠️ ") + r.optString("r", "code " + code));
            if (!r.optBoolean("ok")) lastOpen.put(g, now - (long) (gap * 60000) + 30000); // לא נפתח – ניסיון חוזר אחרי חצי דקה
            else { result = "open"; state = "🚪 נפתח: " + g; }
        }
        prev.clear(); prev.putAll(cur); prevAt = now;
        return result;
    }
}
