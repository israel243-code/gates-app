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
        if (!Prefs.auto(c) || Prefs.locUrl(c) == null) return;
        BluetoothDevice d = Build.VERSION.SDK_INT >= 33
                ? i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                : i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null || !Prefs.cars(c).contains(d.getAddress())) return;
        String name = "";
        try { name = d.getName() == null ? "" : d.getName(); } catch (SecurityException e) { /* בלי שם */ }
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) {
            try { LocService.start(c, name, 0); } catch (Exception e) { LocService.state = "אנדרואיד חסם התחלה ברקע – בטל חיסכון סוללה"; }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(i.getAction())) {
            LocService.stop(c);
        }
    }
}
