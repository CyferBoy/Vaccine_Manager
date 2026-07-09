# NeoChild Clinic - Vaccine Manager (v1.1)

NeoChild Clinic is a modern Android application designed for pediatric clinics to manage patient records, vaccination schedules, and consultations efficiently.

## Recent Updates (v1.1)
- **Advanced Reminder Engine:** Implemented a "Requirement-Based Satisfaction" model. Reminders are now tracked per vaccine rather than per visit, ensuring catch-up vaccinations are never missed even if visits are out of order.
- **Automated Staff Notifications:** Integrated a battery-efficient WorkManager system that alerts staff about:
    - Vaccinations due today or tomorrow.
    - Overdue patients (with configurable frequency).
    - Low stock and out-of-stock inventory alerts.
    - Upcoming vaccine expiry warnings.
- **Notification Settings:** New settings screen for clinic staff to customize reminder times, stock thresholds, and overdue alert intervals.
- **Interactive Notifications:** Support for "Mark Vaccinated" and "Dismiss" actions directly from the notification shade.
- **Enhanced Security:** Database version 3 with optimized Room-Firestore sync.

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
