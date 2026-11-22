package il.israel.gates;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 📸 22/11: צילום המסך של האפליקציה (ברכב או בטלפון) ושליחה לשרת לבדיקה – רק בלחיצה, רק עם הקישור האישי של הבעלים */
final class Snap {
    private Snap() {}

    static void take(final Activity a, final String note) {
        try {
            View v = a.getWindow().getDecorView();
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return;
            final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Toast.makeText(a, "📸 מצלם ושולח…", Toast.LENGTH_SHORT).show();
            PixelCopy.request(a.getWindow(), bm, (r) -> {
                if (r != PixelCopy.SUCCESS) { Toast.makeText(a, "⚠️ הצילום נכשל", Toast.LENGTH_SHORT).show(); return; }
                final Context c = a.getApplicationContext();
                new Thread(() -> { String res = upload(c, bm, note); new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c, res, Toast.LENGTH_LONG).show()); }).start();
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception e) { Toast.makeText(a, "⚠️ הצילום נכשל", Toast.LENGTH_SHORT).show(); }
    }

    private static String upload(Context c, Bitmap bm, String note) {
        try {
            String s = Prefs.url(c); if (!Prefs.valid(s)) return "⚠️ אין קישור אישי – אי אפשר לשלוח";
            Bitmap b = bm; int max = Math.max(b.getWidth(), b.getHeight());
            if (max > 1600) { float k = 1600f / max; b = Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * k), Math.round(bm.getHeight() * k), true); }
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG, 75, bo);
            int ver = 0; try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { /* אין */ }
            String info = (Prefs.carScreen(c) ? "מסך הרכב" : "טלפון") + " · " + Build.MANUFACTURER + " " + Build.MODEL + " · אנדרואיד " + Build.VERSION.RELEASE + " · עדכון " + ver + " · " + Net.status(c) + (note == null || note.isEmpty() ? "" : " · " + note);
            Uri u = Uri.parse(s);
            String url = u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/gcfg?snap=1&t=" + Uri.encode(u.getQueryParameter("t"));
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(10000); h.setReadTimeout(30000); h.setRequestMethod("POST"); h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); h.setRequestProperty("User-Agent", "GatesApp/1");
            try (OutputStream o = h.getOutputStream()) { o.write(("info=" + Uri.encode(info) + "&snap=" + Uri.encode(Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP))).getBytes(StandardCharsets.UTF_8)); }
            int code = h.getResponseCode();
            return code == 200 ? "✅ צילום המסך נשלח לבדיקה" : code == 403 ? "⚠️ רק הבעלים יכול לשלוח צילום" : "⚠️ השליחה נכשלה (" + code + ")";
        } catch (Exception e) { return "📵 אין תקשורת – הצילום לא נשלח"; }
    }
}
