package com.clinic.neochild.core.utils

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.GeneralSecurityException
import java.util.*

object SecurityUtils {

    private const val TAG = "SecurityUtils"
    private const val PREFS_NAME = "secure_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    /**
     * Retrieves or generates a secure random passphrase for the database.
     * Stores it in EncryptedSharedPreferences (backed by Android Keystore).
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
        try {
            return getOrGeneratePassphrase(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing encrypted shared preferences: ${e.message}", e)
            
            // If it's a security exception (like AEADBadTagException), try to clear and restart
            // AEADBadTagException can be nested or thrown directly depending on where it occurs
            if (isSecurityException(e)) {
                Log.w(TAG, "Attempting to recover by clearing encrypted preferences")
                try {
                    clearEncryptedPreferences(context)
                    return getOrGeneratePassphrase(context)
                } catch (retryException: Exception) {
                    Log.e(TAG, "Recovery failed: ${retryException.message}", retryException)
                }
            }
            
            // Fallback: This will likely lead to database opening error, but at least we don't crash here
            // Returning a random UUID ensures we have something, though it might fail DB open
            return UUID.randomUUID().toString().toByteArray()
        }
    }

    private fun isSecurityException(e: Throwable?): Boolean {
        var current = e
        while (current != null) {
            if (current is GeneralSecurityException) return true
            // Also check for specific crypto exceptions that might not inherit from GeneralSecurityException on all Android versions
            if (current.javaClass.name.contains("crypto") || current.javaClass.name.contains("security")) return true
            current = current.cause
        }
        return false
    }

    private fun getOrGeneratePassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var passphrase = sharedPreferences.getString(DB_PASSPHRASE_KEY, null)
        
        if (passphrase == null) {
            passphrase = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(DB_PASSPHRASE_KEY, passphrase).apply()
        }

        return passphrase.toByteArray()
    }

    private fun clearEncryptedPreferences(context: Context) {
        try {
            // Clear the shared preferences file
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear shared preferences via API", e)
        }
        
        // Also delete the physical file to be sure
        try {
            val sharedPrefsDir = File(context.dataDir, "shared_prefs")
            val prefsFile = File(sharedPrefsDir, "$PREFS_NAME.xml")
            if (prefsFile.exists()) {
                val deleted = prefsFile.delete()
                Log.d(TAG, "Shared prefs file deleted: $deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete shared preferences file", e)
        }
    }
}
