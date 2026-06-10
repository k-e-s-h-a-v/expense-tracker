# Expense Tracker

Android app that reads debit SMS messages and tracks spending. Built with Kotlin and Jetpack Compose.


[📥 Download the Latest APK](https://github.com/k-e-s-h-a-v/expense-tracker/releases/latest/download/ExpenseTracker.apk)
## Commands

Build and install the debug app on a connected device.
```
./gradlew installDebug
```

Clean build artifacts, then reinstall debug with a full stack trace on failure.
```
./gradlew clean; ./gradlew installDebug --stacktrace
```

Build the debug APK without installing it.
```
./gradlew assembleDebug
```

Pair with a device over wireless debugging (use the IP and port shown on the phone).
```
adb pair <device-ip>:<pairing-port>
```

Connect to a paired device for wireless ADB.
```
adb connect <device-ip>:<connect-port>
```

Build the signed release APK.
```
./gradlew assembleRelease
```
