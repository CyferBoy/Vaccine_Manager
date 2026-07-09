package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccine
import com.clinic.neochild.data.model.Vaccine
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineRepositoryImpl @Inject constructor(
    private val vaccineDao: VaccineDao,
    private val firestore: FirebaseFirestore
) : VaccineRepository {

    override fun getInventory(): Flow<List<Vaccine>> {
        return vaccineDao.getAllVaccines().map { entities -> 
            entities.map { it.toVaccine() } 
        }
    }

    override suspend fun refreshInventory() {
        try {
            val snapshot = firestore.collection("inventory").get().await()
            val vaccines = snapshot.documents.mapNotNull { doc ->
                FirestoreMappers.toVaccine(doc)
            }
            vaccineDao.insertVaccines(vaccines.map { it.toEntity(isSynced = true) })
        } catch (e: Exception) {
            // Handle error
        }
    }

    override suspend fun updateStock(vaccineId: String, newStock: Int) {
        val entity = vaccineDao.getVaccineById(vaccineId)
        if (entity != null) {
            val updated = entity.copy(stock = newStock, isSynced = false)
            vaccineDao.insertVaccine(updated)
            
            try {
                firestore.collection("inventory").document(vaccineId)
                    .update("stock", newStock).await()
                vaccineDao.insertVaccine(updated.copy(isSynced = true))
            } catch (e: Exception) { }
        }
    }

    override suspend fun deleteVaccine(id: String) {
        vaccineDao.deleteVaccine(id)
        try {
            firestore.collection("inventory").document(id).delete().await()
        } catch (e: Exception) { }
    }
}
