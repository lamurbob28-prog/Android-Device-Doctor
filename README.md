# Android Device Doctor

A small native Android diagnostic app. GitHub Actions compiles the APK.

## What it checks

- Device model, manufacturer, Android version, and SDK level
- Battery level, charging state, temperature, health, voltage, and technology
- Internal storage total/free/used space
- Network type and internet validation status
- Uptime and elapsed realtime
- App runtime memory
- Sensor count
- Thermal status on supported Android versions

## Build

GitHub Actions builds a debug APK.

After the workflow runs, go to:

Actions -> Build Android APK -> latest run -> Artifacts -> android-device-doctor-debug-apk

Download the artifact ZIP, extract it, and install the APK on your Android phone.

## Note

This is a debug APK intended for personal testing and sideloading.
