package com.lamurbob28.devicedoctor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout reportLayout;
    private final DecimalFormat oneDecimal = new DecimalFormat("0.0");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshReport();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF101114);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView title = text("Device Doctor", 30, true, 0xFFF1F1F1);
        root.addView(title);

        TextView subtitle = text("A simple local health report for your Android device.", 15, false, 0xFFB6BAC4);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        Button refresh = new Button(this);
        refresh.setText("Refresh Report");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> refreshReport());
        root.addView(refresh);

        reportLayout = new LinearLayout(this);
        reportLayout.setOrientation(LinearLayout.VERTICAL);
        reportLayout.setPadding(0, dp(14), 0, 0);
        root.addView(reportLayout);

        setContentView(scrollView);
    }

    private void refreshReport() {
        reportLayout.removeAllViews();
        addCard("Device", getDeviceReport());
        addCard("Battery", getBatteryReport());
        addCard("Storage", getStorageReport());
        addCard("Network", getNetworkReport());
        addCard("Runtime Memory", getMemoryReport());
        addCard("Sensors", getSensorReport());
        addCard("System Time", getTimeReport());
        addCard("Quick Notes", getQuickNotes());
    }

    private String getDeviceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(line("Manufacturer", Build.MANUFACTURER));
        sb.append(line("Model", Build.MODEL));
        sb.append(line("Device", Build.DEVICE));
        sb.append(line("Brand", Build.BRAND));
        sb.append(line("Android", Build.VERSION.RELEASE));
        sb.append(line("SDK", String.valueOf(Build.VERSION.SDK_INT)));
        sb.append(line("Security patch", Build.VERSION.SECURITY_PATCH));
        sb.append(line("Build ID", Build.ID));

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                sb.append(line("Thermal status", thermalName(pm.getCurrentThermalStatus())));
            } catch (Exception e) {
                sb.append(line("Thermal status", "Unavailable"));
            }
        } else {
            sb.append(line("Thermal status", "Requires Android 10+"));
        }
        return sb.toString();
    }

    private String getBatteryReport() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return "Battery data unavailable.";

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int tempTenthsC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String tech = battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        StringBuilder sb = new StringBuilder();
        sb.append(line("Level", percent >= 0 ? percent + "%" : "Unknown"));
        sb.append(line("Status", batteryStatusName(status)));
        sb.append(line("Plugged in", pluggedName(plugged)));
        sb.append(line("Health", batteryHealthName(health)));
        sb.append(line("Temperature", tempTenthsC >= 0 ? oneDecimal.format(tempTenthsC / 10.0) + " C" : "Unknown"));
        sb.append(line("Voltage", voltageMv > 0 ? voltageMv + " mV" : "Unknown"));
        sb.append(line("Technology", tech != null ? tech : "Unknown"));
        return sb.toString();
    }

    private String getStorageReport() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long total = stat.getBlockCountLong() * blockSize;
        long free = stat.getAvailableBlocksLong() * blockSize;
        long used = total - free;
        double usedPct = total > 0 ? used * 100.0 / total : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(line("Internal total", bytes(total)));
        sb.append(line("Internal used", bytes(used) + " (" + oneDecimal.format(usedPct) + "%)"));
        sb.append(line("Internal free", bytes(free)));
        if (usedPct > 90) sb.append("\nWarning: storage is very full. Android may slow down when free space gets low.");
        else if (usedPct > 80) sb.append("\nNote: storage is getting high. Cleanup may help.");
        else sb.append("\nStorage level looks okay.");
        return sb.toString();
    }

    private String getNetworkReport() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return "Connectivity service unavailable.";
        Network network = cm.getActiveNetwork();
        if (network == null) return "No active network detected.";
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return "Network detected, but capabilities are unavailable.";

        String type = "Unknown";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "Wi-Fi";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "Cellular";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "Ethernet";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) type = "VPN";

        StringBuilder sb = new StringBuilder();
        sb.append(line("Type", type));
        sb.append(line("Internet capability", yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))));
        sb.append(line("Validated internet", yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))));
        sb.append(line("Not metered", yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))));
        sb.append(line("Link downstream", caps.getLinkDownstreamBandwidthKbps() + " Kbps estimate"));
        sb.append(line("Link upstream", caps.getLinkUpstreamBandwidthKbps() + " Kbps estimate"));
        return sb.toString();
    }

    private String getMemoryReport() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(info);

        Runtime rt = Runtime.getRuntime();
        long appMax = rt.maxMemory();
        long appTotal = rt.totalMemory();
        long appFree = rt.freeMemory();
        long appUsed = appTotal - appFree;

        StringBuilder sb = new StringBuilder();
        if (am != null) {
            sb.append(line("System available", bytes(info.availMem)));
            sb.append(line("System low memory", yesNo(info.lowMemory)));
            sb.append(line("Low memory threshold", bytes(info.threshold)));
        }
        sb.append(line("App max heap", bytes(appMax)));
        sb.append(line("App used heap", bytes(appUsed)));
        sb.append(line("App free heap", bytes(appFree)));
        return sb.toString();
    }

    private String getSensorReport() {
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null) return "Sensor service unavailable.";
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder();
        sb.append(line("Total sensors", String.valueOf(sensors.size())));
        int limit = Math.min(8, sensors.size());
        for (int i = 0; i < limit; i++) sb.append("- ").append(sensors.get(i).getName()).append("\n");
        if (sensors.size() > limit) sb.append("- +").append(sensors.size() - limit).append(" more\n");
        return sb.toString();
    }

    private String getTimeReport() {
        long uptime = SystemClock.uptimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        StringBuilder sb = new StringBuilder();
        sb.append(line("Uptime awake", duration(uptime)));
        sb.append(line("Elapsed since boot", duration(elapsed)));
        sb.append(line("Automatic time", systemSetting(Settings.Global.AUTO_TIME)));
        sb.append(line("Automatic timezone", systemSetting(Settings.Global.AUTO_TIME_ZONE)));
        return sb.toString();
    }

    private String getQuickNotes() {
        return "This app avoids dangerous cleanup buttons. It reports what Android allows a normal app to see. No root tricks and no fake RAM booster nonsense.";
    }

    private void addCard(String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFF1B1D22);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);

        TextView h = text(heading, 19, true, 0xFFF1F1F1);
        h.setPadding(0, 0, 0, dp(7));
        card.addView(h);

        TextView b = text(body.trim(), 14, false, 0xFFB6BAC4);
        b.setLineSpacing(0, 1.08f);
        card.addView(b);
        reportLayout.addView(card);
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.START);
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }

    private String line(String label, String value) { return label + ": " + value + "\n"; }
    private String yesNo(boolean b) { return b ? "Yes" : "No"; }

    private String bytes(long value) {
        double gb = value / 1024.0 / 1024.0 / 1024.0;
        if (gb >= 1) return oneDecimal.format(gb) + " GB";
        double mb = value / 1024.0 / 1024.0;
        return oneDecimal.format(mb) + " MB";
    }

    private String duration(long ms) {
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format(Locale.US, "%dd %dh %dm", days, hours, minutes);
    }

    private String systemSetting(String key) {
        try {
            int value = Settings.Global.getInt(getContentResolver(), key);
            return value == 1 ? "Enabled" : "Disabled";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String batteryStatusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    private String pluggedName(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC charger";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            default: return "No";
        }
    }

    private String batteryHealthName(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Failure";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    private String thermalName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "None";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Shutdown";
            default: return "Unknown";
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
