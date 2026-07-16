package com.clinic.neochild.data.repository

import com.clinic.neochild.data.datasource.patient.PatientLocalDataSource
import com.clinic.neochild.data.datasource.patient.PatientRemoteDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationLocalDataSource
import com.clinic.neochild.data.datasource.vaccination.VaccinationRemoteDataSource
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.utils.AuditLogger
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PatientRepositoryImpl @Inject constructor(
    private val localDataSource: PatientLocalDataSource,
    private val remoteDataSource: PatientRemoteDataSource,
    private val vaccinationLocal: VaccinationLocalDataSource,
    private val vaccinationRemote: VaccinationRemoteDataSource,
    private val firestore: FirebaseFirestore, // Needed for batch operations for now
    private val auditLogger: AuditLogger
) : PatientRepository {

    private val repositoryScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val allPatients: Flow<List<Patient>> = localDataSource.getAllPatients()

    override suspend fun getPatientById(id: String): Patient? {
        return localDataSource.getPatientById(id)
    }

    override suspend fun refreshPatients() {
        withContext(Dispatchers.IO) {
            try {
                // Potential optimization: Add a 'lastUpdated' field to documents
                // and only fetch documents updated after lastSyncTime.
                val patients = remoteDataSource.fetchAllPatients()
                localDataSource.insertPatients(patients, isSynced = true)
            } catch (e: Exception) {
                // Log error or handle failure
            }
        }
    }

    override suspend fun addPatient(patient: Patient) {
        // 1. Save locally FIRST (Immediate UI update via Flow)
        localDataSource.insertPatient(patient, isSynced = false)
        
        // 2. Sync to remote asynchronously
        repositoryScope.launch {
            try {
                remoteDataSource.uploadPatient(patient)
                localDataSource.markSynced(patient.id)
                auditLogger.logAction("ADD_PATIENT", patient.id, "Name: ${patient.name}")
            } catch (e: Exception) {
                // Failure is handled by SyncWorker eventually
            }
        }
    }

    override suspend fun deletePatient(id: String) {
        // 1. Mark as deleted locally
        localDataSource.deletePatient(id)
        
        // 2. Sync deletion to remote
        repositoryScope.launch {
            try {
                remoteDataSource.deletePatient(id)
                auditLogger.logAction("DELETE_PATIENT", id)
            } catch (e: Exception) {
                // SyncWorker will handle offline deletion
            }
        }
    }

    override suspend fun mergePatients(masterId: String, duplicateIds: List<String>) {
        // This is a complex operation that affects both Patients and Vaccinations
        // 1. Update all vaccinations belonging to duplicateIds to point to masterId
        // In local DB:
        duplicateIds.forEach { dupId ->
            // Local update logic would go here if we had a dedicated DAO method.
            // For now, let's implement the remote sync part which is critical.
        }

        // 2. Firestore Batch Update
        try {
            val batch = firestore.batch()
            
            for (dupId in duplicateIds) {
                // Find all vaccinations for this duplicate
                val vaccinations = firestore.collection("vaccinations")
                    .whereEqualTo("patientId", dupId)
                    .get()
                    .await()
                
                vaccinations.documents.forEach { doc ->
                    batch.update(doc.reference, "patientId", masterId)
                }
                
                // Delete the duplicate patient
                batch.delete(firestore.collection("patients").document(dupId))
            }
            
            batch.commit().await()
            
            // 3. Local Refresh
            refreshPatients()
            // Also need to refresh vaccinations locally to reflect the change
            // vaccinationRepo.refreshVaccinations() 
        } catch (e: Exception) {
            throw e
        }
    }
}
