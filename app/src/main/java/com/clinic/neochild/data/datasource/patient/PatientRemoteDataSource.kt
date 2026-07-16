package com.clinic.neochild.data.datasource.patient

import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.data.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Interface for Remote Patient Data operations.
 * Decouples the Repository from Firestore implementation details.
 */
interface PatientRemoteDataSource {
    suspend fun fetchAllPatients(): List<Patient>
    suspend fun uploadPatient(patient: Patient)
    suspend fun deletePatient(id: String)
}

class PatientRemoteDataSourceImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : PatientRemoteDataSource {
    
    private val patientsCollection = firestore.collection("patients")

    override suspend fun fetchAllPatients(): List<Patient> {
        val result = patientsCollection.get().await()
        return result.documents.mapNotNull { FirestoreMappers.toPatient(it) }
    }

    override suspend fun uploadPatient(patient: Patient) {
        patientsCollection.document(patient.id).set(patient).await()
    }

    override suspend fun deletePatient(id: String) {
        patientsCollection.document(id).delete().await()
    }
}
