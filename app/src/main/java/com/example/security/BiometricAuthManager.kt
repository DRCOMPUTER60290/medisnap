package com.example.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED
}

object BiometricAuthManager {

    fun getAllowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun checkStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(getAllowedAuthenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun isAvailable(context: Context): Boolean {
        return checkStatus(context) == BiometricStatus.AVAILABLE
    }

    fun getStatusDescription(context: Context): String {
        return when (checkStatus(context)) {
            BiometricStatus.AVAILABLE -> "Biométrie et code de verrouillage prêts"
            BiometricStatus.NO_HARDWARE -> "Aucun capteur biométrique ou verrou configuré"
            BiometricStatus.HW_UNAVAILABLE -> "Capteur biométrique temporairement indisponible"
            BiometricStatus.NONE_ENROLLED -> "Aucune empreinte ni code PIN configuré sur l’appareil"
            BiometricStatus.SECURITY_UPDATE_REQUIRED -> "Mise à jour de sécurité système requise"
            BiometricStatus.UNSUPPORTED -> "Authentification biométrique non prise en charge"
        }
    }

    fun createEnrollIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, getAllowedAuthenticators())
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Accès Sécurisé MediSnap",
        subtitle: String = "Authentifiez-vous pour accéder à vos données médicales",
        confirmationRequired: Boolean = false,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, message: String) -> Unit,
        onFailedAttempt: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val message = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> "Authentification annulée par l’utilisateur."
                    BiometricPrompt.ERROR_LOCKOUT -> "Trop de tentatives. Veuillez patienter 30 secondes."
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biométrie bloquée. Utilisez le code de l’appareil."
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "Aucune biométrie ou code PIN configuré sur cet appareil."
                    else -> errString.toString().ifBlank { "Erreur d’authentification biométrique." }
                }
                onError(errorCode, message)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailedAttempt()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(getAllowedAuthenticators())
            .setConfirmationRequired(confirmationRequired)
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(-1, e.localizedMessage ?: "Impossible d’ouvrir le dialogue biométrique.")
        }
    }
}
