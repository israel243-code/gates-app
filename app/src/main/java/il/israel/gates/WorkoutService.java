package il.israel.gates;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

// 🏃 מעקב ריצה / הליכה ב-GPS (21/11). רק בלחיצה בדף "הכושר שלי"; שירות חזית כדי שימשיך גם כשהמסך כבוי.
// בסיום: האימון נשמר בקובץ (workouts-pending) ונשלח לשרת דרך הקישור האישי (/gcfg?wk=1) – בלי רשת יישלח בפעם הבאה.
public class WorkoutService extends Service implements LocationListener {
    static final String CH = "workout_live"; // 23/11: ערוץ חדש בחשיבות רגילה (בלי צליל) – כדי שיוצג במסך הנעילה; הישן "workout" היה נמוך
    static volatile boolean on = false, paused = false, gps = false;
    static volatile String type = "walk", msg = "";
    static volatile long start = 0, pausedMs = 0, pauseAt = 0;
    static volatile double dist = 0;
    static volatile float acc = -1; // דיוק אחרון (מ׳) – להצגה כשמחכים ל-GPS
    static final ArrayList<double[]> PTS = new ArrayList<>();
    private static Location last;
    private static volatile boolean fusedSeen = false;
    private LocationManager lm;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { if (on) { update(); h.postDelayed(this, 5000); } } };

    static long dur() { long now = paused ? pauseAt : System.currentTimeMillis(); return start == 0 ? 0 : Math.max(0, (now - start - pausedMs) / 1000); }

    static void begin(Context c, String t) {
        if (on) return;
        type = "run".equals(t) ? "run" : "walk"; msg = "";
        Intent i = new Intent(c, WorkoutService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    static void togglePause() {
        if (!on) return;
        if (paused) { pausedMs += System.currentTimeMillis() - pauseAt; paused = false; last = null; } else { pauseAt = System.currentTimeMillis(); paused = true; }
    }
    static void finish(Context c) { if (on) c.stopService(new Intent(c, WorkoutService.class)); }

    static String track(int from) { StringBuilder sb = new StringBuilder("["); synchronized (PTS) { for (int k = Math.max(0, from); k < PTS.size(); k++) { double[] p = PTS.get(k); if (sb.length() > 1) sb.append(','); sb.append(String.format(Locale.US, "[%.6f,%.6f]", p[0], p[1])); } } return sb.append("]").toString(); }
    static String state(Context c) {
        return "{\"on\":" + on + ",\"paused\":" + paused + ",\"gps\":" + gps + ",\"type\":\"" + type + "\",\"dur\":" + dur() + ",\"dist\":" + Math.round(dist) +
            ",\"acc\":" + Math.round(acc) + ",\"pts\":" + PTS.size() + ",\"pending\":" + pendingCount(c) + ",\"msg\":\"" + msg.replace("\"", "'") + "\"}";
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i == null) { stopSelf(); return START_NOT_STICKY; } // לא ממשיכים אימון "רפאים" אחרי שהמערכת סגרה את האפליקציה
        if ("pause".equals(i.getAction())) { if (on) { togglePause(); update(); } return START_NOT_STICKY; } // כפתורים בהתראה
        if ("stop".equals(i.getAction())) { if (on) stopSelf(); return START_NOT_STICKY; }
        if (on) return START_NOT_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            try { nm.deleteNotificationChannel("workout"); } catch (Exception e) { /* אין */ }
            NotificationChannel ch = new NotificationChannel(CH, "אימון פעיל", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null); ch.enableVibration(false); ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, notif("מתחיל…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION); else startForeground(7, notif("מתחיל…"));
        } catch (Exception e) { msg = "⚠️ אין הרשאת מיקום – אשר מיקום בהגדרות"; stopSelf(); return START_NOT_STICKY; }
        synchronized (PTS) { PTS.clear(); }
        on = true; paused = false; gps = false; dist = 0; acc = -1; fusedSeen = false; start = System.currentTimeMillis(); pausedMs = 0; last = null;
        try { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gates:workout"); wl.acquire(6 * 3600 * 1000L); } catch (Exception e) { /* בלי */ }
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 3, this); } catch (SecurityException e) { msg = "⚠️ אין הרשאת מיקום"; stopSelf(); return START_NOT_STICKY; }
        try { if (Build.VERSION.SDK_INT >= 31 && lm.getAllProviders().contains("fused")) lm.requestLocationUpdates("fused", 2000, 3, this); } catch (Exception e) { /* בלי */ } // 21/11: גם מיקום משולב – מתחיל מהר יותר
        h.postDelayed(tick, 5000);
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location l) {
        if (!on || paused) return;
        if ("fused".equals(l.getProvider())) fusedSeen = true; else if (fusedSeen) return; // שני מקורות מיקום – משתמשים באחד, כדי שהמרחק לא יתנפח מקפיצות ביניהם
        if (l.hasAccuracy()) acc = l.getAccuracy();
        if (l.hasAccuracy() && l.getAccuracy() > 30) return; // GPS לא מדויק – מחכים (21/11: 30 מ׳; היה 25)
        gps = true;
        if (last != null) {
            float d = last.distanceTo(l); long dt = Math.max(1, (l.getTime() - last.getTime()) / 1000);
            if (d / dt > 12) return; // קפיצה לא הגיונית (מעל 43 קמ"ש) – מתעלמים
            if (d < 2) return; // עומד במקום
            dist += d;
        }
        last = l;
        synchronized (PTS) { if (PTS.size() < 8000) PTS.add(new double[]{ l.getLatitude(), l.getLongitude(), dur() }); }
    }
    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    private void update() { getSystemService(NotificationManager.class).notify(7, notif(text())); }
    private String text() {
        long s = dur(); String km = String.format(Locale.US, "%.2f", dist / 1000) + " ק\"מ";
        String pace = "";
        if (dist >= 50 && s > 0) { long p = Math.round(s / (dist / 1000)); if (p < 3600) pace = " · " + String.format(Locale.US, "%d:%02d", p / 60, p % 60) + " דק׳/ק\"מ"; }
        return (paused ? "⏸ מושהה · " : "") + km + pace + (gps ? "" : " · מחפש GPS");
    }
    // 23/11: מסך נעילה – שעון רץ, ק"מ, קצב, כפתורי השהה / סיום; באנדרואיד 16 – "עדכון חי" (מוצמד)
    private Notification notif(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 7, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pp = PendingIntent.getService(this, 71, new Intent(this, WorkoutService.class).setAction("pause"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent ps = PendingIntent.getService(this, 72, new Intent(this, WorkoutService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(("run".equals(type) ? "🏃 ריצה" : "🚶 הליכה")).setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_WORKOUT);
        if (on && !paused && start > 0) b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - dur() * 1000);
        else b.setShowWhen(false);
        if (on) {
            b.addAction(new Notification.Action.Builder(null, paused ? "▶ המשך" : "⏸ השהה", pp).build());
            b.addAction(new Notification.Action.Builder(null, "⏹ סיום ושמירה", ps).build());
        }
        if (Build.VERSION.SDK_INT >= 36) {
            try { Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true); } catch (Exception e) { /* אין */ }
            try { Notification.Builder.class.getMethod("setShortCriticalText", String.class).invoke(b, String.format(Locale.US, "%.1f ק\"מ", dist / 1000)); } catch (Exception e) { /* אין */ }
        }
        return b.build();
    }

    @Override public void onDestroy() {
        h.removeCallbacks(tick);
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception e) { /* אין */ }
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { /* אין */ }
        if (on) {
            if (paused) { pausedMs += System.currentTimeMillis() - pauseAt; paused = false; }
            long s = dur();
            if (s >= 30) {
                StringBuilder sb = new StringBuilder("{\"type\":\"" + type + "\",\"start\":" + start + ",\"dur\":" + s + ",\"dist\":" + Math.round(dist) + ",\"pts\":[");
                synchronized (PTS) { for (int k = 0; k < PTS.size(); k++) { double[] p = PTS.get(k); if (k > 0) sb.append(','); sb.append(String.format(Locale.US, "[%.5f,%.5f,%d]", p[0], p[1], (long) p[2])); } }
                sb.append("]}");
                savePending(this, sb.toString());
                msg = "✅ האימון נשמר: " + String.format(Locale.US, "%.2f", dist / 1000) + " ק\"מ";
            } else msg = "האימון היה קצר מחצי דקה – לא נשמר";
        }
        on = false; gps = false; start = 0;
        final Context app = getApplicationContext();
        new Thread(() -> sendPending(app)).start();
        super.onDestroy();
    }

    // ---- שליחה לשרת (עם שמירה בקובץ עד שמצליח) ----
    private static File dir(Context c) { File d = new File(c.getFilesDir(), "workouts"); d.mkdirs(); return d; }
    static int pendingCount(Context c) { String[] f = dir(c).list(); return f == null ? 0 : f.length; }
    static void savePending(Context c, String json) {
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir(c), System.currentTimeMillis() + ".json"))) { o.write(json.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { /* אין */ }
    }
    static synchronized void sendPending(Context c) {
        File[] fs = dir(c).listFiles(); if (fs == null) return;
        String s = Prefs.url(c); if (!Prefs.valid(s)) return;
        Uri u = Uri.parse(s);
        String url = u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/gcfg?wk=1&t=" + Uri.encode(u.getQueryParameter("t"));
        for (File f : fs) {
            try {
                String body = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(15000); h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); h.setRequestProperty("User-Agent", "GatesApp/1");
                try (OutputStream o = h.getOutputStream()) { o.write(("wk=" + Uri.encode(body)).getBytes(StandardCharsets.UTF_8)); }
                int code = h.getResponseCode();
                if (code == 200 || code == 403) f.delete(); // 403 = לא הקישור של הבעלים – אין טעם לנסות שוב
            } catch (Exception e) { return; } // בלי רשת – בפעם הבאה
        }
    }
}
