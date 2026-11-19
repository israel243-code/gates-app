package il.israel.bydmod;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * סקריפט 3: מצלמות BYD – מי נחשב מצלמה תקפה, ורמת ההגנה של ההרשאות. קריאה בלבד:
 * רק פעולות שליפה סטטיות של BmmCameraInfo (קוראות מאפייני מערכת), מידע על הרשאות וחבילות. לא נפתחת מצלמה ולא נשלחת פקודה.
 */
public class Main {
    public static String run(Context c, Consumer<String> progress) {
        StringBuilder out = new StringBuilder();
        out.append("סקריפט 3: זיהוי מצלמות והרשאות (קריאה בלבד, בלי לפתוח מצלמה)\n");

        step(progress, "BmmCameraInfo");
        out.append("\n-- BmmCameraInfo (מי מצלמה תקפה)\n");
        try {
            Class<?> bi = Class.forName("android.hardware.BmmCameraInfo");
            try { out.append("  getCameraNumbers = ").append(bi.getMethod("getCameraNumbers").invoke(null)).append('\n'); } catch (Throwable e) { out.append("  getCameraNumbers ✗ ").append(cause(e)).append('\n'); }
            try { out.append("  getValidCameraTag = ").append(bi.getMethod("getValidCameraTag").invoke(null)).append('\n'); } catch (Throwable e) { out.append("  getValidCameraTag ✗ ").append(cause(e)).append('\n'); }
            Method valid = null, idOf = null;
            try { valid = bi.getMethod("isValidCamera", String.class); idOf = bi.getMethod("getCameraId", String.class); } catch (Throwable e) { out.append("  ✗ ").append(cause(e)).append('\n'); }
            for (Field f : bi.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(null);
                    if (f.getType() == String.class && f.getName().startsWith("CAMERA_CAR_")) {
                        String tag = (String) v;
                        out.append("  ").append(f.getName()).append(" = ").append(tag);
                        if (valid != null) try { out.append(" | תקפה: ").append(valid.invoke(null, tag)); } catch (Throwable e) { out.append(" | ✗ ").append(cause(e)); }
                        if (idOf != null) try { out.append(" | מזהה: ").append(idOf.invoke(null, tag)); } catch (Throwable e) { out.append(" | ✗ ").append(cause(e)); }
                        out.append('\n');
                    } else if (f.getType() == int.class) {
                        out.append("  ").append(f.getName()).append(" = ").append(v).append('\n');
                    }
                } catch (Throwable e) { out.append("  ").append(f.getName()).append(" ✗ ").append(cause(e)).append('\n'); }
            }
        } catch (Throwable e) { out.append("  ✗ ").append(cause(e)).append('\n'); }

        step(progress, "מאפייני מערכת");
        out.append("\n-- מאפייני מערכת (מצלמה / AVM / BMM)\n").append(sh("getprop", "(?i)cam|avm|bmm|pano"));

        step(progress, "הרשאות");
        out.append("\n-- רמת ההגנה של הרשאות מצלמה\n");
        PackageManager pm = c.getPackageManager();
        for (String p : new String[]{"android.permission.BYD_CAMERA", "android.permission.CAMERA", "android.permission.SYSTEM_CAMERA",
                "android.permission.BYDAUTO_PANORAMA_GET", "android.permission.BYDAUTO_PANORAMA_SET", "android.permission.BYDAUTO_PANORAMA_COMMON"}) {
            try {
                PermissionInfo pi = pm.getPermissionInfo(p, 0);
                int base = pi.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                out.append("  ").append(p).append(" | רמה: ").append(base == 0 ? "רגילה" : base == 1 ? "מסוכנת (המשתמש מאשר)" : base == 2 ? "חתימה" : "מערכת/" + base)
                        .append((pi.protectionLevel & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0 ? " + מיוחסת" : "").append(" | מוגדרת ב: ").append(pi.packageName)
                        .append(" | ללא הרשאה בהצהרה שלנו: ").append(c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED ? "יש" : "אין").append('\n');
            } catch (Throwable e) { out.append("  ").append(p).append(" | לא מוגדרת / ").append(e.getClass().getSimpleName()).append('\n'); }
        }

        step(progress, "אפליקציות מצלמה של BYD");
        out.append("\n-- אפליקציות המצלמה של BYD\n");
        int flags = PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_ACTIVITIES;
        for (String n : new String[]{"com.byd.bydcamera", "com.byd.cameramanager", "com.byd.auto_camera", "com.byd.cdrservice", "com.byd.cdr", "com.byd.avm"}) {
            try {
                PackageInfo pi = pm.getPackageInfo(n, flags);
                out.append("\n📦 ").append(n).append(" | גרסה ").append(pi.versionName).append(" | משתמש משותף: ").append(pi.sharedUserId).append('\n');
                if (pi.requestedPermissions != null) out.append("  מבקשת: ").append(Arrays.toString(pi.requestedPermissions)).append('\n');
                if (pi.services != null) for (ServiceInfo s : pi.services) out.append("  שירות: ").append(s.name).append(s.exported ? " [פתוח]" : "").append(s.permission != null ? " הרשאה=" + s.permission : "").append('\n');
                if (pi.receivers != null) for (ActivityInfo r : pi.receivers) if (r.exported) out.append("  מקלט: ").append(r.name).append(r.permission != null ? " הרשאה=" + r.permission : "").append('\n');
                if (pi.activities != null) for (ActivityInfo a : pi.activities) if (a.exported) out.append("  מסך: ").append(a.name).append('\n');
            } catch (Throwable e) { out.append("\n📦 ").append(n).append(": לא מותקנת\n"); }
        }
        out.append("\n=== סוף ===\n");
        return out.toString();
    }

    private static String cause(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static void step(Consumer<String> p, String s) { if (p != null) p.accept(s); }

    private static String sh(String cmd, String filter) {
        StringBuilder b = new StringBuilder();
        Pattern f = Pattern.compile(filter);
        try {
            Process pr = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String l; int n = 0;
                while ((l = r.readLine()) != null && n < 200) if (f.matcher(l).find()) { b.append("  ").append(l).append('\n'); n++; }
            }
            pr.waitFor();
        } catch (Throwable e) { b.append("  ✗ ").append(e).append('\n'); }
        return b.length() == 0 ? "  (אין)\n" : b.toString();
    }
}
