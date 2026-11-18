package il.israel.gates;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * הגדרות: הרשאות (בשלבים), אילו רכבים מפעילים שידור ברקע, שידור ידני, והחלפת הקישור.
 * שידור ברקע רק כשהטלפון מחובר לרכב שסומן כאן – לא סתם כל הזמן.
 */
public class SettingsActivity extends Activity {
    private TextView status;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) { super.onCreate(b); Ui.theme(this); getWindow().setStatusBarColor(Color.parseColor("#1E3A8A")); build(); }
    @Override protected void onResume() { super.onResume(); build(); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacksAndMessages(null); }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { build(); }

    private boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(0, 0, 0, Ui.dp(this, 28));
        sv.addView(root);

        root.addView(Ui.hero(this, "⚙️ הגדרות", "השערים נפתחים לבד כשמתקרבים – רק כשאתה ברכב"));

        // --- 1. הרשאות ---
        boolean fine = has(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean bg = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        boolean nt = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS);
        boolean bt = Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT);
        boolean batt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        int done = (fine ? 1 : 0) + (bg ? 1 : 0) + (nt ? 1 : 0) + (bt ? 1 : 0) + (batt ? 1 : 0);
        LinearLayout perm = Ui.card(this, root, "🔐 הרשאות · " + done + " מתוך 5");
        Ui.step(this, perm, 1, fine, "מיקום", "כדי לדעת כשאתה מתקרב לשער", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2));
        Ui.step(this, perm, 2, bg, "מיקום כל הזמן", Build.VERSION.SDK_INT >= 30 ? "בהגדרות: מיקום ← \"לאפשר כל הזמן\"" : "נדרש לשידור ברקע", "פתח", v -> {
            if (!fine) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2); return; }
            if (Build.VERSION.SDK_INT >= 30) openAppSettings(); else requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
        });
        Ui.step(this, perm, 3, bt, "בלוטות'", "כדי לזהות שנכנסת לרכב", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1));
        Ui.step(this, perm, 4, nt, "התראות", "רואים מתי משדרים ומתי שער נפתח", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4));
        Ui.step(this, perm, 5, batt, "בלי חיסכון סוללה", "אחרת אנדרואיד חוסם התחלה ברקע", "פתח", v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { openAppSettings(); }
        });
        if (Build.MANUFACTURER.toLowerCase().matches(".*(xiaomi|redmi|poco).*"))
            Ui.note(this, perm, "בשיאומי / רדמי: גם בהגדרות האפליקציה ← \"חיסכון בסוללה\" ← \"ללא הגבלות\", ו\"הפעלה אוטומטית\" מופעלת.");

        // --- 2. רכבים ---
        LinearLayout cars = Ui.card(this, root, "🚗 הרכבים שלי");
        Switch auto = new Switch(this);
        auto.setText("לשדר לבד כשמתחברים לרכב");
        auto.setTextSize(15);
        auto.setTextColor(Ui.INK);
        auto.setChecked(Prefs.auto(this));
        auto.setOnCheckedChangeListener((v, on) -> Prefs.setAuto(this, on));
        cars.addView(auto);
        Ui.note(this, cars, "סמן את הבלוטות' של הרכב. השידור מתחיל כשהטלפון מתחבר אליו, ונעצר כשהוא מתנתק.");
        if (!bt) {
            Ui.note(this, cars, "כדי לראות את הרכבים – אשר בלוטות' (שלב 3 למעלה).");
        } else {
            BluetoothAdapter ad = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
            Set<String> sel = Prefs.cars(this);
            List<BluetoothDevice> list = new ArrayList<>();
            try { if (ad != null) list.addAll(ad.getBondedDevices()); } catch (SecurityException e) { /* אין הרשאה */ }
            list.sort((x, y) -> Boolean.compare(!sel.contains(x.getAddress()), !sel.contains(y.getAddress()))); // רכבים מסומנים ראשונים (18/11)
            if (list.isEmpty()) Ui.note(this, cars, "לא נמצאו מכשירי בלוטות'. צמד קודם את הטלפון לרכב.");
            for (BluetoothDevice d : list) {
                CheckBox cb = new CheckBox(this);
                String n;
                try { n = d.getName(); } catch (SecurityException e) { n = null; }
                cb.setText("  " + (n == null || n.isEmpty() ? d.getAddress() : n));
                cb.setTextSize(15);
                cb.setTextColor(Ui.INK);
                cb.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                cb.setChecked(sel.contains(d.getAddress()));
                cb.setOnCheckedChangeListener((v, on) -> {
                    Set<String> s = Prefs.cars(this);
                    if (on) s.add(d.getAddress()); else s.remove(d.getAddress());
                    Prefs.setCars(this, s);
                });
                cars.addView(cb);
            }
        }

        // --- פרטיות (ניסיוני) – תשתית לאפשרות ג' ---
        LinearLayout priv = Ui.card(this, root, "🔒 חישוב בטלפון (ניסיוני)");
        Switch loc = new Switch(this);
        loc.setText("המיקום לא נשלח לשרת – הטלפון מחליט לבד מתי לפתוח");
        loc.setTextSize(15);
        loc.setTextColor(Ui.INK);
        loc.setChecked(Prefs.local(this));
        loc.setOnCheckedChangeListener((v, on) -> Prefs.setLocal(this, on));
        priv.addView(loc);
        Ui.note(this, priv, "אותם כללים: בתוך המרחק, מתקרבים, וזמן בין פתיחות. לשרת נשלחת רק בקשה \"לפתוח את השער\". המיקום שלך לא יופיע במפה של אף אחד.");

        // --- 3. שידור ידני ---
        LinearLayout man = Ui.card(this, root, "📡 שידור עכשיו");
        Ui.note(this, man, "למשל ברכב אחר. נעצר לבד בסוף הזמן.");
        status = Ui.text(this, "", 14, Ui.INK, true);
        status.setPadding(0, 0, 0, Ui.dp(this, 6));
        man.addView(status);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int m : new int[]{15, 30, 60}) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            Ui.button(this, cell, m == 60 ? "שעה" : m + " דק׳", false, v -> { LocService.start(this, "ידני", m); refresh(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            row.addView(cell, lp);
        }
        man.addView(row);
        Ui.button(this, man, "⏹️ עצור שידור", false, v -> { LocService.stop(this); h.postDelayed(this::refresh, 500); });

        // --- עדכונים (כל עוד האפליקציה לא בחנות) ---
        LinearLayout upd = Ui.card(this, root, "🔄 עדכונים");
        Ui.note(this, upd, "הגרסה שלך: " + Updater.currentName(this) + ". האפליקציה בודקת לבד פעם ביום; אפשר גם לבדוק עכשיו.");
        Ui.button(this, upd, "🔄 בדוק עדכונים", false, v -> Updater.check(this, false));

        // --- 4. קישור ---
        LinearLayout link = Ui.card(this, root, "🔗 הקישור האישי");
        Ui.note(this, link, "קיבלת קישור חדש? פשוט לחץ עליו בוואטסאפ ובחר \"שערים\".");
        Ui.button(this, link, "החלף קישור", false, v -> {
            Prefs.setUrl(this, "");
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });

        LinearLayout end = new LinearLayout(this);
        end.setOrientation(LinearLayout.VERTICAL);
        end.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), 0);
        Button fin = Ui.button(this, end, done == 5 ? "✔️ סיום" : "המשך (אפשר להשלים אחר כך)", true, v -> finish());
        fin.setGravity(Gravity.CENTER);
        root.addView(end);

        setContentView(sv);
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        status.setText(LocService.running ? "🟢 משדר עכשיו" + (LocService.state.isEmpty() ? "" : " · " + LocService.state)
                : "⚪ לא משדר" + (LocService.state.isEmpty() ? "" : " · " + LocService.state));
        h.removeCallbacksAndMessages(null);
        h.postDelayed(this::refresh, 2000);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
}
