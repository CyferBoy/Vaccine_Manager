# NeoChild Clinic - Vaccine Manager (v1.0)

NeoChild Clinic is a modern Android application designed for pediatric clinics to manage patient records, vaccination schedules, and consultations efficiently.

## Version 1.0 Info
- **Version Name:** 1.0
- **Version Code:** 1
- **Release Status:** Initial Release

## Features

- **Patient Management:** Easily add, edit, and view patient details.
- **Vaccination Tracking:** Manage vaccination history and automated due date calculation.
- **Consultations:** Record clinical notes and generate receipts.
- **Inventory Management:** Track vaccine stock, batches, and expiry dates.
- **Offline Support:** Local database (Room) with SQLCipher encryption.
- **Real-time Sync:** Powered by Firebase Firestore.
- **Notifications:** Integrated with Firebase Cloud Messaging (FCM).
- **Widgets:** Quick view of upcoming vaccinations on the home screen.

## Setup Instructions

To protect privacy and security, specific configuration files are not included in this repository. Follow these steps to set up the project:

### 1. Clone the Repository
```bash
git clone https://github.com/CyferBoy/Vaccine_Manager.git
```

### 2. Firebase Configuration
1. Create a new project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app to your Firebase project.
3. Download the `google-services.json` file.
4. Place the `google-services.json` file in the `app/` directory of the project.

### 3. Firebase Services Setup
Enable the following services in your Firebase console:
- **Authentication:** Email/Password provider.
- **Firestore Database:** Create a database in production or test mode.
- **Cloud Messaging:** To enable notifications.
- **App Check (Optional):** The app is configured with Play Integrity/Debug provider.

### 4. Build and Run
- Open the project in Android Studio.
- Sync Gradle files.
- Build and run the app on an emulator or a physical device.

## Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **Database:** Room with SQLCipher
- **Backend:** Firebase (Firestore, Auth, FCM)
- **Background Tasks:** WorkManager
- **Navigation:** Compose Navigation

## License
This project is for demonstration purposes. Please contact the developer for licensing information.
