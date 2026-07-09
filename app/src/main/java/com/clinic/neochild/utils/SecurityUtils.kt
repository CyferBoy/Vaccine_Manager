package com.clinic.neochild.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.*

object SecurityUtils {

    private const val PREFS_NAME = "secure_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    /**
     * Retrieves or generates a secure random passphrase for the database.
     * Stores it in EncryptedSharedPreferences (backed by Android Keystore).
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
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
}
