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
        post(c, ok ? "🚪 " + label + " נפתח" : "⚠️ " + label + " לא נפתח", ok ? "" : text);
    }

    static void service(Context c, String text) {
        if (Prefs.flag(c, "nService", false)) post(c, text, "");
    }

    private static void post(Context c, String title, String body) {
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
        try { nm.notify(seq++, b.build()); } catch (SecurityException e) { /* אין הרשאת התראות */ }
    }
}
