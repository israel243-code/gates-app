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
    static volatile boolean bound = false, cellSeen = false;

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
    }

    private static synchronized void decide(ConnectivityManager cm) {
        try {
            Network d = cm.getActiveNetwork();
            NetworkCapabilities nc = d == null ? null : cm.getNetworkCapabilities(d);
            boolean defOk = nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && !nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            if (!defOk && cell != null && !bound) { bound = cm.bindProcessToNetwork(cell); LocalEngine.log("📶 עובר לסים של הרכב" + (bound ? "" : " – נכשל")); }
            else if ((defOk || cell == null) && bound) { cm.bindProcessToNetwork(null); bound = false; LocalEngine.log("📶 חוזר לרשת הרגילה"); }
        } catch (Exception e) { /* בלי */ }
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
        String sim = bound ? "האפליקציה משתמשת בסים של הרכב ✅" : cellSeen ? "הסים של הרכב זמין לאפליקציה" : "הסים של הרכב לא זמין לאפליקציה";
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
