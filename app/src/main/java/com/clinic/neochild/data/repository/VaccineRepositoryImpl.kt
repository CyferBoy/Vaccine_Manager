package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.dao.VaccineDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccine
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.repository.VaccineRepository
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineRepositoryImpl @Inject constructor(
    private val vaccineDao: VaccineDao,
    private val syncRepository: SyncRepository
) : VaccineRepository {

    override fun getInventory(): Flow<List<Vaccine>> {
        return vaccineDao.getAllVaccines().map { entities -> 
            entities.map { entity ->
                val totalStock = vaccineDao.getTotalStockForVaccine(entity.id) ?: 0
                entity.toVaccine(totalStock)
            } 
        }
    }

    override suspend fun refreshInventory() {
        // Implementation for remote refresh using SyncRepository or Direct fetch
    }

    override suspend fun updateStock(vaccineId: String, newStock: Int) {
        // This is now handled via transactions and batches in InventoryRepository
    }

    override suspend fun deleteVaccine(id: String) {
        vaccineDao.insertVaccine(
            vaccineDao.getVaccineById(id)?.copy(isDeleted = true) ?: return
        )
        syncRepository.enqueue(
            entityName = "INVENTORY",
            entityId = id,
            operation = SyncOperation.DELETE
        )
    }
}
