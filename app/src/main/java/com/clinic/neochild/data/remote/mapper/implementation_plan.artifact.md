# Implementation Plan - Robust & Backward Compatible Firestore Sync

Address critical sync issues by making the Firestore mapper backward compatible, improving error visibility, and adding sync statistics/validation.

## User Review Required

> [!IMPORTANT]
> - **Schema Migration**: Historical documents using single-string fields (e.g., `batchNumber`) will be automatically converted to list-based fields (e.g., `batchNumbers`) during sync.
> - **Data Integrity**: Documents failing basic validation (e.g., missing Patient ID) will be logged as errors instead of silently ignored, allowing for better debugging.

## Proposed Changes

### 1. Firestore Mapper Enhancements

#### [MODIFY] [FirestoreMappers.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/remote/mapper/FirestoreMappers.kt)
- Implement a set of safe retrieval helpers:
    - `safeGetList(doc, field, fallbackField)`: Handles `List`, comma-separated `String`, or fallback to a single-value legacy field.
    - `safeGetDouble(doc, field)`: Handles `Number` types safely.
    - `safeGetLong(doc, field)`: Handles `Number` types safely.
    - `safeGetBoolean(doc, field, fallbackField)`: Handles multiple field names for the same logic.
- Update `toVaccination`:
    - Map legacy `batchNumber` -> `batchNumbers`.
    - Map legacy `expiryDate` -> `expiryDates`.
    - Map legacy `nextVaccineName` -> `nxtVaccineNames`.
    - Ensure `inventoryStatus` defaults to `"SKIPPED"` if missing in remote (assuming old records).
- Add `Log.e` to all `catch` blocks with document ID and field details.

### 2. Repository & Sync Logic

#### [MODIFY] [VaccinationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/VaccinationRepositoryImpl.kt)
- Update `refreshVaccinations`:
    - Track counts: `totalDownloaded`, `imported`, `failedValidation`, `failedMapping`.
    - Implement a `validateVaccination(Vaccination)` check before insertion:
        - `id` must not be blank.
        - `patientId` must not be blank.
        - `dateGiven` should be present.
    - Log a summary at the end: `"Sync Summary: Total: 120, Imported: 118, Failed: 2 (reasons in logs)"`.

### 3. Domain Model Update (Optional but recommended)

#### [MODIFY] [Vaccination.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/model/Vaccination.kt)
- Add a `@Keep` annotation if missing or ensure Proguard won't strip fields used by Firestore (though we are using manual mapping, so it's safer).

## Verification Plan

### Automated Tests
- Run Gradle build: `./gradlew :app:assembleDebug`.

### Manual Verification
1. Manually edit a document in Firestore to use legacy fields (`done: true`, `batchNumber: "B1"`).
2. Trigger "Force Refresh" from the Sync screen.
3. Verify the record appears in the app with the correct data (converted to list format).
4. Check Logcat for "Sync Summary" and any detailed error logs for malformed documents.
