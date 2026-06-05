# Device Doctor v3.1 Cleanup

This release cleans up the architecture after the fast v1-v3 feature sprint.

## What changed

- `MainActivity` now launches `CleanDeviceDoctorActivity` directly.
- The old inheritance chain remains in the repository as fallback/history, but it is no longer the active launcher path.
- The active app flow is contained in one clean implementation instead of stacking versioned activity subclasses.
- Duplicate Network Doctor buttons were removed. One Network Doctor card remains.
- The app version is bumped to `3.1`.
- MIT license added.

## Still intentionally simple

This is still a Java/no-Compose app to keep the GitHub Actions APK build simple and phone-installable. A future rewrite could move to Kotlin, Compose, MVVM, Room, and WorkManager, but that is a larger migration and should not be mixed with this cleanup release.

## Known technical debt

- Old versioned activity classes still exist and can be removed later after v3.1 is verified.
- History still uses SharedPreferences. It works for 10 scans, but Room would be cleaner for long-term history.
- Network Doctor targets are still hardcoded.
- The APK is still distributed as a debug artifact rather than a signed GitHub Release.
