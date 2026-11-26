package studio.mises.countdown;

import android.app.Application;
import android.content.res.Configuration;

public class CountdownApplication extends Application {
    private int nightMode;

    @Override
    public void onCreate() {
        super.onCreate();
        nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        int newMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (newMode != nightMode) {
            nightMode = newMode;
            CountdownWidget.refresh(this);
        }
    }
}
