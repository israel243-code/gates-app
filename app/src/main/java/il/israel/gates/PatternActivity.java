package il.israel.gates;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 🔐 23/11: מסך תבנית החלקה (3×3) לנעילה במסך הרכב. פעם ראשונה – יצירת תבנית (פעמיים); אחר כך – פתיחה.
 * next = מסך לחזור אליו (הגדרות / קליפים), url = כתובת לטעון בדף הראשי (הדשבורד).
 */
public class PatternActivity extends Activity {
    private TextView msg;
    private String first = null;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.theme(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.hero(this, "🔐 נעילה", CarLock.hasPattern(this) ? "החלק את התבנית כדי לפתוח" : "צור תבנית החלקה לנעילת מסך הרכב"));
        msg = Ui.text(this, CarLock.hasPattern(this) ? "דף השערים פתוח לכולם. כל השאר – אחרי התבנית, פעם אחת בכל הפעלה של הרכב." : "צייר תבנית של 4 נקודות לפחות. תצטרך לצייר אותה פעמיים.", 17 * Ui.k(this), Ui.INK, true);
        msg.setGravity(Gravity.CENTER); msg.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));
        root.addView(msg);
        Grid g = new Grid(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 320), Ui.dp(this, 320)); lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = Ui.dp(this, 8);
        root.addView(g, lp);
        LinearLayout k = new LinearLayout(this); k.setOrientation(LinearLayout.VERTICAL); k.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16));
        root.addView(k);
        Ui.button(this, k, "↩️ חזרה לשערים", false, v -> back());
        if (CarLock.hasPattern(this)) Ui.button(this, k, "שכחתי את התבנית", false, v -> new android.app.AlertDialog.Builder(this)
            .setMessage("התבנית תימחק, ומסך הרכב יתנתק. כדי להתחבר שוב צריך לסרוק את הברקוד מהטלפון שלך (הגדרות ← חיבור מסך רכב). להמשיך?")
            .setPositiveButton("כן, מחק", (d, w) -> { CarLock.forget(this); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); })
            .setNegativeButton("ביטול", null).show());
        setContentView(root);
    }

    private void back() { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); }
    @Override public void onBackPressed() { back(); }

    private void done(String pattern) {
        if (!CarLock.hasPattern(this)) {
            if (pattern.length() < 4) { say("לפחות 4 נקודות. נסה שוב.", true); return; }
            if (first == null) { first = pattern; say("צייר את אותה תבנית שוב לאישור", false); return; }
            if (!first.equals(pattern)) { first = null; say("התבניות לא זהות. התחל מחדש.", true); return; }
            CarLock.set(this, pattern);
        } else {
            long w = CarLock.waitMs(this);
            if (w > 0) { say("יותר מדי ניסיונות. נסה שוב בעוד " + (w / 1000 + 1) + " שניות.", true); return; }
            if (!CarLock.check(this, pattern)) { say("תבנית שגויה", true); return; }
        }
        CarLock.unlocked = true;
        String next = getIntent().getStringExtra("next"), url = getIntent().getStringExtra("url");
        try {
            if (next != null) startActivity(new Intent(this, Class.forName(next)));
            else startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("unlockUrl", url));
        } catch (Exception e) { back(); return; }
        finish();
    }
    private void say(String s, boolean bad) { msg.setText(s); msg.setTextColor(bad ? Color.parseColor("#DC2626") : Ui.INK); }

    /** רשת 3×3: מגע ← מחברים נקודות; הרמת האצבע = התבנית ("0-4-8-…") */
    private class Grid extends View {
        private final java.util.List<Integer> sel = new java.util.ArrayList<>();
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), on = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fx = -1, fy = -1;
        Grid(Context c) {
            super(c);
            dot.setColor(Color.parseColor("#94A3B8")); on.setColor(Color.parseColor("#2563EB"));
            line.setColor(Color.parseColor("#2563EB")); line.setStrokeWidth(Ui.dp(c, 6)); line.setStrokeCap(Paint.Cap.ROUND); line.setAlpha(170);
        }
        private float cx(int i) { return getWidth() * (1 + 2 * (i % 3)) / 6f; }
        private float cy(int i) { return getHeight() * (1 + 2 * (i / 3)) / 6f; }
        @Override protected void onDraw(Canvas cv) {
            for (int i = 1; i < sel.size(); i++) cv.drawLine(cx(sel.get(i - 1)), cy(sel.get(i - 1)), cx(sel.get(i)), cy(sel.get(i)), line);
            if (!sel.isEmpty() && fx >= 0) cv.drawLine(cx(sel.get(sel.size() - 1)), cy(sel.get(sel.size() - 1)), fx, fy, line);
            for (int i = 0; i < 9; i++) cv.drawCircle(cx(i), cy(i), sel.contains(i) ? getWidth() / 16f : getWidth() / 26f, sel.contains(i) ? on : dot);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            fx = e.getX(); fy = e.getY();
            if (e.getAction() == MotionEvent.ACTION_DOWN) sel.clear();
            for (int i = 0; i < 9; i++) if (!sel.contains(i) && Math.hypot(fx - cx(i), fy - cy(i)) < getWidth() / 9f) sel.add(i);
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                StringBuilder s = new StringBuilder(); for (int i : sel) s.append(i);
                fx = -1; invalidate();
                final String pat = s.toString();
                ui.postDelayed(() -> { sel.clear(); invalidate(); }, 300);
                if (pat.length() > 0) done(pat);
                return true;
            }
            invalidate();
            return true;
        }
    }
}
