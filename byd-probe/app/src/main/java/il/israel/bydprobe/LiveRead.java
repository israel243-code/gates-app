package il.israel.bydprobe;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** בדיקה 2: קריאה בפועל של ערכים מהרכב דרך הממשק של BYD. רק פעולות get / is / has – בלי שום set. */
public class LiveRead {
    static final String PKG = "android.hardware.bydauto.";
    // הסדר = מה שהכי חשוב לבעלים קודם
    static final String[] DEVICES = {
            "statistic.BYDAutoStatisticDevice", "charging.BYDAutoChargingDevice", "energy.BYDAutoEnergyDevice",
            "doorlock.BYDAutoDoorLockDevice", "bodywork.BYDAutoBodyworkDevice", "panorama.BYDAutoPanoramaDevice",
            "ac.BYDAutoAcDevice", "speed.BYDAutoSpeedDevice", "tyre.BYDAutoTyreDevice", "gearbox.BYDAutoGearboxDevice",
            "light.BYDAutoLightDevice", "instrument.BYDAutoInstrumentDevice", "power.BYDAutoPowerDevice",
            "security.BYDAutoSecurityDevice", "sensor.BYDAutoSensorDevice", "vehicledata.BYDAutoVehicleDataDevice",
            "motor.BYDAutoMotorDevice", "safetybelt.BYDAutoSafetyBeltDevice", "radar.BYDAutoRadarDevice",
            "location.BYDAutoLocationDevice", "version.BYDAutoVersionDevice"};

    public static void run(Context c, StringBuilder out, Probe.Progress p) {
        for (String d : DEVICES) {
            if (p != null) p.step("קורא: " + d.substring(d.indexOf('.') + 1));
            device(c, PKG + d, out);
        }
    }

    private static void device(Context c, String cls, StringBuilder out) {
        out.append("\n-- ● ").append(cls.substring(PKG.length())).append('\n');
        Object dev;
        Class<?> k;
        try {
            k = Class.forName(cls);
            dev = k.getMethod("getInstance", Context.class).invoke(null, c);
            if (dev == null) { out.append("  getInstance החזיר null\n"); return; }
        } catch (Throwable e) { out.append("  ⚠️ לא נפתח: ").append(cause(e)).append('\n'); return; }

        List<Method> ms = new ArrayList<>(Arrays.asList(k.getMethods()));
        ms.sort(Comparator.comparing(Method::getName));
        int ok = 0, denied = 0, fail = 0;
        for (Method m : ms) {
            String n = m.getName();
            if (!(n.startsWith("get") || n.startsWith("is") || n.startsWith("has"))) continue;
            if (n.equals("getInstance") || n.equals("getClass") || m.getDeclaringClass() == Object.class) continue;
            if (m.getReturnType() == void.class || Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] ps = m.getParameterTypes();
            try {
                if (ps.length == 0) {
                    out.append("  ").append(n).append(" = ").append(show(m.invoke(dev))).append('\n'); ok++;
                } else if (ps.length == 1 && ps[0] == int.class) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i <= 6; i++) {
                        try { b.append(i).append("→").append(show(m.invoke(dev, i))).append("  "); }
                        catch (Throwable e) { b.append(i).append("→✗  "); }
                    }
                    out.append("  ").append(n).append("(0..6) = ").append(b).append('\n'); ok++;
                }
            } catch (Throwable e) {
                String why = cause(e);
                if (why.contains("Security") || why.contains("ermission")) denied++; else fail++;
                out.append("  ").append(n).append(" ✗ ").append(why).append('\n');
            }
        }
        out.append("  סיכום: נקראו ").append(ok).append(" | נחסמו (הרשאה) ").append(denied).append(" | שגיאה ").append(fail).append('\n');

        // קבועים: עוזרים להבין מה המספרים אומרים (למשל 1 = בטעינה)
        StringBuilder cs = new StringBuilder();
        int n = 0;
        for (Field f : k.getFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod) || !Modifier.isFinal(mod)) continue;
            if (f.getType() != int.class && f.getType() != String.class) continue;
            try { cs.append(f.getName()).append('=').append(f.get(null)).append("  "); } catch (Throwable ignored) {}
            if (++n >= 400) break;
        }
        if (cs.length() > 0) out.append("  קבועים: ").append(cs).append('\n');
    }

    private static String show(Object v) {
        if (v == null) return "null";
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof double[]) return Arrays.toString((double[]) v);
        if (v instanceof float[]) return Arrays.toString((float[]) v);
        if (v instanceof byte[]) return "bytes" + ((byte[]) v).length;
        if (v instanceof Object[]) return Arrays.deepToString((Object[]) v);
        String s = String.valueOf(v);
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    static String cause(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m == null ? "" : ": " + (m.length() > 160 ? m.substring(0, 160) : m));
    }

    /** מצלמות: מנסה לפתוח כל מצלמה (ממשק ישן) ולקבל תמונה אחת. מדווח רק אם התמונה לא שחורה – בלי לשמור אותה. */
    @SuppressWarnings("deprecation")
    public static void cameras(StringBuilder out, Probe.Progress p) {
        int n;
        try { n = android.hardware.Camera.getNumberOfCameras(); } catch (Throwable e) { n = 0; }
        out.append("מספר מצלמות לפי המערכת: ").append(n).append(" (בודק 0..7 בכל מקרה)\n");
        HandlerThread ht = new HandlerThread("cam");
        ht.start();
        Handler h = new Handler(ht.getLooper());
        for (int i = 0; i < Math.max(n, 8); i++) {
            final int id = i;
            if (p != null) p.step("מצלמה " + id);
            final String[] res = {"⏱️ לא ענתה"};
            final CountDownLatch done = new CountDownLatch(1);
            h.post(() -> {
                android.hardware.Camera cam = null;
                try {
                    cam = android.hardware.Camera.open(id);
                    android.hardware.Camera.Parameters pr = cam.getParameters();
                    android.hardware.Camera.Size sz = pr.getPreviewSize();
                    StringBuilder sizes = new StringBuilder();
                    for (android.hardware.Camera.Size s : pr.getSupportedPreviewSizes()) sizes.append(s.width).append('x').append(s.height).append(' ');
                    cam.setPreviewTexture(new SurfaceTexture(10 + id));
                    final android.hardware.Camera fc = cam;
                    final String info = "נפתחה ✅ | תצוגה " + sz.width + "x" + sz.height + " | גדלים: " + sizes;
                    res[0] = info + " | אין תמונה";
                    cam.setOneShotPreviewCallback((data, c2) -> {
                        long sum = 0; int cnt = 0;
                        int lim = Math.min(data.length, sz.width * sz.height);
                        for (int j = 0; j < lim; j += 97) { sum += data[j] & 0xff; cnt++; }
                        long avg = cnt == 0 ? 0 : sum / cnt;
                        res[0] = info + " | תמונה התקבלה, בהירות ממוצעת " + avg + (avg < 8 ? " (שחורה)" : " (יש תמונה)");
                        try { fc.stopPreview(); fc.release(); } catch (Throwable ignored) {}
                        done.countDown();
                    });
                    cam.startPreview();
                    h.postDelayed(() -> { try { fc.stopPreview(); fc.release(); } catch (Throwable ignored) {} done.countDown(); }, 4000);
                } catch (Throwable e) {
                    res[0] = "✗ " + cause(e);
                    if (cam != null) try { cam.release(); } catch (Throwable ignored) {}
                    done.countDown();
                }
            });
            try { done.await(6, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            out.append("  מצלמה ").append(id).append(": ").append(res[0]).append('\n');
        }
        ht.quitSafely();
    }
}
