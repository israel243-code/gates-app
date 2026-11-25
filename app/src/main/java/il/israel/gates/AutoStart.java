package il.israel.gates;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 🚗 הפעלה אוטומטית לבד (25/11, הבעלים: "כמו בוואן דרייב – שהאפליקציה תדליק לבד דרך ההגדרות").
 * הרכב (BYD) חוסם הפעלה בהתנעה לכל אפליקציה שהותקנה, ומאפס את זה בכל עדכון. ההגדרה במסך של BYD
 * (com.byd.appstartmanagement): מתג ליד כל אפליקציה – דלוק = חסום, כבוי = מותר.
 * דרך שירות הנגישות (WakeAccess): פותחים את המסך, מוצאים את השורה של "שערים", מכבים את המתג, מוודאים, וסוגרים (חזור).
 * לפי הקוד של OverDrive (AutoStartEnabler). רק במסך הרכב. התוצאה ביומן: "🚗 הפעלה אוטומטית: ...".
 */
final class AutoStart {
    private AutoStart() {}
    static final String PKG = "com.byd.appstartmanagement";
    static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    static Intent intent() {
        Intent i = new Intent(); i.setComponent(new ComponentName(PKG, PKG + ".frame.AppStartManagement")); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); return i;
    }

    /** אחרי כל עדכון (פעם אחת לכל גרסה, עד 3 ניסיונות בהפרש 2 דקות) – מ-WakeAccess כשהשירות מתחבר */
    static void afterUpdate(final AccessibilityService s) {
        if (!Prefs.carScreen(s)) return;
        final SharedPreferences sp = s.getSharedPreferences("gates", Context.MODE_PRIVATE);
        final int ver = Updater.current(s);
        if (sp.getInt("asVer", 0) == ver) return; // כבר מותר בגרסה הזו
        if (sp.getInt("asTryVer", 0) != ver) sp.edit().putInt("asTryVer", ver).putInt("asTries", 0).apply();
        if (sp.getInt("asTries", 0) >= 3 || System.currentTimeMillis() - sp.getLong("asTryAt", 0) < 120000) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> start(s, "אחרי עדכון"), 20000);
    }

    /** מריץ ברקע; מחזיר false אם כבר רץ */
    static boolean start(final AccessibilityService s, final String why) {
        if (s == null || RUNNING.get()) return false;
        final SharedPreferences sp = s.getSharedPreferences("gates", Context.MODE_PRIVATE);
        sp.edit().putInt("asTries", sp.getInt("asTries", 0) + 1).putLong("asTryAt", System.currentTimeMillis()).apply();
        new Thread(() -> {
            String r = run(s);
            LocalEngine.log("🚗 הפעלה אוטומטית (" + why + "): " + r);
            if (r.startsWith("✅")) sp.edit().putInt("asVer", Updater.current(s)).apply(); // הכרטיס "לאפשר שוב" לא יוצג
            final Context x = s.getApplicationContext(); LocalEngine.upload(x);
        }).start();
        return true;
    }

    static String run(AccessibilityService s) {
        if (!RUNNING.compareAndSet(false, true)) return "כבר רץ";
        boolean seen = false;
        try {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try { s.startActivity(intent()); } catch (android.content.ActivityNotFoundException e) { return "❌ אין ברכב מסך ניהול הפעלה אוטומטית"; }
                AccessibilityNodeInfo root = waitRoot(s, 5000);
                if (root == null) { if (attempt == 2) return "❌ המסך של הרכב לא נפתח"; continue; }
                seen = true;
                AccessibilityNodeInfo sw = findSwitch(s, root);
                if (sw == null) return "❌ לא מצאתי את \"שערים\" ברשימה של הרכב";
                if (!sw.isChecked()) return "✅ כבר מותר (המתג כבוי)";
                boolean clicked = (sw.isClickable() && sw.performAction(AccessibilityNodeInfo.ACTION_CLICK)) || tap(s, sw);
                SystemClock.sleep(600);
                if (sw.refresh() && !sw.isChecked()) return "✅ הודלקה לבד – המתג כובה";
                AccessibilityNodeInfo r2 = findRoot(s), sw2 = r2 == null ? null : findSwitch(s, r2);
                if (sw2 != null && !sw2.isChecked()) return "✅ הודלקה לבד – המתג כובה";
                if (attempt == 2) return "❌ לחצתי על המתג (" + clicked + ") אבל הוא לא השתנה";
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); seen = false; SystemClock.sleep(800);
            }
            return "❌ לא הצליח";
        } catch (Throwable t) { return "❌ שגיאה: " + t.getClass().getSimpleName();
        } finally {
            if (seen) { SystemClock.sleep(400); s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); } // סוגרים את המסך של הרכב
            RUNNING.set(false);
        }
    }

    // המסך של BYD מוצג מעל המסך הראשי, ולכן מחפשים אותו בין כל החלונות (getWindows), לא רק בחלון הפעיל
    static AccessibilityNodeInfo findRoot(AccessibilityService s) {
        List<AccessibilityWindowInfo> ws = s.getWindows();
        if (ws != null) for (AccessibilityWindowInfo w : ws) { AccessibilityNodeInfo r = w == null ? null : w.getRoot(); if (r != null && r.getPackageName() != null && PKG.contentEquals(r.getPackageName())) return r; }
        AccessibilityNodeInfo a = s.getRootInActiveWindow();
        return a != null && a.getPackageName() != null && PKG.contentEquals(a.getPackageName()) ? a : null;
    }
    static AccessibilityNodeInfo waitRoot(AccessibilityService s, long ms) {
        long end = SystemClock.elapsedRealtime() + ms;
        while (SystemClock.elapsedRealtime() < end) { AccessibilityNodeInfo r = findRoot(s); if (r != null) return r; SystemClock.sleep(250); }
        return null;
    }

    /** השורה של האפליקציה שלנו: קודם מה שנראה, ואחר כך גלילה לראש הרשימה ומשם קדימה */
    static AccessibilityNodeInfo findSwitch(AccessibilityService s, AccessibilityNodeInfo root) {
        String[] labels = labels(s);
        AccessibilityNodeInfo sw = locate(root, labels); if (sw != null) return sw;
        AccessibilityNodeInfo cur = root;
        for (int i = 0; i < 20; i++) { AccessibilityNodeInfo sc = scrollable(cur); if (sc == null || !sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break; SystemClock.sleep(400); cur = findRoot(s); if (cur == null) return null; }
        for (int i = 0; i <= 20; i++) {
            if (i > 0) { cur = findRoot(s); if (cur == null) return null; }
            sw = locate(cur, labels); if (sw != null) return sw;
            AccessibilityNodeInfo sc = scrollable(cur);
            if (sc == null || !sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return null;
            SystemClock.sleep(400);
        }
        return null;
    }
    static String[] labels(Context c) {
        String l = "שערים"; try { l = String.valueOf(c.getApplicationInfo().loadLabel(c.getPackageManager())); } catch (Exception e) { }
        return l.equals("שערים") ? new String[] { "שערים", "העוזר שלי" } : new String[] { l, "שערים", "העוזר שלי" };
    }
    static AccessibilityNodeInfo locate(AccessibilityNodeInfo root, String[] labels) {
        for (String label : labels) {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByText(label);
            if (ns == null) continue;
            for (AccessibilityNodeInfo n : ns) {
                if (!norm(n.getText()).equals(norm(label)) && !norm(n.getContentDescription()).equals(norm(label))) continue; // בדיוק השם, לא חלק ממנו
                AccessibilityNodeInfo a = n;
                for (int k = 0; k < 6 && a != null; k++) { if (a.isScrollable()) break; AccessibilityNodeInfo sw = switchIn(a); if (sw != null) return sw; a = a.getParent(); } // לא לעלות מעל השורה – אחרת זה המתג של אפליקציה אחרת
            }
        }
        return null;
    }
    static String norm(CharSequence cs) { if (cs == null) return ""; StringBuilder b = new StringBuilder(); for (char ch : cs.toString().toCharArray()) if (!Character.isWhitespace(ch) && !Character.isSpaceChar(ch)) b.append(ch); return b.toString(); }
    static AccessibilityNodeInfo switchIn(AccessibilityNodeInfo n) {
        if (n == null) return null;
        CharSequence c = n.getClassName(); if (c != null && c.toString().contains("Switch")) return n;
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo f = switchIn(n.getChild(i)); if (f != null) return f; }
        return null;
    }
    static AccessibilityNodeInfo scrollable(AccessibilityNodeInfo n) {
        if (n == null) return null; if (n.isScrollable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo f = scrollable(n.getChild(i)); if (f != null) return f; }
        return null;
    }
    /** לחיצה במרכז המתג (כשלחיצה רגילה לא עובדת). לא על השורה – אצל BYD זה פותח את פרטי האפליקציה */
    static boolean tap(AccessibilityService s, AccessibilityNodeInfo n) {
        Rect b = new Rect(); n.getBoundsInScreen(b); if (b.isEmpty()) return false;
        Path p = new Path(); p.moveTo(b.centerX(), b.centerY());
        final java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1); final boolean[] ok = { false };
        boolean d = s.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, 50)).build(), new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { ok[0] = true; l.countDown(); }
            @Override public void onCancelled(GestureDescription g) { l.countDown(); } }, null);
        if (!d) return false;
        try { l.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException e) { }
        return ok[0];
    }
}
