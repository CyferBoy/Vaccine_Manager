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
     * Centralized logging for all business modules.
     */
    fun log(
        module: String,
        entityType: String,
        entityId: String,
        action: String,
        patientId: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
        remarks: String? = null
    ) {
        val user = auth.currentUser
        val userEmail = user?.email ?: "Unknown"
        val timestamp = System.currentTimeMillis()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"

        val logEntity = AuditLogEntity(
            timestamp = timestamp,
            user = userEmail,
            module = module,
            entityType = entityType,
            entityId = entityId,
            action = action,
            oldValue = oldValue,
            newValue = newValue,
            remarks = remarks,
            device = device,
            patientId = patientId,
            isSynced = false
        )

        // 1. Local Log
        scope.launch {
            auditLogDao.insertLog(logEntity)
        }

        // 2. Remote Log
        val remoteLog = hashMapOf(
            "timestamp" to Date(timestamp),
            "user" to userEmail,
            "module" to module,
            "entityType" to entityType,
            "entityId" to entityId,
            "action" to action,
            "oldValue" to oldValue,
            "newValue" to newValue,
            "remarks" to remarks,
            "device" to device,
            "patientId" to patientId
        )
        
        firestore.collection("audit_logs").add(remoteLog)
    }

    /**
     * Legacy support mapper.
     */
    fun logAction(action: String, patientId: String?, details: String = "") {
        log(
            module = "LEGACY",
            entityType = "UNKNOWN",
            entityId = "0",
            action = action,
            patientId = patientId,
            remarks = details
        )
    }
}
