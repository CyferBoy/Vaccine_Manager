package com.clinic.neochild.data.datasource.vaccination

import com.clinic.neochild.data.local.dao.VaccinationDao
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

interface VaccinationLocalDataSource {
    fun getAllVaccinations(): Flow<List<Vaccination>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>>
    suspend fun getVaccinationById(id: String): Vaccination?
    suspend fun insertVaccination(vaccination: Vaccination, isSynced: Boolean)
    suspend fun insertVaccinations(vaccinations: List<Vaccination>, isSynced: Boolean)
    suspend fun deleteVaccination(id: String)
    suspend fun getUnsyncedVaccinations(): List<Vaccination>
    suspend fun markSynced(id: String)
    
    fun getTodayCount(date: String): Flow<Int>
    fun getTodayRevenue(date: String): Flow<Double?>
    fun getTodayCash(date: String): Flow<Double?>
    fun getTodayOnline(date: String): Flow<Double?>
    fun getMonthlyCount(pattern: String): Flow<Int>
    fun getMonthlyRevenue(pattern: String): Flow<Double?>
}

class VaccinationLocalDataSourceImpl(private val vaccinationDao: VaccinationDao) : VaccinationLocalDataSource {
    override fun getAllVaccinations(): Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().map { entities -> entities.map { it.toVaccination() } }

    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        vaccinationDao.getVaccinationsForPatient(patientId).map { entities -> entities.map { it.toVaccination() } }

    override suspend fun getVaccinationById(id: String): Vaccination? = 
        vaccinationDao.getVaccinationById(id)?.toVaccination()

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

    override fun getTodayCount(date: String): Flow<Int> = vaccinationDao.getCountByDate(date)
    override fun getTodayRevenue(date: String): Flow<Double?> = vaccinationDao.getRevenueByDate(date)
    override fun getTodayCash(date: String): Flow<Double?> = vaccinationDao.getCashByDate(date)
    override fun getTodayOnline(date: String): Flow<Double?> = vaccinationDao.getOnlineByDate(date)
    override fun getMonthlyCount(pattern: String): Flow<Int> = vaccinationDao.getMonthlyCount(pattern)
    override fun getMonthlyRevenue(pattern: String): Flow<Double?> = vaccinationDao.getMonthlyRevenue(pattern)
}
