package il.israel.gates;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 🎥 סוכן קליפים במסך הרכב (20/11): כל דקה שולח לשרת את רשימת ההקלטות של ה-DVR של BYD (360Videos, 3 ימים אחרונים),
 * ומעלה בחתיכות רק קליפים שהבעלים ביקש בדשבורד. רק קריאה של קבצים – לא נוגע במצלמות. פועל רק במסך הרכב, עם הקישור של הבעלים.
 */
final class ClipAgent {
    private ClipAgent() {}

    static final String BASE = "https://isrnb.duckdns.org/byd";
    static final long KEEP_MS = 3L * 24 * 3600 * 1000;
    static final int CHUNK = 4 * 1024 * 1024;
    private static ScheduledExecutorService ex;
    private static final Map<String, File> FILES = new HashMap<>();

    static synchronized void start(Context c) {
        if (ex != null || !Prefs.carScreen(c)) return;
        Net.start(c);
        final Context app = c.getApplicationContext();
        ex = Executors.newSingleThreadScheduledExecutor();
        loop(app, 5); // 22/11: הקצב נקבע לפי השרת – 60 שניות, או 5 כשלוחצים "בדוק עכשיו" בדף
        LocalEngine.log("🎥 סוכן הקליפים התחיל");
    }

    static volatile int every = 60;
    private static void loop(Context app, int delay) {
        ScheduledExecutorService e = ex; if (e == null) return;
        try { e.schedule(() -> { try { tick(app); } catch (Throwable t) { lastErr = t.getClass().getSimpleName(); LocalEngine.log("🎥 שגיאה: " + lastErr); } loop(app, every); }, delay, TimeUnit.SECONDS); } catch (Exception x) { /* נעצר */ }
    }
    static volatile String lastErr = "";
    static volatile int fails = 0; // כמה פעמים ברצף לא הייתה תקשורת לשרת
    // 📶 22/11: איזו תקשורת יש לאפליקציה ברכב – רשת אלחוטית (נקודה חמה) / סים של הרכב / כלום
    static String netType(Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "none";
            if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) return "cell";
            if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) return "eth";
            if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH)) return "bt";
            return "other";
        } catch (Exception e) { return "?"; }
    }
    static volatile int dRoots, dDirs, dDays;
    static synchronized void stop() { if (ex != null) { ex.shutdownNow(); ex = null; } }

    private static String token(Context c) {
        String u = Prefs.url(c);
        return Prefs.valid(u) ? Uri.parse(u).getQueryParameter("t") : null;
    }

    private static void tick(Context c) throws Exception {
        String t = token(c);
        if (t == null) return;
        JSONArray items = new JSONArray();
        synchronized (FILES) {
            FILES.clear();
            for (File f : scan()) {
                String id = id(f.getAbsolutePath());
                FILES.put(id, f);
                String n = f.getName();
                items.put(new JSONObject().put("id", id).put("k", kindOf(f))
                        .put("s", f.length()).put("t", f.lastModified()));
            }
        }
        boolean perm = android.os.Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        int ver = 0; try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { /* אין */ }
        JSONObject diag = new JSONObject().put("perm", perm).put("roots", dRoots).put("dirs", dDirs).put("days", dDays).put("files", items.length()).put("ver", ver).put("err", lastErr).put("net", Net.type(c)).put("sim", Net.bound ? "bound" : Net.cellBlocked ? "blocked" : Net.cellSeen ? "avail" : "no").put("fails", fails).put("paths", dPaths); // 22/11: למה אין קליפים – מוצג בדף
        String r = post(BASE + "?step=clipidx&t=" + Uri.encode(t), new JSONObject().put("items", items).put("diag", diag).toString().getBytes(StandardCharsets.UTF_8), "application/json");
        if (r == null) { fails++; return; }
        lastErr = ""; fails = 0;
        JSONObject jr = new JSONObject(r); every = Math.max(5, Math.min(300, jr.optInt("every", 60)));
        JSONArray want = jr.optJSONArray("want");
        for (int i = 0; want != null && i < want.length(); i++) {
            JSONObject w = want.getJSONObject(i);
            File f; synchronized (FILES) { f = FILES.get(w.optString("id")); }
            if (f != null) upload(t, w.optString("id"), f, w.optLong("have"));
        }
    }

    /** 24/11: חיפוש חכם – כל סרטון מ-3 הימים האחרונים בכל האחסון (לא רק 360Videos של BYD), כי אפליקציות הקלטה שונות שומרות בתיקיות שונות.
     *  הסריקה המלאה של העץ – פעם ב-10 דקות (מוצאת "תיקיות סרטונים"); בכל דקה – רק התיקיות שנמצאו. */
    static volatile String dPaths = "";
    private static List<File> vdirs = new ArrayList<>();
    private static long vdirsAt = 0;
    private static boolean isVid(String n) { n = n.toLowerCase(java.util.Locale.ROOT); return n.endsWith(".mp4") || n.endsWith(".ts") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov"); }
    private static void walk(File d, int depth, long since, java.util.Set<File> found) {
        if (depth > 6 || found.size() > 80) return;
        File[] fs = d.listFiles(); if (fs == null) return;
        boolean has = false;
        for (File f : fs) {
            String n = f.getName();
            if (f.isDirectory()) { if (n.startsWith(".") || n.equals("Android") || n.equalsIgnoreCase("LOST.DIR") || n.equalsIgnoreCase("Thumbs") || n.equalsIgnoreCase("AlertThumbs")) continue; if (f.lastModified() >= since - 86400000L || depth < 2) walk(f, depth + 1, since, found); }
            else if (!has && isVid(n) && f.lastModified() >= since && f.length() > 300000) has = true;
        }
        if (has) found.add(d);
    }
    static String kindOf(File f) {
        String n = f.getName(), p = f.getPath().toLowerCase(java.util.Locale.ROOT);
        if (n.startsWith("S_") || p.contains("sentry") || p.contains("guard") || p.contains("parking") || p.contains("event") || p.contains("shomer")) return "sentry";
        if (n.startsWith("R_") || p.contains("dvr") || p.contains("dashcam") || p.contains("drive") || p.contains("record") || p.contains("normal")) return "drive";
        return "other";
    }
    static List<File> scan() {
        List<File> out = new ArrayList<>();
        List<File> roots = new ArrayList<>();
        roots.add(new File("/storage/emulated/0"));
        File[] st = new File("/storage").listFiles();
        if (st != null) for (File f : st) if (!f.getName().equals("self") && !f.getName().equals("emulated")) roots.add(f);
        long since = System.currentTimeMillis() - KEEP_MS;
        if (System.currentTimeMillis() - vdirsAt > 600000L || vdirs.isEmpty() && System.currentTimeMillis() - vdirsAt > 120000L) {
            java.util.Set<File> found = new java.util.LinkedHashSet<>();
            for (File r : roots) walk(r, 0, since, found);
            vdirs = new ArrayList<>(found); vdirsAt = System.currentTimeMillis();
            StringBuilder b = new StringBuilder(); for (File d : vdirs) { if (b.length() > 600) break; b.append(d.getPath()).append(" | "); }
            dPaths = b.toString();
            LocalEngine.log("🎥 תיקיות סרטונים: " + vdirs.size() + (vdirs.isEmpty() ? "" : " – " + dPaths));
        }
        int ny = 0;
        for (File d : vdirs) {
            File[] fs = d.listFiles(); if (fs == null) continue; ny++;
            for (File f : fs) if (f.isFile() && isVid(f.getName()) && f.lastModified() >= since && f.length() > 300000) out.add(f);
        }
        dRoots = roots.size(); dDirs = vdirs.size(); dDays = ny;
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out.size() > 600 ? out.subList(0, 600) : out;
    }

    private static void upload(String t, String id, File f, long have) throws Exception {
        long total = f.length();
        if (System.currentTimeMillis() - f.lastModified() < 15000) return; // עדיין מוקלט
        LocalEngine.log("🎥 מעלה קליפ " + f.getName() + " (" + total / 1048576 + "MB)");
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long off = have;
            while (off < total) {
                int n = (int) Math.min(CHUNK, total - off);
                byte[] b = new byte[n];
                raf.seek(off); raf.readFully(b);
                String r = post(BASE + "?step=clipup&t=" + Uri.encode(t) + "&id=" + id + "&off=" + off + "&total=" + total, b, "application/octet-stream");
                if (r == null) return;
                JSONObject j = new JSONObject(r);
                if (!j.optBoolean("ok")) { if (j.has("have")) { off = j.getLong("have"); continue; } LocalEngine.log("🎥 השרת דחה: " + j.optString("r")); return; }
                off = j.optLong("have", off + n);
                if (j.optBoolean("done")) { LocalEngine.log("🎥 הקליפ הועלה"); return; }
            }
        }
    }

    private static String id(String path) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(path.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    private static String post(String url, byte[] body, String type) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setRequestMethod("POST"); h.setDoOutput(true); h.setConnectTimeout(15000); h.setReadTimeout(120000);
            h.setRequestProperty("Content-Type", type);
            h.setFixedLengthStreamingMode(body.length);
            try (OutputStream o = h.getOutputStream()) { o.write(body); }
            InputStream is = h.getResponseCode() < 400 ? h.getInputStream() : h.getErrorStream();
            if (is == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        } catch (Exception e) { return null; }
    }
}
