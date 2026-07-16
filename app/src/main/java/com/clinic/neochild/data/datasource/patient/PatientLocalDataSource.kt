package com.clinic.neochild.data.datasource.patient

import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.domain.model.Patient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Interface for Local Patient Data operations.
 * Decouples the Repository from Room-specific DAO implementation.
 */
interface PatientLocalDataSource {
    fun getAllPatients(): Flow<List<Patient>>
    suspend fun getPatientById(id: String): Patient?
    suspend fun insertPatient(patient: Patient, isSynced: Boolean)
    suspend fun insertPatients(patients: List<Patient>, isSynced: Boolean)
    suspend fun deletePatient(id: String)
    suspend fun getUnsyncedPatients(): List<Patient>
    suspend fun markSynced(id: String)
}

class PatientLocalDataSourceImpl(private val patientDao: PatientDao) : PatientLocalDataSource {
    override fun getAllPatients(): Flow<List<Patient>> = 
        patientDao.getAllPatients().map { entities -> entities.map { it.toPatient() } }

    override suspend fun getPatientById(id: String): Patient? = 
        patientDao.getPatientById(id)?.toPatient()

    override suspend fun insertPatient(patient: Patient, isSynced: Boolean) = 
        patientDao.insertPatient(patient.toEntity(isSynced))

    override suspend fun insertPatients(patients: List<Patient>, isSynced: Boolean) = 
        patientDao.insertPatients(patients.map { it.toEntity(isSynced) })

    override suspend fun deletePatient(id: String) = 
        patientDao.deletePatient(id)

    override suspend fun getUnsyncedPatients(): List<Patient> = 
        patientDao.getUnsyncedPatients().map { it.toPatient() }

    override suspend fun markSynced(id: String) = 
        patientDao.markSynced(id)
}
