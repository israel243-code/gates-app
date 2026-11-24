package il.israel.gates;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 🔔 התראות מהאפליקציה – גם כשהיא סגורה (19/11): שער נפתח / לא נפתח / השידור התחיל או נעצר. לפי ההגדרות. */
final class Notify {
    private Notify() {}
    private static final String LOUD = "gates_events", QUIET = "gates_events_quiet";
    private static int seq = 100;

    static void gate(Context c, String label, boolean ok, String text) {
        if (ok ? !Prefs.flag(c, "nOpen", true) : !Prefs.flag(c, "nFail", true)) return;
        post(c, ok ? "🚪 " + label + " נפתח" : "⚠️ " + label + " לא נפתח", ok ? "" : text, 1000 + Math.abs(label.hashCode() % 500), ok ? 120000 : 1800000); // 24/11: התראה אחת לכל שער (מחליפה את הקודמת), נעלמת לבד – פתיחה אחרי 2 דק', תקלה אחרי חצי שעה
    }

    /** 🚧 שער נתקע (22/11): נפתח לפני דקה+ ואתה עדיין עומד מולו – התראה עם כפתור "פתח שוב". עם נעילה לפני פתיחה – הכפתור פותח את האפליקציה. */
    static void stuck(Context c, String label) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(LOUD, "פתיחת שערים", NotificationManager.IMPORTANCE_HIGH));
        int id = 50 + Math.abs(label.hashCode() % 40);
        PendingIntent app = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent again = Prefs.lock(c) != 0 ? app : PendingIntent.getBroadcast(c, id, new Intent(c, ReopenReceiver.class).putExtra("gate", label).putExtra("nid", id), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, LOUD) : new Notification.Builder(c);
        b.setContentTitle("🚧 " + label + " – השער תקוע?").setContentText("נפתח לפני יותר מדקה ואתה עדיין מולו").setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(app).setAutoCancel(true).setWhen(System.currentTimeMillis()).setShowWhen(true)
            .addAction(new Notification.Action.Builder(null, "🔓 פתח שוב", again).build());
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(300000); // 24/11: נעלם לבד אחרי 5 דקות
        try { nm.notify(id, b.build()); } catch (SecurityException e) { /* אין הרשאת התראות */ }
    }

    static void service(Context c, String text) {
        if (Prefs.flag(c, "nService", false)) post(c, text, "", 900, 60000);
    }

    private static void post(Context c, String title, String body, int id, long timeoutMs) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        boolean loud = Prefs.flag(c, "nSound", true);
        String ch = loud ? LOUD : QUIET;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(LOUD, "פתיחת שערים", NotificationManager.IMPORTANCE_HIGH));
            nm.createNotificationChannel(new NotificationChannel(QUIET, "פתיחת שערים (שקט)", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
        b.setContentTitle(title).setSmallIcon(R.drawable.ic_stat).setContentIntent(open).setAutoCancel(true).setWhen(System.currentTimeMillis()).setShowWhen(true);
        if (body != null && !body.isEmpty()) b.setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body));
        if (Build.VERSION.SDK_INT >= 26 && timeoutMs > 0) b.setTimeoutAfter(timeoutMs);
        try { nm.notify(id > 0 ? id : seq++, b.build()); } catch (SecurityException e) { /* אין הרשאת התראות */ }
    }
}
