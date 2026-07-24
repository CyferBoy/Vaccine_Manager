# Implementation Plan - Domain-Driven Vaccination Workflow Refactor

Refactor the vaccination workflow to separate Clinical operations from Inventory operations, ensuring medical records are never lost due to inventory issues.

## User Review Required

> [!IMPORTANT]
> - **Decoupling**: I am splitting `VaccinationManager` into `ClinicalVaccinationService` and `InventoryProcessingService`.
> - **New Fields**: `VisitEntity` (Vaccination) will now track `inventoryStatus` (PENDING, COMPLETED, FAILED).
> - **Reconciliation**: A new "Inventory Issues" screen will be provided to resolve stock discrepancies manually.
> - **Firestore**: A new `inventory_transactions` collection will be added to mirror the Room changes.

## Proposed Changes

### 1. Domain Models & Database (Clinical & Inventory)

#### [MODIFY] [InventoryEnums.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/model/InventoryEnums.kt)
- Add `ADJUSTMENT` to `InventoryTransactionType`.
- Add `InventoryStatus` enum: `PENDING`, `COMPLETED`, `FAILED`.

#### [MODIFY] [VaccinationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/entity/VaccinationEntity.kt)
- Add `inventoryStatus: String` to `VisitEntity` with default `"COMPLETED"`.

#### [MODIFY] [VaccineEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/entity/VaccineEntity.kt)
- Update `InventoryTransactionEntity` to include:
    - `status: String` (PENDING, COMPLETED, FAILED)
    - `failureReason: String?`
    - `createdAt: Long`
    - `processedAt: Long?`
    - `processedBy: String?`
    - `syncStatus: String`

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/database/AppDatabase.kt)
- Increment database version and add migration logic to add new columns.

---

### 2. Service Layer (New Services)

#### [NEW] [ClinicalVaccinationService.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/service/ClinicalVaccinationService.kt)
- Responsible for:
    - Saving the `VisitEntity`.
    - Updating `ReminderEntity`.
    - Recording the medical audit log.
- Executes in its own Room transaction.

#### [NEW] [InventoryProcessingService.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/service/InventoryProcessingService.kt)
- Responsible for:
    - Finding appropriate batches.
    - Deducting stock.
    - Creating `InventoryTransactionEntity`.
    - Updating `VisitEntity.inventoryStatus`.
- Handles exceptions gracefully to avoid rolling back clinical data.

---

### 3. Business Logic & Orchestration

#### [MODIFY] [VaccinationManager.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/manager/VaccinationManager.kt)
- Refactor to use the new Services.
- Change `completeVaccination` to return a `String?` (warning message).

#### [NEW] [BackfillInventoryUsageUseCase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/usecase/inventory/BackfillInventoryUsageUseCase.kt)
- Implement historical usage reconciliation.

---

### 4. UI Layer

#### [NEW] [InventoryIssuesScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/InventoryIssuesScreen.kt)
- Display `VisitEntity` records with `inventoryStatus == PENDING` or `FAILED`.
- Provide "Resolve" flow to manually pick a batch and retry deduction.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/settings/SettingsScreen.kt)
- Add "Backfill Inventory" and "Manage Inventory Issues" buttons.

---

### 5. Data Synchronization

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/SyncRepositoryImpl.kt)
- Add support for syncing the new `inventory_transactions` collection.

## Verification Plan

### Sequence Verification
- **Successful Path**: Clinical Save -> Inventory Success -> Status COMPLETED.
- **Stock Failure Path**: Clinical Save -> Inventory Error -> Status PENDING -> UI Warning.
- **Reconciliation Path**: Inventory Issues -> Pick Batch -> Resolve -> Status COMPLETED.

### Automated Tests
- Run Gradle build: `./gradlew assembleDebug`.
- Verify database migrations.

### Manual Verification
1. Record a vaccination for a vaccine with NO stock.
2. Verify vaccination record exists in patient history.
3. Verify "Insufficient stock" warning appeared.
4. Navigate to "Inventory Issues" and verify the record is listed.
5. Add stock to inventory.
6. Resolve the issue from the new screen and verify stock deduction.
