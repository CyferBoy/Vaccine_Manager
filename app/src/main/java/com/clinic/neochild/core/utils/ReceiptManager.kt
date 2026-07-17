package com.clinic.neochild.core.utils

import android.content.Context
import com.clinic.neochild.domain.model.Consultation
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.Vaccination

object ReceiptManager {

    suspend fun downloadReceipt(context: Context, patient: Patient, vaccination: Vaccination) {
        ReceiptGenerator.downloadReceipt(context, patient, vaccination)
    }

    fun printReceipt(context: Context, patient: Patient, vaccination: Vaccination) {
        ReceiptPrinter.printReceipt(context, patient, vaccination)
    }

    fun printConsultationReceipt(context: Context, patient: Patient, consultation: Consultation) {
        ReceiptPrinter.printConsultationReceipt(context, patient, consultation)
    }
}
