# Implementation Plan - Inventory Module Refactor (Two-Level Hierarchy)

Refactor the Inventory module to follow a strict Vaccine -> Vaccine Batches hierarchy while preserving existing Firestore data and relationships.

## User Review Required

> [!IMPORTANT]
> The current code uses a Firestore collection named `batches` for vaccine batches. However, your instructions explicitly state that the collection `vaccine_batches` must be used. I will update all code references (Sync, Refresh, Repository) to point to `vaccine_batches`. Please confirm if this matches your production environment.

## Proposed Changes

### 1. Navigation & Routes

#### [MODIFY] [Routes.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/app/Routes.kt)
- Add `ADD_VACCINE_DEFINITION = "add_vaccine_definition"`
- Add `ADD_BATCH = "add_batch/{vaccineId}/{brandName}"`

#### [MODIFY] [Navigation.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/app/Navigation.kt)
- Add composable for `ADD_VACCINE_DEFINITION` using `AddVaccineScreen`.
- Add composable for `ADD_BATCH` using `AddBatchScreen`.

### 2. Data Layer (Repository & Sync)

#### [MODIFY] [InventoryRepository.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/repository/InventoryRepository.kt)
- Add `addVaccine(vaccine: VaccineEntity, user: String)`
- Add `addBatch(batch: VaccineBatchEntity, user: String)`

#### [MODIFY] [InventoryRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/InventoryRepositoryImpl.kt)
- Implement `addVaccine`: Only saves to `vaccines` collection and Room `vaccines` table.
- Implement `addBatch`: Only saves to `vaccine_batches` collection and Room `vaccine_batches` table, linked via `vaccineId`.
- Update `refreshInventory` to pull from `vaccine_batches` instead of `batches`.
- Update all internal collection references to `vaccine_batches`.

#### [MODIFY] [VaccineRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/VaccineRepositoryImpl.kt)
- Update `refreshInventory` to pull from `vaccine_batches` instead of `batches`.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/SyncRepositoryImpl.kt)
- Update `uploadEntity` mapping for `BATCH` from `batches` to `vaccine_batches`.

### 3. ViewModel Layer

#### [NEW] [AddVaccineViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/AddVaccineViewModel.kt)
- Handle logic for creating a new `VaccineEntity`.

#### [NEW] [AddBatchViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/AddBatchViewModel.kt)
- Handle logic for creating a `VaccineBatchEntity` for a specific `vaccineId`.

### 4. UI Layer

#### [MODIFY] [VaccineInventoryScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/VaccineInventoryScreen.kt)
- Update FAB (+) to navigate to `ADD_VACCINE_DEFINITION`.
- Update `VaccineItemCard`:
    - Display expanded section with batches list.
    - Add "+ Add Batch" button inside the expanded section.
    - Remove batch-level info from the collapsed state.
- Update `onEditBatch` navigation to pass `vaccineId` if needed, or keep as is if `batchId` is sufficient.

#### [NEW] [AddVaccineScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/AddVaccineScreen.kt)
- Form for Vaccine level info only (Brand, Type, Company).

#### [NEW] [AddBatchScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/inventory/AddBatchScreen.kt)
- Form for Batch level info only (Batch Number, Qty, Expiry, MRP, Net Rate).

## Verification Plan

### Automated Tests
- Run Gradle build to ensure compilation.
- Verify Room DAOs and Repositories via manual inspection of logic.

### Manual Verification
- **Add Vaccine**: Verify document is created only in `vaccines` collection.
- **Add Batch**: Verify document is created only in `vaccine_batches` collection with correct `vaccineId`.
- **Inventory Screen**: Verify tiles expand to show batches and "+ Add Batch" button.
- **Calculations**: Verify Total Stock and Batch Count are correctly summed from active batches.
- **Sync**: Check if data propagates correctly to/from Firestore.
