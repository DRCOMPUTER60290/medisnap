package com.example.security

import android.content.Context
import android.content.SharedPreferences

class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isBiometricLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, value).apply()

    var autoLockTimeoutSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_TIMEOUT_SECONDS, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT_SECONDS, value).apply()

    var requireBiometricForSensitiveActions: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_BIOMETRIC_SENSITIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_BIOMETRIC_SENSITIVE, value).apply()

    var hideAppPreviewInRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_APP_PREVIEW, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_APP_PREVIEW, value).apply()

    var isMasterKeyVaultEnabled: Boolean
        get() = prefs.getBoolean(KEY_VAULT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VAULT_ENABLED, value).apply()

    var sealedMasterKeyCiphertext: String?
        get() = prefs.getString(KEY_VAULT_CIPHERTEXT, null)
        set(value) = prefs.edit().putString(KEY_VAULT_CIPHERTEXT, value).apply()

    var sealedMasterKeyIv: String?
        get() = prefs.getString(KEY_VAULT_IV, null)
        set(value) = prefs.edit().putString(KEY_VAULT_IV, value).apply()

    var rgpdConsentAcceptedAtMs: Long
        get() = prefs.getLong(KEY_RGPD_CONSENT_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_RGPD_CONSENT_MS, value).apply()

    var lastLocalBackupMs: Long
        get() = prefs.getLong(KEY_LAST_LOCAL_BACKUP_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_LOCAL_BACKUP_MS, value).apply()

    var lastSovereignSyncMs: Long
        get() = prefs.getLong(KEY_LAST_SOVEREIGN_SYNC_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SOVEREIGN_SYNC_MS, value).apply()

    var sovereignWebDavUrl: String
        get() = prefs.getString(KEY_WEBDAV_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBDAV_URL, value).apply()

    var sovereignWebDavUsername: String
        get() = prefs.getString(KEY_WEBDAV_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBDAV_USER, value).apply()

    fun clearVaultData() {
        prefs.edit()
            .remove(KEY_VAULT_CIPHERTEXT)
            .remove(KEY_VAULT_IV)
            .putBoolean(KEY_VAULT_ENABLED, false)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "medisnap_security_prefs"
        private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT_SECONDS = "auto_lock_timeout_seconds"
        private const val KEY_REQUIRE_BIOMETRIC_SENSITIVE = "require_biometric_sensitive"
        private const val KEY_HIDE_APP_PREVIEW = "hide_app_preview"
        private const val KEY_VAULT_ENABLED = "vault_enabled"
        private const val KEY_VAULT_CIPHERTEXT = "vault_ciphertext"
        private const val KEY_VAULT_IV = "vault_iv"
        private const val KEY_RGPD_CONSENT_MS = "rgpd_consent_ms"
        private const val KEY_LAST_LOCAL_BACKUP_MS = "last_local_backup_ms"
        private const val KEY_LAST_SOVEREIGN_SYNC_MS = "last_sovereign_sync_ms"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USER = "webdav_user"
    }
}
