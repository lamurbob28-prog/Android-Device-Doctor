# Version 5 rebuild

The active launcher retains `.v4.V4MainActivity` and the v4 database path for compatibility. The user-facing app version is 5.0.0 / versionCode 11.

## Responsibilities

- `Models.kt`: immutable readings, findings, reports, and the unchanged Room entity.
- `DiagnosticRules.kt`: deterministic diagnostic decisions. Dates are evaluated against the snapshot timestamp. Unknowns never receive a passing finding; one critical finding always produces an attention status.
- `DiagnosticsEngine.kt`: individually guarded Android reads, report assembly, and comparisons.
- `DoctorViewModel.kt`: lifecycle-owned work, off-main-thread snapshots, scan deduplication, serialized database operations, report export, and cancellation.
- `ScanDao.kt`: atomic save plus retention of 50 entries; timestamp ties resolve by ID.
- `NetworkDoctor.kt`: two HTTPS checks on the captured network.
- `HttpsProbe.kt`: strict responses, bounded worker count and queue, timeout/cancellation, and resource cleanup; tested with fake connections.
- `NetworkAssessment.kt`: pure response/summary decisions.
- `V4MainActivity.kt`, `ToolsScreen.kt`, `HardwareTests.kt`: Compose UI, settings handoffs, and user-driven tests. Sensor listeners and audio stop on lifecycle pause/disposal.

The missing `android.useAndroidX=true` build setting is supplied. Legacy Java versions are archived outside the active source set, so only the current implementation is packaged. The standard Gradle wrapper pins version 8.9 and its published SHA-256 distribution checksum. Tool versions remain aligned instead of mixing an unrelated compiler/framework upgrade into the remake.

## Verification scope

Unit tests exercise diagnostic and connection decisions. Android instrumentation tests exercise a real Room database, the original version-1 table shape, UI navigation, report retention across recreation, repeated scan requests, reset confirmation, and manual display/touch interaction. Emulators provide API compatibility and UI behavior evidence; they cannot validate physical speaker quality, vibration strength, sensor calibration, real battery readings, or manufacturer-specific settings destinations. Device tests deliberately do not rely on external endpoint availability.

No destructive Room migration or data fallback is used. A history read/save error remains visible without deleting old data. Clear history is a deliberate confirmed action and does not trigger an immediate replacement scan. The next launch or explicit scan starts a new baseline.

Network results are endpoint-specific. HTTP redirects and unexpected content are inconclusive; one blocked endpoint does not establish a total outage. HTTPS uses platform certificate validation and disables redirect following. Stock platform DNS can ignore thread interruption on older versions, so timeouts cancel waiting work and the fixed pool prevents unbounded thread creation.

API references used:
- https://developer.android.com/build/releases/agp-8-7-0-release-notes
- https://developer.android.com/reference/android/os/BatteryManager
- https://developer.android.com/reference/android/net/NetworkCapabilities
- https://developer.android.com/reference/android/net/Network

Gradle wrapper scripts and JAR are from the official Gradle v8.9.0 source tree and retain their Apache 2.0 notices. Distribution checksum reference: https://gradle.org/release-checksums/
