package il.israel.gates;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.media.MediaBrowserService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * אנדרואיד אוטו דרך "אפליקציית מדיה" (18/11): אפליקציות מספריית הרכב לא מוצגות כשהן לא מחנות Play,
 * אבל אפליקציית מדיה כן ("מקורות לא ידועים"). ברכב: רשימת השערים כמו רשימת שירים – לחיצה על שער = פתיחה.
 * מובנה באנדרואיד, בלי ספריות.
 */
public class GatesMediaService extends MediaBrowserService {
    private MediaSession session;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "gates");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlayFromMediaId(String id, Bundle extras) { open(id); }
            @Override public void onPlay() { show("בחר שער מהרשימה", PlaybackState.STATE_PAUSED); }
        });
        setSessionToken(session.getSessionToken());
        show("בחר שער מהרשימה", PlaybackState.STATE_PAUSED);
        session.setActive(true);
    }

    @Override public void onDestroy() { if (session != null) session.release(); super.onDestroy(); }

    @Override
    public BrowserRoot onGetRoot(String pkg, int uid, Bundle hints) {
        // תצוגת רשת (אריחים גדולים) במקום רשימת שירים (הבעלים, 18/11: "לא אהבתי שזה כמו נגן")
        Bundle x = new Bundle();
        x.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);
        x.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2);
        x.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2);
        return new BrowserRoot("root", x);
    }

    /** סמל עגול גדול לאריח: צבע + אימוג'י */
    private static Bitmap icon(String emoji, int color) {
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(128, 128, 124, p);
        p.setColor(0xFFFFFFFF); p.setTextSize(130); p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics f = p.getFontMetrics();
        c.drawText(emoji, 128, 128 - (f.ascent + f.descent) / 2, p);
        return b;
    }

    private void show(String title, int st) {
        Bitmap art = icon(title.startsWith("✅") ? "✅" : title.startsWith("⚠") ? "⚠️" : title.startsWith("⏳") ? "⏳" : "🚪",
                title.startsWith("✅") ? 0xFF16A34A : title.startsWith("⚠") ? 0xFFDC2626 : 0xFF2563EB);
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, "שערים")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "שערים").putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art).build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY)
                .setState(st, 0, 0).build());
    }

    private String url(String sub) {
        String s = Prefs.url(this);
        if (!Prefs.valid(s)) return null;
        Uri u = Uri.parse(s);
        return u.getScheme() + "://" + u.getAuthority() + u.getPath().replaceAll("/+$", "") + "/" + sub + "?t=" + Uri.encode(u.getQueryParameter("t"));
    }

    private static String read(HttpURLConnection h) throws Exception {
        try (InputStream in = h.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) { return sc.hasNext() ? sc.next() : "{}"; }
    }

    private static MediaBrowser.MediaItem item(String id, String title, String sub, boolean near) {
        return new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId(id).setTitle(title).setSubtitle(sub)
                .setIconBitmap(icon("🚪", near ? 0xFF16A34A : 0xFF2563EB)).build(), MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }

    @Override
    public void onLoadChildren(String parent, Result<List<MediaBrowser.MediaItem>> result) {
        result.detach();
        new Thread(() -> {
            List<MediaBrowser.MediaItem> list = new ArrayList<>();
            JSONObject st = null;
            String u = url("st");
            if (u != null) {
                try {
                    HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                    h.setConnectTimeout(6000); h.setReadTimeout(6000);
                    if (h.getResponseCode() == 200) st = new JSONObject(read(h));
                } catch (Exception e) { /* בלי רשת – הנתונים השמורים */ }
                if (st == null) try { String c = getSharedPreferences("gates", Context.MODE_PRIVATE).getString("stCache", ""); if (!c.isEmpty()) st = new JSONObject(c); } catch (Exception e) { /* אין */ }
            }
            if (st == null) list.add(item("none", "פתח את אפליקציית השערים בטלפון", "והתחבר (קישור או מספר טלפון)", false));
            else {
                JSONArray allowed = st.optJSONArray("allowed");
                JSONObject dists = st.optJSONObject("dists"), have = st.optJSONObject("have");
                for (int i = 0; allowed != null && i < allowed.length(); i++) {
                    String g = allowed.optString(i);
                    if (have != null && have.has(g) && !have.optBoolean(g)) continue; // לא ב-Google Home שלו
                    String sub = "לחיצה פותחת"; boolean near = false;
                    if (dists != null && dists.has(g)) { near = dists.optDouble(g) < 500; double d = dists.optDouble(g); sub = (d < 1000 ? Math.round(d) + " מ׳" : String.format(java.util.Locale.US, "%.1f", d / 1000) + " ק\"מ") + " ממך · לחיצה פותחת"; }
                    list.add(item(g, g, sub, near)); // ירוק = קרוב (עד 500 מ׳)
                }
            }
            main.post(() -> result.sendResult(list));
        }).start();
    }

    private void open(String gate) {
        if (gate == null || "none".equals(gate)) return;
        final String u = url("open");
        if (u == null) return;
        show("⏳ פותח את " + gate + "…", PlaybackState.STATE_BUFFERING);
        new Thread(() -> {
            String r;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(30000); h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = h.getOutputStream()) { o.write(("gate=" + URLEncoder.encode(gate, "UTF-8")).getBytes(StandardCharsets.UTF_8)); }
                r = new JSONObject(read(h)).optString("r", "בוצע");
            } catch (Exception e) { r = "⚠️ אין חיבור – " + gate + " לא נפתח"; }
            final String fr = r;
            main.post(() -> show(fr, PlaybackState.STATE_PAUSED));
        }).start();
    }
}
