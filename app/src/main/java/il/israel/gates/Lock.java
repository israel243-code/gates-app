package il.israel.gates;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

/** נעילה לבחירת המשתמש (18/11): טביעת אצבע / פנים / קוד הטלפון – מובנה באנדרואיד, בלי ספריות. */
final class Lock {
    private Lock() {}

    /** יש בטלפון נעילת מסך (אחרת אין במה לאמת) */
    static boolean available(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return Build.VERSION.SDK_INT >= 28 && km != null && km.isDeviceSecure();
    }

    static void auth(Activity a, String title, Runnable ok, Runnable fail) {
        if (!available(a)) { ok.run(); return; }
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a).setTitle(title).setSubtitle("טביעת אצבע, פנים או קוד הטלפון");
        if (Build.VERSION.SDK_INT >= 30)
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        else if (Build.VERSION.SDK_INT >= 29) b.setDeviceCredentialAllowed(true);
        else b.setNegativeButton("ביטול", a.getMainExecutor(), (d, w) -> fail.run());
        b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { ok.run(); }
            @Override public void onAuthenticationError(int code, CharSequence msg) { fail.run(); }
        });
    }
}
