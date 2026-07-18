package com.clinic.neochild.core.logger

import android.os.Build
import com.clinic.neochild.data.local.dao.AuditLogDao
import com.clinic.neochild.data.local.entity.AuditLogEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val auditLogDao: AuditLogDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Logs a sensitive action to both Local Room and Firestore.
     */
    fun logAction(action: String, patientId: String?, details: String = "") {
        val user = auth.currentUser
        val userEmail = user?.email ?: "Unknown"
        val timestamp = System.currentTimeMillis()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"

        // 1. Local Log
        scope.launch {
            auditLogDao.insertLog(
                AuditLogEntity(
                    patientId = patientId,
                    action = action,
                    details = details,
                    staffMember = userEmail,
                    timestamp = timestamp,
                    device = device
                )
            )
        }

        // 2. Remote Log
        val log = hashMapOf(
            "timestamp" to Date(timestamp),
            "userId" to (user?.uid ?: "Unknown"),
            "userEmail" to userEmail,
            "action" to action,
            "patientId" to patientId,
            "details" to details,
            "device" to device
        )
        
        firestore.collection("audit_logs").add(log)
    }
}
