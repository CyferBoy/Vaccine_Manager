package com.clinic.neochild.data.datasource.vaccination

import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface VaccinationLocalDataSource {
    fun getAllVaccinations(): Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    suspend fun insertVaccination(vaccination: Vaccination, isSynced: Boolean)
    suspend fun insertVaccinations(vaccinations: List<Vaccination>, isSynced: Boolean)
    suspend fun deleteVaccination(id: String)
    suspend fun getUnsyncedVaccinations(): List<Vaccination>
    suspend fun markSynced(id: String)
}

class VaccinationLocalDataSourceImpl(private val vaccinationDao: VaccinationDao) : VaccinationLocalDataSource {
    override fun getAllVaccinations(): Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().map { entities -> entities.map { it.toVaccination() } }

    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        vaccinationDao.getVaccinationsForPatient(patientId).map { entities -> entities.map { it.toVaccination() } }

    override suspend fun insertVaccination(vaccination: Vaccination, isSynced: Boolean) = 
        vaccinationDao.insertVaccination(vaccination.toEntity(isSynced))

    override suspend fun insertVaccinations(vaccinations: List<Vaccination>, isSynced: Boolean) = 
        vaccinationDao.insertVaccinations(vaccinations.map { it.toEntity(isSynced) })

    override suspend fun deleteVaccination(id: String) = 
        vaccinationDao.deleteVaccination(id)

    override suspend fun getUnsyncedVaccinations(): List<Vaccination> = 
        vaccinationDao.getUnsyncedVaccinations().map { it.toVaccination() }

    override suspend fun markSynced(id: String) = 
        vaccinationDao.markSynced(id)
}
