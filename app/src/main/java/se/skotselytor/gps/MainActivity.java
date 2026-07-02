package se.skotselytor.gps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 1001;
    private WebView webView;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Appen ska kunna visas ovanpå låsskärmen och väcka skärmen, eftersom
        // telefonen sitter fastmonterad på klipparen och användaren behöver
        // kunna se kartan direkt utan att låsa upp telefonen manuellt.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
        }

        requestIgnoreBatteryOptimizations();

        webView = new WebView(this);
        setContentView(webView);

        // Tvinga GPU-rendering och högsta möjliga uppdateringsfrekvens för
        // WebView, annars kan kartan kännas trög/hackig även på skärmar
        // med 90/120Hz eftersom Android annars kan falla tillbaka på
        // mjukvaru-rendering i vissa WebView-lägen.
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        requestHighRefreshRate();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setOffscreenPreRaster(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geoOrigin = origin;
                geoCallback = callback;

                boolean granted =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

                if (granted) {
                    callback.invoke(origin, true, false);
                } else {
                    requestPermissions(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_REQUEST);
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");

        // Starta bakgrundstjänsten direkt vid appstart, eftersom telefonen
        // är fastmonterad och spårning ska kunna fortsätta även om skärmen
        // släcks eller telefonen låses medan klipparen körs.
        startBackgroundTracking();
    }

    private void requestHighRefreshRate() {
        // Enkel, säker variant: sätt bara den attribut-baserade hint:en på
        // fönstret. Ingen manipulation av Display.Mode (det orsakade
        // krascher tidigare) - det här är bara en "önskan" till systemet
        // och kan aldrig ge en SecurityException eller krasch.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.preferredRefreshRate = 120f;
                getWindow().setAttributes(lp);
            }
        } catch (Exception ignored) {
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        } catch (Exception ignored) {
            // Om enheten inte stödjer detta eller dialogen inte kan visas
            // fortsätter appen ändå att fungera, bara med risk för att
            // systemet kan strypa bakgrundsspårningen mer aggressivt.
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (geoCallback != null && geoOrigin != null) {
                geoCallback.invoke(geoOrigin, granted, false);
                geoCallback = null;
                geoOrigin = null;
            }

            if (granted) {
                startBackgroundTracking();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public void startBackgroundTracking() {
        try {
            Intent intent = new Intent(this, LocationForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public void stopBackgroundTracking() {
        try {
            Intent intent = new Intent(this, LocationForegroundService.class);
            stopService(intent);
        } catch (Exception ignored) {
        }
    }
}
