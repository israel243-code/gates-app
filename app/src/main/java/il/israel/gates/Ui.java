package il.israel.gates;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** עיצוב משותף למסכים של האפליקציה: כותרת צבעונית, כרטיסים, כפתורים ושורות שלבים. */
final class Ui {
    private Ui() {}

    static int BG = Color.parseColor("#EEF2F7"), INK = Color.parseColor("#0F172A"), MUTED = Color.parseColor("#64748B"),
            SOFT = Color.parseColor("#F1F5F9"), CARD = Color.WHITE;
    static final int PRI = Color.parseColor("#2563EB"), OK = Color.parseColor("#16A34A"), WARN = Color.parseColor("#D97706");

    /** מצב לילה לפי הטלפון – אותם צבעים כמו בדשבורד (18/11) */
    static void theme(Context c) {
        boolean dark = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        BG = Color.parseColor(dark ? "#0B1220" : "#EEF2F7");
        INK = Color.parseColor(dark ? "#E2E8F0" : "#0F172A");
        MUTED = Color.parseColor(dark ? "#94A3B8" : "#64748B");
        SOFT = Color.parseColor(dark ? "#1E293B" : "#F1F5F9");
        CARD = dark ? Color.parseColor("#131C2E") : Color.WHITE;
    }

    static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    /** כותרת עליונה בגרדיאנט כחול-סגול */
    static LinearLayout hero(Context c, String title, String sub) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(c, 22), dp(c, 34), dp(c, 22), dp(c, 30));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#1E3A8A"), Color.parseColor("#2563EB"), Color.parseColor("#7C3AED")});
        h.setBackground(g);
        TextView t = text(c, title, 24, Color.WHITE, true);
        h.addView(t);
        if (sub != null) { TextView s = text(c, sub, 14, Color.parseColor("#DBEAFE"), false); s.setPadding(0, dp(c, 4), 0, 0); h.addView(s); }
        return h;
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** כרטיס לבן מעוגל עם כותרת */
    static LinearLayout card(Context c, LinearLayout parent, String title) {
        LinearLayout k = new LinearLayout(c);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        k.setBackground(round(CARD, dp(c, 18)));
        k.setElevation(dp(c, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 14), dp(c, 12), dp(c, 14), 0);
        parent.addView(k, lp);
        if (title != null) { TextView t = text(c, title, 17, INK, true); t.setPadding(0, 0, 0, dp(c, 8)); k.addView(t); }
        return k;
    }

    static TextView note(Context c, LinearLayout parent, String s) {
        TextView t = text(c, s, 13, MUTED, false);
        t.setPadding(0, dp(c, 2), 0, dp(c, 6));
        parent.addView(t);
        return t;
    }

    /** כפתור מלא (ראשי) או רך (משני) */
    static Button button(Context c, LinearLayout parent, String s, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : PRI);
        b.setBackground(round(primary ? PRI : SOFT, dp(c, 14)));
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, 0);
        parent.addView(b, lp);
        return b;
    }

    /** שורת שלב: עיגול מצב (✓ ירוק / מספר כתום) + טקסט + כפתור פעולה */
    static void step(Context c, LinearLayout parent, int n, boolean done, String title, String sub, String action, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 8), 0, dp(c, 8));
        TextView dot = text(c, done ? "✓" : String.valueOf(n), 15, Color.WHITE, true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(round(done ? OK : WARN, dp(c, 15)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        txt.addView(text(c, title, 15, INK, true));
        if (sub != null) txt.addView(text(c, sub, 12, MUTED, false));
        row.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (!done && action != null) {
            Button b = new Button(c);
            b.setText(action);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setTextColor(Color.WHITE);
            b.setBackground(round(PRI, dp(c, 12)));
            b.setStateListAnimator(null);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
            b.setOnClickListener(l);
            row.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 40)));
        }
        parent.addView(row);
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.parseColor("#E2E8F0"));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }
}
