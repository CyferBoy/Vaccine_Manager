# Walkthrough - Out-of-Stock Vaccine Selection

I have updated the vaccination recording flow to allow the selection of vaccines even when they are out of stock. This ensures medical records can always be saved, while the inventory system tracks the deduction as an "Inventory Issue" to be resolved later.

## Changes Made

### 1. Expanded Inventory Visibility
- **`AddVaccinationViewModel.kt`**: Changed the inventory data stream to use `InventoryFilter.ALL` instead of `InventoryFilter.AVAILABLE`. This makes all vaccines in your catalog visible during the search, regardless of current stock levels.

### 2. Flexible Selection Logic
- **`AddVaccinationViewModel.kt`**: Updated the selection logic to handle cases where no active batches exist. The app will now proceed with the vaccination recording even without a resolved batch ID.

### 3. UI Accessibility
- **`VaccinationSections.kt`**: Enabled the selection of "Out of Stock" items in the vaccine search dropdown. These items remain visually distinct (red text with "Out of Stock" label) to maintain staff awareness, but they are no longer blocked from selection.

## Verification Results

### Automated Tests
- Successfully ran Gradle build `:app:assembleDebug`.

### Manual Verification
- Verified that vaccines with zero stock now appear in the "Select Vaccine" dropdown.
- Confirmed that selecting an out-of-stock vaccine correctly adds it to the list of vaccines to be administered.
- Verified that saving a vaccination with an out-of-stock vaccine persists the record and correctly triggers the new "Inventory Issue" status (visible as a badge in history).
