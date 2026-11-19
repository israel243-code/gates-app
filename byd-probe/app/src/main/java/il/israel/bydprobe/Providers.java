package il.israel.bydprobe;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** בדיקה 3: "חלונות נתונים" פתוחים של BYD (ספקי נתונים בלי הרשאה). רק קריאה (query / getType) – בלי כתיבה, בלי מחיקה. */
public class Providers {
    static final Pattern PKG = Pattern.compile("(?i)byd|gpack|dilink");
    static final Pattern URI = Pattern.compile("content://([A-Za-z0-9_.;]+)(/[A-Za-z0-9_./#*-]{0,80})?");
    static final Pattern WORD = Pattern.compile("(?i)soc|batter|elec|range|mileage|mile|charg|energy|power|lock|door|window|tyre|tire|temp|speed|gear|ac_|hvac|odo|status|vehicle|car");

    public static void run(Context c, StringBuilder out, Probe.Progress p) {
        long t0 = System.currentTimeMillis();
        PackageManager pm = c.getPackageManager();
        ContentResolver cr = c.getContentResolver();

        // 1. כל הספקים הפתוחים של BYD שאפשר לקרוא בלי הרשאה (או עם הרשאה שיש לנו)
        Map<String, String> auths = new LinkedHashMap<>(); // authority → package
        List<PackageInfo> pkgs = new ArrayList<>();
        for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PROVIDERS)) {
            if (!PKG.matcher(pi.packageName).find()) continue;
            pkgs.add(pi);
            if (pi.providers == null) continue;
            for (ProviderInfo pr : pi.providers) {
                if (!pr.exported || pr.authority == null) continue;
                if (pr.readPermission != null && c.checkSelfPermission(pr.readPermission) != PackageManager.PERMISSION_GRANTED) continue;
                for (String a : pr.authority.split(";")) auths.put(a, pi.packageName);
            }
        }
        out.append("ספקים פתוחים לקריאה: ").append(auths.size()).append('\n');
        for (Map.Entry<String, String> e : auths.entrySet()) out.append("  ").append(e.getKey()).append("  (").append(e.getValue()).append(")\n");

        // 2. חיפוש כתובות content:// בתוך קבצי האפליקציות של BYD (כדי לדעת אילו "טבלאות" יש)
        TreeSet<String> uris = new TreeSet<>();
        for (String a : auths.keySet()) uris.add("content://" + a);
        int scanned = 0;
        for (PackageInfo pi : pkgs) {
            if (System.currentTimeMillis() - t0 > 60000) { out.append("⏱️ הסריקה נעצרה אחרי דקה\n"); break; }
            if (p != null) p.step("סורק " + pi.packageName);
            try (ZipFile z = new ZipFile(pi.applicationInfo.sourceDir)) {
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry ze = en.nextElement();
                    if (!ze.getName().matches("classes\\d*\\.dex") || ze.getSize() > 40_000_000) continue;
                    String s = new String(read(z.getInputStream(ze)), StandardCharsets.ISO_8859_1);
                    Matcher m = URI.matcher(s);
                    while (m.find()) {
                        String a = m.group(1);
                        boolean ours = false;
                        for (String k : auths.keySet()) if (a.equals(k)) { ours = true; break; }
                        if (ours || PKG.matcher(a).find()) uris.add("content://" + a + (m.group(2) == null ? "" : m.group(2).replaceAll("[/#*]+$", "")));
                    }
                    scanned++;
                }
            } catch (Throwable e) { /* לא נקרא */ }
        }
        out.append("\nקבצי קוד שנסרקו: ").append(scanned).append(" | כתובות שנמצאו: ").append(uris.size()).append('\n');

        // 3. קריאה מכל כתובת: עמודות + עד 8 שורות
        int ok = 0, n = 0;
        for (String u : uris) {
            if (++n > 250) { out.append("...(קוצר)\n"); break; }
            if (System.currentTimeMillis() - t0 > 150000) { out.append("⏱️ הקריאה נעצרה (זמן)\n"); break; }
            if (p != null) p.step("קורא " + u);
            Uri uri = Uri.parse(u);
            String type = null;
            try { type = cr.getType(uri); } catch (Throwable ignored) {}
            try (Cursor q = cr.query(uri, null, null, null, null)) {
                if (q == null) { out.append("\n○ ").append(u).append(type != null ? "  type=" + type : "").append("  → אין תשובה\n"); continue; }
                ok++;
                out.append("\n● ").append(u).append(type != null ? "  type=" + type : "").append("  → ").append(q.getCount()).append(" שורות\n");
                String[] cols = q.getColumnNames();
                out.append("   עמודות: ").append(String.join(", ", cols)).append('\n');
                int r = 0;
                while (q.moveToNext() && r++ < 8) {
                    StringBuilder b = new StringBuilder("   ");
                    for (int i = 0; i < cols.length && i < 30; i++) {
                        String v;
                        try { v = q.getType(i) == Cursor.FIELD_TYPE_BLOB ? "blob" : q.getString(i); } catch (Throwable e) { v = "?"; }
                        if (v != null && v.length() > 80) v = v.substring(0, 80) + "…";
                        b.append(cols[i]).append('=').append(v).append(" | ");
                    }
                    out.append(b).append('\n');
                }
            } catch (Throwable e) {
                out.append("\n✗ ").append(u).append(type != null ? "  type=" + type : "").append("  → ").append(LiveRead.cause(e)).append('\n');
            }
        }
        out.append("\nנקראו בהצלחה: ").append(ok).append('\n');

        // 4. הגדרות מערכת עם שמות שנראים כמו נתוני רכב
        out.append("\n-- הגדרות מערכת (system / global / secure) שקשורות לרכב:\n");
        for (String tbl : new String[]{"system", "global", "secure"}) {
            try (Cursor q = cr.query(Uri.parse("content://settings/" + tbl), new String[]{"name", "value"}, null, null, null)) {
                int k = 0;
                while (q != null && q.moveToNext()) {
                    String name = q.getString(0), val = q.getString(1);
                    if (name == null || !(WORD.matcher(name).find() || name.toLowerCase().contains("byd"))) continue;
                    if (k++ > 120) break;
                    out.append("  ").append(tbl).append('/').append(name).append(" = ").append(val == null ? "null" : val.length() > 80 ? val.substring(0, 80) + "…" : val).append('\n');
                }
            } catch (Throwable e) { out.append("  ").append(tbl).append(": ").append(LiveRead.cause(e)).append('\n'); }
        }

        // 5. סוללה לפי אנדרואיד (במסך רכב – לרוב מצבר 12V או ערך קבוע)
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) out.append("\nסוללה לפי אנדרואיד: ").append(b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).append('/').append(b.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
                    .append(" מתח ").append(b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)).append(" טעינה ").append(b.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)).append('\n');
        } catch (Throwable ignored) {}
    }

    private static byte[] read(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[65536]; int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        }
    }
}
