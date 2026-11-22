// build retry 22/11
package il.israel.gates;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

// 21/11: שם האפליקציה במסך הבית – "העוזר שלי" רק למנהל (קישור דשבורד שמור), "שערים" לכל השאר ולמסך הרכב
final class Brand {
    static void apply(Context c) {
        try {
            boolean admin = !Prefs.admin(c).isEmpty() && !Prefs.carScreen(c);
            PackageManager pm = c.getPackageManager();
            set(pm, new ComponentName(c, "il.israel.gates.AdminLauncher"), admin);
            set(pm, new ComponentName(c, "il.israel.gates.GatesLauncher"), !admin);
        } catch (Exception e) { /* בלי */ }
    }
    private static void set(PackageManager pm, ComponentName cn, boolean on) {
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(cn) != want) pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP);
    }
}
