package com.clinic.neochild.core.logger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    /**
     * Logs a sensitive action to Firestore.
     * Useful for tracking who added or deleted medical records.
     */
    fun logAction(action: String, entityId: String, details: String = "") {
        val user = auth.currentUser ?: return
        val log = hashMapOf(
            "timestamp" to Date(),
            "userId" to user.uid,
            "userEmail" to (user.email ?: "Unknown"),
            "action" to action,
            "entityId" to entityId,
            "details" to details
        )
        
        // Fire and forget - don't block the UI for logging
        firestore.collection("audit_logs").add(log)
    }
}
