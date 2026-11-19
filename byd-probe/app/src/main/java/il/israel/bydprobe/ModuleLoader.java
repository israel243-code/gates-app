package il.israel.bydprobe;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.function.Consumer;

import dalvik.system.DexClassLoader;

/**
 * סקריפט מתעדכן (19/11): מוריד מהשרת את קוד הבדיקה העדכני בלי להתקין אפליקציה.
 * אבטחה: הקוד רץ רק אם החתימה שלו (RSA, המפתח הפרטי רק בשרת) מתאימה למפתח הציבורי שבתוך האפליקציה, וגם ה-SHA-256 של הקובץ תואם.
 * רץ רק בלחיצה על הכפתור – לא לבד.
 */
final class ModuleLoader {
    static final String BASE = "https://isrnb.duckdns.org/mod/";
    static final String PUB = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1zZ6tPFQXbMgfud8cFYhJDMG532wUnqKPz2x94JfKgB5ny0GCZ8R+CxknZ4FvbIbUQJn72nqaEUlvfKTv0KMUIuspzPFAR6EOfAG5F2avbQYvp8RwcL88z8KJuHcPkmRhPbzluGOTZaF2A9cpSHX/n13IF/mreQeEpGEkKqe2xbB7pQQaREwIh3Q+WDlFZIzwY2zcOdnI5MdZuLjKldSfvCrsk5RRjgCCtfIV8THc1iGsQGJkXpuV1K+KiNZiRbrr1Ub+8KrhtHYYWtP/TK03+z66f5mLV7VonpZ+EOX1qjmXlGmoFlqycrBAF30vjf3xlLJ7E/Fh0tCM48ETuPaCQIDAQAB";

    private ModuleLoader() {}

    static String run(Context c, Consumer<String> progress) throws Exception {
        progress.accept("בודק גרסת סקריפט");
        JSONObject m = new JSONObject(new String(get(BASE + "probe.json"), StandardCharsets.UTF_8));
        String v = m.getString("v"), sha = m.getString("sha256"), entry = m.getString("entry"), jar = m.getString("jar");
        if (!jar.matches("probe-\\d{1,6}\\.jar") || !entry.matches("[A-Za-z0-9_.]{3,80}")) throw new SecurityException("מניפסט לא תקין");
        verify(v + "|" + sha + "|" + entry, m.getString("sig"));

        File dir = new File(c.getFilesDir(), "mod");
        dir.mkdirs();
        File f = new File(dir, jar);
        if (!f.exists() || !sha.equalsIgnoreCase(sha256(f))) {
            progress.accept("מוריד סקריפט " + v);
            File tmp = new File(dir, jar + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(get(BASE + jar)); }
            if (!sha.equalsIgnoreCase(sha256(tmp))) { tmp.delete(); throw new SecurityException("הקובץ שהורד לא תואם לחתימה"); }
            f.delete();
            if (!tmp.renameTo(f)) throw new IllegalStateException("שמירה נכשלה");
        }
        f.setReadOnly();
        File[] old = dir.listFiles();
        if (old != null) for (File o : old) if (!o.getName().equals(jar)) o.delete();

        progress.accept("מריץ סקריפט " + v);
        DexClassLoader cl = new DexClassLoader(f.getAbsolutePath(), c.getCodeCacheDir().getAbsolutePath(), null, ModuleLoader.class.getClassLoader());
        Object r = cl.loadClass(entry).getMethod("run", Context.class, Consumer.class).invoke(null, c, progress);
        return "🧩 סקריפט מתעדכן, גרסה " + v + "\n" + r;
    }

    private static void verify(String msg, String sigB64) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(PUB, Base64.DEFAULT))));
        s.update(msg.getBytes(StandardCharsets.UTF_8));
        if (!s.verify(Base64.decode(sigB64, Base64.DEFAULT))) throw new SecurityException("החתימה של הסקריפט לא תקינה – לא הורץ");
    }

    private static String sha256(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream i = new FileInputStream(f)) { byte[] b = new byte[65536]; int n; while ((n = i.read(b)) > 0) d.update(b, 0, n); }
        StringBuilder sb = new StringBuilder();
        for (byte x : d.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000); h.setReadTimeout(30000);
        if (h.getResponseCode() != 200) throw new IllegalStateException("השרת ענה " + h.getResponseCode() + " (עוד לא פורסם סקריפט?)");
        try (InputStream i = h.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[65536]; int n;
            while ((n = i.read(b)) > 0) { o.write(b, 0, n); if (o.size() > 5_000_000) throw new IllegalStateException("קובץ גדול מדי"); }
            return o.toByteArray();
        }
    }
}
