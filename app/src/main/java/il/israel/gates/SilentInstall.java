package il.israel.gates;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * התקנה דרך PackageInstaller (19/11): אם האפליקציה היא "המתקין הרשום" שלה, אנדרואיד 12+ מאפשר עדכון בלי חלון אישור
 * (USER_ACTION_NOT_REQUIRED). בהתקנה הראשונה בדרך הזו עדיין יופיע אישור רגיל, ומשם העדכונים אמורים להיות שקטים.
 * כל כשל = חוזרים לדרך הישנה (פתיחת קובץ ההתקנה).
 */
final class SilentInstall {
    private SilentInstall() {}

    /** true = ההתקנה הועברה למערכת (התוצאה מגיעה ל-InstallResult). false = להשתמש בדרך הישנה */
    static boolean start(Context app, Uri apk) {
        if (Build.VERSION.SDK_INT < 31) return false;
        try (InputStream in = app.getContentResolver().openInputStream(apk)) {
            if (in == null) return false;
            PackageInstaller pi = app.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(app.getPackageName());
            sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            int sid = pi.createSession(sp);
            try (PackageInstaller.Session s = pi.openSession(sid)) {
                try (OutputStream out = s.openWrite("gates.apk", 0, -1)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    s.fsync(out);
                }
                PendingIntent pend = PendingIntent.getBroadcast(app, sid, new Intent(app, InstallResult.class).putExtra("u", apk.toString()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                s.commit(pend.getIntentSender());
            }
            LocalEngine.log("⬇️ עדכון נשלח להתקנה דרך המערכת (מתקין רשום)");
            return true;
        } catch (Throwable e) {
            LocalEngine.log("⚠️ התקנה שקטה לא התחילה (" + e.getClass().getSimpleName() + ") – הדרך הרגילה");
            return false;
        }
    }

    static void open(Context c, Uri u) {
        c.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** תוצאת ההתקנה: דרוש אישור (פעם ראשונה) → מציגים את החלון; כשל → הדרך הישנה */
    public static class InstallResult extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            try {
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class) : i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirm); }
                } else if (st == PackageInstaller.STATUS_SUCCESS) {
                    LocalEngine.log("✅ העדכון הותקן");
                } else {
                    LocalEngine.log("⚠️ התקנה דרך המערכת נכשלה (" + st + ") – הדרך הרגילה");
                    String u = i.getStringExtra("u");
                    if (u != null) open(c, Uri.parse(u));
                }
            } catch (Throwable e) { LocalEngine.log("⚠️ " + e.getClass().getSimpleName()); }
        }
    }
}
