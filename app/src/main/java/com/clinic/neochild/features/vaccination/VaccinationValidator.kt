package com.clinic.neochild.features.vaccination

import android.content.Context
import android.widget.Toast
import com.clinic.neochild.domain.model.Vaccination
import java.util.*

object VaccinationValidator {
    fun validateForm(context: Context, patientId: String, vaccines: List<String>): Boolean {
        if (patientId.isBlank() || vaccines.isEmpty()) {
            Toast.makeText(context, "Patient ID and at least one Vaccine are required", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun createVaccination(
        id: String?, patientId: String, vaccines: List<String>, nextVaccine: String, dateGiven: String, nextDue: String,
        cost: String, cash: String, online: String, total: Double, withFees: Boolean, doctorsAcc: Boolean,
        batches: List<String>, expiries: List<String>, performedBy: String = ""
    ) = Vaccination(
        id = id ?: UUID.randomUUID().toString(),
        patientId = patientId,
        vaccineNames = vaccines,
        nxtVaccineNames = nextVaccine.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        dateGiven = dateGiven,
        nextDueDate = nextDue,
        cost = cost.toDoubleOrNull() ?: 0.0,
        cashAmount = cash.toDoubleOrNull() ?: 0.0,
        onlineAmount = online.toDoubleOrNull() ?: 0.0,
        totalPaid = total,
        withFees = withFees,
        doctorsAcc = doctorsAcc,
        batchNumbers = batches,
        expiryDates = expiries,
        performedBy = performedBy
    )
}
