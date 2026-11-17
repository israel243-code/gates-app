package il.israel.gates;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** כפתור "עצור" בהתראה. */
public class StopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        LocService.state = "נעצר";
        LocService.stop(c);
    }
}
