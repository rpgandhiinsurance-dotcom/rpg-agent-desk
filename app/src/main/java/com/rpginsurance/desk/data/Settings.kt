package com.rpginsurance.desk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The Airtable token lives only in EncryptedSharedPreferences (AES-256, key held in the
 * Android Keystore). It is never in the APK, logs, or backups.
 */
class Settings(context: Context) {
    private val secure: SharedPreferences = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val plain: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var token: String?
        get() = secure.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = secure.edit { if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value.trim()) }

    val hasToken: Boolean get() = token != null

    var dailyNotification: Boolean
        get() = plain.getBoolean(KEY_DAILY, true)
        set(value) = plain.edit { putBoolean(KEY_DAILY, value) }

    private companion object {
        const val KEY_TOKEN = "airtable_pat"
        const val KEY_DAILY = "daily_notification"
    }
}
