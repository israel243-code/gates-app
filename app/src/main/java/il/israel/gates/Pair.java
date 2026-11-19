package il.israel.gates;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

/** 🚗 חיבור מסך הרכב מהטלפון (19/11): סריקה בסורק של גוגל, הקלדת קוד, או קישור מהמצלמה – ואז אישור. */
final class Pair {
    private Pair() {}

    static void scan(Activity a) {
        try {
            GmsBarcodeScanning.getClient(a, new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                    .startScan()
                    .addOnSuccessListener(b -> {
                        Uri u = Uri.parse(b.getRawValue() == null ? "" : b.getRawValue());
                        if ("isrnb.duckdns.org".equals(u.getHost()) && "/pair".equals(u.getPath()) && u.getQueryParameter("c") != null) approve(a, u.getQueryParameter("c"), false);
                        else Toast.makeText(a, "זה לא ברקוד של מסך רכב", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> { Toast.makeText(a, "הסורק לא נפתח – הקלד את הקוד", Toast.LENGTH_LONG).show(); askCode(a); });
        } catch (Throwable e) { askCode(a); }
    }

    static void askCode(Activity a) {
        EditText in = new EditText(a);
        in.setInputType(InputType.TYPE_CLASS_NUMBER); in.setTextSize(28); in.setGravity(Gravity.CENTER); in.setHint("123456");
        new AlertDialog.Builder(a).setTitle("🔢 הקוד שעל מסך הרכב").setView(in)
                .setPositiveButton("חבר", (d, w) -> send(a, "code=" + in.getText().toString().replaceAll("\\D", "")))
                .setNegativeButton("ביטול", null).show();
    }

    static void approve(Activity a, String id, boolean ask) {
        if (!ask) { confirm(a, () -> send(a, "id=" + Uri.encode(id))); return; }
    }

    static void confirm(Activity a, Runnable go) {
        new AlertDialog.Builder(a).setTitle("🚗 לחבר את מסך הרכב?")
                .setMessage("מסך הרכב יקבל את החשבון שלך ואת השערים שלך, ויפתח אותם לבד כשהרכב מתקרב.")
                .setPositiveButton("✅ חבר", (d, w) -> go.run()).setNegativeButton("ביטול", null).show();
    }

    private static void send(Activity a, String what) {
        String u = Prefs.url(a);
        if (!Prefs.valid(u)) { Toast.makeText(a, "קודם צריך להתחבר לשערים בטלפון הזה", Toast.LENGTH_LONG).show(); return; }
        String t = Uri.parse(u).getQueryParameter("t");
        new Thread(() -> {
            String r = MainActivity.http("https://isrnb.duckdns.org/pair?step=approve", what + "&t=" + Uri.encode(t == null ? "" : t));
            String msg = "⚠️ אין חיבור לאינטרנט";
            try { msg = new org.json.JSONObject(r).optString("r", msg); } catch (Exception e) { /* נשאר */ }
            final String m = msg;
            a.runOnUiThread(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
        }).start();
    }
}
