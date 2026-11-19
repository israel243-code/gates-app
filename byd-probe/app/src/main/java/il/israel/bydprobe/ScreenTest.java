package il.israel.bydprobe;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;

/**
 * בדיקה 4 ד': האם אפשר להקליט את המסך של הרכב, ובמיוחד אם תצוגת 360 נראית בהקלטה או שחורה.
 * לא נשמר שום קובץ ולא נשלחת תמונה: רק מספרים (בהירות ממוצעת) וציור גס של 48x14 תווים.
 */
final class ScreenTest {
    interface Done { void done(String report); }

    private static final String RAMP = " .:-=+*#%@";

    static void run(Activity a, MediaProjection mp, Done done) {
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            HandlerThread ht = new HandlerThread("scr");
            ht.start();
            Handler hh = new Handler(ht.getLooper());
            VirtualDisplay vd = null;
            ImageReader ir = null;
            try {
                DisplayMetrics dm = a.getResources().getDisplayMetrics();
                int w = Math.max(64, dm.widthPixels / 2), h = Math.max(64, dm.heightPixels / 2);
                out.append("הקלטת מסך אושרה. גודל תפיסה ").append(w).append('x').append(h).append('\n');
                mp.registerCallback(new MediaProjection.Callback() { }, hh);
                ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
                vd = mp.createVirtualDisplay("probe", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.getSurface(), null, hh);
                for (int i = 1; i <= 15; i++) {
                    Thread.sleep(2000);
                    Image img = ir.acquireLatestImage();
                    if (img == null) { out.append("  t=").append(i * 2).append("s: אין פריים\n"); continue; }
                    try {
                        Image.Plane p = img.getPlanes()[0];
                        ByteBuffer bb = p.getBuffer();
                        int ps = p.getPixelStride(), rs = p.getRowStride();
                        long sum = 0; int black = 0, n = 0;
                        for (int gy = 0; gy < 36; gy++) for (int gx = 0; gx < 64; gx++) {
                            int l = luma(bb, rs, ps, (gx * w + w / 2) / 64, (gy * h + h / 2) / 36);
                            sum += l; n++; if (l < 8) black++;
                        }
                        out.append("  t=").append(i * 2).append("s: בהירות ").append(sum / Math.max(1, n)).append(" | שחור ").append(black * 100 / Math.max(1, n)).append("%\n");
                        if (i == 3 || i == 7 || i == 11 || i == 14) { // ציור גס של המסך
                            for (int gy = 0; gy < 14; gy++) {
                                StringBuilder row = new StringBuilder("    ");
                                for (int gx = 0; gx < 48; gx++) {
                                    int l = luma(bb, rs, ps, (gx * w + w / 2) / 48, (gy * h + h / 2) / 14);
                                    row.append(RAMP.charAt(Math.min(9, l * 10 / 256)));
                                }
                                out.append(row).append('\n');
                            }
                        }
                    } finally { img.close(); }
                }
            } catch (Throwable e) {
                out.append("✗ ").append(LiveRead.cause(e)).append('\n');
            } finally {
                try { if (vd != null) vd.release(); } catch (Throwable ignored) {}
                try { if (ir != null) ir.close(); } catch (Throwable ignored) {}
                try { mp.stop(); } catch (Throwable ignored) {}
                ht.quitSafely();
            }
            done.done(out.toString());
        }).start();
    }

    private static int luma(ByteBuffer bb, int rowStride, int pixStride, int x, int y) {
        int off = y * rowStride + x * pixStride;
        if (off < 0 || off + 2 >= bb.limit()) return 0;
        int r = bb.get(off) & 0xff, g = bb.get(off + 1) & 0xff, b = bb.get(off + 2) & 0xff;
        return (r * 3 + g * 6 + b) / 10;
    }
}
