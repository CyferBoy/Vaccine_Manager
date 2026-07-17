package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PatientRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : PatientRepository {

    private val patientDao = database.patientDao()

    override val allPatients: Flow<List<Patient>> = 
        patientDao.getAllPatients().map { list -> list.map { it.toPatient() } }

    override suspend fun getPatientById(id: String): Patient? {
        return patientDao.getPatientById(id)?.toPatient()
    }

    override suspend fun refreshPatients() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("patients").get().await()
                val patients = snapshot.documents.mapNotNull { FirestoreMappers.toPatient(it) }
                patientDao.insertPatients(patients.map { it.toEntity() })
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    override suspend fun addPatient(patient: Patient) {
        // 1. Save locally FIRST
        patientDao.insertPatient(patient.toEntity())
        
        // 2. Queue for background sync
        syncRepository.enqueue(
            entityName = "PATIENT",
            entityId = patient.id,
            operation = SyncOperation.CREATE,
            priority = SyncPriority.HIGH
        )
        
        auditLogger.logAction("ADD_PATIENT", patient.id, "Name: ${patient.name}")
    }

    override suspend fun deletePatient(id: String) {
        // 1. Mark as deleted locally
        patientDao.deletePatient(id)
        
        // 2. Queue for background sync
        syncRepository.enqueue(
            entityName = "PATIENT",
            entityId = id,
            operation = SyncOperation.DELETE,
            priority = SyncPriority.MEDIUM
        )
        
        auditLogger.logAction("DELETE_PATIENT", id)
    }

    override fun searchPatients(query: String): Flow<List<Patient>> {
        return patientDao.searchPatients("%$query%").map { list -> list.map { it.toPatient() } }
    }

    override fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()
}

