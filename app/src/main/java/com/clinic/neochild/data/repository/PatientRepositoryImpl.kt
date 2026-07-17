package com.clinic.neochild.data.repository

import com.clinic.neochild.data.datasource.patient.PatientLocalDataSource
import com.clinic.neochild.data.datasource.patient.PatientRemoteDataSource
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.SyncOperation
import com.clinic.neochild.domain.model.SyncPriority
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.utils.AuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PatientRepositoryImpl @Inject constructor(
    private val localDataSource: PatientLocalDataSource,
    private val remoteDataSource: PatientRemoteDataSource,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : PatientRepository {

    override val allPatients: Flow<List<Patient>> = localDataSource.getAllPatients()

    override suspend fun getPatientById(id: String): Patient? {
        return localDataSource.getPatientById(id)
    }

    override suspend fun refreshPatients() {
        withContext(Dispatchers.IO) {
            try {
                // Potential optimization: Use lastUpdated timestamp to fetch only changes.
                val patients = remoteDataSource.fetchAllPatients()
                localDataSource.insertPatients(patients, isSynced = true)
            } catch (e: Exception) {
                // Error handling handled by callers or logging
            }
        }
    }

    override suspend fun addPatient(patient: Patient) {
        // 1. Save locally FIRST (Immediate UI update)
        localDataSource.insertPatient(patient, isSynced = false)
        
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
        localDataSource.deletePatient(id)
        
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
        return localDataSource.searchPatients(query)
    }

    override fun getPatientCount(): Flow<Int> = localDataSource.getPatientCount()
}
