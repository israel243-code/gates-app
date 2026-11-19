package il.israel.gates;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** רכב שהוגדר מתחבר בבלוטות' → מתחילים לשדר; מתנתק → עוצרים. רכבים אחרים / מכשירים אחרים – לא עושים כלום. */
public class BtReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        LocalEngine.init(c);
        if (!Prefs.auto(c) || Prefs.locUrl(c) == null) { if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) LocalEngine.log("🚗 בלוטות' התחבר אבל " + (!Prefs.auto(c) ? "הפתיחה האוטומטית כבויה בהגדרות" : "אין קישור אישי")); return; }
        BluetoothDevice d = Build.VERSION.SDK_INT >= 33
                ? i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                : i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String act = i.getAction() == null ? "" : i.getAction();
        String kind = act.endsWith("ACL_CONNECTED") ? "התחבר" : act.endsWith("ACL_DISCONNECTED") ? "התנתק" : act.contains("a2dp") ? "אודיו" : "דיבורית";
        String name = "";
        if (d != null) try { name = d.getName() == null ? "" : d.getName(); } catch (SecurityException e) { /* בלי שם */ }
        if (d == null || !Prefs.cars(c).contains(d.getAddress())) { // #50: לרשום גם מה שמתעלמים ממנו – כדי לדעת אם אנדרואיד שלח משהו
            if (!kind.equals("אודיו") && !kind.equals("דיבורית")) LocalEngine.log("ℹ️ בלוטות' " + kind + ": " + (d == null ? "מכשיר לא ידוע" : name) + " – לא רכב מסומן");
            return;
        }
        boolean profile = act.endsWith("profile.action.CONNECTION_STATE_CHANGED");
        if (profile) LocalEngine.log("ℹ️ בלוטות' " + kind + " (" + name + "): מצב " + i.getIntExtra("android.bluetooth.profile.extra.STATE", -1) + (LocService.running ? ", כבר משדר" : ""));
        if (profile && (i.getIntExtra("android.bluetooth.profile.extra.STATE", -1) != 2 || LocService.running)) return; // גיבוי: רק "התחבר", ורק אם עוד לא משדרים
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(act) || profile) {
            if (LocService.running) { LocalEngine.log("ℹ️ הרכב התחבר (" + name + ") – כבר משדר"); return; }
            CarCheck.stopRecheck(c);
            LocalEngine.log("🚗 הרכב התחבר" + (profile ? " (גיבוי: " + (act.contains("a2dp") ? "אודיו" : "דיבורית") + ")" : "") + ": " + name);
            final PendingResult pr0 = goAsync(); new Thread(() -> { try { LocalEngine.upload(c.getApplicationContext()); } finally { pr0.finish(); } }).start();
            try { LocService.start(c, name, 0); } catch (Exception e) { LocService.state = "אנדרואיד חסם התחלה ברקע – בטל חיסכון סוללה"; LocalEngine.log("⚠️ " + LocService.state + " (" + e.getClass().getSimpleName() + ")"); }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(act)) {
            LocalEngine.log("🚗 הרכב התנתק: " + name);
            LocService.stop(c);
            CarCheck.startRecheck(c); // #49: חצי שעה של בדיקה כל דקה – אם הרכב מתחבר שוב וההודעה פוספסה
            final PendingResult pr = goAsync(); new Thread(() -> { try { LocalEngine.upload(c.getApplicationContext()); } finally { pr.finish(); } }).start(); // היומן לשרת מיד
        }
    }
}
