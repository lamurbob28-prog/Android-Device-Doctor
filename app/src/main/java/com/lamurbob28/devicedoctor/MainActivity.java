package com.lamurbob28.devicedoctor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
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
import android.widget.Toast;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int GOOD = 0;
    private static final int WARNING = 1;
    private static final int BAD = 2;
    private static final int INFO = 3;

    private LinearLayout reportLayout;
    private TextView networkDoctorText;
    private ScanReport lastReport;
    private String lastNetworkDoctorReport = "Network Doctor has not been run yet.";
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

        TextView subtitle = text("Health score, warnings, quick fixes, and Network Doctor.", 15, false, 0xFFB6BAC4);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        Button refresh = button("Refresh Scan");
        refresh.setOnClickListener(v -> refreshReport());
        root.addView(refresh);

        Button network = button("Run Network Doctor");
        network.setOnClickListener(v -> runNetworkDoctor());
        root.addView(network);

        Button copy = button("Copy Full Report");
        copy.setOnClickListener(v -> copyReport());
        root.addView(copy);

        Button share = button("Share Full Report");
        share.setOnClickListener(v -> shareReport());
        root.addView(share);

        reportLayout = new LinearLayout(this);
        reportLayout.setOrientation(LinearLayout.VERTICAL);
        reportLayout.setPadding(0, dp(14), 0, 0);
        root.addView(reportLayout);

        setContentView(scrollView);
    }

    private void refreshReport() {
        lastReport = scanDevice();
        reportLayout.removeAllViews();
        addScoreCard(lastReport);
        addProblemsCard(lastReport);
        addNetworkDoctorCard();
        addShortcutsCard();
        addRawReportCard(lastReport.rawReport);
    }

    private ScanReport scanDevice() {
        ScanReport report = new ScanReport();
        report.timestamp = now();
        report.score = 100;

        StringBuilder raw = new StringBuilder();
        raw.append("Device Doctor v2.1 Report\n");
        raw.append("Scan time: ").append(report.timestamp).append("\n\n");

        String securityPatch = safe(Build.VERSION.SECURITY_PATCH);
        raw.append("DEVICE\n");
        raw.append(line("Manufacturer", safe(Build.MANUFACTURER)));
        raw.append(line("Model", safe(Build.MODEL)));
        raw.append(line("Device", safe(Build.DEVICE)));
        raw.append(line("Brand", safe(Build.BRAND)));
        raw.append(line("Android", safe(Build.VERSION.RELEASE)));
        raw.append(line("SDK", String.valueOf(Build.VERSION.SDK_INT)));
        raw.append(line("Security patch", securityPatch));
        raw.append(line("Build ID", safe(Build.ID)));
        raw.append("\n");
        evaluateSecurityPatch(report, securityPatch);

        int thermalStatus = getThermalStatus();
        raw.append("THERMAL\n");
        raw.append(line("Thermal status", thermalStatus >= 0 ? thermalName(thermalStatus) : "Unavailable"));
        raw.append("\n");
        evaluateThermal(report, thermalStatus);

        BatterySnapshot battery = getBatterySnapshot();
        raw.append("BATTERY\n");
        raw.append(line("Level", battery.percent >= 0 ? battery.percent + "%" : "Unknown"));
        raw.append(line("Status", batteryStatusName(battery.status)));
        raw.append(line("Plugged in", pluggedName(battery.plugged)));
        raw.append(line("Health", batteryHealthName(battery.health)));
        raw.append(line("Temperature", battery.tempC >= 0 ? oneDecimal.format(battery.tempC) + " C" : "Unknown"));
        raw.append(line("Voltage", battery.voltageMv > 0 ? battery.voltageMv + " mV" : "Unknown"));
        raw.append(line("Technology", battery.technology));
        raw.append("\n");
        evaluateBattery(report, battery);

        StorageSnapshot storage = getStorageSnapshot();
        raw.append("STORAGE\n");
        raw.append(line("Internal total", bytes(storage.total)));
        raw.append(line("Internal used", bytes(storage.used) + " (" + oneDecimal.format(storage.usedPct) + "%)"));
        raw.append(line("Internal free", bytes(storage.free)));
        raw.append("\n");
        evaluateStorage(report, storage);

        NetworkSnapshot network = getNetworkSnapshot();
        raw.append("NETWORK\n");
        raw.append(line("Type", network.type));
        raw.append(line("Internet capability", yesNo(network.hasInternet)));
        raw.append(line("Validated internet", yesNo(network.validated)));
        raw.append(line("Not metered", yesNo(network.notMetered)));
        raw.append(line("Link downstream", network.downKbps >= 0 ? network.downKbps + " Kbps estimate" : "Unknown"));
        raw.append(line("Link upstream", network.upKbps >= 0 ? network.upKbps + " Kbps estimate" : "Unknown"));
        raw.append("\n");
        evaluateNetwork(report, network);

        MemorySnapshot memory = getMemorySnapshot();
        raw.append("MEMORY\n");
        raw.append(line("System available", bytes(memory.availMem)));
        raw.append(line("System low memory", yesNo(memory.lowMemory)));
        raw.append(line("Low memory threshold", bytes(memory.threshold)));
        raw.append(line("App max heap", bytes(memory.appMax)));
        raw.append(line("App used heap", bytes(memory.appUsed)));
        raw.append(line("App free heap", bytes(memory.appFree)));
        raw.append("\n");
        evaluateMemory(report, memory);

        SensorSnapshot sensors = getSensorSnapshot();
        raw.append("SENSORS\n");
        raw.append(line("Total sensors", String.valueOf(sensors.total)));
        for (String sensor : sensors.preview) raw.append("- ").append(sensor).append("\n");
        if (sensors.more > 0) raw.append("- +").append(sensors.more).append(" more\n");
        raw.append("\n");
        addFinding(report, GOOD, "Sensors", sensors.total + " sensors detected.", "Sensor service is responding normally.", 0, null, null);

        long uptime = SystemClock.uptimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        raw.append("SYSTEM TIME\n");
        raw.append(line("Uptime awake", duration(uptime)));
        raw.append(line("Elapsed since boot", duration(elapsed)));
        raw.append(line("Automatic time", systemSetting(Settings.Global.AUTO_TIME)));
        raw.append(line("Automatic timezone", systemSetting(Settings.Global.AUTO_TIME_ZONE)));
        raw.append("\n");
        evaluateUptime(report, elapsed);

        if (report.score < 0) report.score = 0;
        if (report.score > 100) report.score = 100;
        if (report.score >= 85 && report.badCount == 0) report.overallStatus = GOOD;
        else if (report.score >= 60) report.overallStatus = WARNING;
        else report.overallStatus = BAD;

        raw.append("SUMMARY\n");
        raw.append(line("Overall status", statusName(report.overallStatus)));
        raw.append(line("Score", report.score + "/100"));
        raw.append(line("Warnings", String.valueOf(report.warningCount)));
        raw.append(line("Bad issues", String.valueOf(report.badCount)));
        raw.append(line("Good checks", String.valueOf(report.goodCount)));

        report.rawReport = raw.toString();
        return report;
    }

    private void runNetworkDoctor() {
        if (networkDoctorText != null) networkDoctorText.setText("Running network tests... hold still while the rectangle interrogates the internet.");
        Toast.makeText(this, "Running Network Doctor", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            String result = performNetworkDoctorTests();
            lastNetworkDoctorReport = result;
            runOnUiThread(() -> {
                if (networkDoctorText != null) networkDoctorText.setText(result);
                Toast.makeText(this, "Network Doctor finished", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private String performNetworkDoctorTests() {
        StringBuilder sb = new StringBuilder();
        sb.append("Network Doctor v2.1\n");
        sb.append("Run time: ").append(now()).append("\n\n");

        NetworkSnapshot network = getNetworkSnapshot();
        sb.append(line("Android network type", network.type));
        sb.append(line("Android validated", yesNo(network.validated)));
        sb.append(line("Android internet capability", yesNo(network.hasInternet)));
        sb.append("\n");

        TimedResult dnsGoogle = timedDns("google.com");
        TimedResult dnsCloudflare = timedDns("cloudflare.com");
        TimedResult tcpCloudflare = timedTcp("1.1.1.1", 443, 3000);
        TimedResult tcpGoogleDns = timedTcp("8.8.8.8", 443, 3000);
        HttpResult http204 = timedHttp("https://www.google.com/generate_204", 5000);

        sb.append("TESTS\n");
        sb.append(formatTimed("DNS google.com", dnsGoogle));
        sb.append(formatTimed("DNS cloudflare.com", dnsCloudflare));
        sb.append(formatTimed("TCP 1.1.1.1:443", tcpCloudflare));
        sb.append(formatTimed("TCP 8.8.8.8:443", tcpGoogleDns));
        sb.append("HTTPS generate_204: ").append(http204.success ? "OK" : "FAILED")
                .append(" in ").append(http204.ms).append(" ms")
                .append(" (HTTP ").append(http204.code).append(")");
        if (http204.error != null) sb.append(" - ").append(http204.error);
        sb.append("\n\n");

        int problems = 0;
        if (!network.hasNetwork || !network.hasInternet) problems++;
        if (!dnsGoogle.success && !dnsCloudflare.success) problems++;
        if (!tcpCloudflare.success && !tcpGoogleDns.success) problems++;
        if (!http204.success) problems++;
        if (tcpCloudflare.success && tcpCloudflare.ms > 800) problems++;

        sb.append("RESULT\n");
        if (problems == 0) {
            sb.append("Status: GOOD\n");
            sb.append("Internet looks reachable. Latency and DNS are not screaming for help. Rare behavior from modern networking, honestly.\n");
        } else if (problems <= 2) {
            sb.append("Status: WARNING\n");
            sb.append("Some network checks failed or looked slow. Try toggling Wi-Fi, switching networks, or restarting the router if this keeps happening.\n");
        } else {
            sb.append("Status: BAD\n");
            sb.append("Multiple network checks failed. This connection may be broken, captive, blocked, or cursed by router firmware.\n");
        }
        return sb.toString();
    }

    private TimedResult timedDns(String host) {
        long start = SystemClock.elapsedRealtime();
        TimedResult result = new TimedResult();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            result.ms = SystemClock.elapsedRealtime() - start;
            result.success = addresses != null && addresses.length > 0;
            result.message = result.success ? addresses[0].getHostAddress() : "No addresses";
        } catch (Exception e) {
            result.ms = SystemClock.elapsedRealtime() - start;
            result.success = false;
            result.message = simpleError(e);
        }
        return result;
    }

    private TimedResult timedTcp(String host, int port, int timeoutMs) {
        long start = SystemClock.elapsedRealtime();
        TimedResult result = new TimedResult();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            result.ms = SystemClock.elapsedRealtime() - start;
            result.success = true;
            result.message = "Connected";
        } catch (Exception e) {
            result.ms = SystemClock.elapsedRealtime() - start;
            result.success = false;
            result.message = simpleError(e);
        } finally {
            try { socket.close(); } catch (Exception ignored) { }
        }
        return result;
    }

    private HttpResult timedHttp(String urlText, int timeoutMs) {
        long start = SystemClock.elapsedRealtime();
        HttpResult result = new HttpResult();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.connect();
            result.code = connection.getResponseCode();
            result.ms = SystemClock.elapsedRealtime() - start;
            result.success = result.code >= 200 && result.code < 400;
        } catch (Exception e) {
            result.ms = SystemClock.elapsedRealtime() - start;
            result.code = -1;
            result.success = false;
            result.error = simpleError(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    private String formatTimed(String label, TimedResult result) {
        String status = result.success ? "OK" : "FAILED";
        return label + ": " + status + " in " + result.ms + " ms - " + result.message + "\n";
    }

    private void evaluateBattery(ScanReport report, BatterySnapshot battery) {
        if (battery.tempC < 0) addFinding(report, INFO, "Battery Temperature", "Temperature is unavailable.", "Android did not expose battery temperature on this scan.", 0, null, null);
        else if (battery.tempC >= 45) addFinding(report, BAD, "Battery Temperature", "Battery is very hot: " + oneDecimal.format(battery.tempC) + " C.", "Stop heavy use and let the phone cool down before charging or gaming.", 20, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else if (battery.tempC >= 40) addFinding(report, BAD, "Battery Temperature", "Battery is hot: " + oneDecimal.format(battery.tempC) + " C.", "Heat wears batteries down faster. Let it cool if possible.", 15, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else if (battery.tempC >= 35) addFinding(report, WARNING, "Battery Temperature", "Battery is warm: " + oneDecimal.format(battery.tempC) + " C.", "Warm is not panic mode, but avoid stacking heat with gaming plus charging.", 5, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else addFinding(report, GOOD, "Battery Temperature", "Battery temperature looks normal: " + oneDecimal.format(battery.tempC) + " C.", "No heat problem detected.", 0, null, null);

        if (battery.health == BatteryManager.BATTERY_HEALTH_GOOD) addFinding(report, GOOD, "Battery Health", "Android reports battery health as good.", "Nothing concerning detected here.", 0, null, null);
        else if (battery.health <= 0 || battery.health == BatteryManager.BATTERY_HEALTH_UNKNOWN) addFinding(report, INFO, "Battery Health", "Battery health is unknown.", "Some devices do not expose useful battery health data.", 0, null, null);
        else addFinding(report, BAD, "Battery Health", "Android reports battery health as " + batteryHealthName(battery.health) + ".", "Keep an eye on charging, heat, and sudden shutdowns.", 20, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
    }

    private void evaluateStorage(ScanReport report, StorageSnapshot storage) {
        if (storage.usedPct >= 95) addFinding(report, BAD, "Storage", "Storage is critically full: " + oneDecimal.format(storage.usedPct) + "% used.", "Free space soon. Android can get weird when storage is packed full.", 30, "Open Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        else if (storage.usedPct >= 90) addFinding(report, BAD, "Storage", "Storage is very full: " + oneDecimal.format(storage.usedPct) + "% used.", "Clear downloads, videos, cache, or unused apps.", 20, "Open Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        else if (storage.usedPct >= 80) addFinding(report, WARNING, "Storage", "Storage is getting high: " + oneDecimal.format(storage.usedPct) + "% used.", "Cleanup is not urgent, but it is getting close to annoying.", 10, "Open Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        else addFinding(report, GOOD, "Storage", "Storage looks okay: " + oneDecimal.format(storage.usedPct) + "% used.", "Free space is not currently a problem.", 0, null, null);
    }

    private void evaluateNetwork(ScanReport report, NetworkSnapshot network) {
        if (!network.hasNetwork) addFinding(report, BAD, "Network", "No active network detected.", "Connect to Wi-Fi or mobile data if you expected internet.", 20, "Open Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        else if (!network.hasInternet) addFinding(report, BAD, "Network", "A network exists, but Android does not see internet capability.", "Reconnect or switch networks.", 20, "Open Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        else if (!network.validated) addFinding(report, WARNING, "Network", "Connected, but Android has not validated real internet.", "Run Network Doctor, reconnect Wi-Fi, or check for a login page.", 10, "Open Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        else addFinding(report, GOOD, "Network", "Internet connection is validated on " + network.type + ".", "Network looks normal from Android's view. Run Network Doctor for deeper checks.", 0, null, null);

        if (network.hasNetwork && !network.notMetered) addFinding(report, INFO, "Metered Network", "This connection may be metered.", "Large downloads may use mobile data or a limited connection.", 0, "Open Network Settings", "android.settings.WIRELESS_SETTINGS");
    }

    private void evaluateMemory(ScanReport report, MemorySnapshot memory) {
        if (memory.lowMemory) addFinding(report, WARNING, "Memory", "Android reports low memory pressure.", "Close heavy apps or restart if the phone feels sluggish.", 10, "Open App Settings", "APP_DETAILS");
        else addFinding(report, GOOD, "Memory", "Android is not reporting low memory pressure.", "No RAM emergency detected. Fake RAM boosters may remain unemployed.", 0, null, null);
    }

    private void evaluateThermal(ScanReport report, int thermalStatus) {
        if (thermalStatus < 0) {
            addFinding(report, INFO, "Thermals", "Thermal status is unavailable.", "This device or Android version may not expose thermal status.", 0, null, null);
            return;
        }
        if (thermalStatus == PowerManager.THERMAL_STATUS_NONE) addFinding(report, GOOD, "Thermals", "System thermal status is normal.", "No device-level thermal throttling reported.", 0, null, null);
        else if (thermalStatus == PowerManager.THERMAL_STATUS_LIGHT) addFinding(report, WARNING, "Thermals", "System thermal status is light.", "The phone is slightly warm at the system level.", 5, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else if (thermalStatus == PowerManager.THERMAL_STATUS_MODERATE) addFinding(report, WARNING, "Thermals", "System thermal status is moderate.", "Let the device cool if performance feels worse.", 10, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else addFinding(report, BAD, "Thermals", "System thermal status is " + thermalName(thermalStatus) + ".", "Stop heavy use and let the phone cool down.", 25, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
    }

    private void evaluateSecurityPatch(ScanReport report, String patch) {
        long days = securityPatchAgeDays(patch);
        if (days < 0) addFinding(report, INFO, "Security Patch", "Security patch date is unavailable.", "Check Android updates manually if this looks wrong.", 0, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        else if (days <= 90) addFinding(report, GOOD, "Security Patch", "Security patch is recent: " + patch + ".", "Patch age is about " + days + " days.", 0, null, null);
        else if (days <= 180) addFinding(report, WARNING, "Security Patch", "Security patch is aging: " + patch + ".", "Patch age is about " + days + " days. Check for updates.", 5, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        else if (days <= 365) addFinding(report, WARNING, "Security Patch", "Security patch is stale: " + patch + ".", "Patch age is about " + days + " days. System update check recommended.", 10, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        else addFinding(report, BAD, "Security Patch", "Security patch is very old: " + patch + ".", "Patch age is about " + days + " days. Update if your phone offers one.", 20, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
    }

    private void evaluateUptime(ScanReport report, long elapsedMs) {
        long days = elapsedMs / 86400000L;
        if (days >= 30) addFinding(report, WARNING, "Uptime", "Phone has been running for " + days + " days since boot.", "Restarting can clear weird background-service problems.", 15, "Open Device Settings", "android.settings.SETTINGS");
        else if (days >= 14) addFinding(report, WARNING, "Uptime", "Phone has been running for " + days + " days since boot.", "A restart may help if anything feels strange.", 10, "Open Device Settings", "android.settings.SETTINGS");
        else if (days >= 7) addFinding(report, WARNING, "Uptime", "Phone has been running for " + days + " days since boot.", "Not terrible, but a restart can freshen things up.", 5, "Open Device Settings", "android.settings.SETTINGS");
        else addFinding(report, GOOD, "Uptime", "Recent boot: " + duration(elapsedMs) + " since startup.", "No restart recommendation right now.", 0, null, null);
    }

    private void addFinding(ScanReport report, int severity, String title, String details, String advice, int penalty, String actionLabel, String action) {
        Finding f = new Finding();
        f.severity = severity;
        f.title = title;
        f.details = details;
        f.advice = advice;
        f.penalty = penalty;
        f.actionLabel = actionLabel;
        f.action = action;
        report.findings.add(f);
        report.score -= penalty;
        if (severity == BAD) report.badCount++;
        else if (severity == WARNING) report.warningCount++;
        else if (severity == GOOD) report.goodCount++;
        else report.infoCount++;
    }

    private BatterySnapshot getBatterySnapshot() {
        BatterySnapshot out = new BatterySnapshot();
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return out;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        out.percent = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        out.status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        out.plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        out.health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int tempTenthsC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        out.tempC = tempTenthsC >= 0 ? tempTenthsC / 10.0 : -1;
        out.voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String tech = battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        out.technology = tech != null ? tech : "Unknown";
        return out;
    }

    private StorageSnapshot getStorageSnapshot() {
        StorageSnapshot out = new StorageSnapshot();
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        out.total = stat.getBlockCountLong() * blockSize;
        out.free = stat.getAvailableBlocksLong() * blockSize;
        out.used = out.total - out.free;
        out.usedPct = out.total > 0 ? out.used * 100.0 / out.total : 0;
        return out;
    }

    private NetworkSnapshot getNetworkSnapshot() {
        NetworkSnapshot out = new NetworkSnapshot();
        out.type = "None";
        out.downKbps = -1;
        out.upKbps = -1;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return out;
        Network network = cm.getActiveNetwork();
        if (network == null) return out;
        out.hasNetwork = true;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return out;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) out.type = "Wi-Fi";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) out.type = "Cellular";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) out.type = "Ethernet";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) out.type = "VPN";
        else out.type = "Other";
        out.hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        out.validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        out.notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        out.downKbps = caps.getLinkDownstreamBandwidthKbps();
        out.upKbps = caps.getLinkUpstreamBandwidthKbps();
        return out;
    }

    private MemorySnapshot getMemorySnapshot() {
        MemorySnapshot out = new MemorySnapshot();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(info);
            out.availMem = info.availMem;
            out.lowMemory = info.lowMemory;
            out.threshold = info.threshold;
        }
        Runtime rt = Runtime.getRuntime();
        out.appMax = rt.maxMemory();
        long appTotal = rt.totalMemory();
        long appFree = rt.freeMemory();
        out.appUsed = appTotal - appFree;
        out.appFree = appFree;
        return out;
    }

    private SensorSnapshot getSensorSnapshot() {
        SensorSnapshot out = new SensorSnapshot();
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null) return out;
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        out.total = sensors.size();
        int limit = Math.min(8, sensors.size());
        for (int i = 0; i < limit; i++) out.preview.add(sensors.get(i).getName());
        out.more = sensors.size() - limit;
        return out;
    }

    private int getThermalStatus() {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.getCurrentThermalStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    private void addScoreCard(ScanReport report) {
        LinearLayout card = newCard();
        card.addView(text("Overall Health", 20, true, 0xFFF1F1F1));
        TextView score = text(report.score + "/100  -  " + statusName(report.overallStatus), 32, true, statusColor(report.overallStatus));
        score.setPadding(0, dp(6), 0, dp(6));
        card.addView(score);
        String summary = report.badCount + " bad, " + report.warningCount + " warnings, " + report.goodCount + " good, " + report.infoCount + " info\nLast scan: " + report.timestamp;
        card.addView(text(summary, 14, false, 0xFFB6BAC4));
        reportLayout.addView(card);
    }

    private void addProblemsCard(ScanReport report) {
        TextView heading = text("Diagnosis", 22, true, 0xFFF1F1F1);
        heading.setPadding(0, dp(4), 0, dp(8));
        reportLayout.addView(heading);
        boolean showedProblem = false;
        for (Finding f : report.findings) {
            if (f.severity == BAD || f.severity == WARNING) {
                addFindingCard(f);
                showedProblem = true;
            }
        }
        if (!showedProblem) addCard("No major problems found", "Nothing bad or warning-level showed up in this scan. Suspiciously competent behavior from a phone.");
        TextView goodHeading = text("Other Checks", 20, true, 0xFFF1F1F1);
        goodHeading.setPadding(0, dp(6), 0, dp(8));
        reportLayout.addView(goodHeading);
        for (Finding f : report.findings) {
            if (f.severity == GOOD || f.severity == INFO) addFindingCard(f);
        }
    }

    private void addFindingCard(Finding f) {
        LinearLayout card = newCard();
        card.addView(text(f.title + "  -  " + statusName(f.severity), 18, true, statusColor(f.severity)));
        TextView details = text(f.details, 14, false, 0xFFF1F1F1);
        details.setPadding(0, dp(7), 0, dp(3));
        card.addView(details);
        card.addView(text(f.advice, 14, false, 0xFFB6BAC4));
        if (f.action != null && f.actionLabel != null) {
            Button action = button(f.actionLabel);
            action.setOnClickListener(v -> openSettingsAction(f.action));
            card.addView(action);
        }
        reportLayout.addView(card);
    }

    private void addNetworkDoctorCard() {
        LinearLayout card = newCard();
        card.addView(text("Network Doctor", 20, true, 0xFFF1F1F1));
        networkDoctorText = text(lastNetworkDoctorReport, 14, false, 0xFFB6BAC4);
        networkDoctorText.setPadding(0, dp(6), 0, dp(8));
        card.addView(networkDoctorText);
        Button run = button("Run Network Doctor");
        run.setOnClickListener(v -> runNetworkDoctor());
        card.addView(run);
        reportLayout.addView(card);
    }

    private void addShortcutsCard() {
        LinearLayout card = newCard();
        card.addView(text("Useful Shortcuts", 20, true, 0xFFF1F1F1));
        TextView hint = text("These open Android settings. The app does not delete anything by itself.", 14, false, 0xFFB6BAC4);
        hint.setPadding(0, dp(4), 0, dp(8));
        card.addView(hint);
        addShortcutButton(card, "Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        addShortcutButton(card, "Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        addShortcutButton(card, "Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        addShortcutButton(card, "System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        addShortcutButton(card, "This App Info", "APP_DETAILS");
        reportLayout.addView(card);
    }

    private void addShortcutButton(LinearLayout card, String label, String action) {
        Button button = button(label);
        button.setOnClickListener(v -> openSettingsAction(action));
        card.addView(button);
    }

    private void addRawReportCard(String raw) {
        addCard("Full Raw Report", raw);
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFF1B1D22);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private void addCard(String heading, String body) {
        LinearLayout card = newCard();
        TextView h = text(heading, 19, true, 0xFFF1F1F1);
        h.setPadding(0, 0, 0, dp(7));
        card.addView(h);
        TextView b = text(body.trim(), 14, false, 0xFFB6BAC4);
        b.setLineSpacing(0, 1.08f);
        card.addView(b);
        reportLayout.addView(card);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Device Doctor Report", combinedReport()));
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
        } else Toast.makeText(this, "Could not copy report", Toast.LENGTH_SHORT).show();
    }

    private void shareReport() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Device Doctor Report");
        send.putExtra(Intent.EXTRA_TEXT, combinedReport());
        startActivity(Intent.createChooser(send, "Share Device Doctor Report"));
    }

    private String combinedReport() {
        if (lastReport == null) lastReport = scanDevice();
        return lastReport.rawReport + "\n\n" + lastNetworkDoctorReport;
    }

    private void openSettingsAction(String action) {
        try {
            Intent intent;
            if ("APP_DETAILS".equals(action)) {
                intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                intent.setData(Uri.parse("package:" + getPackageName()));
            } else intent = new Intent(action);
            startActivity(intent);
        } catch (Exception firstFailure) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception secondFailure) { Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show(); }
        }
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

    private int statusColor(int severity) {
        if (severity == GOOD) return 0xFF75D69C;
        if (severity == WARNING) return 0xFFFFD166;
        if (severity == BAD) return 0xFFFF6B6B;
        return 0xFF8AB4F8;
    }

    private String statusName(int severity) {
        if (severity == GOOD) return "GOOD";
        if (severity == WARNING) return "WARNING";
        if (severity == BAD) return "BAD";
        return "INFO";
    }

    private String line(String label, String value) { return label + ": " + value + "\n"; }
    private String yesNo(boolean b) { return b ? "Yes" : "No"; }
    private String safe(String value) { return value == null || value.trim().length() == 0 ? "Unknown" : value; }
    private String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }
    private String simpleError(Exception e) { return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""); }

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
        try { return Settings.Global.getInt(getContentResolver(), key) == 1 ? "Enabled" : "Disabled"; }
        catch (Exception e) { return "Unknown"; }
    }

    private long securityPatchAgeDays(String patch) {
        try {
            if (patch == null || patch.length() < 10 || "Unknown".equals(patch)) return -1;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            fmt.setLenient(false);
            Date patchDate = fmt.parse(patch);
            if (patchDate == null) return -1;
            long diff = System.currentTimeMillis() - patchDate.getTime();
            if (diff < 0) return 0;
            return diff / 86400000L;
        } catch (Exception e) { return -1; }
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

    private static class ScanReport {
        int score;
        int overallStatus;
        int badCount;
        int warningCount;
        int goodCount;
        int infoCount;
        String timestamp;
        String rawReport;
        final List<Finding> findings = new ArrayList<>();
    }

    private static class Finding {
        int severity;
        String title;
        String details;
        String advice;
        int penalty;
        String actionLabel;
        String action;
    }

    private static class BatterySnapshot {
        int percent = -1;
        int status = -1;
        int plugged = -1;
        int health = -1;
        double tempC = -1;
        int voltageMv = -1;
        String technology = "Unknown";
    }

    private static class StorageSnapshot { long total; long used; long free; double usedPct; }
    private static class NetworkSnapshot { boolean hasNetwork; boolean hasInternet; boolean validated; boolean notMetered; String type; int downKbps; int upKbps; }
    private static class MemorySnapshot { long availMem; boolean lowMemory; long threshold; long appMax; long appUsed; long appFree; }
    private static class SensorSnapshot { int total; int more; final List<String> preview = new ArrayList<>(); }
    private static class TimedResult { boolean success; long ms; String message; }
    private static class HttpResult { boolean success; long ms; int code; String error; }
}
