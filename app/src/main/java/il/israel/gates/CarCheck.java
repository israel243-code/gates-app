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
        if (RECHECK.equals(i.getAction())) { // #50: בדיקה שקטה כל 2 דקות, כל הזמן – אם ההודעה "הרכב התחבר" פוספסה
            run(c, "בדיקה תקופתית");
            return;
        }
        if (Prefs.carScreen(c)) LocalEngine.log("🖥️ התעוררות: " + i.getAction()); // 24/11: יומן – לדעת אילו אירועים מגיעים כשהרכב נדלק
        if (Prefs.carScreen(c) && !LocService.running && Prefs.auto(c)) wakeUi(c); // 24/11 (כמו OverDrive): מפעילים את המסך הראשי ומיד מורידים לרקע – תהליך שנפתח פעם אחת שורד
        run(c, "אחרי " + (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()) ? "עדכון" : "הפעלה של " + (Prefs.carScreen(c) ? "מסך הרכב" : "הטלפון")));
        if (Prefs.carScreen(c)) { final Context x = c.getApplicationContext(); new Thread(() -> LocalEngine.upload(x)).start(); }
    }

    /** פותח את MainActivity ומיד מוריד לרקע (דורש "הצגה מעל אפליקציות" באנדרואיד 10+; בלי זה – רק התחלת שידור) */
    static void wakeUi(Context c) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(c)) { LocalEngine.log("🖥️ אין הרשאת \"הצגה מעל אפליקציות\" – לא פותח את המסך ברקע"); return; }
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("minimize", true));
            LocalEngine.log("🖥️ פתחתי את המסך הראשי ברקע");
        } catch (Exception e) { LocalEngine.log("⚠️ פתיחת המסך ברקע נחסמה (" + e.getClass().getSimpleName() + ")"); }
    }

    static void startRecheck(Context c) { next(c); }

    static void stopRecheck(Context c) { /* #50: הבדיקה התקופתית רצה תמיד; כשמשדרים היא לא עושה כלום */ }

    private static void next(Context c) {
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        if (am == null) return;
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 49, new Intent(c, CarCheck.class).setAction(RECHECK),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        long at = android.os.SystemClock.elapsedRealtime() + 120000L;
        try { // 24/11 (כמו OverDrive): התראה מדויקת – "גם כשהמכשיר ישן" – מעירה את האפליקציה כל 2 דקות
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } catch (SecurityException e) { am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi); }
    }

    static void run(Context c, String why) {
        next(c); // שומר על שרשרת הבדיקה כל 2 דקות (אותו מזהה – מחליף, לא מכפיל)
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
