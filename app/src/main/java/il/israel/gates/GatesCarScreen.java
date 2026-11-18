package il.israel.gates;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** מסך השערים ברכב: שורה לכל שער (שם + מרחק), לחיצה = פתיחה. הנתונים מהשרת דרך הקישור האישי (כמו דף השערים). */
public class GatesCarScreen extends Screen {
    private JSONObject st;          // תשובת /st
    private String err = null;      // הודעה כשאין נתונים
    private boolean loading = true;
    private final Handler main = new Handler(Looper.getMainLooper());

    GatesCarScreen(@NonNull CarContext c) {
        super(c);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStart(@NonNull LifecycleOwner o) { load(); }
        });
    }

    private String url(String sub) {
        String s = Prefs.url(getCarContext());
        if (!Prefs.valid(s)) return null;
        Uri u = Uri.parse(s);
        return u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/" + sub + "?t=" + Uri.encode(u.getQueryParameter("t"));
    }

    private static String read(HttpURLConnection h) throws Exception {
        try (InputStream in = h.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) { return sc.hasNext() ? sc.next() : "{}"; }
    }

    private void load() {
        final String u = url("st");
        if (u == null) { loading = false; err = "פתח את אפליקציית השערים בטלפון והתחבר (קישור או מספר טלפון)."; invalidate(); return; }
        new Thread(() -> {
            JSONObject j = null; String e = null;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(8000);
                if (h.getResponseCode() == 403) e = "הגישה שלך הסתיימה. בקש גישה חדשה.";
                else j = new JSONObject(read(h));
            } catch (Exception x) { e = "אין חיבור לאינטרנט. נסה שוב."; }
            final JSONObject fj = j; final String fe = e;
            main.post(() -> { st = fj; err = fe; loading = false; invalidate(); });
        }).start();
    }

    private void open(String gate) {
        final String u = url("open");
        if (u == null) return;
        CarToast.makeText(getCarContext(), "פותח את " + gate + "…", CarToast.LENGTH_SHORT).show();
        new Thread(() -> {
            String r;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(30000);
                h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = h.getOutputStream()) { o.write(("gate=" + URLEncoder.encode(gate, "UTF-8")).getBytes(StandardCharsets.UTF_8)); }
                r = new JSONObject(read(h)).optString("r", "בוצע");
            } catch (Exception x) { r = "⚠️ אין חיבור – השער לא נפתח"; }
            final String fr = r;
            main.post(() -> { CarToast.makeText(getCarContext(), fr, CarToast.LENGTH_LONG).show(); load(); });
        }).start();
    }

    private static String dist(double d) { return d < 1000 ? Math.round(d) + " מ׳ ממך" : String.format(java.util.Locale.US, "%.1f", d / 1000) + " ק\"מ ממך"; }

    @NonNull @Override
    public Template onGetTemplate() {
        ActionStrip strip = new ActionStrip.Builder().addAction(new Action.Builder().setTitle("רענן").setOnClickListener(() -> { loading = true; invalidate(); load(); }).build()).build();
        if (st == null) {
            MessageTemplate.Builder m = new MessageTemplate.Builder(loading ? "טוען את השערים…" : (err == null ? "אין נתונים" : err)).setTitle("שערים").setHeaderAction(Action.APP_ICON);
            if (loading) m.setLoading(true); else m.addAction(new Action.Builder().setTitle("נסה שוב").setOnClickListener(() -> { loading = true; invalidate(); load(); }).build());
            return m.build();
        }
        ItemList.Builder list = new ItemList.Builder();
        JSONArray allowed = st.optJSONArray("allowed");
        JSONObject dists = st.optJSONObject("dists");
        int n = 0;
        for (int i = 0; allowed != null && i < allowed.length() && n < 6; i++, n++) {
            final String g = allowed.optString(i);
            Row.Builder r = new Row.Builder().setTitle("🚪 " + g).setOnClickListener(() -> open(g));
            if (dists != null && dists.has(g)) r.addText(dist(dists.optDouble(g)) + " · לחיצה פותחת");
            else r.addText("לחיצה פותחת");
            list.addItem(r.build());
        }
        if (n == 0) list.setNoItemsMessage("אין שערים מותרים");
        String title = st.optBoolean("needGoogle") ? "שערים · ⚠️ התחבר עם גוגל בטלפון" : "שערים";
        return new ListTemplate.Builder().setSingleList(list.build()).setTitle(title).setHeaderAction(Action.APP_ICON).setActionStrip(strip).build();
    }
}
