package il.israel.bydprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.function.Consumer;

/**
 * 📶 בדיקת הסים של הרכב, מובנית באפליקציה (23/11 – הסקריפט המתעדכן לא יורד בלי אינטרנט). נכתב לפי סקריפט 5: למה הסים של הרכב "חוסם" את אפליקציית השערים. קריאה בלבד – לא משנה שום הגדרה.
 * לכל רשת: סוג, יכולות, DNS; דרך כל רשת: פענוח שם + בקשה קצרה לכמה אתרים (גוגל, BYD, השרת שלנו).
 * כך יודעים אם הסים פתוח רק לשרתים של BYD (APN פרטי), חסום לאפליקציות מסוימות, או בכלל בלי אינטרנט.
 */
final class SimCheck {
    private SimCheck() {}
    static final String[][] HOSTS = {
        { "גוגל (בדיקת אינטרנט)", "http://connectivitycheck.gstatic.com/generate_204" },
        { "השרת שלנו", "https://isrnb.duckdns.org/join" },
        { "BYD", "https://www.byd.com/" },
        { "BYD סין", "https://www.bydauto.com.cn/" },
    };

    static String run(Context c, Consumer<String> progress) {
        StringBuilder out = new StringBuilder("בדיקת רכב – הסים (קריאה בלבד) – ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date())).append("\n");
        final ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        // ניסיון לבקש את רשת הסים (ייתכן שאין הרשאה באפליקציה הזו – אז רק הרשתות שכבר קיימות)
        final Network[] got = { null };
        ConnectivityManager.NetworkCallback cb = null;
        try {
            cb = new ConnectivityManager.NetworkCallback() { @Override public void onAvailable(Network n) { got[0] = n; } };
            cm.requestNetwork(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(), cb);
            if (progress != null) progress.accept("מבקש את רשת הסים (עד 15 שניות)");
            for (int i = 0; i < 30 && got[0] == null; i++) Thread.sleep(500);
            out.append("בקשת רשת סים: ").append(got[0] != null ? "התקבלה" : "לא התקבלה תוך 15 שניות").append('\n');
        } catch (Throwable e) { out.append("בקשת רשת סים: ").append(e.getClass().getSimpleName()).append(" (אין הרשאה באפליקציה הזו – בודקים את הרשתות הקיימות)\n"); cb = null; }

        try {
            Network def = cm.getActiveNetwork();
            out.append("\nרשת ברירת מחדל: ").append(def).append('\n');
            Network[] all = cm.getAllNetworks();
            out.append("רשתות במערכת: ").append(all.length).append('\n');
            java.util.LinkedHashSet<Network> list = new java.util.LinkedHashSet<>(java.util.Arrays.asList(all));
            if (got[0] != null) list.add(got[0]);
            int k = 0;
            for (Network n : list) {
                k++;
                if (progress != null) progress.accept("בודק רשת " + k + " מתוך " + list.size());
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                LinkProperties lp = cm.getLinkProperties(n);
                NetworkInfo ni = null; try { ni = cm.getNetworkInfo(n); } catch (Throwable e) { /* אין */ }
                out.append("\n=== רשת ").append(n).append(n.equals(def) ? " (ברירת מחדל)" : "").append(" ===\n");
                if (nc != null) {
                    out.append("סוג: ").append(nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "סים" : nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "רשת אלחוטית" : nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ? "בלוטות׳" : nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "כבל" : "אחר")
                       .append(" · אינטרנט: ").append(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                       .append(" · מאומת: ").append(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                       .append(" · לא מוגבל: ").append(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
                       .append(" · לא בתשלום: ").append(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).append('\n');
                    out.append("יכולות: ").append(nc.toString().replaceAll("\\s+", " ")).append('\n');
                }
                if (ni != null) out.append("מידע: ").append(ni.getTypeName()).append(" / ").append(ni.getSubtypeName()).append(" · APN: ").append(ni.getExtraInfo()).append(" · מצב: ").append(ni.getDetailedState()).append('\n');
                if (lp != null) out.append("ממשק: ").append(lp.getInterfaceName()).append(" · DNS: ").append(lp.getDnsServers()).append(" · דומיין: ").append(lp.getDomains()).append('\n');
                for (String[] h : HOSTS) out.append(test(n, h[0], h[1])).append('\n');
            }
        } catch (Throwable e) { out.append("שגיאה: ").append(e).append('\n'); }
        if (cb != null) try { cm.unregisterNetworkCallback(cb); } catch (Throwable e) { /* בלי */ }

        // מגבלות נתונים של המערכת לאפליקציות (שערים + האפליקציה הזו)
        try {
            out.append("\nחיסכון בנתונים (רקע): ").append(cm.getRestrictBackgroundStatus()).append(" (1=כבוי, 2=רשימה לבנה, 3=מופעל)\n");
            Object npm = c.getSystemService("netpolicy");
            for (String pkg : new String[] { "il.israel.gates", c.getPackageName() }) {
                int uid = -1; try { uid = c.getPackageManager().getApplicationInfo(pkg, 0).uid; } catch (Throwable e) { /* לא מותקנת */ }
                String pol; try { pol = String.valueOf(npm.getClass().getMethod("getUidPolicy", int.class).invoke(npm, uid)); } catch (Throwable e) { pol = e.getClass().getSimpleName(); }
                out.append(pkg).append(" uid ").append(uid).append(" · מדיניות נתונים: ").append(pol).append('\n');
            }
        } catch (Throwable e) { out.append("מדיניות: ").append(e).append('\n'); }
        return out.append("\n=== סוף ===\n").toString();
    }

    /** הדוח נשמר בקובץ ונשלח לשרת כשיש חיבור כלשהו (אחרת – בפתיחה הבאה של האפליקציה) */
    static void save(Context c, String report) {
        try { java.nio.file.Files.write(new java.io.File(c.getFilesDir(), "simcheck.txt").toPath(), report.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (Throwable e) { /* בלי */ }
        flush(c);
    }
    static void flush(Context c) {
        final java.io.File f = new java.io.File(c.getFilesDir(), "simcheck.txt");
        if (!f.exists()) return;
        new Thread(() -> {
            try {
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                HttpURLConnection h = (HttpURLConnection) new URL("https://isrnb.duckdns.org/byd?step=send").openConnection();
                h.setRequestMethod("POST"); h.setDoOutput(true); h.setConnectTimeout(15000); h.setReadTimeout(30000);
                h.setRequestProperty("Content-Type", "text/plain; charset=utf-8"); h.setFixedLengthStreamingMode(b.length);
                try (java.io.OutputStream o = h.getOutputStream()) { o.write(b); }
                if (h.getResponseCode() == 200) f.delete();
                h.disconnect();
            } catch (Throwable e) { /* בלי חיבור – בפעם הבאה */ }
        }).start();
    }

    static String test(Network n, String name, String url) {
        StringBuilder s = new StringBuilder("  ").append(name).append(": ");
        long t0 = System.currentTimeMillis();
        try {
            InetAddress[] a = n.getAllByName(new URL(url).getHost());
            s.append("DNS ✓ (").append(a.length > 0 ? a[0].getHostAddress() : "-").append(") ");
        } catch (Throwable e) { return s.append("DNS ✗ ").append(e.getClass().getSimpleName()).append(' ').append(String.valueOf(e.getMessage())).toString(); }
        try {
            HttpURLConnection h = (HttpURLConnection) n.openConnection(new URL(url));
            h.setConnectTimeout(7000); h.setReadTimeout(7000); h.setInstanceFollowRedirects(false); h.setRequestMethod("HEAD");
            int code = h.getResponseCode(); h.disconnect();
            s.append("חיבור ✓ ").append(code);
        } catch (Throwable e) { s.append("חיבור ✗ ").append(e.getClass().getSimpleName()).append(' ').append(String.valueOf(e.getMessage())); }
        return s.append(" (").append(System.currentTimeMillis() - t0).append(" ms)").toString();
    }
}
