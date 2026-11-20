package il.israel.bydmod;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/** סקריפט 4: הורדת קובץ התרגום לעברית (template.he.json) לתיקיית ההורדות במסך הרכב. */
public class Main {
    static final String URL_FILE = "https://isrnb.duckdns.org/mod/f/06db4b76cb1c34fc.json";
    static final String NAME = "template.he.json";

    public static String run(Context c, Consumer<String> progress) {
        StringBuilder out = new StringBuilder("סקריפט 4: הורדת קובץ התרגום לעברית\n");
        File[] dirs = { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), c.getExternalFilesDir(null) };
        byte[] data;
        try {
            if (progress != null) progress.accept("מוריד את קובץ התרגום");
            HttpURLConnection h = (HttpURLConnection) new URL(URL_FILE).openConnection();
            h.setConnectTimeout(15000); h.setReadTimeout(60000);
            if (h.getResponseCode() != 200) return out.append("✗ השרת ענה ").append(h.getResponseCode()).append('\n').toString();
            try (InputStream in = h.getInputStream()) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[65536]; int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                data = bo.toByteArray();
            }
        } catch (Throwable e) { return out.append("✗ ההורדה נכשלה: ").append(e).append('\n').toString(); }
        out.append("הורד: ").append(data.length / 1024).append(" KB\n");
        for (File d : dirs) {
            if (d == null) continue;
            try {
                d.mkdirs();
                File f = new File(d, NAME);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
                out.append("✅ נשמר: ").append(f.getAbsolutePath()).append('\n');
            } catch (Throwable e) { out.append("✗ לא נשמר ב-").append(d).append(": ").append(e).append('\n'); }
        }
        return out.append("\n=== סוף ===\n").toString();
    }
}
