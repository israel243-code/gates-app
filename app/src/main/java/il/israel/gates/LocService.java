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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * שידור מיקום ברקע (התראה קבועה): GPS כל ~3 שניות → השרת, שמחליט לבד אם לפתוח שער.
 * מתחיל כשהטלפון מתחבר לרכב שהוגדר (BtReceiver), או ידנית לזמן קבוע; נעצר בניתוק / בסוף הזמן / בכפתור "עצור".
 */
public class LocService extends Service implements LocationListener {
    static final String CH = "gates";
    static volatile boolean running = false;
    static volatile String state = "";

    private LocationManager lm;
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private Handler h;
    private long until = 0, lastSent = 0;
    private boolean firstFix = true;
    private PowerManager.WakeLock wl;
    private String why = "";
    private LocalEngine engine;

    static void start(Context c, String why, int minutes) {
        Intent i = new Intent(c, LocService.class).putExtra("why", why).putExtra("min", minutes);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void stop(Context c) { c.stopService(new Intent(c, LocService.class)); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        LocalEngine.init(this);
        if (i != null) LocalEngine.log("▶️ השידור התחיל (" + (i.getStringExtra("why") == null ? "" : i.getStringExtra("why")) + ") – חישוב בטלפון");
        // הפעלה מחדש של המערכת אחרי שהאפליקציה נסגרה (בלי בקשה): לא משדרים בלי הגבלה – עוצרים (בדיקת קוד, 17/11)
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        why = i.getStringExtra("why") != null ? i.getStringExtra("why") : "";
        int min = i != null ? i.getIntExtra("min", 0) : 0;
        until = min > 0 ? System.currentTimeMillis() + min * 60000L : 0;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CH, "שידור מיקום לשערים", NotificationManager.IMPORTANCE_LOW));
        Notification n = notif("📡 משדר מיקום" + (why.isEmpty() ? "" : " · " + why));
        try { // אנדרואיד 14: בלי הרשאת מיקום מתאימה startForeground זורק – עוצרים במקום לקרוס (18/11)
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION); else startForeground(1, n);
        } catch (Exception e) {
            state = "חסרה הרשאת מיקום";
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            h = new Handler(Looper.getMainLooper());
            lm = getSystemService(LocationManager.class);
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, this, Looper.getMainLooper());
            } catch (SecurityException e) {
                state = "אין הרשאת מיקום";
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            state = "מחכה ל-GPS";
            PowerManager pm = getSystemService(PowerManager.class);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gates:loc");
            wl.acquire(4 * 60 * 60 * 1000L);
            h.postDelayed(tick, 5000);
        }
        return START_STICKY;
    }

    private int ticks = 0;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (until > 0 && System.currentTimeMillis() > until) { state = "הזמן נגמר"; stopSelf(); return; }
            if (++ticks % 12 == 0) net.execute(() -> LocalEngine.upload(LocService.this)); // כל דקה – היומן לשרת (מצב מנהל)
            h.postDelayed(this, 5000);
        }
    };

    private Notification notif(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getBroadcast(this, 1, new Intent(this, StopReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        return b.setContentTitle("שערים").setContentText(text).setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "עצור", stop).build()).build();
    }

    private void show(String text) {
        getSystemService(NotificationManager.class).notify(1, notif(text));
    }

    @Override
    public void onLocationChanged(Location l) {
        long now = System.currentTimeMillis();
        if (now - lastSent < 2500) return;
        lastSent = now;
        if (lastSent == 0 || firstFix) { firstFix = false; LocalEngine.log("📡 מיקום ראשון מה-GPS" + (l.hasAccuracy() ? " (דיוק " + Math.round(l.getAccuracy()) + " מ׳)" : "")); }
        if (l.hasAccuracy() && l.getAccuracy() > 80) { state = "GPS חלש"; if (Prefs.local(this)) LocalEngine.log("GPS חלש (" + Math.round(l.getAccuracy()) + " מ׳) – מחכה"); return; }
        if (Prefs.local(this)) { // חישוב בטלפון: המיקום לא יוצא מהטלפון
            if (engine == null) engine = new LocalEngine(this);
            final double la = l.getLatitude(), lo = l.getLongitude();
            net.execute(() -> {
                try {
                    String r = engine.onFix(la, lo);
                    state = engine.state.isEmpty() ? "משדר (חישוב בטלפון)" : engine.state;
                    if ("expired".equals(r)) h.post(() -> { show("⌛ הקישור פג – בקש קישור חדש"); stopSelf(); });
                    else if ("open".equals(r)) h.post(() -> show(engine.state));
                } catch (Exception e) { state = "שגיאה"; LocalEngine.log("שגיאה: " + e.getClass().getSimpleName() + " " + String.valueOf(e.getMessage())); }
            });
            return;
        }
        final String u = Prefs.locUrl(this);
        if (u == null) { state = "אין קישור"; return; }
        final String body = "lat=" + l.getLatitude() + "&lon=" + l.getLongitude() + "&acc=" + (l.hasAccuracy() ? l.getAccuracy() : 0);
        net.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.setRequestProperty("User-Agent", "GatesApp/1");
                try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
                int code = c.getResponseCode();
                if (code == 403) {
                    state = "הקישור פג";
                    h.post(() -> { show("⌛ הקישור פג – בקש קישור חדש"); stopSelf(); });
                } else {
                    String r = "";
                    try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) { if (s.hasNext()) r = s.next(); }
                    state = "משדר";
                    if (r.contains("\"open\"")) h.post(() -> show("🚪 נשלחה פקודת פתיחה"));
                }
                c.disconnect();
            } catch (Exception e) {
                state = "אין אינטרנט";
            }
        });
    }

    @Override
    public void onDestroy() {
        LocalEngine.log("⏹️ השידור נעצר" + (state == null || state.isEmpty() ? "" : " (" + state + ")"));
        final android.content.Context ctx = getApplicationContext(); new Thread(() -> LocalEngine.upload(ctx)).start();
        running = false;
        if (lm != null) lm.removeUpdates(this);
        if (wl != null && wl.isHeld()) wl.release();
        if (h != null) h.removeCallbacksAndMessages(null);
        net.shutdownNow();
        super.onDestroy();
    }

    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) { state = "ה-GPS כבוי"; }
    @Override public void onStatusChanged(String p, int s, Bundle b) {}
}
