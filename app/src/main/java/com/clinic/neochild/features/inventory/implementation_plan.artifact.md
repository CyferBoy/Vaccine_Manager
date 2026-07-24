# Implementation Plan - Inventory Backfill Utility

Create a one-time manual inventory backfill utility to reconcile historical vaccine usage with current stock levels.

## User Review Required

> [!IMPORTANT]
> This utility will deduct stock from current active batches using FEFO logic based on ALL historical vaccination records found in the local database. This is a destructive, one-time operation intended to synchronize inventory levels with past activity.

## Proposed Changes

### 1. Domain Model

#### [MODIFY] [InventoryEnums.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/model/InventoryEnums.kt)
- Add `ADJUSTMENT` to `InventoryTransactionType` enum (if not already present).

### 2. Domain Layer (Use Case)

#### [NEW] [BackfillInventoryUsageUseCase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/usecase/inventory/BackfillInventoryUsageUseCase.kt)
- **Goal**: Reconcile total vaccine usage from history.
- **Logic**:
    - Fetch all vaccinations via `vaccinationRepository.allVaccinations`.
    - Flatten and count occurrences of every vaccine brand name used (case-insensitive, trimmed).
    - Match names to inventory vaccines using brand name fuzzy matching.
    - Call `inventoryRepository.deductStock` for each match with `InventoryTransactionType.ADJUSTMENT`.
    - Return a summary of successes and failures per vaccine.

### 3. Dependency Injection

#### [MODIFY] [UseCaseModule.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/di/UseCaseModule.kt)
- Register `BackfillInventoryUsageUseCase`.

### 4. ViewModel Layer

#### [MODIFY] [NotificationSettingsViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/settings/NotificationSettingsViewModel.kt)
- Inject `BackfillInventoryUsageUseCase` and `FirebaseAuth`.
- Expose `isAdmin` status.
- Add `runInventoryBackfill()` method to trigger the use case.
- Expose `backfillResults` and `isBackfilling` states for UI feedback.

### 5. UI Layer

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/settings/SettingsScreen.kt)
- Add an "Admin Maintenance" expandable section (visible only to admins).
- Add a "Backfill Inventory From History" button.
- Implement a confirmation dialog ("This cannot be undone").
- Implement a results dialog to display a scrollable list of the backfill outcomes.

## Verification Plan

### Automated Tests
- Run Gradle build: `./gradlew :app:assembleDebug`.

### Manual Verification
1. Log in as an administrator.
2. Go to **Settings**.
3. Expand **Maintenance (Admin)**.
4. Tap **Backfill Inventory From History**.
5. Confirm the dialog.
6. Verify that the summary correctly lists vaccines and usage counts.
7. Verify that the inventory stock has decreased by the corresponding amounts in the Inventory screen.
