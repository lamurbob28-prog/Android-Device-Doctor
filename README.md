# Android Device Doctor

A small native Android diagnostic app. GitHub Actions compiles the APK.

## Version 2.0

Device Doctor now does more than display raw Android numbers. It scores the device and explains what needs attention.

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

## New in v2

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
