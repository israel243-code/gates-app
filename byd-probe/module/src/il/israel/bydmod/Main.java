package il.israel.bydmod;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * סקריפט הבדיקה המתעדכן (נטען מהשרת, בלי התקנת אפליקציה). רק קריאה.
 * כדי לשנות בדיקה: עורכים את הקובץ הזה, דוחפים, והאפליקציה ברכב מורידה את הגרסה החדשה בלחיצה על "הרץ סקריפט".
 */
public class Main {
    public static String run(Context c, Consumer<String> progress) {
        StringBuilder out = new StringBuilder();
        out.append("סקריפט מתעדכן: גרסה בסיס\n");
        step(progress, "זהות התהליך");
        out.append("\n-- id\n").append(sh("id", null));
        step(progress, "שירותי מערכת");
        out.append("\n-- שירותים (מצלמה / BYD / DVR / פנורמה)\n").append(sh("service list", "(?i)cam|avm|byd|cdr|pano|dvr"));
        step(progress, "מאפייני מערכת");
        out.append("\n-- מאפייני מערכת (מצלמה / DVR / פנורמה)\n").append(sh("getprop", "(?i)cam|avm|dvr|cdr|pano|sentry"));
        step(progress, "אחסון");
        out.append("\n-- /storage\n").append(sh("ls -la /storage", null));
        out.append("\n=== סוף ===\n");
        return out.toString();
    }

    private static void step(Consumer<String> p, String s) { if (p != null) p.accept(s); }

    private static String sh(String cmd, String filter) {
        StringBuilder b = new StringBuilder();
        Pattern f = filter == null ? null : Pattern.compile(filter);
        try {
            Process pr = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String l; int n = 0;
                while ((l = r.readLine()) != null && n < 400) if (f == null || f.matcher(l).find()) { b.append("  ").append(l).append('\n'); n++; }
            }
            pr.waitFor();
        } catch (Throwable e) { b.append("  ✗ ").append(e).append('\n'); }
        return b.length() == 0 ? "  (אין)\n" : b.toString();
    }
}
