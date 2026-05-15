# Hop Print — Sunmi V2s

A minimal Android app for the Sunmi V2s POS that prints receipts from text input using the built-in thermal printer.

## Features

- Single text input screen with a print button
- Connects to the Sunmi built-in printer via AIDL service
- Printer status indicator (connected / disconnected)

## Build

```bash
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

## Setup

1. Generate a signing keystore:
   ```bash
   keytool -genkeypair -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hopprint -storepass hopprint123 -keypass hopprint123
   ```
2. Build the APK and sideload it onto the Sunmi V2s.

## Tech

- Kotlin, Android SDK 34, minSdk 24
- Sunmi AIDL printer service (`woyou.aidlservice.jiuiv5`)
