#!/usr/bin/env bash
set -u
chmod +x gradlew
adb shell am force-stop com.google.android.apps.nexuslauncher
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace
test_result=$?
adb pull /sdcard/Android/data/com.weathercard.app/files/screenshots screenshots
screenshots_result=$?
if [ "$test_result" -ne 0 ]; then
  exit "$test_result"
fi
exit "$screenshots_result"
