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

    @Override public void finish() { super.finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right); } // יציאה רכה

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(0, 0, 0, Ui.dp(this, 28));
        sv.addView(root);

        LinearLayout hero = Ui.hero(this, "⚙️ הגדרות", "השערים נפתחים לבד כשמתקרבים – רק כשאתה ברכב");
        TextView back = Ui.text(this, "→ חזרה", 16, Color.WHITE, true); // חזרה נעימה למעלה, לא רק "סיום" בתחתית (18/11)
        back.setPadding(0, 0, 0, Ui.dp(this, 12));
        back.setOnClickListener(v -> finish());
        hero.addView(back, 0);
        root.addView(hero);

        // --- 1. הרשאות ---
        boolean fine = has(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean bg = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        boolean nt = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS);
        boolean car = Prefs.carScreen(this); // 🖥️ מסך הרכב: בלי בלוטות' ובלי חיסכון סוללה (19/11, הבעלים: "לא רלוונטי ברכב")
        boolean bt = car || Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT);
        boolean batt = car || getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean files = !car || Build.VERSION.SDK_INT > 32 || has(Manifest.permission.READ_EXTERNAL_STORAGE); // 🎥 קליפים מה-DVR (מסך הרכב)
        int done = (fine ? 1 : 0) + (bg ? 1 : 0) + (nt ? 1 : 0) + (car ? (files ? 1 : 0) : (bt ? 1 : 0) + (batt ? 1 : 0));
        LinearLayout perm = Ui.card(this, root, "🔐 הרשאות · " + done + " מתוך " + (car ? 4 : 5));
        Ui.step(this, perm, 1, fine, "מיקום", "כדי לדעת כשאתה מתקרב לשער", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2));
        Ui.step(this, perm, 2, bg, "מיקום כל הזמן", Build.VERSION.SDK_INT >= 30 ? "בהגדרות: מיקום ← \"לאפשר כל הזמן\"" : "נדרש לשידור ברקע", "פתח", v -> {
            if (!fine) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2); return; }
            if (Build.VERSION.SDK_INT >= 30) openAppSettings(); else requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
        });
        if (!car) Ui.step(this, perm, 3, bt, "בלוטות'", "כדי לזהות שנכנסת לרכב", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1));
        Ui.step(this, perm, car ? 3 : 4, nt, "התראות", "רואים מתי משדרים ומתי שער נפתח", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4));
        if (car) Ui.step(this, perm, 4, files, "קבצים", "כדי לשלוח לטלפון קליפים מהמצלמות של הרכב", "אפשר",
                v -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 5));
        if (!car) Ui.step(this, perm, 5, batt, "בלי חיסכון סוללה", "אחרת אנדרואיד חוסם התחלה ברקע", "פתח", v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { openAppSettings(); }
        });
        if (Build.MANUFACTURER.toLowerCase().matches(".*(xiaomi|redmi|poco).*"))
            Ui.note(this, perm, "בשיאומי / רדמי: גם בהגדרות האפליקציה ← \"חיסכון בסוללה\" ← \"ללא הגבלות\", ו\"הפעלה אוטומטית\" מופעלת.");

        // --- 2. רכבים ---
        Ui.button(this, root, "📸 צלם את המסך ושלח לבדיקה", false, v -> Snap.take(this, "מסך ההגדרות")); // 22/11
        if (Prefs.carScreen(this)) Ui.button(this, root, "🎥 קליפים מהרכב (מהכרטיס, בלי אינטרנט)", true, v -> startActivity(new Intent(this, ClipsActivity.class)));
        LinearLayout cars = Ui.card(this, root, car ? "🚗 מסך הרכב" : "🚗 הרכבים שלי");
        Switch auto = new Switch(this);
        auto.setText(car ? "🚪 פתיחה אוטומטית – השערים נפתחים לבד כשהרכב מתקרב" : "🚪 פתיחה אוטומטית – כשנכנסים לרכב, השערים נפתחים לבד כשמתקרבים");
        auto.setTextSize(15 * Ui.k(this));
        auto.setPadding(0, Ui.dp(this, Prefs.carScreen(this) ? 12 : 4), 0, Ui.dp(this, Prefs.carScreen(this) ? 12 : 4));
        auto.setTextColor(Ui.INK);
        auto.setChecked(Prefs.auto(this));
        auto.setOnCheckedChangeListener((v, on) -> { Prefs.setAuto(this, on); if (on) CarCheck.run(this, "הופעל בהגדרות"); else if (LocService.running) LocService.stop(this); });
        cars.addView(auto);
        Ui.note(this, cars, "כבוי = השערים נפתחים רק בכפתור \"פתח\". הסימון \"פתיחה אוטומטית\" ליד כל שער בדף השערים בוחר אילו שערים.");
        Switch scr = new Switch(this);
        scr.setText("🖥️ זה מסך הרכב – לשדר תמיד כשהרכב דלוק (בלי טלפון)");
        scr.setTextSize(15 * Ui.k(this));
        scr.setPadding(0, Ui.dp(this, Prefs.carScreen(this) ? 12 : 4), 0, Ui.dp(this, Prefs.carScreen(this) ? 12 : 4));
        scr.setTextColor(Ui.INK);
        scr.setChecked(Prefs.carScreen(this));
        scr.setOnCheckedChangeListener((v, on) -> { Prefs.setCarScreen(this, on); if (on) CarCheck.run(this, "הופעל בהגדרות"); else if (LocService.running) LocService.stop(this); });
        cars.addView(scr);
        if (car) {
            Net.start(this); Ui.note(this, cars, Net.status(this) + "\nאם כתוב \"לא זמין\": בהגדרות הרכב ← שימוש בנתונים / ניהול נתונים ← לאפשר לאפליקציה \"שערים\" נתונים ניידים ונתוני רקע."); // 📶 22/11
            Ui.note(this, cars, "המיקום מגיע מה-GPS של הרכב, כל עוד הרכב דלוק. לא צריך טלפון ולא בלוטות'.");
        } else {
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
        }
        // 🚗 חיבור מסך רכב מהטלפון: סריקת הברקוד או הקלדת הקוד שעל המסך (19/11)
        if (!car && Prefs.valid(Prefs.url(this))) {
            LinearLayout pc = Ui.card(this, root, "🖥️ חיבור מסך רכב");
            Ui.note(this, pc, "במסך הרכב, באפליקציית השערים, מופיע ברקוד וקוד של 6 ספרות.");
            Ui.button(this, pc, "📷 סרוק את הברקוד", true, v -> Pair.scan(this));
            Ui.button(this, pc, "🔢 הקלד את הקוד", false, v -> Pair.askCode(this));
        }

        // --- פרטיות (ניסיוני) – תשתית לאפשרות ג'. רק במצב מנהל (הבעלים, 18/11) ---
        if (!Prefs.isDash(Prefs.admin(this))) { if (Prefs.local(this)) Prefs.setLocal(this, false); } else {
        LinearLayout priv = Ui.card(this, root, "🔒 חישוב בטלפון (ניסיוני)");
        if (car) priv.setVisibility(View.GONE); // 20/11: מסך הרכב – בלי דברים שלא רלוונטיים
        Switch loc = new Switch(this);
        loc.setText("המיקום לא נשלח לשרת – הטלפון מחליט לבד מתי לפתוח");
        loc.setTextSize(15);
        loc.setTextColor(Ui.INK);
        loc.setChecked(Prefs.local(this));
        loc.setOnCheckedChangeListener((v, on) -> Prefs.setLocal(this, on));
        priv.addView(loc);
        Ui.button(this, priv, "🔄 עדכן עכשיו את נתוני השערים", false, v -> { getSharedPreferences("gates", MODE_PRIVATE).edit().putBoolean("stDirty", true).apply(); android.widget.Toast.makeText(this, "יתעדכן בתחילת השידור הבא", android.widget.Toast.LENGTH_SHORT).show(); });
        // 📋 יומן החישוב בטלפון – מה הטלפון ראה והחליט (18/11)
        StringBuilder lg = new StringBuilder();
        synchronized (LocalEngine.LOG) { for (int i = 0; i < Math.min(15, LocalEngine.LOG.size()); i++) lg.append(LocalEngine.LOG.get(i)).append('\n'); }
        Ui.note(this, priv, "📋 היומן המלא, וכפתור \"שלח עכשיו לבדיקה\": בדשבורד ← 🛠️ ניהול ← 📱 יומן הטלפון.");
        Ui.note(this, priv, "📋 מה קרה לאחרונה (" + (LocService.running ? "משדר עכשיו: " + LocService.state : "לא משדר עכשיו") + "):\n" + (lg.length() == 0 ? "עוד אין רישומים – יופיעו בנסיעה הבאה." : lg.toString().trim()));
        Ui.note(this, priv, "אותם כללים: בתוך המרחק, מתקרבים, וזמן בין פתיחות. לשרת נשלחת רק בקשה \"לפתוח את השער\". המיקום שלך לא יופיע במפה של אף אחד.");
        }

        // --- 3. שידור ידני ---
        LinearLayout man = Ui.card(this, root, "📡 שידור עכשיו");
        if (car) man.setVisibility(View.GONE); // ברכב משדרים לבד, אין "שידור מרכב אחר"
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

        // --- 🔔 התראות (19/11): גם כשהאפליקציה סגורה ---
        LinearLayout ntc = Ui.card(this, root, "🔔 התראות");
        if (car) ntc.setVisibility(View.GONE);
        Ui.note(this, ntc, "התראות בטלפון – גם כשהאפליקציה סגורה, בזמן שהיא משדרת.");
        String[][] NT = {{"nOpen", "כששער נפתח", "1"}, {"nFail", "כשפתיחה נכשלה", "1"}, {"nSound", "עם צליל ורטט", "1"}, {"nService", "כשהשידור מתחיל ונעצר", "0"}};
        for (String[] x : NT) {
            Switch sw = new Switch(this);
            sw.setText(x[1]); sw.setTextSize(15); sw.setTextColor(Ui.INK); sw.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            sw.setChecked(Prefs.flag(this, x[0], "1".equals(x[2])));
            final String k = x[0];
            sw.setOnCheckedChangeListener((v, on) -> Prefs.setFlag(this, k, on));
            ntc.addView(sw);
        }

        // --- נעילה (לבחירת המשתמש, 18/11) ---
        LinearLayout lk = Ui.card(this, root, "🔒 נעילה");
        if (car) lk.setVisibility(View.GONE); // אין טביעת אצבע / נעילת מסך במסך הרכב
        Ui.note(this, lk, Lock.available(this) ? "טביעת אצבע, פנים או קוד הטלפון. הפתיחה האוטומטית ברכב לא מושפעת."
                : "בטלפון אין נעילת מסך, לכן הנעילה לא תפעל. מגדירים נעילת מסך בהגדרות הטלפון.");
        String[] LK = {"בלי נעילה", "לפני כל פתיחה בכפתור", "בכל כניסה לאפליקציה"};
        android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
        for (int i = 0; i < 3; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(100 + i); rb.setText(LK[i]); rb.setTextColor(Ui.INK); rb.setTextSize(15);
            rg.addView(rb);
        }
        rg.check(100 + Prefs.lock(this));
        rg.setOnCheckedChangeListener((g, id) -> {
            int nv = id - 100;
            if (nv == Prefs.lock(this)) return;
            Lock.auth(this, "שינוי הנעילה", () -> Prefs.setLock(this, nv), this::build); // שינוי דורש אימות; ביטול = חוזר למצב הקודם
        });
        lk.addView(rg);

        // --- מצב מנהל ---
        LinearLayout adm = Ui.card(this, root, "🛡️ מצב מנהל (לבעלים)");
        if (car) adm.setVisibility(View.GONE);
        if (!Prefs.isDash(Prefs.admin(this))) {
            Ui.note(this, adm, "שלח לבוט \"דשבורד\", העתק את הקישור ולחץ כאן. האפליקציה תציג את הדשבורד המלא; הכניסה בקוד מוואטסאפ.");
            Ui.button(this, adm, "📋 הדבק קישור דשבורד", false, v -> {
                android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                CharSequence t = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0 ? cm.getPrimaryClip().getItemAt(0).coerceToText(this) : null;
                String s = t == null ? "" : t.toString().trim().replaceAll("(?s)^.*?(https://\\S+).*$", "$1");
                if (!Prefs.isDash(s)) { android.widget.Toast.makeText(this, "בלוח אין קישור דשבורד", android.widget.Toast.LENGTH_LONG).show(); return; }
                Prefs.setAdmin(this, s); Prefs.setAdminView(this, true);
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
            });
        } else {
            Ui.note(this, adm, Prefs.adminView(this) ? "המסך הראשי: הדשבורד המלא." : "המסך הראשי: דף השערים.");
            Ui.button(this, adm, Prefs.adminView(this) ? "🚪 עבור לדף השערים" : "🛡️ עבור לדשבורד", false, v -> {
                Prefs.setAdminView(this, !Prefs.adminView(this));
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
            });
            Ui.button(this, adm, "הסר מצב מנהל", false, v -> {
                Prefs.setAdmin(this, "");
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
            });
        }

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
