package il.israel.bydprobe;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/** בדיקה 4: איזו דרך למצלמות עובדת. קריאה בלבד: רשימת קבצים, שאילתת מצב אחת לשירות המצלמות, התקני USB. שום פקודת שליטה. */
public class CamProbe {
    static final Pattern DIR = Pattern.compile("(?i)dvr|cdr|video|record|camera|dcim|event|normal|loop|sentry|movie|avm|pano|lock");
    static final Pattern MEDIA = Pattern.compile("(?i).*\\.(mp4|avi|mkv|ts|mov|3gp|jpg|jpeg|png|h264)$");

    private static String fmt(long t) { return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(t)); }

    // ---------- א. אחסון וקבצי הקלטה ----------
    public static void storage(Context c, StringBuilder out) {
        List<File> roots = new ArrayList<>();
        roots.add(Environment.getExternalStorageDirectory());
        File[] st = new File("/storage").listFiles();
        out.append("/storage: ");
        if (st == null) out.append("(אין גישה)");
        else for (File f : st) { out.append(f.getName()).append(' '); String n = f.getName(); if (!n.equals("self") && !n.equals("emulated")) roots.add(f); }
        out.append('\n');
        long deadline = System.currentTimeMillis() + 40000;
        for (File r : roots) {
            out.append("\n-- ").append(r.getAbsolutePath());
            try { StatFs s = new StatFs(r.getAbsolutePath()); out.append(" | סה\"כ ").append(s.getTotalBytes() >> 30).append("GB | פנוי ").append(s.getAvailableBytes() >> 30).append("GB"); } catch (Throwable e) { out.append(" | (אין מידע על נפח)"); }
            out.append('\n');
            if (r.listFiles() == null) { out.append("  (אין גישה לקריאה)\n"); continue; }
            walk(r, 0, out, new long[]{6000, deadline});
        }
    }

    private static void walk(File d, int depth, StringBuilder out, long[] budget) {
        if (budget[0] <= 0 || System.currentTimeMillis() > budget[1]) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        int media = 0; long size = 0, newest = 0; String newestName = "";
        List<File> dirs = new ArrayList<>();
        for (File f : fs) {
            budget[0]--;
            if (f.isDirectory()) dirs.add(f);
            else if (MEDIA.matcher(f.getName()).matches()) { media++; size += f.length(); if (f.lastModified() > newest) { newest = f.lastModified(); newestName = f.getName(); } }
        }
        if (media > 0 || (depth > 0 && DIR.matcher(d.getName()).find()))
            out.append("  ").append(d.getAbsolutePath()).append(" | ").append(media).append(" קבצי מדיה | ").append(size >> 20).append("MB")
                    .append(media > 0 ? " | אחרון: " + newestName + " (" + fmt(newest) + ")" : "").append('\n');
        if (depth >= 4) return;
        for (File s : dirs) { if (depth == 0 && s.getName().equals("Android")) continue; walk(s, depth + 1, out, budget); }
    }

    // ---------- ב. שירות המצלמות של BYD (קריאה בלבד) ----------
    public static void service(StringBuilder out) throws Throwable {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method get = sm.getMethod("getService", String.class);
        for (String n : new String[]{"bydcameramanager", "bmmcameraserver", "IBYDCDRService"}) {
            IBinder b = (IBinder) get.invoke(null, n);
            if (b == null) { out.append(n).append(": לא נמצא\n"); continue; }
            String desc;
            try { desc = b.getInterfaceDescriptor(); } catch (Throwable e) { desc = "? " + LiveRead.cause(e); }
            out.append(n).append(": קיים | ממשק: ").append(desc).append(" | חי: ").append(b.pingBinder()).append('\n');
            // שאילתה אחת, קריאה בלבד: מי משתמש במצלמה עכשיו. רק אם הממשק הוא בדיוק מה שציפינו
            if (n.equals("bydcameramanager") && "android.hardware.IBYDCameraService".equals(desc)) {
                try {
                    Object svc = Class.forName("android.hardware.IBYDCameraService$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
                    Object u = svc.getClass().getMethod("getCurrentCameraUser").invoke(svc);
                    out.append("  getCurrentCameraUser = ").append(u == null ? "null (אף אחד לא משתמש במצלמה)" : String.valueOf(u)).append('\n');
                } catch (Throwable e) { out.append("  getCurrentCameraUser ✗ ").append(LiveRead.cause(e)).append('\n'); }
            }
        }
        out.append("לא נשלחה שום פקודת שליטה (openCamera / registerUser / setCommand)\n");
    }

    // ---------- ג. USB ומצלמות חיצוניות ----------
    public static void usb(Context c, StringBuilder out) throws Throwable {
        PackageManager pm = c.getPackageManager();
        out.append("מארח USB: ").append(pm.hasSystemFeature("android.hardware.usb.host"))
                .append(" | מצלמה חיצונית (feature): ").append(pm.hasSystemFeature("android.hardware.camera.external")).append('\n');
        UsbManager um = (UsbManager) c.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> m = um == null ? null : um.getDeviceList();
        out.append("התקני USB מחוברים: ").append(m == null ? 0 : m.size()).append('\n');
        if (m != null) for (UsbDevice d : m.values()) {
            StringBuilder cls = new StringBuilder();
            boolean video = d.getDeviceClass() == 14;
            for (int i = 0; i < d.getInterfaceCount(); i++) { UsbInterface f = d.getInterface(i); cls.append(f.getInterfaceClass()).append(' '); if (f.getInterfaceClass() == 14) video = true; }
            String name = ""; try { name = String.valueOf(d.getProductName()); } catch (Throwable ignored) {}
            out.append("  ").append(d.getDeviceName()).append(" | ").append(Integer.toHexString(d.getVendorId())).append(':').append(Integer.toHexString(d.getProductId()))
                    .append(" | ").append(name).append(" | מחלקות ").append(cls).append(video ? "| 🎥 וידאו (UVC)" : "").append('\n');
        }
        // האם מצלמה מופיעה / נעלמת בזמן ההרצה (חיבור USB)
        CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        final List<String> seen = new ArrayList<>();
        CameraManager.AvailabilityCallback cb = new CameraManager.AvailabilityCallback() {
            @Override public void onCameraAvailable(String id) { synchronized (seen) { seen.add(id); } }
        };
        cm.registerAvailabilityCallback(cb, new Handler(Looper.getMainLooper()));
        Thread.sleep(3000);
        cm.unregisterAvailabilityCallback(cb);
        out.append("מצלמות שדווחו כזמינות תוך 3 שניות: ").append(seen).append('\n');
    }
}
