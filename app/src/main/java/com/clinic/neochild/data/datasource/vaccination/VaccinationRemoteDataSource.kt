package com.clinic.neochild.data.datasource.vaccination

import com.clinic.neochild.data.model.Vaccination
import com.clinic.neochild.utils.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface VaccinationRemoteDataSource {
    suspend fun fetchAllVaccinations(): List<Vaccination>
    suspend fun uploadVaccination(vaccination: Vaccination)
    suspend fun deleteVaccination(id: String)
}

class VaccinationRemoteDataSourceImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : VaccinationRemoteDataSource {
    
    private val vaccinationsCollection = firestore.collection("vaccinations")

    override suspend fun fetchAllVaccinations(): List<Vaccination> {
        val result = vaccinationsCollection.get().await()
        return result.documents.mapNotNull { FirestoreMappers.toVaccination(it) }
    }

    override suspend fun uploadVaccination(vaccination: Vaccination) {
        vaccinationsCollection.document(vaccination.id).set(vaccination).await()
    }

    override suspend fun deleteVaccination(id: String) {
        vaccinationsCollection.document(id).delete().await()
    }
}
