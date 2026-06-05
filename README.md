# Android Device Doctor

A small native Android diagnostic app. GitHub Actions compiles the APK.

## Version 2.3

Device Doctor now includes a System Update Verifier, because the app already caught an outdated security patch and apparently decided to become useful. Disturbing development.

## What it checks

- Overall health score from 0 to 100
- Good, warning, bad, and info cards
- Battery level, charging state, temperature, health, voltage, and technology
- Internal storage total/free/used space with fullness warnings
- Network type, internet capability, and validation status
- Uptime and restart recommendations
- App/runtime memory pressure
- Sensor count
- Thermal status on supported Android versions
- Security patch age

## New in v2.3

- System Update Verifier card
- Shows current Android security patch age
- Compares current patch against the previous scan
- Confirms when an update actually changed the patch date
- Adds a direct Open System Update button inside the verifier

## From v2.2

- Smart Summary card
- What Changed card
- Local last-scan history using SharedPreferences
- Copy Smart Report button
- Share Smart Report button
- Clearer warning list at the top of the report

## From v2.1

- Network Doctor button
- DNS lookup tests for google.com and cloudflare.com
- TCP connection tests to 1.1.1.1:443 and 8.8.8.8:443
- HTTPS reachability test using generate_204
- INTERNET permission for network tests

## Build

GitHub Actions builds a debug APK.

After the workflow runs, go to:

Actions -> Build Android APK -> latest run -> Artifacts -> android-device-doctor-debug-apk

Download the artifact ZIP, extract it, and install the APK on your Android phone.

## Note

This is a debug APK intended for personal testing and sideloading.
