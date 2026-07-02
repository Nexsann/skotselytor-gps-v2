package se.skotselytor.gps;

import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private MainActivity activity;

    public WebAppInterface(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void startBackgroundTracking() {
        activity.startBackgroundTracking();
    }

    @JavascriptInterface
    public void stopBackgroundTracking() {
        activity.stopBackgroundTracking();
    }
}
