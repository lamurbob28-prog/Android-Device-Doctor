# Device Doctor 5.0

A native Android checkup app with useful readings, honest limits, and a practical next step.

## What's new

- **Overview:** a readable dashboard for battery, free storage, available RAM, connection state, and priority findings.
- **Checks:** filter by battery, storage, connection, or system; see measured evidence and a relevant Android settings shortcut.
- **Tools:** opt-in HTTPS connection checks, a multi-touch grid, display colors, a short audio/vibration check, and live motion/light/proximity readings.
- **History:** keep the latest 50 scans, view earlier reports, compare readings, and track storage usage.
- **Reports:** copy, share, or save a text file through Android's file picker.
- **Reliability:** scans run off the main thread, overlapping requests are blocked, errors are visible, network requests are cancellable and bounded, and clearing history stays cleared.

### Better diagnoses

Offline mode and long uptime are information, not hardware faults. Missing measurements remain unavailable. A sensor inventory does not certify working sensors. Android's battery `Good` flag is not a battery-wear or remaining-capacity measurement. A redirect or unexpected HTTP page no longer passes a connection test. Patch dates are parsed strictly in UTC; future dates are not treated as current.

The main dashboard reports findings rather than a misleading physical-health percentage. Exported reports retain a clearly labeled **heuristic checklist score** for reference. Temperature and storage alerts use general thresholds, not a manufacturer-specific certification.

## Get the APK

Open **Actions → Build Android APK → a successful run → Artifacts → device-doctor-5.0.0-debug-apk**. Download and extract the ZIP, then install `Device-Doctor-5.0.0-debug.apk`. The ZIP includes a SHA-256 checksum.

Pull requests also build APKs and run verification. Prefer a run where both Android device-test jobs passed. Artifacts expire after 30 days; the workflow can be run again from Actions.

This is a debug test build. GitHub's fresh runners create debug signing keys; an APK signed by a different key cannot replace an existing installation. If Android reports a signature/package conflict, export any reports you need before removing the old app. A production distribution needs a stable private signing key. No signing key is included in this repository.

## Compatibility and saved data

- Android 6.0 / API 23 and later; targets API 35.
- The original v4 database name and version-1 table schema are retained. Compatible in-place app updates preserve saved scans.
- Older Java activity implementations are archived in `docs/legacy/java/` and are not compiled into the app.
- Sensor and thermal capabilities vary by phone. Hardware tools require you to observe the result; missing hardware is not reported as a failed test.

## Privacy and permissions

No account, advertising, analytics, background scanner, or automatic file deletion. Diagnostic scans run locally. Up to 50 reports are stored in the app's private database. Android cloud backup is disabled. Reports contain model, Android version, patch date, and diagnostic readings; inspect them before sharing.

The optional connection test requests `https://www.google.com/generate_204` and `https://cp.cloudflare.com/generate_204`. Those services see ordinary connection metadata such as the public IP, but no device report is uploaded. Each request uses the captured active network, including its DNS/proxy/VPN route; switching networks invalidates the overall conclusion. An 8-second per-endpoint limit also bounds UI waiting during a stuck DNS lookup. Two fixed worker threads bound legacy resolver resource use.

Permissions are limited to `ACCESS_NETWORK_STATE`, `INTERNET`, and `VIBRATE`. No location, all-files access, contacts, camera, microphone, or accessibility-service permission. The app cannot read other apps' private files, remove their caches, inspect all malware, or repair hardware.

## Build and verify

Use JDK 17, Gradle **8.9**, Android SDK platform **35**, and build tools **34.0.0**. These match Android Gradle Plugin 8.7.3. Gradle is provisioned at its pinned version in Actions; locally install Gradle 8.9 or import the project in Android Studio with that version.

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
# With an Android emulator or test device connected:
gradle :app:connectedDebugAndroidTest
```

CI builds the app, runs unit tests and lint, then exercises it on API 23 and API 35 emulators. It uploads test reports and screenshots. Tests cover unavailable and invalid readings, cold temperatures, exact thresholds, misleading network responses, history retention, v4 schema compatibility, navigation, touch/display tools, activity recreation, duplicate scans, and history clearing.

See [development notes](docs/V5_REBUILD.md) for architecture and validation limits. MIT licensed.
