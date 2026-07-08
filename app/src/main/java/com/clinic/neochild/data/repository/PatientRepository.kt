package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.model.Patient
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PatientRepository(
    private val patientDao: PatientDao,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allPatients: Flow<List<Patient>> = patientDao.getAllPatients().map { entities ->
        entities.map { it.toPatient() }
    }

    suspend fun refreshPatients() {
        try {
            val result = db.collection("patients").get().await()
            val patients = result.documents.mapNotNull { FirestoreMappers.toPatient(it) }
            patientDao.insertPatients(patients.map { it.toEntity(isSynced = true) })
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun addPatient(patient: Patient) {
        // 1. Save locally FIRST - This triggers Room Flow immediately
        patientDao.insertPatient(patient.toEntity(isSynced = false))
        
        // 2. Launch sync ASYNCHRONOUSLY - Do not await it
        repositoryScope.launch {
            try {
                db.collection("patients").document(patient.id).set(patient).await()
                patientDao.markSynced(patient.id)
            } catch (e: Exception) {
                // Offline or Firestore error - Handled by SyncWorker later
            }
        }
    }

    suspend fun deletePatient(id: String) {
        patientDao.deletePatient(id)
        try {
            db.collection("patients").document(id).delete().await()
            // If successful, we could remove it from local db entirely, 
            // but for now, we just leave it marked as deleted.
        } catch (e: Exception) {
            // WorkManager will handle
        }
    }
}
