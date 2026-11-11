package com.sawn.app.system

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * بصمة/وجه للإعدادات — BiometricPrompt مع PIN احتياطي دائمًا.
 * يُستخدم فقط إن وُجود بصمة مسجلة + التشفير العتادي.
 */
object BiometricHelper {

    fun canUseBiometric(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * يعرض حوار البصمة. عند النجاح → [onSuccess] (مع PIN احتياطي مُقدَّم من UI).
     * لا يخزن أي بيانات بيومترية.
     */
    fun show(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // PIN احتياطي يبقى متاحًا في UI — لا نغلق التطبيق
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onError(errString.toString())
                }
            }
        }
        val prompt = BiometricPrompt(
            activity,
            executor,
            callback,
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}
