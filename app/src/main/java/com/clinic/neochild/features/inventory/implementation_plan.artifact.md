# Implementation Plan - Vaccination Collection Name Alignment

The user reported that vaccination history stored under the `vaccinations` collection is not visible, and new vaccinations are being saved under `visits`. The goal is to consolidate all vaccination records into the `vaccinations` collection and ensure consistency across the codebase.

## User Review Required

> [!IMPORTANT]
> I will rename all Firestore collection references from `visits` to `vaccinations`. This change will only affect remote synchronization and data fetching; local Room tables (like `patient_visits`) will remain unchanged to preserve local database integrity unless migration is specifically required.

## Proposed Changes

### 1. Data Layer (Repository & Sync)

#### [MODIFY] [VaccinationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/VaccinationRepositoryImpl.kt)
- Update `refreshVaccinations()`: change `firestore.collection("visits")` to `firestore.collection("vaccinations")`.
- Update `auditLogger` calls: change `entityType = "VISIT"` to `entityType = "VACCINATION"`.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/SyncRepositoryImpl.kt)
- Update `uploadEntity` mapping: change `"VISIT", "VACCINATION" -> "visits"` to `"VISIT", "VACCINATION" -> "vaccinations"`.

#### [MODIFY] [PatientRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/PatientRepositoryImpl.kt)
- Update `auditLogger` calls where `entityType = "VISIT"` is used (if any).

### 2. Search & Audit

#### [VERIFY]
- Project-wide search for `"visits"` and `"VISIT"` to ensure no other Firestore-related strings are left behind.

## Verification Plan

### Automated Tests
- Run Gradle build to ensure compilation.

### Manual Verification
- **Refresh Data**: Trigger a manual sync/refresh and verify that records from the `vaccinations` collection in Firestore are pulled into the local database.
- **Add Vaccination**: Save a new vaccination and verify that it is uploaded to the `vaccinations` collection in Firestore.
- **Audit Logs**: Verify that audit logs correctly reference the vaccination events.
