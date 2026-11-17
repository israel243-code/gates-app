package il.israel.gates;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
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
 * הגדרות: אילו רכבים (בלוטות') מפעילים שידור ברקע, הרשאות, שידור ידני, והחלפת הקישור.
 * שידור ברקע רק כשהטלפון מחובר לרכב שסומן כאן – לא סתם כל הזמן.
 */
public class SettingsActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#0F172A"));
        t.setPadding(0, dp(18), 0, dp(6));
        root.addView(t);
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(Color.parseColor("#64748B"));
        root.addView(t);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b);
        return b;
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        sv.setBackgroundColor(Color.WHITE);

        TextView head = title("⚙️ הגדרות האפליקציה");
        head.setTextSize(24);

        // --- רכבים ---
        title("🚗 הרכבים שלי");
        Switch auto = new Switch(this);
        auto.setText("לשדר מיקום לבד כשהטלפון מתחבר לרכב");
        auto.setChecked(Prefs.auto(this));
        auto.setOnCheckedChangeListener((v, on) -> Prefs.setAuto(this, on));
        root.addView(auto);
        note("סמן את הבלוטות' של הרכב (או הרכבים). השידור מתחיל רק כשהטלפון מתחבר לאחד מהם, ונעצר כשהוא מתנתק.");
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            button("🔵 אפשר גישה לבלוטות' (כדי לראות את הרכבים)", v -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1));
        } else {
            BluetoothAdapter ad = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
            Set<String> cars = Prefs.cars(this);
            List<BluetoothDevice> list = new ArrayList<>();
            try { if (ad != null) list.addAll(ad.getBondedDevices()); } catch (SecurityException e) { /* אין הרשאה */ }
            if (list.isEmpty()) note("לא נמצאו מכשירי בלוטות' מצומדים. צמד קודם את הטלפון לרכב בהגדרות הבלוטות'.");
            for (BluetoothDevice d : list) {
                CheckBox cb = new CheckBox(this);
                String n;
                try { n = d.getName(); } catch (SecurityException e) { n = null; }
                cb.setText((n == null || n.isEmpty() ? d.getAddress() : n));
                cb.setChecked(cars.contains(d.getAddress()));
                cb.setOnCheckedChangeListener((v, on) -> {
                    Set<String> s = Prefs.cars(this);
                    if (on) s.add(d.getAddress()); else s.remove(d.getAddress());
                    Prefs.setCars(this, s);
                });
                root.addView(cb);
            }
        }

        // --- הרשאות ---
        title("🔐 הרשאות (פעם אחת)");
        boolean fine = has(Manifest.permission.ACCESS_FINE_LOCATION);
        button((fine ? "✅" : "1️⃣") + " מיקום", v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2));
        boolean bg = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        button((bg ? "✅" : "2️⃣") + " מיקום כל הזמן (נדרש לשידור ברקע)", v -> {
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2); return; }
            if (Build.VERSION.SDK_INT >= 30) openAppSettings(); else requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
        });
        if (!bg && Build.VERSION.SDK_INT >= 30) note("בהגדרות שנפתחות: הרשאות ← מיקום ← \"לאפשר כל הזמן\".");
        if (Build.VERSION.SDK_INT >= 33) {
            boolean nt = has(Manifest.permission.POST_NOTIFICATIONS);
            button((nt ? "✅" : "3️⃣") + " התראות (רואים מתי משדרים ומתי שער נפתח)", v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4));
        }
        PowerManager pm = getSystemService(PowerManager.class);
        boolean batt = pm.isIgnoringBatteryOptimizations(getPackageName());
        button((batt ? "✅" : "4️⃣") + " בלי חיסכון סוללה (אחרת אנדרואיד חוסם התחלה ברקע)", v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { openAppSettings(); }
        });
        note("בשיאומי / רדמי: גם בהגדרות האפליקציה ← \"חיסכון בסוללה\" ← \"ללא הגבלות\", ו\"הפעלה אוטומטית\" מופעלת.");

        // --- שידור ידני ---
        title("📡 שידור ידני");
        note("למשל כשנוסעים ברכב אחר. נעצר לבד בסוף הזמן.");
        status = note("");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int m : new int[]{15, 30, 60}) {
            Button bt = new Button(this);
            bt.setText(m == 60 ? "שעה" : m + " דק׳");
            bt.setOnClickListener(v -> { LocService.start(this, "ידני", m); refresh(); });
            row.addView(bt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(row);
        button("⏹️ עצור שידור", v -> { LocService.stop(this); h.postDelayed(this::refresh, 500); });

        // --- קישור ---
        title("🔗 הקישור האישי");
        note("אם קיבלת קישור חדש – פשוט לחץ עליו בוואטסאפ ובחר \"שערים\".");
        button("החלף קישור", v -> { Prefs.setUrl(this, ""); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });

        button("✔️ סיום", v -> finish());
        setContentView(sv);
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        status.setText(LocService.running ? "🟢 משדר עכשיו · " + LocService.state : "⚪ לא משדר" + (LocService.state.isEmpty() ? "" : " · " + LocService.state));
        h.removeCallbacksAndMessages(null);
        h.postDelayed(this::refresh, 2000);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
    }
}
