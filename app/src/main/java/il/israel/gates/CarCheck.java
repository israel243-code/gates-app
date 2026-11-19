package il.israel.gates;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * גיבוי לבלוטות' (19/11: "האפליקציה לא התחברה למיקום כשהבלוטות' מחובר"): אנדרואיד שולח "הרכב התחבר" רק ברגע החיבור.
 * אם באותו רגע האפליקציה התעדכנה / הטלפון הופעל מחדש / ההודעה פוספסה – בודקים כאן אם רכב מסומן מחובר עכשיו, ומתחילים לשדר.
 * רץ: בפתיחת האפליקציה, אחרי עדכון של האפליקציה, ואחרי הפעלה של הטלפון.
 */
public class CarCheck extends BroadcastReceiver {
    static final String RECHECK = "il.israel.gates.RECHECK";

    @Override public void onReceive(Context c, Intent i) {
        LocalEngine.init(c);
        if (RECHECK.equals(i.getAction())) { // #49: אחרי ירידה מהרכב – בדיקה שקטה כל דקה, חצי שעה
            long until = c.getSharedPreferences("gates", Context.MODE_PRIVATE).getLong("recheckUntil", 0);
            if (LocService.running || System.currentTimeMillis() > until) return;
            run(c, "בדיקה אחרי ירידה מהרכב");
            next(c);
            return;
        }
        run(c, "אחרי " + (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()) ? "עדכון" : "הפעלה של הטלפון"));
    }

    static void startRecheck(Context c) {
        c.getSharedPreferences("gates", Context.MODE_PRIVATE).edit().putLong("recheckUntil", System.currentTimeMillis() + 30 * 60000L).apply();
        next(c);
    }

    static void stopRecheck(Context c) { c.getSharedPreferences("gates", Context.MODE_PRIVATE).edit().putLong("recheckUntil", 0).apply(); }

    private static void next(Context c) {
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        if (am == null) return;
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 49, new Intent(c, CarCheck.class).setAction(RECHECK),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 60000L, pi);
    }

    static void run(Context c, String why) {
        if (LocService.running || !Prefs.auto(c) || Prefs.locUrl(c) == null) return;
        if (Prefs.carScreen(c)) { // מסך הרכב: הרכב עצמו משדר, בלי לחכות לבלוטות'
            LocalEngine.log("🖥️ מסך הרכב – מתחיל לשדר (" + why + ")");
            try { LocService.start(c.getApplicationContext(), "מסך הרכב", 0); } catch (Exception e) { LocalEngine.log("⚠️ אנדרואיד חסם התחלה ברקע (" + e.getClass().getSimpleName() + ")"); }
            return;
        }
        if (Prefs.cars(c).isEmpty()) return;
        BluetoothManager bm = c.getSystemService(BluetoothManager.class);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) return;
        final Context app = c.getApplicationContext();
        for (int profile : new int[]{BluetoothProfile.HEADSET, BluetoothProfile.A2DP}) {
            try {
                ad.getProfileProxy(app, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int p, BluetoothProfile proxy) {
                        try {
                            for (BluetoothDevice d : proxy.getConnectedDevices()) {
                                if (!Prefs.cars(app).contains(d.getAddress()) || LocService.running) continue;
                                String name = ""; try { name = d.getName() == null ? "" : d.getName(); } catch (SecurityException e) { /* בלי שם */ }
                                LocalEngine.log("🚗 רכב כבר מחובר (" + why + "): " + name);
                                try { LocService.start(app, name, 0); } catch (Exception e) { LocalEngine.log("⚠️ אנדרואיד חסם התחלה ברקע (" + e.getClass().getSimpleName() + ")"); }
                            }
                        } catch (SecurityException e) { /* אין הרשאת בלוטות' */ }
                        ad.closeProfileProxy(p, proxy);
                    }
                    @Override public void onServiceDisconnected(int p) { }
                }, profile);
            } catch (Exception e) { /* אין */ }
        }
    }
}
