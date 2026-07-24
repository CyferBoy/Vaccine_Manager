# Implementation Plan - Automatic Follow-up Scheduling

Enhance the "Schedule Follow-up" flow in `AddVaccinationScreen` to automatically use the `nextDueDate` from the recorded vaccination.

## Proposed Changes

### 1. ViewModel Layer

#### [MODIFY] [AddVaccinationViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/vaccination/AddVaccinationViewModel.kt)
- Add `scheduleFollowUp()` method that takes `Vaccination` as input.
- Extract necessary fields (patientId, visitId, nxtVaccineNames, nextDueDate) and call `reminderRepository.scheduleFollowUp()`.
- Default `notes` to "Scheduled automatically", `priority` to "NORMAL", and `reminderEnabled` to `true`.

### 2. UI Layer

#### [MODIFY] [AddVaccinationScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/clinic/neochild/features/vaccination/AddVaccinationScreen.kt)
- Update the "Schedule Follow-up" button click listener in the `showFollowUpDialog`.
- Call `viewModel.scheduleFollowUp(saved)` followed by `onBack()`.
- Update the `onScheduleFollowUp` lambda in the screen signature if necessary, but calling ViewModel directly is cleaner for "automatic" behavior.

## Verification Plan

### Automated Tests
- Run Gradle build to ensure compilation.

### Manual Verification
- Record a vaccination with a `Next Due Date`.
- Tap "Schedule Follow-up" in the success popup.
- Verify that a follow-up entry appears in the `Due` list or `Patient Details` follow-up section with the correct date.
- Verify that the `source` is `CLINIC` (default behavior).
