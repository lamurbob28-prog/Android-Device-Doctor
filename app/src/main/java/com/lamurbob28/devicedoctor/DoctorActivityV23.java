package com.lamurbob28.devicedoctor;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DoctorActivityV23 extends DoctorActivity {
    @Override
    void scan() {
        DoctorActivity.History old = load();
        report = make(old);
        list.removeAllViews();
        scoreCard();
        card("Smart Summary", report.summary);
        updateVerifierCard(old, report);
        card("What Changed", report.changes);
        diagnosis();
        netCard();
        shortcuts();
        card("Full Raw Details", report.raw);
        save(report);
    }

    void updateVerifierCard(DoctorActivity.History old, DoctorActivity.Report current) {
        LinearLayout card = box();
        card.addView(tv("System Update Verifier", 20, true, 0xfff1f1f1));

        long days = patchAge(current.patch);
        int status = GOOD;
        String statusText;
        if (days < 0) {
            status = INFO;
            statusText = "Patch age unknown";
        } else if (days <= 90) {
            statusText = "Patch is current";
        } else if (days <= 180) {
            status = WARN;
            statusText = "Patch is aging";
        } else if (days <= 365) {
            status = WARN;
            statusText = "Patch is stale";
        } else {
            status = BAD;
            statusText = "Patch is very old";
        }

        TextView statusLine = tv(statusText, 24, true, color(status));
        statusLine.setPadding(0, dp(6), 0, dp(6));
        card.addView(statusLine);

        StringBuilder body = new StringBuilder();
        body.append("Current security patch: ").append(current.patch).append("\n");
        if (days >= 0) body.append("Approx patch age: ").append(days).append(" days\n");
        else body.append("Approx patch age: unavailable\n");

        if (old == null) {
            body.append("Previous scan: none yet\n");
            body.append("Run another scan after any system update and this card will verify the change.\n");
        } else if (!current.patch.equals(old.patch)) {
            body.append("Previous patch: ").append(old.patch).append("\n");
            body.append("Result: PATCH CHANGED. The system update changed your reported Android security patch.\n");
        } else {
            body.append("Previous patch: ").append(old.patch).append("\n");
            body.append("Result: patch unchanged since the last scan. If you just updated, restart once and scan again.\n");
        }

        if (days > 180) body.append("Advice: check System Update when you have Wi-Fi and enough battery.\n");
        else if (days > 90) body.append("Advice: not an emergency, but check for updates soon.\n");
        else if (days >= 0) body.append("Advice: patch age looks healthy.\n");

        TextView bodyText = tv(body.toString(), 14, false, 0xffb6bac4);
        bodyText.setLineSpacing(0, 1.08f);
        card.addView(bodyText);

        Button update = btn("Open System Update");
        update.setOnClickListener(v -> open("android.settings.SYSTEM_UPDATE_SETTINGS"));
        card.addView(update);

        list.addView(card);
    }
}
