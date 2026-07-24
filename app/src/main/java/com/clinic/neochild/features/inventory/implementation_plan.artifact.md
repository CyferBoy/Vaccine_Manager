# Implementation Plan - Vaccination↔Inventory Reconciliation Architecture

Refactor the vaccination workflow to decouple clinical record saving from inventory stock deduction, ensuring data integrity and providing a robust reconciliation mechanism.

## User Review Required

> [!IMPORTANT]
> - **Migration**: Database version will be bumped to 24. Existing vaccinations will be marked as `SKIPPED` for inventory reconciliation to avoid double-deducting historical data.
> - **Asynchronous Processing**: Inventory deduction will now happen as a background task after the vaccination is saved. If it fails, the vaccination record is still preserved.

## Proposed Changes

### 1. Data Layer (Entities & Room)

#### [MODIFY] [VaccinationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/entity/VaccinationEntity.kt)
- Update `VisitEntity`: set default `inventoryStatus` to `"PENDING"`.
- Update `toVaccination` and `toEntity` mappers to include `inventoryStatus`.

#### [MODIFY] [Vaccination.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/model/Vaccination.kt)
- Add `inventoryStatus: String = "PENDING"`.

#### [NEW] [InventoryDeductionEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/entity/InventoryDeductionEntity.kt)
- Track per-vaccine deduction status for each visit.

#### [NEW] [InventoryDeductionDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/dao/InventoryDeductionDao.kt)
- Manage `InventoryDeductionEntity` records.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/database/AppDatabase.kt)
- Bump version to 24.
- Register new entity and DAO.
- Implement `MIGRATION_23_24`:
    - Ensure `inventoryStatus` exists in `patient_visits`.
    - Update all existing rows to `SKIPPED`.
    - Create `inventory_deductions` table.

### 2. DAOs & Repository Enhancements

#### [MODIFY] [VaccineDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/dao/VaccineDao.kt)
- Add `getBatchByVaccineAndNumber(vaccineId, batchNumber)`.

#### [MODIFY] [VaccinationDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/local/dao/VaccinationDao.kt)
- Add `getVaccinationsPendingReconciliation()`.

#### [MODIFY] [InventoryRepository.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/repository/InventoryRepository.kt)
- Add `reverseDeduction(batchId, quantity, user)`.

#### [MODIFY] [InventoryRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/InventoryRepositoryImpl.kt)
- Implement `reverseDeduction`.

### 3. Business Logic (Use Case)

#### [NEW] [ReconcileInventoryUseCase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/domain/usecase/inventory/ReconcileInventoryUseCase.kt)
- Core logic to process pending vaccinations.
- Idempotent: checks for existing `COMPLETED` deductions.
- Resolves batches using provided numbers or falls back to FEFO.

---

### 4. Integration & UI

#### [MODIFY] [AddVaccinationViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/vaccination/AddVaccinationViewModel.kt)
- Trigger `reconcileInventoryUseCase.execute()` in a fire-and-forget coroutine after successful save.

#### [MODIFY] [VaccinationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/data/repository/VaccinationRepositoryImpl.kt)
- Update `deleteVaccination` to perform reversal of deductions before deleting the visit record.

#### [MODIFY] [VaccinationCards.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/patient/VaccinationCards.kt)
- Display orange "Stock not updated" badge if status is `PARTIAL` or `FAILED`.
- Show deduction details in a dialog on tap.

## Verification Plan

### Automated Tests
- Run Gradle build: `./gradlew assembleDebug`.
- Verify database migration 23 -> 24.

### Manual Verification
1. Save a vaccination with valid stock -> Status becomes COMPLETED.
2. Save a vaccination with insufficient stock -> Record is saved, Status becomes FAILED, Badge appears in history.
3. Delete a COMPLETED vaccination -> Verify stock is returned to the batch.
4. Run Backfill utility -> Ensure consistency.
