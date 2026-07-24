# Implementation Plan - Enable Selection of Out-of-Stock Vaccines

The user needs to record vaccinations even for vaccines that have zero current stock. This aligns with the new decoupled architecture where medical records are persisted regardless of inventory status.

## Proposed Changes

### 1. ViewModel Layer

#### [MODIFY] [AddVaccinationViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/vaccination/AddVaccinationViewModel.kt)
- Update `observeInventory()` to use `InventoryFilter.ALL` instead of `InventoryFilter.AVAILABLE`. This ensures all vaccines in the catalog are visible.
- Update `onVaccineSelected()` to handle the case where `batches` is empty (out of stock).
- Update `addVaccineToForm()` to accept a nullable `VaccineBatchEntity`.

### 2. UI Layer

#### [MODIFY] [VaccinationSections.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/vaccination/VaccinationSections.kt)
- Update `VaccineSelectionSection`:
    - Set `enabled = true` in `DropdownMenuItem` for all items, including out-of-stock ones.
    - Retain the visual warnings ("Out of Stock" label, red text) for awareness.

## Verification Plan

### Automated Tests
- Run Gradle build: `./gradlew :app:assembleDebug`.

### Manual Verification
1. Navigate to "Add Vaccination".
2. Search for a vaccine that is currently out of stock.
3. Verify that the vaccine appears in the dropdown.
4. Verify that the vaccine can be selected.
5. Save the vaccination and verify it is recorded in the patient's history (with an "Inventory Issue" badge).
