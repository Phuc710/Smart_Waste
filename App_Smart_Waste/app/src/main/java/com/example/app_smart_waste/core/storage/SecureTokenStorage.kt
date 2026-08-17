package com.example.app_smart_waste.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStorage(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "smartwaste_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("smartwaste_fallback_prefs", Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveUser(id: String, username: String, fullName: String, role: String, isActive: Boolean) {
        prefs.edit()
            .putString(KEY_USER_ID, id)
            .putString(KEY_USERNAME, username)
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_ROLE, role)
            .putBoolean(KEY_IS_ACTIVE, isActive)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getFullName(): String? = prefs.getString(KEY_FULL_NAME, "Tài xế thu gom")
    fun getRole(): String? = prefs.getString(KEY_ROLE, "staff")
    fun isActive(): Boolean = prefs.getBoolean(KEY_IS_ACTIVE, true)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "user_name"
        private const val KEY_FULL_NAME = "user_full_name"
        private const val KEY_ROLE = "user_role"
        private const val KEY_IS_ACTIVE = "user_is_active"

        @Volatile
        private var INSTANCE: SecureTokenStorage? = null

        fun getInstance(context: Context): SecureTokenStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureTokenStorage(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
