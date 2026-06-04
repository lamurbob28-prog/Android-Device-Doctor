# Android Device Doctor

A small native Android diagnostic app. GitHub Actions compiles the APK.

## Version 2.1

Device Doctor now includes a deeper Network Doctor in addition to the v2 health score system.

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

## New in v2.1

- Network Doctor button
- DNS lookup tests for google.com and cloudflare.com
- TCP connection tests to 1.1.1.1:443 and 8.8.8.8:443
- HTTPS reachability test using generate_204
- Share Full Report button
- Copy/share output includes the Network Doctor result
- INTERNET permission added for network tests

## From v2.0

- Health score at the top
- Problem cards shown first
- Plain-English advice for each finding
- Buttons that open relevant Android settings screens
- Copy Full Report button
- Full raw diagnostic report at the bottom

## Build

GitHub Actions builds a debug APK.

After the workflow runs, go to:

Actions -> Build Android APK -> latest run -> Artifacts -> android-device-doctor-debug-apk

Download the artifact ZIP, extract it, and install the APK on your Android phone.

## Note

This is a debug APK intended for personal testing and sideloading.
