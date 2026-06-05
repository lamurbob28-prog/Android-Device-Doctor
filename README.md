# Android Device Doctor

A native Android diagnostic app. GitHub Actions compiles the APK.

## Version 4.0

Device Doctor v4.0 is the migration release. The active app is now Kotlin, Jetpack Compose, ViewModel-based state, and Room database history.

## What it checks

- Overall health score from 0 to 100
- Good, warning, bad, and info findings
- Battery level, temperature, health, voltage, and technology
- Internal storage total, free, used, and fullness warnings
- Network type, internet capability, and validation status
- Uptime and restart recommendations
- Memory pressure
- Sensor count
- Thermal status on supported Android versions
- Security patch age

## New in v4.0

- Kotlin app layer
- Jetpack Compose UI
- Room database scan history
- AndroidViewModel app state
- Coroutine-based Network Doctor
- Active launcher points to `.v4.V4MainActivity`
- Java v3.1 code remains in the repository for reference

## From v3.1

- Dashboard
- System Update Verifier
- History Center
- Storage Doctor Lite
- Smart Summary
- What Changed report
- One Network Doctor card
- MIT license

## Build

GitHub Actions builds the APK.

After the workflow runs, go to:

Actions -> Build Android APK -> latest run -> Artifacts -> android-device-doctor-debug-apk

Download the artifact ZIP, extract it, and install the APK on your Android phone.
