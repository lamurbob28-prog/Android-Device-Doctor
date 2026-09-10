#!/usr/bin/env bash
# Keep one shell so artifact collection cannot erase the actual test exit status.
set -uo pipefail
doctor_test_status=0
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace || doctor_test_status=$?
if ! adb pull /sdcard/Download/device-doctor-previews app/build/device-screenshots; then
    if [ "$doctor_test_status" -eq 0 ]; then
        doctor_test_status=1
    fi
fi
exit "$doctor_test_status"
