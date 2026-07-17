package com.clinic.neochild.core.ui

import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Helper function to delete a document from Firestore and show a Toast.
 */
fun deleteFirestoreDocument(
    context: android.content.Context,
    collectionPath: String,
    documentId: String,
    onSuccess: () -> Unit = {}
) {
    FirebaseFirestore.getInstance().collection(collectionPath).document(documentId).delete()
        .addOnSuccessListener {
            Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}
