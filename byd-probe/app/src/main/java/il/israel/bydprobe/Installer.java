package il.israel.bydprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

/** הורדה והתקנה בתוך האפליקציה (בלי דפדפן ובלי קבצים): עדכון של עצמה, וגם התקנה / עדכון של אפליקציית השערים. */
final class Installer {
    static final String SELF = "https://github.com/israel243-code/gates-app/releases/download/byd-latest/byd-probe.apk";
    static final String GATES = "https://github.com/israel243-code/gates-app/releases/download/latest/gates.apk";

    private Installer() {}

    static void install(Activity a, String url, String what) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a).setTitle("אישור חד-פעמי")
                    .setMessage("כדי להתקין ולעדכן מכאן, אנדרואיד מבקש לאשר \"התקנה ממקור זה\" פעם אחת.\nבמסך שנפתח – מדליקים את המתג, חוזרים, ולוחצים שוב.")
                    .setPositiveButton("פתח", (d, w) -> {
                        try { a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); }
                        catch (Exception e) { Toast.makeText(a, "המסך הזה חסום ברכב – נסה שוב", Toast.LENGTH_LONG).show(); }
                    })
                    .setNegativeButton("ביטול", null).show();
            return;
        }
        try {
            final DownloadManager dm = a.getSystemService(DownloadManager.class);
            final long id = dm.enqueue(new DownloadManager.Request(Uri.parse(url))
                    .setTitle(what).setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, "app-" + System.currentTimeMillis() + ".apk"));
            Toast.makeText(a, "⬇️ מוריד: " + what + "…", Toast.LENGTH_LONG).show();
            final Context app = a.getApplicationContext();
            BroadcastReceiver done = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { app.unregisterReceiver(this); } catch (Exception e) { /* כבר בוטל */ }
                    Uri u = dm.getUriForDownloadedFile(id);
                    if (u == null) { Toast.makeText(app, "ההורדה נכשלה. נסה שוב.", Toast.LENGTH_LONG).show(); return; }
                    app.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            };
            app.registerReceiver(done, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Exception e) {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); // גיבוי אחרון: דרך הדפדפן
        }
    }
}
