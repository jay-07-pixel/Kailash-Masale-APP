# KAILASH MASALE 2

Android application for Kailash Masale field and sales operations.  
It includes login, dashboard analytics, attendance flows, profile management, and business utility screens.

## Overview

This project is built with Java + Android XML UI and uses Firebase services (Firestore and Storage).  
The app is designed for internal employee use.

## Core Features

- Domain-based login with password visibility toggle
- Dashboard with quick metrics and cards
- Pending tasks and notifications UI
- Check IN/OUT flow
- Working days, DA, TA, productivity, and performance pages
- Profile page with editable input fields
- Upload and planning related flows

## Tech Stack

- Language: Java
- Build system: Gradle (Kotlin DSL)
- Compile SDK: 36
- Target SDK: 36
- Min SDK: 23
- Package: `com.example.kailashmasale`

### Main Libraries

- AndroidX AppCompat `1.7.1`
- Material Components `1.13.0`
- AndroidX Activity `1.11.0`
- ConstraintLayout `2.2.1`
- CardView `1.0.0`
- DrawerLayout `1.2.0`
- Firebase BoM `34.9.0`
- Firebase Firestore
- Firebase Storage

## Project Structure

```text
app/
  src/main/
    java/com/example/kailashmasale/
      MainActivity.java
      DashboardActivity.java
      CheckInOutActivity.java
      PerformanceActivity.java
      ProfileActivity.java
      ...other feature activities
    res/
      layout/
      drawable/
      values/
    AndroidManifest.xml
```

## Setup

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 11+
- Android SDK 36

### Firebase Config (Important)

This repository does **not** include `app/google-services.json` (it contains API keys). After cloning, download your Firebase Android config from Firebase Console → Project settings → Your apps, save it as `app/google-services.json`, or copy `app/google-services.json.example` to `app/google-services.json` and replace the placeholders with values from Firebase.

Place your Firebase config file in:

`app/google-services.json`

If your file is currently named like `google-services (7).json`, rename it to exactly:

`google-services.json`

## Run Locally

1. Open the project in Android Studio
2. Let Gradle sync complete
3. Select device/emulator
4. Run app (`Shift + F10`)

## Build Commands

From project root:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
./gradlew connectedAndroidTest
```

On Windows PowerShell/CMD, you can use:

```bash
gradlew.bat assembleDebug
```

## APK Output

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## Notes

- This app is intended for internal organizational use.
- Keep API keys and service config files private.

## License

Proprietary software. All rights reserved.

