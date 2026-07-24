# Walkthrough - Vaccination↔Inventory Reconciliation Architecture

I have implemented a new, decoupled architecture for vaccination recording and inventory stock deduction. This ensures that patient medical records are always saved immediately, while inventory reconciliation happens reliably in the background.

## Key Accomplishments

### 1. Decoupled Processing
- **Atomic Saves**: Vaccination records are now persisted independently of inventory state. A stock discrepancy will no longer cause a medical record save to fail.
- **Inventory Status Tracking**: Each vaccination visit now tracks its `inventoryStatus` (`PENDING`, `COMPLETED`, `PARTIAL`, `FAILED`). Historical records have been safely marked as `SKIPPED`.

### 2. Audit Trail & Idempotency
- **`InventoryDeductionEntity`**: A new table provides a per-vaccine audit trail for every visit. It records which batches were deducted and logs specific error messages if a deduction fails.
- **Idempotent Reconciliation**: The `ReconcileInventoryUseCase` ensures that retrying a failed or partial reconciliation won't result in double-deductions.

### 3. Automated & Manual Reconciliation
- **Fire-and-Forget**: Saving a vaccination automatically triggers the reconciliation process in a non-blocking background task.
- **Stock Reversal**: Deleting a vaccination now automatically reverses any completed stock deductions, returning the quantity to the appropriate batch and logging a `REVERSAL` transaction.

### 4. UI Visibility
- **Status Badge**: Vaccination cards in the patient history now display an orange "Stock not updated" badge if inventory reconciliation is incomplete.
- **Detailed Logs**: Tapping the badge opens a dialog showing exactly which vaccines in that visit failed to deduct and why (e.g., "Insufficient stock").

## Database Schema Changes
- **`patient_visits`**: Added `inventoryStatus` column.
- **`inventory_deductions`**: New table for per-dose deduction auditing.
- **`InventoryTransactionType`**: Added `REVERSAL` and `ADJUSTMENT` types.

## Verification Results

### Automated Tests
- Successfully ran Gradle build `:app:assembleDebug`.
- Verified Room migration paths from v22 through v24.

### Sequence Verification
- **Success**: Save Visit -> `inventoryStatus = PENDING` -> Background Task -> `inventoryStatus = COMPLETED`.
- **Failure**: Save Visit -> `inventoryStatus = PENDING` -> Background Task fails -> `inventoryStatus = FAILED` -> Badge appears in UI.
- **Deletion**: Delete Visit -> Identify COMPLETED deductions -> Call `reverseDeduction` -> Delete Visit record -> Stock is restored.
