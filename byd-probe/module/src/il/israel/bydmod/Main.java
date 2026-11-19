package il.israel.bydmod;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * סקריפט 2: מחלקות המצלמה של BYD (AVMCamera ב-bmmcamera.jar). קריאה בלבד:
 * רק רשימת מחלקות / פעולות / שמות שדות. לא נפתחת מצלמה, לא נקראים ערכים של קבועים (כדי לא להפעיל קוד אתחול של המחלקה), לא נשלחת פקודה.
 */
public class Main {
    public static String run(Context c, Consumer<String> progress) {
        StringBuilder out = new StringBuilder();
        out.append("סקריפט 2: מחלקות המצלמה של BYD (קריאה בלבד, בלי לפתוח מצלמה)\n");
        File jar = new File("/system/framework/bmmcamera.jar");
        out.append("bmmcamera.jar: ").append(jar.exists() ? "קיים, " + jar.length() + " בתים" : "לא קיים").append('\n');
        out.append("libais_client.so (דיליק 5 בלבד): ").append(new File("/vendor/lib64/libais_client.so").exists()).append('\n');

        step(progress, "רשימת המחלקות");
        List<String> names = new ArrayList<>();
        try {
            @SuppressWarnings("deprecation") dalvik.system.DexFile df = new dalvik.system.DexFile(jar);
            Enumeration<String> en = df.entries();
            while (en.hasMoreElements()) names.add(en.nextElement());
            df.close();
        } catch (Throwable e) { out.append("✗ קריאת הקובץ: ").append(e).append('\n'); }
        Collections.sort(names);
        out.append("מחלקות בקובץ: ").append(names.size()).append('\n');
        for (String n : names) out.append("  ").append(n).append('\n');

        ClassLoader boot = Main.class.getClassLoader();
        ClassLoader own = null;
        for (String n : names) {
            if (n.contains("$") && !n.matches(".*\\$[A-Z].*")) continue; // מחלקות אנונימיות ($1) לא מעניינות
            step(progress, n);
            dump(n, boot, jar, c, out);
        }

        step(progress, "חבילות");
        out.append("\n-- חבילות מותקנות שקשורות למצלמות / פנורמה\n");
        Pattern p = Pattern.compile("(?i)avm|bmm|panor|camera|dvr|cdr|\\.ts\\.");
        PackageManager pm = c.getPackageManager();
        TreeSet<String> pk = new TreeSet<>();
        for (PackageInfo pi : pm.getInstalledPackages(0)) if (p.matcher(pi.packageName).find()) pk.add(pi.packageName);
        for (String s : pk) out.append("  ").append(s).append('\n');
        out.append("\n=== סוף ===\n");
        return out.toString();
    }

    private static void dump(String name, ClassLoader boot, File jar, Context c, StringBuilder out) {
        out.append("\n● ").append(name).append('\n');
        Class<?> k = null;
        try { k = Class.forName(name, false, boot); } catch (Throwable e) { /* ננסה מהקובץ עצמו */ }
        if (k == null) {
            try { k = Class.forName(name, false, new dalvik.system.PathClassLoader(jar.getAbsolutePath(), boot)); }
            catch (Throwable e) { out.append("  ✗ לא נטענה: ").append(e.getClass().getSimpleName()).append('\n'); return; }
        }
        try {
            out.append("  ").append(Modifier.toString(k.getModifiers())).append(" extends ").append(k.getSuperclass() == null ? "-" : k.getSuperclass().getName());
            Class<?>[] ifs = k.getInterfaces();
            if (ifs.length > 0) { out.append(" implements "); for (Class<?> i : ifs) out.append(i.getName()).append(' '); }
            out.append('\n');
            for (Constructor<?> ct : k.getDeclaredConstructors()) out.append("  new(").append(types(ct.getParameterTypes())).append(")\n");
            List<String> ms = new ArrayList<>();
            for (Method m : k.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                ms.add(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " " + m.getName() + "(" + types(m.getParameterTypes()) + ")");
            }
            Collections.sort(ms);
            for (String m : ms) out.append("  ").append(m).append('\n');
            // שמות ומסוגי שדות בלבד (לא קוראים ערכים)
            StringBuilder fs = new StringBuilder();
            for (Field f : k.getDeclaredFields()) if (!f.isSynthetic()) fs.append(Modifier.isStatic(f.getModifiers()) ? "static " : "").append(f.getType().getSimpleName()).append(' ').append(f.getName()).append("; ");
            if (fs.length() > 0) out.append("  שדות: ").append(fs.length() > 1500 ? fs.substring(0, 1500) + "…" : fs).append('\n');
        } catch (Throwable e) { out.append("  ✗ ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n'); }
    }

    private static String types(Class<?>[] t) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length; i++) b.append(i > 0 ? ", " : "").append(t[i].getSimpleName());
        return b.toString();
    }

    private static void step(Consumer<String> p, String s) { if (p != null) p.accept(s); }
}
