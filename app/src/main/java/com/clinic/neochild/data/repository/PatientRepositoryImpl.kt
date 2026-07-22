package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.dao.PatientDao
import com.clinic.neochild.data.local.dao.AuditLogDao
import com.clinic.neochild.data.local.dao.PatientNotesDao
import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.entity.AuditLogEntity
import com.clinic.neochild.data.local.entity.PatientNotesEntity
import com.clinic.neochild.data.local.entity.toPatient
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.PatientRepository
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val patientDao: PatientDao,
    private val vaccinationDao: VaccinationDao,
    private val auditLogDao: AuditLogDao,
    private val notesDao: PatientNotesDao,
    private val firestore: FirebaseFirestore,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) : PatientRepository {

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
            } catch (_: Exception) {}
        }
    }

    override suspend fun addPatient(patient: Patient) {
        database.withTransaction {
            // Business Rule: patientClinicId must be unique
            if (patient.patientClinicId.isNotBlank()) {
                val existing = patientDao.getPatientByClinicId(patient.patientClinicId)
                if (existing != null && existing.id != patient.id) {
                    throw IllegalStateException("A patient with Clinic ID ${patient.patientClinicId} already exists.")
                }
            }

            val entity = patient.toEntity(isSynced = false)
            patientDao.insertPatient(entity)
            
            syncRepository.enqueue(
                entityName = "PATIENT",
                entityId = patient.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH
            )

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "PATIENT",
                entityId = patient.id,
                action = "CREATED",
                patientId = patient.id,
                remarks = "Patient ${patient.name} registered"
            )
        }
    }

    override suspend fun deletePatient(id: String) {
        database.withTransaction {
            patientDao.deletePatient(id)
            syncRepository.enqueue("PATIENT", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
            
            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "PATIENT",
                entityId = id,
                action = "DELETED",
                patientId = id
            )
        }
    }

    override fun searchPatients(query: String): Flow<List<Patient>> =
        patientDao.searchPatients(query).map { list -> list.map { it.toPatient() } }

    override fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()

    override suspend fun getTotalPatientCount(): Int = patientDao.getTotalPatientCount()

    override fun getPatientTimeline(patientId: String): Flow<List<AuditLogEntity>> {
        return auditLogDao.getLogsForPatient(patientId)
    }

    override fun getPatientHistory(patientId: String): Flow<List<Vaccination>> {
        return vaccinationDao.getVaccinationsForPatient(patientId).map { list ->
            list.map { it.toVaccination() }
        }
    }

    override fun getNotes(patientId: String): Flow<List<PatientNotesEntity>> {
        return notesDao.getNotesForPatient(patientId)
    }

    override suspend fun addNote(patientId: String, content: String, author: String) {
        val note = PatientNotesEntity(
            patientId = patientId,
            content = content,
            author = author
        )
        val id = notesDao.insertNote(note)
        syncRepository.enqueue("PATIENT_NOTE", id.toString(), SyncOperation.CREATE, SyncPriority.LOW)
    }

    override suspend fun deleteNote(noteId: Long) {
        notesDao.deleteNote(noteId)
        syncRepository.enqueue("PATIENT_NOTE", noteId.toString(), SyncOperation.DELETE, SyncPriority.LOW)
    }
}
