package il.israel.gates;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

/**
 * 📶 22/11: תקשורת במסך הרכב. כשהרשת הרגילה (נקודה חמה / אנדרואיד אוטו) בלי אינטרנט – מבקשים מהמערכת את הסים של הרכב
 * ומחברים אליו את כל האפליקציה (bindProcessToNetwork, כולל הדפים). כשהרשת הרגילה חוזרת עם אינטרנט – חוזרים אליה.
 * אם הרכב חוסם את הסים לאפליקציה (הגדרה של BYD לכל אפליקציה) – זה מוצג במצב, כדי שיהיה ברור מה לשנות בהגדרות הרכב.
 */
final class Net {
    private Net() {}
    private static boolean started = false;
    private static volatile Network cell = null;
    static volatile boolean bound = false, cellSeen = false, cellBlocked = false, probing = false;

    static synchronized void start(Context c0) {
        if (started || !Prefs.carScreen(c0)) return;
        started = true;
        final Context c = c0.getApplicationContext();
        final ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            cm.requestNetwork(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(),
                new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) { cell = n; cellSeen = true; decide(cm); }
                    @Override public void onLost(Network n) { if (n.equals(cell)) { cell = null; decide(cm); } }
                });
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { decide(cm); }
                @Override public void onLost(Network n) { decide(cm); }
                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) { decide(cm); }
            });
        } catch (Exception e) { LocalEngine.log("📶 אין אפשרות לבקש את הסים: " + e.getClass().getSimpleName()); }
        // 25/11 (הבעלים: "כתוב שאין תקשורת"): בדיקה אחת שנכשלה השאירה את האפליקציה בלי אינטרנט עד שינוי רשת הבא – עכשיו בודקים שוב כל דקה כל עוד לא עברנו לסים
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        // 26/11 (שער נגוהות: אחרי הפעלה מחדש עברו 2 דקות עד אינטרנט): בדיקה מיד, ואז כל 15 שניות עד שיש אינטרנט; אחר כך כל דקה
        decide(cm);
        h.postDelayed(new Runnable() { @Override public void run() { if (!bound) decide(cm); h.postDelayed(this, bound ? 60000 : 15000); } }, 15000);
    }

    // 22/11 (תיקון): עוברים לסים רק אחרי בדיקה אמיתית שהשרת עונה דרכו – אם הרכב חוסם את הסים לאפליקציה, לא עוברים (אחרת האפליקציה נשארת בלי כלום)
    private static void decide(final ConnectivityManager cm) {
        if (probing) return; probing = true;
        new Thread(() -> {
            try {
                Network d = cm.getActiveNetwork();
                boolean defOk = d != null && !isCell(cm, d) && reach(d);
                if (defOk) { if (bound) { cm.bindProcessToNetwork(null); bound = false; LocalEngine.log("📶 חוזר לרשת הרגילה"); } }
                else if (cell != null && !bound) {
                    if (reach(cell)) { cellBlocked = false; bound = cm.bindProcessToNetwork(cell); LocalEngine.log("📶 אין אינטרנט ברשת הרגילה – עובר לסים של הרכב" + (bound ? "" : " (נכשל)")); }
                    else { if (!cellBlocked) LocalEngine.log("📶 הסים של הרכב לא עונה כרגע – אנסה שוב בעוד דקה"); cellBlocked = true; }
                } else if (cell == null && bound) { cm.bindProcessToNetwork(null); bound = false; }
            } catch (Exception e) { /* בלי */ } finally { probing = false; }
        }).start();
    }
    private static boolean isCell(ConnectivityManager cm, Network n) { NetworkCapabilities nc = cm.getNetworkCapabilities(n); return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR); }
    /** האם השרת עונה דרך הרשת הזו (עד 6 שניות) */
    static boolean reach(Network n) {
        try { java.net.HttpURLConnection h = (java.net.HttpURLConnection) n.openConnection(new java.net.URL("https://isrnb.duckdns.org/join")); h.setConnectTimeout(6000); h.setReadTimeout(6000); h.setRequestMethod("HEAD"); int c = h.getResponseCode(); h.disconnect(); return c > 0; }
        catch (Exception e) { return false; }
    }

    /** שורת מצב בעברית: איזו תקשורת יש עכשיו ומה עם הסים של הרכב */
    static String status(Context c) {
        String now;
        switch (type(c)) {
            case "wifi": now = "רשת אלחוטית (נקודה חמה)"; break;
            case "cell": now = "הסים של הרכב"; break;
            case "bt": now = "בלוטות׳ דרך הטלפון"; break;
            case "none": now = "אין תקשורת"; break;
            default: now = "אחר";
        }
        String sim = bound ? "האפליקציה משתמשת בסים של הרכב ✅" : cellBlocked ? "❌ הסים של הרכב חוסם את האפליקציה – בהגדרות הרכב לאפשר ל\"שערים\" נתונים ניידים" : cellSeen ? "הסים של הרכב זמין (בשימוש רק כשאין אינטרנט ברשת הרגילה)" : "הסים של הרכב לא זמין לאפליקציה";
        return "📶 עכשיו: " + now + " · " + sim;
    }

    static String type(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getBoundNetworkForProcess(); if (n == null) n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) return "none";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cell";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) return "bt";
            return "other";
        } catch (Exception e) { return "?"; }
    }
}
