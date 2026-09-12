package sk.adykub.wifianalyzer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 1001;
    private WebView webView;
    private WifiManager wifiManager;
    private boolean receiverRegistered = false;
    private long lastScanRequestMs = 0L;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            sendResults(updated ? "fresh" : "cached");
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidWiFi");
        webView.loadUrl("file:///android_asset/index.html");

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
        receiverRegistered = true;
        requestNeededPermissions();
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private boolean hasFineLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNearbyPermission() {
        return Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        ArrayList<String> need = new ArrayList<>();
        if (!hasFineLocation()) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && !hasNearbyPermission()) need.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            webView.postDelayed(this::performScan, 350);
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) { return false; }
    }

    private void performScan() {
        if (!hasFineLocation()) {
            sendError("permission", "Pre sken Wi‑Fi povoľ aplikácii presnú polohu. Android toto povolenie vyžaduje pre zoznam okolitých sietí.");
            requestNeededPermissions();
            return;
        }
        if (!isLocationEnabled()) {
            sendError("location_off", "Služby polohy sú vypnuté. Zapni Poloha/Location; bez toho Android neposkytne výsledky Wi‑Fi skenu.");
            return;
        }
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            sendError("wifi_off", "Wi‑Fi je vypnuté. Zapni Wi‑Fi a skús sken znova.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanRequestMs < 9000) {
            sendResults("cached");
            return;
        }
        lastScanRequestMs = now;

        boolean started;
        try { started = wifiManager.startScan(); }
        catch (SecurityException e) {
            sendError("security", "Android nepovolil Wi‑Fi sken: " + e.getMessage());
            return;
        }
        if (!started) {
            // Android scan throttling can reject a new active scan; cached results are still useful.
            sendResults("cached/throttled");
        } else {
            sendStatus("Skenujem okolie…");
        }
    }

    private void sendResults(String mode) {
        try {
            List<ScanResult> raw = wifiManager.getScanResults();
            if (raw == null) raw = new ArrayList<>();
            List<ScanResult> results = new ArrayList<>(raw);
            Collections.sort(results, Comparator.comparingInt((ScanResult r) -> r.level).reversed());

            String currentBssid = "";
            String currentSsid = "";
            try {
                WifiInfo info = wifiManager.getConnectionInfo();
                if (info != null) {
                    if (info.getBSSID() != null) currentBssid = info.getBSSID();
                    if (info.getSSID() != null) currentSsid = cleanSsid(info.getSSID());
                }
            } catch (Exception ignored) {}

            JSONArray arr = new JSONArray();
            for (ScanResult r : results) {
                JSONObject o = new JSONObject();
                String ssid = r.SSID == null || r.SSID.isEmpty() ? "(skrytá sieť)" : r.SSID;
                String bssid = r.BSSID == null ? "" : r.BSSID;
                int freq = r.frequency;
                o.put("ssid", ssid);
                o.put("bssid", bssid);
                o.put("rssi", r.level);
                o.put("frequency", freq);
                o.put("channel", channelFromFrequency(freq));
                o.put("band", bandFromFrequency(freq));
                o.put("width", widthMhz(r.channelWidth));
                o.put("centerFreq0", r.centerFreq0);
                o.put("centerFreq1", r.centerFreq1);
                o.put("capabilities", r.capabilities == null ? "" : r.capabilities);
                o.put("current", !currentBssid.isEmpty() && currentBssid.equalsIgnoreCase(bssid));
                arr.put(o);
            }

            JSONObject root = new JSONObject();
            root.put("ok", true);
            root.put("mode", mode);
            root.put("timestamp", System.currentTimeMillis());
            root.put("currentSsid", currentSsid);
            root.put("currentBssid", currentBssid);
            root.put("locationEnabled", isLocationEnabled());
            root.put("wifiEnabled", wifiManager.isWifiEnabled());
            root.put("supports5", wifiManager.is5GHzBandSupported());
            root.put("supports6", Build.VERSION.SDK_INT >= 30 && wifiManager.is6GHzBandSupported());
            root.put("networks", arr);
            dispatch(root);
        } catch (SecurityException e) {
            sendError("security", "Chýba oprávnenie pre výsledky Wi‑Fi skenu.");
        } catch (Exception e) {
            sendError("error", e.toString());
        }
    }

    private void sendError(String code, String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false); o.put("code", code); o.put("message", message);
            o.put("timestamp", System.currentTimeMillis());
            dispatch(o);
        } catch (Exception ignored) {}
    }

    private void sendStatus(String message) {
        final String js = "window.onNativeStatus && window.onNativeStatus(" + JSONObject.quote(message) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void dispatch(JSONObject obj) {
        final String js = "window.onNativeData && window.onNativeData(" + obj.toString() + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private static String cleanSsid(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        if ("<unknown ssid>".equalsIgnoreCase(s)) return "";
        return s;
    }

    private static String bandFromFrequency(int f) {
        if (f >= 2400 && f < 2500) return "2.4";
        if (f >= 4900 && f < 5925) return "5";
        if (f >= 5925 && f < 7125) return "6";
        return "other";
    }

    private static int channelFromFrequency(int f) {
        if (f == 2484) return 14;
        if (f >= 2412 && f <= 2472) return 1 + (f - 2412) / 5;
        if (f == 5935) return 2;
        if (f >= 5955 && f <= 7115) return (f - 5950) / 5;
        if (f >= 5000 && f <= 5895) return (f - 5000) / 5;
        if (f >= 4910 && f < 5000) return (f - 4000) / 5;
        return 0;
    }

    private static int widthMhz(int channelWidth) {
        switch (channelWidth) {
            case 0: return 20;
            case 1: return 40;
            case 2: return 80;
            case 3: return 160;
            case 4: return 160; // 80+80 shown as effective occupied width
            case 5: return 320;
            default: return 20;
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void requestScan() { runOnUiThread(MainActivity.this::performScan); }
        @JavascriptInterface public void requestPermissions() { runOnUiThread(MainActivity.this::requestNeededPermissions); }
        @JavascriptInterface public void openLocationSettings() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            });
        }
        @JavascriptInterface public String getPlatform() { return "Android " + Build.VERSION.RELEASE; }
    }
}
