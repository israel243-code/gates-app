package il.israel.gates;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** 🎥 22/11: במסך הרכב – הקליפים של מצלמות הרכב ישירות מהכרטיס / האחסון, בלי אינטרנט. לחיצה = ניגון מיידי. */
public class ClipsActivity extends Activity {
    private String kind = "";
    private VideoView player;

    @Override protected void onCreate(Bundle b) { super.onCreate(b); Ui.theme(this); list(); }

    private void list() {
        player = null;
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (16 * getResources().getDisplayMetrics().density); root.setPadding(p, p, p, p); sv.addView(root);
        TextView h = new TextView(this); h.setText("🎥 קליפים מהרכב (מהכרטיס)"); h.setTextSize(24 * Ui.k(this)); h.setTypeface(null, Typeface.BOLD); h.setTextColor(Ui.INK); root.addView(h);
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); root.addView(bar);
        String[][] ks = { { "", "הכל" }, { "drive", "🚗 נסיעה" }, { "sentry", "🛡️ שומר" }, { "back", "→ חזרה" } };
        for (String[] kk : ks) {
            Button bt = Ui.button(this, bar, kk[1], kk[0].equals(kind), v -> { if (kk[0].equals("back")) finish(); else { kind = kk[0]; list(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(4, 8, 4, 0); bt.setLayoutParams(lp);
        }
        List<File> fs;
        try { fs = ClipAgent.scan(); } catch (Exception e) { fs = java.util.Collections.emptyList(); }
        if (fs.isEmpty()) { TextView t = new TextView(this); t.setText("לא נמצאו קליפים מ-3 הימים האחרונים.\nאם יש הקלטות ברכב: הגדרות ← הרשאות ← קבצים."); t.setTextSize(17 * Ui.k(this)); t.setTextColor(Ui.MUTED); t.setPadding(0, p, 0, 0); root.addView(t); }
        SimpleDateFormat day = new SimpleDateFormat("EEEE d/M", new Locale("iw")), tm = new SimpleDateFormat("HH:mm", Locale.US);
        String lastDay = ""; int n = 0;
        for (File f : fs) {
            String k = f.getName().startsWith("S_") ? "sentry" : f.getName().startsWith("R_") ? "drive" : "other";
            if (!kind.isEmpty() && !kind.equals(k)) continue;
            if (++n > 300) break;
            String d = day.format(f.lastModified());
            if (!d.equals(lastDay)) { lastDay = d; TextView dt = new TextView(this); dt.setText(d); dt.setTextSize(17 * Ui.k(this)); dt.setTypeface(null, Typeface.BOLD); dt.setTextColor(Ui.MUTED); dt.setPadding(0, p, 0, p / 3); root.addView(dt); }
            final File ff = f;
            Button it = Ui.button(this, root, "▶️  " + tm.format(f.lastModified()) + "   ·   " + (k.equals("sentry") ? "🛡️ שומר" : "🚗 נסיעה") + "   ·   " + Math.round(f.length() / 1e6) + "MB", false, v -> play(ff));
            it.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
        setContentView(sv);
    }

    private void play(File f) {
        FrameLayout fl = new FrameLayout(this); fl.setBackgroundColor(Color.BLACK);
        player = new VideoView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER); fl.addView(player, lp);
        LinearLayout xb = new LinearLayout(this);
        Button x = Ui.button(this, xb, "✕ סגור", true, v -> list());
        fl.addView(xb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        MediaController mc = new MediaController(this); mc.setAnchorView(player); player.setMediaController(mc);
        player.setVideoPath(f.getAbsolutePath());
        player.setOnErrorListener((mp, what, extra) -> { android.widget.Toast.makeText(this, "⚠️ לא הצלחתי לנגן את הקליף הזה", android.widget.Toast.LENGTH_LONG).show(); list(); return true; });
        player.setOnPreparedListener(mp -> player.start());
        setContentView(fl);
    }

    @Override public void onBackPressed() { if (player != null) list(); else super.onBackPressed(); }
}
