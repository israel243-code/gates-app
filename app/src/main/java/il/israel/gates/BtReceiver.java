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
        if (d == null || !Prefs.cars(c).contains(d.getAddress())) return;
        String name = "";
        try { name = d.getName() == null ? "" : d.getName(); } catch (SecurityException e) { /* בלי שם */ }
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) {
            LocalEngine.log("🚗 הרכב התחבר: " + name);
            final PendingResult pr0 = goAsync(); new Thread(() -> { try { LocalEngine.upload(c.getApplicationContext()); } finally { pr0.finish(); } }).start();
            try { LocService.start(c, name, 0); } catch (Exception e) { LocService.state = "אנדרואיד חסם התחלה ברקע – בטל חיסכון סוללה"; LocalEngine.log("⚠️ " + LocService.state + " (" + e.getClass().getSimpleName() + ")"); }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(i.getAction())) {
            LocalEngine.log("🚗 הרכב התנתק: " + name);
            LocService.stop(c);
            final PendingResult pr = goAsync(); new Thread(() -> { try { LocalEngine.upload(c.getApplicationContext()); } finally { pr.finish(); } }).start(); // היומן לשרת מיד
        }
    }
}
