package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VaccinationRepository(
    private val vaccinationDao: VaccinationDao,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allVaccinations: Flow<List<Vaccination>> = vaccinationDao.getAllVaccinations().map { entities ->
        entities.map { it.toVaccination() }
    }

    suspend fun refreshVaccinations() {
        try {
            val result = db.collection("vaccinations").get().await()
            val vaccinations = result.documents.mapNotNull { FirestoreMappers.toVaccination(it) }
            vaccinationDao.insertVaccinations(vaccinations.map { it.toEntity(isSynced = true) })
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun addVaccination(vaccination: Vaccination) {
        // 1. Save locally FIRST - Triggers Room Flow immediately
        vaccinationDao.insertVaccination(vaccination.toEntity(isSynced = false))
        
        // 2. Launch sync ASYNCHRONOUSLY
        repositoryScope.launch {
            try {
                db.collection("vaccinations").document(vaccination.id).set(vaccination).await()
                vaccinationDao.markSynced(vaccination.id)
            } catch (e: Exception) {
                // Offline
            }
        }
    }

    suspend fun deleteVaccination(id: String) {
        vaccinationDao.deleteVaccination(id)
        try {
            db.collection("vaccinations").document(id).delete().await()
        } catch (e: Exception) {
            // Offline
        }
    }
}
