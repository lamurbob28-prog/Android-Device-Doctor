package com.lamurbob28.devicedoctor;

import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class DoctorActivityV30 extends DoctorActivityV23 {
    static final String V3_PREFS = "device_doctor_v3_history";
    static final int MAX_HISTORY = 10;

    @Override
    void scan() {
        DoctorActivity.History old = load();
        report = make(old);
        list.removeAllViews();
        scoreCard();
        dashboardCard(old, report);
        card("Smart Summary", report.summary);
        updateVerifierCard(old, report);
        historyCenterCard(report);
        storageDoctorCard(old, report);
        card("What Changed", report.changes);
        diagnosis();
        netCard();
        shortcuts();
        card("Full Raw Details", report.raw);
        save(report);
    }

    void dashboardCard(DoctorActivity.History old, DoctorActivity.Report r) {
        LinearLayout c = box();
        c.addView(tv("V3 Dashboard", 20, true, 0xfff1f1f1));
        TextView score = tv(name(r.status) + "  -  " + r.score + "/100", 30, true, color(r.status));
        score.setPadding(0, dp(6), 0, dp(6));
        c.addView(score);

        StringBuilder b = new StringBuilder();
        b.append("Patch: ").append(r.patch);
        if (r.patchDays >= 0) b.append(" (").append(r.patchDays).append(" days old)");
        b.append("\n");
        b.append("Storage used: ").append(df.format(r.usedPct)).append("%\n");
        b.append("Battery temp: ").append(r.temp >= 0 ? df.format(r.temp) + " C" : "Unknown").append("\n");
        b.append("Network: ").append(r.netType).append(" / validated: ").append(yn(r.netValid)).append("\n");

        if (old != null) {
            b.append("Score since last scan: ").append(old.score).append(" -> ").append(r.score).append(" (").append(sign(r.score - old.score)).append(")\n");
        }

        String top = topIssues(r);
        b.append("\nTop issues:\n").append(top);
        TextView body = tv(b.toString(), 14, false, 0xffb6bac4);
        body.setLineSpacing(0, 1.08f);
        c.addView(body);

        Button net = btn("Run Network Doctor");
        net.setOnClickListener(v -> runNet());
        c.addView(net);

        Button update = btn("Open System Update");
        update.setOnClickListener(v -> open("android.settings.SYSTEM_UPDATE_SETTINGS"));
        c.addView(update);

        list.addView(c);
    }

    String topIssues(DoctorActivity.Report r) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (DoctorActivity.F f : r.fs) {
            if (f.sev == BAD || f.sev == WARN) {
                out.append("- ").append(name(f.sev)).append(": ").append(f.title).append("\n");
                shown++;
                if (shown >= 4) break;
            }
        }
        if (shown == 0) out.append("- No bad or warning-level issues. Annoyingly healthy.\n");
        return out.toString();
    }

    void historyCenterCard(DoctorActivity.Report current) {
        LinearLayout c = box();
        c.addView(tv("History Center", 20, true, 0xfff1f1f1));
        StringBuilder b = new StringBuilder();
        b.append("Current scan: ").append(current.time).append("\n");
        b.append("Current score: ").append(current.score).append("/100\n\n");

        ArrayList<DoctorActivity.History> history = readV3History();
        if (history.size() == 0) {
            DoctorActivity.History fallback = super.load();
            if (fallback != null) history.add(fallback);
        }

        if (history.size() == 0) {
            b.append("No stored history yet. After this scan, v3 will start building a timeline.\n");
        } else {
            b.append("Recent stored scans:\n");
            int limit = Math.min(history.size(), MAX_HISTORY);
            for (int i = 0; i < limit; i++) {
                DoctorActivity.History h = history.get(i);
                b.append(i + 1).append(". ").append(h.time)
                        .append(" | score ").append(h.score)
                        .append(" | patch ").append(h.patch)
                        .append(" | storage ").append(bytes(h.used))
                        .append(" | net ").append(yn(h.netValid))
                        .append("\n");
            }
        }

        TextView body = tv(b.toString(), 14, false, 0xffb6bac4);
        body.setLineSpacing(0, 1.08f);
        c.addView(body);

        Button clear = btn("Reset History");
        clear.setOnClickListener(v -> clearHistory());
        c.addView(clear);
        list.addView(c);
    }

    void storageDoctorCard(DoctorActivity.History old, DoctorActivity.Report r) {
        LinearLayout c = box();
        c.addView(tv("Storage Doctor Lite", 20, true, 0xfff1f1f1));

        int sev = GOOD;
        if (r.usedPct >= 90) sev = BAD;
        else if (r.usedPct >= 80) sev = WARN;

        TextView status = tv(storageStatusName(r.usedPct), 24, true, color(sev));
        status.setPadding(0, dp(6), 0, dp(6));
        c.addView(status);

        StringBuilder b = new StringBuilder();
        b.append("Used storage: ").append(df.format(r.usedPct)).append("%\n");
        b.append("Used amount: ").append(bytes(r.used)).append("\n");
        if (old != null) {
            long diff = r.used - old.used;
            b.append("Since last scan: ").append(signBytes(diff)).append("\n");
        }
        b.append("\nCleanup checklist:\n");
        b.append("- Check Downloads for old APKs, videos, and screenshots.\n");
        b.append("- Review apps that store large caches.\n");
        b.append("- Move or delete large videos if storage climbs fast.\n");
        b.append("- Keep at least 10-15 GB free when possible.\n");
        TextView body = tv(b.toString(), 14, false, 0xffb6bac4);
        body.setLineSpacing(0, 1.08f);
        c.addView(body);

        Button storage = btn("Open Storage Settings");
        storage.setOnClickListener(v -> open("android.settings.INTERNAL_STORAGE_SETTINGS"));
        c.addView(storage);

        Button apps = btn("Open App Storage Settings");
        apps.setOnClickListener(v -> open("android.settings.MANAGE_APPLICATIONS_SETTINGS"));
        c.addView(apps);

        list.addView(c);
    }

    String storageStatusName(double pct) {
        if (pct >= 95) return "Storage critical";
        if (pct >= 90) return "Storage very full";
        if (pct >= 80) return "Storage getting high";
        return "Storage healthy";
    }

    void clearHistory() {
        getSharedPreferences(V3_PREFS, MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("device_doctor_history", MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "History reset", Toast.LENGTH_SHORT).show();
        scan();
    }

    @Override
    DoctorActivity.History load() {
        ArrayList<DoctorActivity.History> items = readV3History();
        if (items.size() > 0) return items.get(0);
        return super.load();
    }

    @Override
    void save(DoctorActivity.Report r) {
        ArrayList<DoctorActivity.History> old = readV3History();
        if (old.size() == 0) {
            DoctorActivity.History fallback = super.load();
            if (fallback != null) old.add(fallback);
        }

        DoctorActivity.History current = fromReport(r);
        SharedPreferences.Editor e = getSharedPreferences(V3_PREFS, MODE_PRIVATE).edit();
        e.clear();
        writeHistory(e, 0, current);
        int outIndex = 1;
        for (int i = 0; i < old.size() && outIndex < MAX_HISTORY; i++) {
            DoctorActivity.History h = old.get(i);
            if (h.time != null && h.time.equals(current.time)) continue;
            writeHistory(e, outIndex, h);
            outIndex++;
        }
        e.apply();
        super.save(r);
    }

    DoctorActivity.History fromReport(DoctorActivity.Report r) {
        DoctorActivity.History h = new DoctorActivity.History();
        h.time = r.time;
        h.score = r.score;
        h.used = r.used;
        h.temp = r.temp;
        h.patch = r.patch;
        h.netValid = r.netValid;
        return h;
    }

    ArrayList<DoctorActivity.History> readV3History() {
        ArrayList<DoctorActivity.History> out = new ArrayList<>();
        SharedPreferences p = getSharedPreferences(V3_PREFS, MODE_PRIVATE);
        for (int i = 0; i < MAX_HISTORY; i++) {
            String prefix = "h" + i + "_";
            if (!p.contains(prefix + "time")) continue;
            DoctorActivity.History h = new DoctorActivity.History();
            h.time = p.getString(prefix + "time", "Unknown");
            h.score = p.getInt(prefix + "score", 0);
            h.used = p.getLong(prefix + "used", 0);
            h.temp = Double.longBitsToDouble(p.getLong(prefix + "temp", Double.doubleToLongBits(-1)));
            h.patch = p.getString(prefix + "patch", "Unknown");
            h.netValid = p.getBoolean(prefix + "netValid", false);
            out.add(h);
        }
        return out;
    }

    void writeHistory(SharedPreferences.Editor e, int i, DoctorActivity.History h) {
        String prefix = "h" + i + "_";
        e.putString(prefix + "time", h.time);
        e.putInt(prefix + "score", h.score);
        e.putLong(prefix + "used", h.used);
        e.putLong(prefix + "temp", Double.doubleToRawLongBits(h.temp));
        e.putString(prefix + "patch", h.patch == null ? "Unknown" : h.patch);
        e.putBoolean(prefix + "netValid", h.netValid);
    }
}
