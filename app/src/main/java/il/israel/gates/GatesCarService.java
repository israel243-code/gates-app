package il.israel.gates;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/** אנדרואיד אוטו (18/11): מסך שערים על מסך הרכב – קטגוריית "מכשירים חכמים" (IOT). */
public class GatesCarService extends CarAppService {
    @NonNull @Override
    public HostValidator createHostValidator() {
        // רק אנדרואיד אוטו של גוגל (הרשימה הרשמית שבספרייה) – לא כל אפליקציה שמנסה להתחבר
        return new HostValidator.Builder(getApplicationContext()).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build();
    }

    @NonNull @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull @Override
            public Screen onCreateScreen(@NonNull Intent intent) { return new GatesCarScreen(getCarContext()); }
        };
    }
}
