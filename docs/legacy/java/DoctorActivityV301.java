package com.lamurbob28.devicedoctor;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class DoctorActivityV301 extends DoctorActivityV30 {
    @Override
    void ui() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xff101114);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        sv.addView(root);

        root.addView(tv("Device Doctor", 30, true, 0xfff1f1f1));
        TextView sub = tv("V3 dashboard, history, storage, updates, and one Network Doctor.", 15, false, 0xffb6bac4);
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        Button refresh = btn("Refresh Scan");
        refresh.setOnClickListener(v -> scan());
        root.addView(refresh);

        Button copy = btn("Copy Smart Report");
        copy.setOnClickListener(v -> copy());
        root.addView(copy);

        Button share = btn("Share Smart Report");
        share.setOnClickListener(v -> share());
        root.addView(share);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(14), 0, 0);
        root.addView(list);

        setContentView(sv);
    }

    @Override
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
        b.append("\nTop issues:\n").append(topIssues(r));

        TextView body = tv(b.toString(), 14, false, 0xffb6bac4);
        body.setLineSpacing(0, 1.08f);
        c.addView(body);

        Button update = btn("Open System Update");
        update.setOnClickListener(v -> open("android.settings.SYSTEM_UPDATE_SETTINGS"));
        c.addView(update);

        list.addView(c);
    }

    @Override
    String shareText(DoctorActivity.Report r) {
        return "Device Doctor v3.0.1 Smart Report\n\nSMART SUMMARY\n" + r.summary
                + "\nWHAT CHANGED\n" + r.changes
                + "\nRAW DETAILS\n" + r.raw
                + "\nNETWORK DOCTOR\n" + netReport;
    }

    @Override
    String netTests() {
        StringBuilder s = new StringBuilder();
        DoctorActivity.Net n = net();
        s.append("Network Doctor v3.0.1\nRun time: ").append(now()).append("\n\n")
                .append(line("Android network type", n.type))
                .append(line("Android validated", yn(n.valid)))
                .append(line("Android internet capability", yn(n.internet)))
                .append("\nTESTS\n");

        DoctorActivity.TR dg = dns("google.com");
        DoctorActivity.TR dc = dns("cloudflare.com");
        DoctorActivity.TR c1 = tcp("1.1.1.1", 443, 3000);
        DoctorActivity.TR c8 = tcp("8.8.8.8", 443, 3000);
        DoctorActivity.HR h = http("https://www.google.com/generate_204", 5000);

        s.append(fmt("DNS google.com", dg))
                .append(fmt("DNS cloudflare.com", dc))
                .append(fmt("TCP 1.1.1.1:443", c1))
                .append(fmt("TCP 8.8.8.8:443", c8))
                .append("HTTPS generate_204: ").append(h.ok ? "OK" : "FAILED")
                .append(" in ").append(h.ms).append(" ms (HTTP ").append(h.code).append(")");
        if (h.err != null) s.append(" - ").append(h.err);
        s.append("\n\nRESULT\n");

        int p = 0;
        if (!n.has || !n.internet) p++;
        if (!dg.ok && !dc.ok) p++;
        if (!c1.ok && !c8.ok) p++;
        if (!h.ok) p++;
        if (c1.ok && c1.ms > 800) p++;

        if (p == 0) s.append("Status: GOOD\nInternet looks reachable. Latency and DNS are healthy.\n");
        else if (p <= 2) s.append("Status: WARNING\nSome network checks failed or looked slow. Try toggling Wi-Fi, switching networks, or restarting the router if this keeps happening.\n");
        else s.append("Status: BAD\nMultiple network checks failed. This connection may be broken, captive, or blocked.\n");
        return s.toString();
    }
}
