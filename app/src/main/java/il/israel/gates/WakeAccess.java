package il.israel.gates;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * 24/11 (כמו OverDrive): הרכב עוצר את האפליקציה בכיבוי, ואז שום אירוע התנעה לא מגיע אליה.
 * שירות נגישות שהופעל פעם אחת – המערכת מפעילה אותו בעצמה בכל הדלקה, וכך האפליקציה מתעוררת ומתחילה לשדר.
 * לא קורא שום תוכן מהמסך.
 */
public class WakeAccess extends AccessibilityService {
    @Override protected void onServiceConnected() {
        LocalEngine.init(this);
        if (Prefs.carScreen(this)) LocalEngine.log("🖥️ התעוררות: שירות נגישות");
        if (Prefs.carScreen(this) && !LocService.running && Prefs.auto(this)) CarCheck.wakeUi(this);
        CarCheck.run(this, "שירות נגישות");
        if (Prefs.carScreen(this)) { final android.content.Context x = getApplicationContext(); new Thread(() -> LocalEngine.upload(x)).start(); }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() { }

    static boolean on(android.content.Context c) {
        try {
            String s = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(c.getPackageName() + "/");
        } catch (Exception e) { return false; }
    }
}
