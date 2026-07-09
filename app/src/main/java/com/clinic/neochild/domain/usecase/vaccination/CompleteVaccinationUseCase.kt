package com.clinic.neochild.domain.usecase.vaccination

import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class CompleteVaccinationUseCase @Inject constructor(
    private val vaccinationRepository: VaccinationRepository,
    private val firestore: FirebaseFirestore // Using firestore directly for batch operations during migration
) {
    suspend operator fun invoke(vaccination: Vaccination, isNew: Boolean, selectedVaccineIds: List<String>) {
        // 1. Save the vaccination
        vaccinationRepository.addVaccination(vaccination)
        
        if (isNew) {
            // 2. Orchestrate side effects via Firestore Batch (Temporary until Repositories handle this)
            val batch = firestore.batch()
            
            // Update stock
            selectedVaccineIds.forEach { id ->
                val ref = firestore.collection("inventory").document(id)
                // Note: This needs the current stock. In a better architecture, 
                // the VaccineRepository would have a decrementStock method.
            }
            
            // Mark older records as done
            val previousRecords = firestore.collection("vaccinations")
                .whereEqualTo("patientId", vaccination.patientId)
                .whereEqualTo("isDone", false)
                .get()
                .await()
            
            previousRecords.documents.forEach { doc ->
                if (doc.id != vaccination.id) {
                    batch.update(doc.reference, "isDone", true)
                }
            }
            
            batch.commit().await()
        }
    }
}
