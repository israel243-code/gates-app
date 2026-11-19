package il.israel.bydprobe;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Size;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** אוסף מידע בלבד: מה המערכת ברכב חושפת. לא שולח פקודות ולא משנה כלום. */
public class Probe {
    public interface Progress { void step(String what); }

    // שמות שמעניינים אותנו בחבילות, שירותים ומחלקות
    static final Pattern HOT = Pattern.compile("(?i)byd|dilink|bydauto|autoservice|android\\.car|vehicle|\\bcar\\b|\\.car\\.|avm|panoram|360|camera|electro|tbox|telematic|charg|battery|bms|hvac|aircon|can(bus)?\\b");

    private final Context c;
    private final StringBuilder out = new StringBuilder();
    private final Progress p;

    public Probe(Context c, Progress p) { this.c = c; this.p = p; }

    /** בדיקה 2 (קצרה): הרשאות + קריאה בפועל מהרכב + מצלמות */
    public String runLive() {
        line("בדיקת רכב 3 (חלונות נתונים פתוחים) – " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        line("גרסת אפליקציה: " + ver());
        section("א. ההרשאות של אפליקציית הבדיקה", this::mine);
        section("ב. חלונות נתונים פתוחים של BYD (קריאה בלבד)", () -> Providers.run(c, out, p));
        line("\n=== סוף ===");
        return out.toString();
    }

    /** בדיקה 4: מצלמות – קבצי DVR, שירות המצלמות (קריאה בלבד), USB */
    public String runCams() {
        line("בדיקת רכב 4 (מצלמות: קבצים, שירות, USB) – " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        line("גרסת אפליקציה: " + ver());
        section("א. אחסון וקבצי הקלטה (DVR)", () -> CamProbe.storage(c, out));
        section("ב. שירות המצלמות של BYD (קריאה בלבד)", () -> CamProbe.service(out));
        section("ג. USB ומצלמות חיצוניות", () -> CamProbe.usb(c, out));
        section("ד. מצלמות (Camera2)", this::cameras);
        line("\n=== סוף ===");
        return out.toString();
    }

    private String ver() { try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Throwable e) { return "?"; } }

    public String run() {
        line("בדיקת רכב – " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        section("1. מערכת", this::system);
        section("2. מאפייני מערכת (getprop)", this::props);
        section("3. מצלמות", this::cameras);
        section("4. חיישנים", this::sensors);
        section("5. שירותי מערכת", this::services);
        section("6. הרשאות שהוגדרו במערכת (רכב / BYD)", this::permissions);
        section("7. חבילות", this::packages);
        section("8. מחלקות של BYD / רכב בתוך המערכת", this::classes);
        section("9. קבצים ותיקיות", this::files);
        section("10. רשת", this::network);
        section("11. ההרשאות של אפליקציית הבדיקה", this::mine);
        line("\n=== סוף ===");
        return out.toString();
    }

    // ---------- עזרים ----------
    interface Part { void go() throws Throwable; }

    private void section(String title, Part part) {
        if (p != null) p.step(title);
        out.append("\n\n========== ").append(title).append(" ==========\n");
        long t = System.currentTimeMillis();
        try { part.go(); } catch (Throwable e) { line("⚠️ שגיאה: " + e); }
        line("(" + (System.currentTimeMillis() - t) + " ms)");
    }

    private void line(String s) { out.append(s).append('\n'); }

    private static String exec(String... cmd) {
        try {
            Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String l; int n = 0;
                while ((l = r.readLine()) != null && n++ < 6000) b.append(l).append('\n');
            }
            pr.waitFor();
            return b.toString();
        } catch (Throwable e) { return "⚠️ " + e + "\n"; }
    }

    // ---------- 1 ----------
    private void system() {
        line("יצרן: " + Build.MANUFACTURER + " | מותג: " + Build.BRAND + " | דגם: " + Build.MODEL);
        line("מכשיר: " + Build.DEVICE + " | מוצר: " + Build.PRODUCT + " | חומרה: " + Build.HARDWARE + " | לוח: " + Build.BOARD);
        line("אנדרואיד: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ") | תיקון אבטחה: " + (Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "?"));
        line("גרסת מערכת: " + Build.DISPLAY);
        line("טביעת גרסה: " + Build.FINGERPRINT);
        line("מעבדים: " + Arrays.toString(Build.SUPPORTED_ABIS));
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        line("מסך: " + dm.widthPixels + "x" + dm.heightPixels + " צפיפות " + dm.densityDpi);
        PackageManager pm = c.getPackageManager();
        line("\n-- יכולות (features):");
        FeatureInfo[] fs = pm.getSystemAvailableFeatures();
        TreeSet<String> names = new TreeSet<>();
        if (fs != null) for (FeatureInfo f : fs) if (f.name != null) names.add(f.name);
        for (String n : names) line("  " + n);
        line("\n-- ספריות משותפות:");
        String[] libs = pm.getSystemSharedLibraryNames();
        if (libs != null) { Arrays.sort(libs); for (String l : libs) line("  " + l); }
    }

    // ---------- 2 ----------
    private void props() { out.append(exec("getprop")); }

    // ---------- 3 ----------
    private void cameras() throws Throwable {
        CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        String[] ids = cm.getCameraIdList();
        line("מצלמות (Camera2): " + ids.length + " → " + Arrays.toString(ids));
        for (String id : ids) {
            line("\n-- מצלמה " + id);
            try {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                line("  כיוון: " + (facing == null ? "?" : facing == 0 ? "קדמית" : facing == 1 ? "אחורית" : "חיצונית") + " (" + facing + ")");
                Integer lvl = ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                line("  רמת תמיכה: " + lvl);
                if (Build.VERSION.SDK_INT >= 28) line("  מצלמות פיזיות: " + ch.getPhysicalCameraIds());
                StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    Size[] y = map.getOutputSizes(ImageFormat.YUV_420_888);
                    Size[] j = map.getOutputSizes(ImageFormat.JPEG);
                    line("  גדלים YUV: " + (y == null ? "-" : Arrays.toString(y)));
                    line("  גדלים JPEG: " + (j == null ? "-" : Arrays.toString(j)));
                }
                int[] caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                line("  יכולות: " + (caps == null ? "-" : Arrays.toString(caps)));
            } catch (Throwable e) { line("  ⚠️ " + e); }
        }
        try {
            @SuppressWarnings("deprecation") int n = android.hardware.Camera.getNumberOfCameras();
            line("\nמצלמות (ממשק ישן): " + n);
        } catch (Throwable e) { line("ממשק ישן: ⚠️ " + e); }
        line("\n-- התקני וידאו ב-/dev:");
        File[] dev = new File("/dev").listFiles();
        if (dev != null) {
            List<String> v = new ArrayList<>();
            for (File f : dev) if (f.getName().matches("(?i)video.*|.*cam.*|.*avm.*|v4l.*|media.*")) v.add(f.getName());
            Collections.sort(v);
            line("  " + v);
        } else line("  (אין גישה)");
    }

    // ---------- 4 ----------
    private void sensors() {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> l = sm.getSensorList(Sensor.TYPE_ALL);
        line("סה\"כ: " + l.size());
        for (Sensor s : l) line("  [" + s.getType() + "] " + s.getName() + " | " + s.getVendor() + " | " + (Build.VERSION.SDK_INT >= 20 ? s.getStringType() : ""));
    }

    // ---------- 5 ----------
    private void services() {
        Set<String> all = new TreeSet<>();
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            String[] s = (String[]) sm.getMethod("listServices").invoke(null);
            if (s != null) all.addAll(Arrays.asList(s));
            line("דרך ServiceManager: " + all.size());
        } catch (Throwable e) { line("ServiceManager: ⚠️ " + e); }
        if (all.isEmpty()) {
            String r = exec("service", "list");
            line("דרך 'service list':");
            out.append(r);
            return;
        }
        line("\n-- שירותים שנראים קשורים לרכב / BYD:");
        for (String s : all) if (HOT.matcher(s).find()) line("  ★ " + s);
        line("\n-- כל השירותים:");
        for (String s : all) line("  " + s);
    }

    // ---------- 6 ----------
    private void permissions() {
        PackageManager pm = c.getPackageManager();
        List<PermissionInfo> perms = new ArrayList<>();
        try {
            List<PermissionGroupInfo> gs = pm.getAllPermissionGroups(0);
            for (PermissionGroupInfo g : gs) try { perms.addAll(pm.queryPermissionsByGroup(g.name, 0)); } catch (Throwable ignored) {}
            try { perms.addAll(pm.queryPermissionsByGroup(null, 0)); } catch (Throwable ignored) {}
        } catch (Throwable e) { line("⚠️ " + e); }
        Set<String> seen = new LinkedHashSet<>();
        List<String> rows = new ArrayList<>();
        for (PermissionInfo pi : perms) {
            if (pi == null || !seen.add(pi.name)) continue;
            if (!HOT.matcher(pi.name + " " + pi.packageName).find()) continue;
            rows.add(prot(pi) + "  " + pi.name + "  (" + pi.packageName + ")");
        }
        Collections.sort(rows);
        line("סה\"כ הרשאות במערכת: " + seen.size() + " | קשורות לרכב: " + rows.size());
        line("מקרא: רגילה = אפשר לבקש בלי אישור; מסוכנת = המשתמש מאשר; חתימה / מערכת = רק לאפליקציות של היצרן");
        for (String r : rows) line("  " + r);
    }

    private static String prot(PermissionInfo pi) {
        int base = pi.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
        String s = base == PermissionInfo.PROTECTION_NORMAL ? "[רגילה]" : base == PermissionInfo.PROTECTION_DANGEROUS ? "[מסוכנת]"
                : base == PermissionInfo.PROTECTION_SIGNATURE ? "[חתימה]" : "[מערכת/" + base + "]";
        if ((pi.protectionLevel & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0) s += "[מיוחסת]";
        return s;
    }

    // ---------- 7 ----------
    private void packages() {
        PackageManager pm = c.getPackageManager();
        List<PackageInfo> all = pm.getInstalledPackages(0);
        List<String> names = new ArrayList<>();
        for (PackageInfo pi : all) names.add(pi.packageName);
        Collections.sort(names);
        line("סה\"כ חבילות: " + names.size());
        List<String> hot = new ArrayList<>();
        for (String n : names) if (HOT.matcher(n).find()) hot.add(n);
        line("קשורות לרכב / BYD: " + hot.size());
        int flags = PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS
                | PackageManager.GET_RECEIVERS | PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA;
        for (String n : hot) {
            line("\n-- 📦 " + n);
            try {
                PackageInfo pi = pm.getPackageInfo(n, flags);
                boolean sys = pi.applicationInfo != null && (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                line("  גרסה: " + pi.versionName + " | מערכת: " + (sys ? "כן" : "לא") + " | שם: " + (pi.applicationInfo != null ? pm.getApplicationLabel(pi.applicationInfo) : ""));
                if (pi.applicationInfo != null) line("  נתיב: " + pi.applicationInfo.sourceDir + " | uid " + pi.applicationInfo.uid);
                if (pi.sharedUserId != null) line("  משתמש משותף: " + pi.sharedUserId);
                if (pi.permissions != null) for (PermissionInfo p2 : pi.permissions) line("  מגדירה הרשאה: " + prot(p2) + " " + p2.name);
                if (pi.requestedPermissions != null) line("  מבקשת: " + Arrays.toString(pi.requestedPermissions));
                if (pi.services != null) for (ServiceInfo s : pi.services) line("  שירות: " + s.name + (s.exported ? " [פתוח]" : "") + (s.permission != null ? " הרשאה=" + s.permission : ""));
                if (pi.providers != null) for (ProviderInfo pr : pi.providers) line("  ספק נתונים: " + pr.authority + (pr.exported ? " [פתוח]" : "") + " קריאה=" + pr.readPermission + " כתיבה=" + pr.writePermission);
                if (pi.receivers != null) for (ActivityInfo r : pi.receivers) if (r.exported) line("  מקלט: " + r.name + (r.permission != null ? " הרשאה=" + r.permission : ""));
                if (pi.activities != null) for (ActivityInfo a : pi.activities) if (a.exported) line("  מסך: " + a.name);
            } catch (Throwable e) { line("  ⚠️ " + e); }
        }
        line("\n-- כל החבילות:");
        for (String n : names) line("  " + n);
    }

    // ---------- 8 ----------
    private void classes() {
        Set<String> jars = new LinkedHashSet<>();
        for (String env : new String[]{"BOOTCLASSPATH", "SYSTEMSERVERCLASSPATH"}) {
            String v = System.getenv(env);
            line(env + "=" + v);
            if (v != null) for (String s : v.split(":")) if (!s.isEmpty()) jars.add(s);
        }
        File[] fw = new File("/system/framework").listFiles();
        if (fw != null) for (File f : fw) if (f.getName().endsWith(".jar")) jars.add(f.getAbsolutePath());
        Pattern want = Pattern.compile("(?i)byd|android\\.car\\.|\\.vehicle|autoservice|\\.avm|panoram");
        TreeSet<String> found = new TreeSet<>();
        int ok = 0, bad = 0;
        for (String j : jars) {
            try {
                @SuppressWarnings("deprecation") dalvik.system.DexFile df = new dalvik.system.DexFile(j);
                Enumeration<String> en = df.entries();
                int k = 0;
                while (en.hasMoreElements()) { String n = en.nextElement(); if (want.matcher(n).find()) { found.add(n + "   ← " + new File(j).getName()); k++; } }
                ok++;
                df.close();
            } catch (Throwable e) { bad++; }
        }
        line("קבצי ספרייה שנסרקו: " + ok + " | לא נקראו: " + bad + " | מחלקות שנמצאו: " + found.size());
        int n = 0;
        for (String s : found) { if (n++ > 4000) { line("  ...(קוצר)"); break; } line("  " + s); }

        // לכל מחלקה ראשית שנראית כמו "מנהל" / "התקן" – רשימת הפעולות הציבוריות שלה
        line("\n-- פעולות ציבוריות במחלקות המרכזיות:");
        int shown = 0;
        for (String s : found) {
            String cls = s.substring(0, s.indexOf("   ←"));
            if (cls.contains("$")) continue;
            if (!cls.matches("(?i).*(Device|Manager|Service|Helper|Listener|Callback|Constants|Car)$")) continue;
            if (shown++ > 120) { line("...(קוצר)"); break; }
            try {
                Class<?> k = Class.forName(cls);
                line("\n  ● " + cls);
                TreeSet<String> ms = new TreeSet<>();
                for (Method m : k.getDeclaredMethods()) {
                    if (!Modifier.isPublic(m.getModifiers())) continue;
                    StringBuilder b = new StringBuilder(Modifier.isStatic(m.getModifiers()) ? "static " : "");
                    b.append(m.getReturnType().getSimpleName()).append(' ').append(m.getName()).append('(');
                    Class<?>[] ps = m.getParameterTypes();
                    for (int i = 0; i < ps.length; i++) b.append(i > 0 ? ", " : "").append(ps[i].getSimpleName());
                    ms.add(b.append(')').toString());
                }
                int i = 0;
                for (String m : ms) { if (i++ > 150) { line("     ...(קוצר)"); break; } line("     " + m); }
            } catch (Throwable e) { line("  ● " + cls + " – לא נטענה: " + e.getClass().getSimpleName()); }
        }
        for (String probe : new String[]{"android.car.Car", "android.car.hardware.CarPropertyValue", "android.hardware.bydauto.BYDAutoFeatureIds"}) {
            try { Class.forName(probe); line("קיימת: " + probe); } catch (Throwable e) { line("לא קיימת: " + probe); }
        }
    }

    // ---------- 9 ----------
    private void files() {
        for (String d : new String[]{"/system/framework", "/system/app", "/system/priv-app", "/vendor/app", "/vendor/priv-app",
                "/product/app", "/product/priv-app", "/system/etc/permissions", "/vendor/etc/permissions", "/system/lib64/hw", "/vendor/lib64/hw", "/vendor/lib/hw"}) {
            File[] l = new File(d).listFiles();
            if (l == null) { line(d + ": (אין גישה / לא קיים)"); continue; }
            List<String> n = new ArrayList<>();
            for (File f : l) n.add(f.getName());
            Collections.sort(n);
            line(d + " (" + n.size() + "): " + n);
        }
    }

    // ---------- 10 ----------
    private void network() throws Throwable {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        if (en == null) { line("אין"); return; }
        for (NetworkInterface ni : Collections.list(en)) {
            if (!ni.isUp()) continue;
            List<String> a = new ArrayList<>();
            for (java.net.InetAddress ad : Collections.list(ni.getInetAddresses())) a.add(ad.getHostAddress());
            line("  " + ni.getName() + " " + a);
        }
    }

    // ---------- 11 ----------
    private void mine() throws Throwable {
        PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (pi.requestedPermissions != null) for (String s : pi.requestedPermissions)
            line("  " + s + " → " + (c.checkSelfPermission(s) == PackageManager.PERMISSION_GRANTED ? "✅ יש" : "❌ אין"));
    }
}
