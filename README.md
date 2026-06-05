# Android Device Doctor

A small native Android diagnostic app. GitHub Actions compiles the APK.

## Version 2.2

Device Doctor now includes Smart Summary and local scan history, so the report says what changed instead of dumping raw numbers like a bureaucratic refrigerator.

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

## New in v2.2

- Smart Summary card
- What Changed card
- Local last-scan history using SharedPreferences
- Copy Smart Report button
- Share Smart Report button
- Clearer warning list at the top of the report
- Compares score, storage usage, battery temperature, security patch, and network validation with the previous scan

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
