package com.lamurbob28.devicedoctor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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

public class CleanDeviceDoctorActivity extends Activity {
    private static final int GOOD = 0;
    private static final int WARNING = 1;
    private static final int BAD = 2;
    private static final int INFO = 3;
    private static final int MAX_HISTORY = 10;
    private static final String HISTORY_PREFS = "device_doctor_v31_history";

    private final DecimalFormat oneDecimal = new DecimalFormat("0.0");
    private LinearLayout reportLayout;
    private ScanReport currentReport;
    private String networkDoctorReport = "Network Doctor has not been run yet.";
    private TextView networkDoctorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshScan();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF101114);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        root.addView(text("Device Doctor", 30, true, 0xFFF1F1F1));
        TextView subtitle = text("v3.1 clean build: dashboard, history, updates, storage, and one Network Doctor.", 15, false, 0xFFB6BAC4);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        Button refresh = button("Refresh Scan");
        refresh.setOnClickListener(v -> refreshScan());
        root.addView(refresh);

        Button copy = button("Copy Smart Report");
        copy.setOnClickListener(v -> copyReport());
        root.addView(copy);

        Button share = button("Share Smart Report");
        share.setOnClickListener(v -> shareReport());
        root.addView(share);

        reportLayout = new LinearLayout(this);
        reportLayout.setOrientation(LinearLayout.VERTICAL);
        reportLayout.setPadding(0, dp(14), 0, 0);
        root.addView(reportLayout);

        setContentView(scrollView);
    }

    private void refreshScan() {
        HistoryEntry previous = loadMostRecentHistory();
        currentReport = scanDevice(previous);
        reportLayout.removeAllViews();

        addDashboardCard(currentReport, previous);
        addCard("Smart Summary", currentReport.smartSummary);
        addUpdateVerifierCard(currentReport, previous);
        addHistoryCard(currentReport);
        addStorageDoctorCard(currentReport, previous);
        addCard("What Changed", currentReport.changeSummary);
        addDiagnosisCards(currentReport);
        addNetworkDoctorCard();
        addShortcutsCard();
        addCard("Full Raw Details", currentReport.rawDetails);

        saveHistory(currentReport);
    }

    private ScanReport scanDevice(HistoryEntry previous) {
        ScanReport report = new ScanReport();
        report.timestamp = now();
        report.score = 100;

        StringBuilder raw = new StringBuilder();
        String securityPatch = safe(Build.VERSION.SECURITY_PATCH);
        report.securityPatch = securityPatch;

        raw.append("DEVICE\n")
                .append(line("Manufacturer", safe(Build.MANUFACTURER)))
                .append(line("Model", safe(Build.MODEL)))
                .append(line("Device", safe(Build.DEVICE)))
                .append(line("Brand", safe(Build.BRAND)))
                .append(line("Android", safe(Build.VERSION.RELEASE)))
                .append(line("SDK", String.valueOf(Build.VERSION.SDK_INT)))
                .append(line("Security patch", securityPatch))
                .append(line("Build ID", safe(Build.ID)))
                .append("\n");
        evaluateSecurityPatch(report, securityPatch);

        int thermal = getThermalStatus();
        report.thermalStatus = thermal;
        raw.append("THERMAL\n").append(line("Thermal status", thermal >= 0 ? thermalName(thermal) : "Unavailable")).append("\n");
        evaluateThermal(report, thermal);

        BatterySnapshot battery = readBattery();
        report.batteryTempC = battery.temperatureC;
        raw.append("BATTERY\n")
                .append(line("Level", battery.levelPercent >= 0 ? battery.levelPercent + "%" : "Unknown"))
                .append(line("Status", batteryStatusName(battery.status)))
                .append(line("Plugged in", pluggedName(battery.plugged)))
                .append(line("Health", batteryHealthName(battery.health)))
                .append(line("Temperature", battery.temperatureC >= 0 ? oneDecimal.format(battery.temperatureC) + " C" : "Unknown"))
                .append(line("Voltage", battery.voltageMv > 0 ? battery.voltageMv + " mV" : "Unknown"))
                .append(line("Technology", battery.technology))
                .append("\n");
        evaluateBattery(report, battery);

        StorageSnapshot storage = readStorage();
        report.storageUsedBytes = storage.usedBytes;
        report.storageUsedPct = storage.usedPct;
        raw.append("STORAGE\n")
                .append(line("Internal total", bytes(storage.totalBytes)))
                .append(line("Internal used", bytes(storage.usedBytes) + " (" + oneDecimal.format(storage.usedPct) + "%)"))
                .append(line("Internal free", bytes(storage.freeBytes)))
                .append("\n");
        evaluateStorage(report, storage);

        NetworkSnapshot network = readNetwork();
        report.networkType = network.type;
        report.networkValidated = network.validated;
        raw.append("NETWORK\n")
                .append(line("Type", network.type))
                .append(line("Internet capability", yesNo(network.hasInternet)))
                .append(line("Validated internet", yesNo(network.validated)))
                .append(line("Not metered", yesNo(network.notMetered)))
                .append(line("Link downstream", network.downKbps >= 0 ? network.downKbps + " Kbps estimate" : "Unknown"))
                .append(line("Link upstream", network.upKbps >= 0 ? network.upKbps + " Kbps estimate" : "Unknown"))
                .append("\n");
        evaluateNetwork(report, network);

        MemorySnapshot memory = readMemory();
        raw.append("MEMORY\n")
                .append(line("System available", bytes(memory.availableBytes)))
                .append(line("System low memory", yesNo(memory.lowMemory)))
                .append(line("Low memory threshold", bytes(memory.thresholdBytes)))
                .append(line("App max heap", bytes(memory.appMaxBytes)))
                .append(line("App used heap", bytes(memory.appUsedBytes)))
                .append(line("App free heap", bytes(memory.appFreeBytes)))
                .append("\n");
        evaluateMemory(report, memory);

        SensorSnapshot sensors = readSensors();
        raw.append("SENSORS\n").append(line("Total sensors", String.valueOf(sensors.total)));
        for (String sensorName : sensors.previewNames) raw.append("- ").append(sensorName).append("\n");
        if (sensors.moreCount > 0) raw.append("- +").append(sensors.moreCount).append(" more\n");
        raw.append("\n");
        addFinding(report, GOOD, "Sensors", sensors.total + " sensors detected.", "Sensor service is responding normally.", 0, null, null);

        long uptime = SystemClock.uptimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        report.uptimeDays = elapsed / 86400000L;
        raw.append("SYSTEM TIME\n")
                .append(line("Uptime awake", duration(uptime)))
                .append(line("Elapsed since boot", duration(elapsed)))
                .append(line("Automatic time", systemSetting(Settings.Global.AUTO_TIME)))
                .append(line("Automatic timezone", systemSetting(Settings.Global.AUTO_TIME_ZONE)))
                .append("\n");
        evaluateUptime(report, elapsed);

        clampAndClassify(report);
        report.smartSummary = buildSmartSummary(report);
        report.changeSummary = buildChangeSummary(previous, report);
        report.rawDetails = raw.toString()
                + "SUMMARY\n"
                + line("Overall status", statusName(report.overallStatus))
                + line("Score", report.score + "/100")
                + line("Warnings", String.valueOf(report.warningCount))
                + line("Bad issues", String.valueOf(report.badCount))
                + line("Good checks", String.valueOf(report.goodCount));
        report.fullReport = buildShareReport(report);
        return report;
    }

    private void clampAndClassify(ScanReport report) {
        if (report.score < 0) report.score = 0;
        if (report.score > 100) report.score = 100;
        if (report.score >= 85 && report.badCount == 0) report.overallStatus = GOOD;
        else if (report.score >= 60) report.overallStatus = WARNING;
        else report.overallStatus = BAD;
    }

    private String buildSmartSummary(ScanReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(statusName(report.overallStatus)).append("\n");
        sb.append("Score: ").append(report.score).append("/100\n");
        sb.append(report.badCount).append(" bad, ").append(report.warningCount).append(" warnings, ").append(report.goodCount).append(" good, ").append(report.infoCount).append(" info\n");
        sb.append("Scan time: ").append(report.timestamp).append("\n\n");

        if (report.badCount == 0 && report.warningCount == 0) {
            sb.append("Problems found: none. The phone looks healthy from this scan.\n");
        } else {
            sb.append("Problems found:\n");
            for (Finding finding : report.findings) {
                if (finding.severity == BAD || finding.severity == WARNING) {
                    sb.append("- ").append(statusName(finding.severity)).append(": ").append(finding.title).append(" - ").append(finding.detail).append("\n");
                }
            }
        }

        sb.append("\nHealthy checks:\n");
        int shown = 0;
        for (Finding finding : report.findings) {
            if (finding.severity == GOOD) {
                sb.append("- ").append(finding.title).append("\n");
                shown++;
                if (shown >= 5) break;
            }
        }
        return sb.toString();
    }

    private String buildChangeSummary(HistoryEntry previous, ScanReport current) {
        if (previous == null) return "No previous scan history yet. Run another scan later and Device Doctor will compare score, storage, battery temperature, network validation, and patch status.";

        StringBuilder sb = new StringBuilder();
        int scoreDiff = current.score - previous.score;
        long storageDiff = current.storageUsedBytes - previous.storageUsedBytes;

        sb.append("Previous scan: ").append(previous.timestamp).append("\n");
        sb.append("Current scan: ").append(current.timestamp).append("\n\n");
        sb.append("Score: ").append(previous.score).append(" -> ").append(current.score).append(" (").append(signedInt(scoreDiff)).append(")\n");
        sb.append("Storage used: ").append(bytes(previous.storageUsedBytes)).append(" -> ").append(bytes(current.storageUsedBytes)).append(" (").append(signedBytes(storageDiff)).append(")\n");

        if (previous.batteryTempC >= 0 && current.batteryTempC >= 0) {
            double tempDiff = current.batteryTempC - previous.batteryTempC;
            sb.append("Battery temp: ").append(oneDecimal.format(previous.batteryTempC)).append(" C -> ").append(oneDecimal.format(current.batteryTempC)).append(" C (").append(tempDiff >= 0 ? "+" : "").append(oneDecimal.format(tempDiff)).append(" C)\n");
        } else {
            sb.append("Battery temp: not enough history data\n");
        }

        if (!current.securityPatch.equals(previous.securityPatch)) sb.append("Security patch changed: ").append(previous.securityPatch).append(" -> ").append(current.securityPatch).append("\n");
        else sb.append("Security patch: unchanged (").append(current.securityPatch).append(")\n");

        if (current.networkValidated != previous.networkValidated) sb.append("Network validation changed: ").append(yesNo(previous.networkValidated)).append(" -> ").append(yesNo(current.networkValidated)).append("\n");
        else sb.append("Network validation: unchanged (").append(yesNo(current.networkValidated)).append(")\n");

        if (Math.abs(scoreDiff) < 3 && Math.abs(storageDiff) < 200L * 1024L * 1024L) sb.append("\nOverall: stable since the last scan.");
        else if (scoreDiff > 0) sb.append("\nOverall: improved since the last scan.");
        else if (scoreDiff < 0) sb.append("\nOverall: worse than the last scan. Check the warnings above.");
        else sb.append("\nOverall: mostly unchanged.");
        return sb.toString();
    }

    private String buildShareReport(ScanReport report) {
        return "Device Doctor v3.1 Smart Report\n\nSMART SUMMARY\n" + report.smartSummary
                + "\nWHAT CHANGED\n" + report.changeSummary
                + "\nRAW DETAILS\n" + report.rawDetails
                + "\nNETWORK DOCTOR\n" + networkDoctorReport;
    }

    private void addDashboardCard(ScanReport report, HistoryEntry previous) {
        LinearLayout card = newCard();
        card.addView(text("V3.1 Dashboard", 20, true, 0xFFF1F1F1));

        TextView score = text(statusName(report.overallStatus) + "  -  " + report.score + "/100", 30, true, statusColor(report.overallStatus));
        score.setPadding(0, dp(6), 0, dp(6));
        card.addView(score);

        StringBuilder body = new StringBuilder();
        body.append("Patch: ").append(report.securityPatch);
        if (report.securityPatchAgeDays >= 0) body.append(" (").append(report.securityPatchAgeDays).append(" days old)");
        body.append("\n");
        body.append("Storage used: ").append(oneDecimal.format(report.storageUsedPct)).append("%\n");
        body.append("Battery temp: ").append(report.batteryTempC >= 0 ? oneDecimal.format(report.batteryTempC) + " C" : "Unknown").append("\n");
        body.append("Network: ").append(report.networkType).append(" / validated: ").append(yesNo(report.networkValidated)).append("\n");
        if (previous != null) body.append("Score since last scan: ").append(previous.score).append(" -> ").append(report.score).append(" (").append(signedInt(report.score - previous.score)).append(")\n");
        body.append("\nTop issues:\n").append(topIssues(report));

        TextView text = text(body.toString(), 14, false, 0xFFB6BAC4);
        text.setLineSpacing(0, 1.08f);
        card.addView(text);

        Button update = button("Open System Update");
        update.setOnClickListener(v -> openSettings("android.settings.SYSTEM_UPDATE_SETTINGS"));
        card.addView(update);

        reportLayout.addView(card);
    }

    private String topIssues(ScanReport report) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (Finding finding : report.findings) {
            if (finding.severity == BAD || finding.severity == WARNING) {
                out.append("- ").append(statusName(finding.severity)).append(": ").append(finding.title).append("\n");
                shown++;
                if (shown >= 4) break;
            }
        }
        if (shown == 0) out.append("- No bad or warning-level issues. Annoyingly healthy.\n");
        return out.toString();
    }

    private void addUpdateVerifierCard(ScanReport report, HistoryEntry previous) {
        LinearLayout card = newCard();
        card.addView(text("System Update Verifier", 20, true, 0xFFF1F1F1));

        int status = GOOD;
        String statusText;
        long days = report.securityPatchAgeDays;
        if (days < 0) { status = INFO; statusText = "Patch age unknown"; }
        else if (days <= 90) statusText = "Patch is current";
        else if (days <= 365) { status = WARNING; statusText = days <= 180 ? "Patch is aging" : "Patch is stale"; }
        else { status = BAD; statusText = "Patch is very old"; }

        TextView statusLine = text(statusText, 24, true, statusColor(status));
        statusLine.setPadding(0, dp(6), 0, dp(6));
        card.addView(statusLine);

        StringBuilder body = new StringBuilder();
        body.append("Current security patch: ").append(report.securityPatch).append("\n");
        body.append(days >= 0 ? "Approx patch age: " + days + " days\n" : "Approx patch age: unavailable\n");
        if (previous == null) body.append("Previous scan: none yet\nRun another scan after any system update and this card will verify the change.\n");
        else if (!report.securityPatch.equals(previous.securityPatch)) body.append("Previous patch: ").append(previous.securityPatch).append("\nResult: PATCH CHANGED. The system update changed your reported Android security patch.\n");
        else body.append("Previous patch: ").append(previous.securityPatch).append("\nResult: patch unchanged since the last scan. If you just updated, restart once and scan again.\n");
        if (days > 180) body.append("Advice: check System Update when you have Wi-Fi and enough battery.\n");
        else if (days > 90) body.append("Advice: not an emergency, but check for updates soon.\n");
        else if (days >= 0) body.append("Advice: patch age looks healthy.\n");

        TextView bodyText = text(body.toString(), 14, false, 0xFFB6BAC4);
        bodyText.setLineSpacing(0, 1.08f);
        card.addView(bodyText);

        Button update = button("Open System Update");
        update.setOnClickListener(v -> openSettings("android.settings.SYSTEM_UPDATE_SETTINGS"));
        card.addView(update);

        reportLayout.addView(card);
    }

    private void addHistoryCard(ScanReport current) {
        LinearLayout card = newCard();
        card.addView(text("History Center", 20, true, 0xFFF1F1F1));
        ArrayList<HistoryEntry> history = readHistory();

        StringBuilder body = new StringBuilder();
        body.append("Current scan: ").append(current.timestamp).append("\n");
        body.append("Current score: ").append(current.score).append("/100\n\n");
        if (history.size() == 0) {
            body.append("No stored history yet. After this scan, v3.1 will start building a timeline.\n");
        } else {
            body.append("Recent stored scans:\n");
            for (int i = 0; i < Math.min(history.size(), MAX_HISTORY); i++) {
                HistoryEntry entry = history.get(i);
                body.append(i + 1).append(". ").append(entry.timestamp)
                        .append(" | score ").append(entry.score)
                        .append(" | patch ").append(entry.securityPatch)
                        .append(" | storage ").append(bytes(entry.storageUsedBytes))
                        .append(" | net ").append(yesNo(entry.networkValidated))
                        .append("\n");
            }
        }

        TextView bodyText = text(body.toString(), 14, false, 0xFFB6BAC4);
        bodyText.setLineSpacing(0, 1.08f);
        card.addView(bodyText);

        Button reset = button("Reset History");
        reset.setOnClickListener(v -> resetHistory());
        card.addView(reset);
        reportLayout.addView(card);
    }

    private void addStorageDoctorCard(ScanReport report, HistoryEntry previous) {
        LinearLayout card = newCard();
        card.addView(text("Storage Doctor Lite", 20, true, 0xFFF1F1F1));

        int severity = GOOD;
        if (report.storageUsedPct >= 90) severity = BAD;
        else if (report.storageUsedPct >= 80) severity = WARNING;

        TextView status = text(storageStatusName(report.storageUsedPct), 24, true, statusColor(severity));
        status.setPadding(0, dp(6), 0, dp(6));
        card.addView(status);

        StringBuilder body = new StringBuilder();
        body.append("Used storage: ").append(oneDecimal.format(report.storageUsedPct)).append("%\n");
        body.append("Used amount: ").append(bytes(report.storageUsedBytes)).append("\n");
        if (previous != null) body.append("Since last scan: ").append(signedBytes(report.storageUsedBytes - previous.storageUsedBytes)).append("\n");
        body.append("\nCleanup checklist:\n");
        body.append("- Check Downloads for old APKs, videos, and screenshots.\n");
        body.append("- Review apps that store large caches.\n");
        body.append("- Move or delete large videos if storage climbs fast.\n");
        body.append("- Keep at least 10-15 GB free when possible.\n");

        TextView bodyText = text(body.toString(), 14, false, 0xFFB6BAC4);
        bodyText.setLineSpacing(0, 1.08f);
        card.addView(bodyText);

        Button storage = button("Open Storage Settings");
        storage.setOnClickListener(v -> openSettings("android.settings.INTERNAL_STORAGE_SETTINGS"));
        card.addView(storage);

        Button apps = button("Open App Storage Settings");
        apps.setOnClickListener(v -> openSettings("android.settings.MANAGE_APPLICATIONS_SETTINGS"));
        card.addView(apps);

        reportLayout.addView(card);
    }

    private String storageStatusName(double pct) {
        if (pct >= 95) return "Storage critical";
        if (pct >= 90) return "Storage very full";
        if (pct >= 80) return "Storage getting high";
        return "Storage healthy";
    }

    private void addDiagnosisCards(ScanReport report) {
        TextView heading = text("Diagnosis", 22, true, 0xFFF1F1F1);
        heading.setPadding(0, dp(4), 0, dp(8));
        reportLayout.addView(heading);

        boolean showedProblem = false;
        for (Finding finding : report.findings) {
            if (finding.severity == BAD || finding.severity == WARNING) {
                addFindingCard(finding);
                showedProblem = true;
            }
        }
        if (!showedProblem) addCard("No major problems found", "Nothing bad or warning-level showed up in this scan.");

        TextView goodHeading = text("Other Checks", 20, true, 0xFFF1F1F1);
        goodHeading.setPadding(0, dp(6), 0, dp(8));
        reportLayout.addView(goodHeading);
        for (Finding finding : report.findings) {
            if (finding.severity == GOOD || finding.severity == INFO) addFindingCard(finding);
        }
    }

    private void addFindingCard(Finding finding) {
        LinearLayout card = newCard();
        card.addView(text(finding.title + "  -  " + statusName(finding.severity), 18, true, statusColor(finding.severity)));
        TextView detail = text(finding.detail, 14, false, 0xFFF1F1F1);
        detail.setPadding(0, dp(7), 0, dp(3));
        card.addView(detail);
        card.addView(text(finding.advice, 14, false, 0xFFB6BAC4));
        if (finding.action != null && finding.actionLabel != null) {
            Button action = button(finding.actionLabel);
            action.setOnClickListener(v -> openSettings(finding.action));
            card.addView(action);
        }
        reportLayout.addView(card);
    }

    private void addNetworkDoctorCard() {
        LinearLayout card = newCard();
        card.addView(text("Network Doctor", 20, true, 0xFFF1F1F1));
        networkDoctorText = text(networkDoctorReport, 14, false, 0xFFB6BAC4);
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

        addShortcut(card, "Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        addShortcut(card, "Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        addShortcut(card, "Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        addShortcut(card, "System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        addShortcut(card, "This App Info", "APP_DETAILS");

        reportLayout.addView(card);
    }

    private void addShortcut(LinearLayout parent, String label, String action) {
        Button button = button(label);
        button.setOnClickListener(v -> openSettings(action));
        parent.addView(button);
    }

    private void runNetworkDoctor() {
        if (networkDoctorText != null) networkDoctorText.setText("Running network tests...");
        Toast.makeText(this, "Running Network Doctor", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String result = runNetworkTests();
            networkDoctorReport = result;
            if (currentReport != null) currentReport.fullReport = buildShareReport(currentReport);
            runOnUiThread(() -> {
                if (networkDoctorText != null) networkDoctorText.setText(result);
                Toast.makeText(this, "Network Doctor finished", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private String runNetworkTests() {
        StringBuilder sb = new StringBuilder();
        NetworkSnapshot network = readNetwork();
        sb.append("Network Doctor v3.1\nRun time: ").append(now()).append("\n\n")
                .append(line("Android network type", network.type))
                .append(line("Android validated", yesNo(network.validated)))
                .append(line("Android internet capability", yesNo(network.hasInternet)))
                .append("\nTESTS\n");

        TimedResult dnsGoogle = timedDns("google.com");
        TimedResult dnsCloudflare = timedDns("cloudflare.com");
        TimedResult tcpCloudflare = timedTcp("1.1.1.1", 443, 3000);
        TimedResult tcpGoogle = timedTcp("8.8.8.8", 443, 3000);
        HttpResult http = timedHttp("https://www.google.com/generate_204", 5000);

        sb.append(formatTimed("DNS google.com", dnsGoogle))
                .append(formatTimed("DNS cloudflare.com", dnsCloudflare))
                .append(formatTimed("TCP 1.1.1.1:443", tcpCloudflare))
                .append(formatTimed("TCP 8.8.8.8:443", tcpGoogle))
                .append("HTTPS generate_204: ").append(http.success ? "OK" : "FAILED")
                .append(" in ").append(http.ms).append(" ms (HTTP ").append(http.code).append(")");
        if (http.error != null) sb.append(" - ").append(http.error);
        sb.append("\n\nRESULT\n");

        int problems = 0;
        if (!network.hasNetwork || !network.hasInternet) problems++;
        if (!dnsGoogle.success && !dnsCloudflare.success) problems++;
        if (!tcpCloudflare.success && !tcpGoogle.success) problems++;
        if (!http.success) problems++;
        if (tcpCloudflare.success && tcpCloudflare.ms > 800) problems++;

        if (problems == 0) sb.append("Status: GOOD\nInternet looks reachable. Latency and DNS are healthy.\n");
        else if (problems <= 2) sb.append("Status: WARNING\nSome network checks failed or looked slow. Try toggling Wi-Fi, switching networks, or restarting the router if this keeps happening.\n");
        else sb.append("Status: BAD\nMultiple network checks failed. This connection may be broken, captive, or blocked.\n");
        return sb.toString();
    }

    private TimedResult timedDns(String host) {
        TimedResult result = new TimedResult();
        long start = SystemClock.elapsedRealtime();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            result.ms = SystemClock.elapsedRealtime() - start;
            result.success = addresses != null && addresses.length > 0;
            result.message = result.success ? addresses[0].getHostAddress() : "No addresses";
        } catch (Exception e) {
            result.ms = SystemClock.elapsedRealtime() - start;
            result.message = simpleError(e);
        }
        return result;
    }

    private TimedResult timedTcp(String host, int port, int timeoutMs) {
        TimedResult result = new TimedResult();
        long start = SystemClock.elapsedRealtime();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            result.success = true;
            result.message = "Connected";
        } catch (Exception e) {
            result.message = simpleError(e);
        } finally {
            result.ms = SystemClock.elapsedRealtime() - start;
            try { socket.close(); } catch (Exception ignored) { }
        }
        return result;
    }

    private HttpResult timedHttp(String urlText, int timeoutMs) {
        HttpResult result = new HttpResult();
        long start = SystemClock.elapsedRealtime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.connect();
            result.code = connection.getResponseCode();
            result.success = result.code >= 200 && result.code < 400;
        } catch (Exception e) {
            result.code = -1;
            result.error = simpleError(e);
        } finally {
            result.ms = SystemClock.elapsedRealtime() - start;
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    private String formatTimed(String label, TimedResult result) {
        return label + ": " + (result.success ? "OK" : "FAILED") + " in " + result.ms + " ms - " + result.message + "\n";
    }

    private void evaluateBattery(ScanReport report, BatterySnapshot battery) {
        if (battery.temperatureC < 0) addFinding(report, INFO, "Battery Temperature", "Temperature is unavailable.", "Android did not expose battery temperature on this scan.", 0, null, null);
        else if (battery.temperatureC >= 45) addFinding(report, BAD, "Battery Temperature", "Battery is very hot: " + oneDecimal.format(battery.temperatureC) + " C.", "Stop heavy use and let the phone cool down.", 20, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else if (battery.temperatureC >= 40) addFinding(report, BAD, "Battery Temperature", "Battery is hot: " + oneDecimal.format(battery.temperatureC) + " C.", "Heat wears batteries down faster.", 15, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else if (battery.temperatureC >= 35) addFinding(report, WARNING, "Battery Temperature", "Battery is warm: " + oneDecimal.format(battery.temperatureC) + " C.", "Avoid stacking heat with gaming plus charging.", 5, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else addFinding(report, GOOD, "Battery Temperature", "Battery temperature looks normal: " + oneDecimal.format(battery.temperatureC) + " C.", "No heat problem detected.", 0, null, null);

        if (battery.health == BatteryManager.BATTERY_HEALTH_GOOD) addFinding(report, GOOD, "Battery Health", "Android reports battery health as good.", "Nothing concerning detected here.", 0, null, null);
        else if (battery.health <= 0 || battery.health == BatteryManager.BATTERY_HEALTH_UNKNOWN) addFinding(report, INFO, "Battery Health", "Battery health is unknown.", "Some devices do not expose useful battery health data.", 0, null, null);
        else addFinding(report, BAD, "Battery Health", "Android reports battery health as " + batteryHealthName(battery.health) + ".", "Watch charging, heat, and sudden shutdowns.", 20, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
    }

    private void evaluateStorage(ScanReport report, StorageSnapshot storage) {
        if (storage.usedPct >= 95) addFinding(report, BAD, "Storage", "Storage is critically full: " + oneDecimal.format(storage.usedPct) + "% used.", "Free space soon.", 30, "Open Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        else if (storage.usedPct >= 90) addFinding(report, BAD, "Storage", "Storage is very full: " + oneDecimal.format(storage.usedPct) + "% used.", "Clear downloads, videos, cache, or unused apps.", 20, "Open Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        else if (storage.usedPct >= 80) addFinding(report, WARNING, "Storage", "Storage is getting high: " + oneDecimal.format(storage.usedPct) + "% used.", "Cleanup is not urgent, but it is getting close.", 10, "Open Storage Settings", "android.settings.INTERNAL_STORAGE_SETTINGS");
        else addFinding(report, GOOD, "Storage", "Storage looks okay: " + oneDecimal.format(storage.usedPct) + "% used.", "Free space is not currently a problem.", 0, null, null);
    }

    private void evaluateNetwork(ScanReport report, NetworkSnapshot network) {
        if (!network.hasNetwork) addFinding(report, BAD, "Network", "No active network detected.", "Connect to Wi-Fi or mobile data.", 20, "Open Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        else if (!network.hasInternet) addFinding(report, BAD, "Network", "A network exists, but Android does not see internet capability.", "Reconnect or switch networks.", 20, "Open Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        else if (!network.validated) addFinding(report, WARNING, "Network", "Connected, but Android has not validated real internet.", "Run Network Doctor or check for a login page.", 10, "Open Wi-Fi Settings", "android.settings.WIFI_SETTINGS");
        else addFinding(report, GOOD, "Network", "Internet connection is validated on " + network.type + ".", "Network looks normal from Android's view.", 0, null, null);
        if (network.hasNetwork && !network.notMetered) addFinding(report, INFO, "Metered Network", "This connection may be metered.", "Large downloads may use mobile data or a limited connection.", 0, "Open Network Settings", "android.settings.WIRELESS_SETTINGS");
    }

    private void evaluateMemory(ScanReport report, MemorySnapshot memory) {
        if (memory.lowMemory) addFinding(report, WARNING, "Memory", "Android reports low memory pressure.", "Close heavy apps or restart if sluggish.", 10, "Open App Settings", "APP_DETAILS");
        else addFinding(report, GOOD, "Memory", "Android is not reporting low memory pressure.", "No RAM emergency detected.", 0, null, null);
    }

    private void evaluateThermal(ScanReport report, int thermalStatus) {
        if (thermalStatus < 0) addFinding(report, INFO, "Thermals", "Thermal status is unavailable.", "This Android version may not expose it.", 0, null, null);
        else if (thermalStatus == PowerManager.THERMAL_STATUS_NONE) addFinding(report, GOOD, "Thermals", "System thermal status is normal.", "No thermal throttling reported.", 0, null, null);
        else if (thermalStatus == PowerManager.THERMAL_STATUS_LIGHT) addFinding(report, WARNING, "Thermals", "System thermal status is light.", "The phone is slightly warm.", 5, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else if (thermalStatus == PowerManager.THERMAL_STATUS_MODERATE) addFinding(report, WARNING, "Thermals", "System thermal status is moderate.", "Let the phone cool if performance feels worse.", 10, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
        else addFinding(report, BAD, "Thermals", "System thermal status is " + thermalName(thermalStatus) + ".", "Stop heavy use and let the phone cool down.", 25, "Open Battery Settings", "android.settings.BATTERY_SAVER_SETTINGS");
    }

    private void evaluateSecurityPatch(ScanReport report, String patch) {
        long days = securityPatchAgeDays(patch);
        report.securityPatchAgeDays = days;
        if (days < 0) addFinding(report, INFO, "Security Patch", "Security patch date is unavailable.", "Check Android updates manually.", 0, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        else if (days <= 90) addFinding(report, GOOD, "Security Patch", "Security patch is recent: " + patch + ".", "Patch age is about " + days + " days.", 0, null, null);
        else if (days <= 180) addFinding(report, WARNING, "Security Patch", "Security patch is aging: " + patch + ".", "Patch age is about " + days + " days. Check for updates.", 5, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        else if (days <= 365) addFinding(report, WARNING, "Security Patch", "Security patch is stale: " + patch + ".", "Patch age is about " + days + " days. System update check recommended.", 10, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
        else addFinding(report, BAD, "Security Patch", "Security patch is very old: " + patch + ".", "Patch age is about " + days + " days. Update if possible.", 20, "Open System Update", "android.settings.SYSTEM_UPDATE_SETTINGS");
    }

    private void evaluateUptime(ScanReport report, long elapsedMs) {
        long days = elapsedMs / 86400000L;
        if (days >= 30) addFinding(report, WARNING, "Uptime", "Phone has been running for " + days + " days since boot.", "Restarting can clear background-service problems.", 15, "Open Device Settings", "android.settings.SETTINGS");
        else if (days >= 14) addFinding(report, WARNING, "Uptime", "Phone has been running for " + days + " days since boot.", "A restart may help if anything feels strange.", 10, "Open Device Settings", "android.settings.SETTINGS");
        else if (days >= 7) addFinding(report, WARNING, "Uptime", "Phone has been running for " + days + " days since boot.", "A restart can freshen things up.", 5, "Open Device Settings", "android.settings.SETTINGS");
        else addFinding(report, GOOD, "Uptime", "Recent boot: " + duration(elapsedMs) + " since startup.", "No restart recommendation right now.", 0, null, null);
    }

    private void addFinding(ScanReport report, int severity, String title, String detail, String advice, int penalty, String actionLabel, String action) {
        Finding finding = new Finding();
        finding.severity = severity;
        finding.title = title;
        finding.detail = detail;
        finding.advice = advice;
        finding.actionLabel = actionLabel;
        finding.action = action;
        report.findings.add(finding);
        report.score -= penalty;
        if (severity == BAD) report.badCount++;
        else if (severity == WARNING) report.warningCount++;
        else if (severity == GOOD) report.goodCount++;
        else report.infoCount++;
    }

    private BatterySnapshot readBattery() {
        BatterySnapshot out = new BatterySnapshot();
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return out;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        out.levelPercent = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        out.status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        out.plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        out.health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int tempTenthsC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        out.temperatureC = tempTenthsC >= 0 ? tempTenthsC / 10.0 : -1;
        out.voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String tech = battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        out.technology = tech == null ? "Unknown" : tech;
        return out;
    }

    private StorageSnapshot readStorage() {
        StorageSnapshot out = new StorageSnapshot();
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        out.totalBytes = stat.getBlockCountLong() * blockSize;
        out.freeBytes = stat.getAvailableBlocksLong() * blockSize;
        out.usedBytes = out.totalBytes - out.freeBytes;
        out.usedPct = out.totalBytes > 0 ? out.usedBytes * 100.0 / out.totalBytes : 0;
        return out;
    }

    private NetworkSnapshot readNetwork() {
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

    private MemorySnapshot readMemory() {
        MemorySnapshot out = new MemorySnapshot();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(info);
            out.availableBytes = info.availMem;
            out.lowMemory = info.lowMemory;
            out.thresholdBytes = info.threshold;
        }
        Runtime rt = Runtime.getRuntime();
        out.appMaxBytes = rt.maxMemory();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        out.appUsedBytes = total - free;
        out.appFreeBytes = free;
        return out;
    }

    private SensorSnapshot readSensors() {
        SensorSnapshot out = new SensorSnapshot();
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null) return out;
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        out.total = sensors.size();
        int limit = Math.min(8, sensors.size());
        for (int i = 0; i < limit; i++) out.previewNames.add(sensors.get(i).getName());
        out.moreCount = sensors.size() - limit;
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

    private HistoryEntry loadMostRecentHistory() {
        ArrayList<HistoryEntry> history = readHistory();
        return history.size() == 0 ? null : history.get(0);
    }

    private ArrayList<HistoryEntry> readHistory() {
        ArrayList<HistoryEntry> entries = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE);
        for (int i = 0; i < MAX_HISTORY; i++) {
            String prefix = "h" + i + "_";
            if (!prefs.contains(prefix + "timestamp")) continue;
            HistoryEntry entry = new HistoryEntry();
            entry.timestamp = prefs.getString(prefix + "timestamp", "Unknown");
            entry.score = prefs.getInt(prefix + "score", 0);
            entry.storageUsedBytes = prefs.getLong(prefix + "storageUsedBytes", 0);
            entry.batteryTempC = Double.longBitsToDouble(prefs.getLong(prefix + "batteryTempC", Double.doubleToLongBits(-1)));
            entry.securityPatch = prefs.getString(prefix + "securityPatch", "Unknown");
            entry.networkValidated = prefs.getBoolean(prefix + "networkValidated", false);
            entries.add(entry);
        }
        return entries;
    }

    private void saveHistory(ScanReport report) {
        ArrayList<HistoryEntry> existing = readHistory();
        HistoryEntry current = HistoryEntry.fromReport(report);
        SharedPreferences.Editor editor = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE).edit();
        editor.clear();
        writeHistory(editor, 0, current);
        int index = 1;
        for (HistoryEntry entry : existing) {
            if (index >= MAX_HISTORY) break;
            if (entry.timestamp != null && entry.timestamp.equals(current.timestamp)) continue;
            writeHistory(editor, index, entry);
            index++;
        }
        editor.apply();
    }

    private void writeHistory(SharedPreferences.Editor editor, int index, HistoryEntry entry) {
        String prefix = "h" + index + "_";
        editor.putString(prefix + "timestamp", entry.timestamp);
        editor.putInt(prefix + "score", entry.score);
        editor.putLong(prefix + "storageUsedBytes", entry.storageUsedBytes);
        editor.putLong(prefix + "batteryTempC", Double.doubleToRawLongBits(entry.batteryTempC));
        editor.putString(prefix + "securityPatch", entry.securityPatch == null ? "Unknown" : entry.securityPatch);
        editor.putBoolean(prefix + "networkValidated", entry.networkValidated);
    }

    private void resetHistory() {
        getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "History reset", Toast.LENGTH_SHORT).show();
        refreshScan();
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Device Doctor Report", combinedReport()));
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Device Doctor Report");
        send.putExtra(Intent.EXTRA_TEXT, combinedReport());
        startActivity(Intent.createChooser(send, "Share Device Doctor Report"));
    }

    private String combinedReport() {
        if (currentReport == null) currentReport = scanDevice(loadMostRecentHistory());
        return currentReport.fullReport;
    }

    private void openSettings(String action) {
        try {
            Intent intent;
            if ("APP_DETAILS".equals(action)) {
                intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                intent.setData(Uri.parse("package:" + getPackageName()));
            } else {
                intent = new Intent(action);
            }
            startActivity(intent);
        } catch (Exception firstFailure) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception secondFailure) { Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show(); }
        }
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
        TextView title = text(heading, 19, true, 0xFFF1F1F1);
        title.setPadding(0, 0, 0, dp(7));
        card.addView(title);
        TextView detail = text(body == null ? "" : body.trim(), 14, false, 0xFFB6BAC4);
        detail.setLineSpacing(0, 1.08f);
        card.addView(detail);
        reportLayout.addView(card);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
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

    private String yesNo(boolean value) { return value ? "Yes" : "No"; }
    private String line(String label, String value) { return label + ": " + value + "\n"; }
    private String safe(String value) { return value == null || value.trim().length() == 0 ? "Unknown" : value; }
    private String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }
    private String simpleError(Exception e) { return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""); }
    private String signedInt(int value) { return value >= 0 ? "+" + value : String.valueOf(value); }
    private String signedBytes(long value) { return value >= 0 ? "+" + bytes(value) : "-" + bytes(Math.abs(value)); }

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
            return diff < 0 ? 0 : diff / 86400000L;
        } catch (Exception e) {
            return -1;
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

    private String thermalName(int thermalStatus) {
        switch (thermalStatus) {
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
        String smartSummary;
        String changeSummary;
        String rawDetails;
        String fullReport;
        String securityPatch = "Unknown";
        long securityPatchAgeDays = -1;
        long storageUsedBytes;
        double storageUsedPct;
        double batteryTempC = -1;
        boolean networkValidated;
        String networkType = "Unknown";
        long uptimeDays;
        int thermalStatus = -1;
        final ArrayList<Finding> findings = new ArrayList<>();
    }

    private static class Finding {
        int severity;
        String title;
        String detail;
        String advice;
        String actionLabel;
        String action;
    }

    private static class HistoryEntry {
        String timestamp;
        int score;
        long storageUsedBytes;
        double batteryTempC;
        String securityPatch;
        boolean networkValidated;

        static HistoryEntry fromReport(ScanReport report) {
            HistoryEntry entry = new HistoryEntry();
            entry.timestamp = report.timestamp;
            entry.score = report.score;
            entry.storageUsedBytes = report.storageUsedBytes;
            entry.batteryTempC = report.batteryTempC;
            entry.securityPatch = report.securityPatch;
            entry.networkValidated = report.networkValidated;
            return entry;
        }
    }

    private static class BatterySnapshot { int levelPercent = -1; int status = -1; int plugged = -1; int health = -1; int voltageMv = -1; double temperatureC = -1; String technology = "Unknown"; }
    private static class StorageSnapshot { long totalBytes; long usedBytes; long freeBytes; double usedPct; }
    private static class NetworkSnapshot { boolean hasNetwork; boolean hasInternet; boolean validated; boolean notMetered; String type = "None"; int downKbps = -1; int upKbps = -1; }
    private static class MemorySnapshot { long availableBytes; long thresholdBytes; long appMaxBytes; long appUsedBytes; long appFreeBytes; boolean lowMemory; }
    private static class SensorSnapshot { int total; int moreCount; ArrayList<String> previewNames = new ArrayList<>(); }
    private static class TimedResult { boolean success; long ms; String message; }
    private static class HttpResult { boolean success; long ms; int code; String error; }
}
