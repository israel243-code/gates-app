package il.israel.gates;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** 🚧 כפתור "פתח שוב" בהתראת "השער תקוע?" (22/11) – פתיחה רגילה בכפתור דרך הקישור האישי. */
public class ReopenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final String g = i.getStringExtra("gate");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(i.getIntExtra("nid", 0));
        String s = Prefs.url(c);
        if (g == null || !Prefs.valid(s)) return;
        Uri u = Uri.parse(s);
        final String url = u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/open?t=" + Uri.encode(u.getQueryParameter("t"));
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(25000); h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); h.setRequestProperty("User-Agent", "GatesApp/1");
                try (OutputStream o = h.getOutputStream()) { o.write(("gate=" + Uri.encode(g)).getBytes(StandardCharsets.UTF_8)); }
                int code = h.getResponseCode(); String body = "";
                try (InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) { if (sc.hasNext()) body = sc.next(); } catch (Exception e) { /* בלי גוף */ }
                JSONObject r = new JSONObject(body.isEmpty() ? "{}" : body);
                LocalEngine.log("🚧 פתח שוב: " + g + " – " + r.optString("r", "code " + code));
                Notify.gate(c, g, r.optBoolean("ok"), r.optString("r", "אין תשובה"));
            } catch (Exception e) {
                Notify.gate(c, g, false, "אין אינטרנט");
            } finally { pr.finish(); }
        }).start();
    }
}
