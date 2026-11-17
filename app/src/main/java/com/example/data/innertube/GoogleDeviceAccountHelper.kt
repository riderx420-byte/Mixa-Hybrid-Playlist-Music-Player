package com.example.data.innertube

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DeviceGoogleAccount(
    val email: String,
    val type: String = GOOGLE_ACCOUNT_TYPE
) {
    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

sealed interface WebLoginHandoffResult {
    data class AutoSessionUrl(val url: String) : WebLoginHandoffResult
    data class ConsentIntent(val intent: Intent) : WebLoginHandoffResult
    data class PrefilledWebLoginUrl(val url: String) : WebLoginHandoffResult
}

/**
 * Integrates Android's phone-level Google AccountManager safely with zero dangerous permissions:
 * - Reads any device Google accounts already visible to Mixa.
 * - Launches the zero-permission system AccountManager chooser so users can pick any signed-in
 *   Google account on the phone.
 * - Attempts native `weblogin:` session handoff for YouTube Music and gracefully falls back to an
 *   email-prefilled Google sign-in URL when interactive confirmation is needed.
 */
object GoogleDeviceAccountHelper {

    private const val YOUTUBE_MUSIC_HOME_URL = "https://music.youtube.com/"
    private const val WEB_LOGIN_TOKEN_TYPE = "weblogin:continue=https%3A%2F%2Fmusic.youtube.com%2F"

    fun buildPrefilledLoginUrl(email: String?): String {
        val base = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true"
        val emailParam = email?.trim()?.takeIf { it.isNotBlank() }?.let { "&Email=${Uri.encode(it)}" }.orEmpty()
        return "${base}${emailParam}&continue=${Uri.encode(YOUTUBE_MUSIC_HOME_URL)}"
    }

    fun getVisibleGoogleAccounts(context: Context): List<DeviceGoogleAccount> {
        return runCatching {
            val manager = AccountManager.get(context)
            manager.getAccountsByType(DeviceGoogleAccount.GOOGLE_ACCOUNT_TYPE)
                .mapNotNull { acct ->
                    acct.name.trim().takeIf { it.isNotBlank() }?.let {
                        DeviceGoogleAccount(email = it, type = acct.type)
                    }
                }
                .distinctBy { it.email.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun buildGoogleAccountPickerIntent(selectedEmail: String? = null): Intent {
        val preselected = selectedEmail
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { Account(it, DeviceGoogleAccount.GOOGLE_ACCOUNT_TYPE) }
        return AccountManager.newChooseAccountIntent(
            preselected,
            null,
            arrayOf(DeviceGoogleAccount.GOOGLE_ACCOUNT_TYPE),
            "Select a Google account signed in on your phone to connect YouTube Music in Mixa.",
            null,
            null,
            null
        )
    }

    @Suppress("DEPRECATION")
    suspend fun requestYouTubeWebLoginHandoff(
        context: Context,
        email: String
    ): WebLoginHandoffResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val fallbackUrl = buildPrefilledLoginUrl(cleanEmail)
        if (cleanEmail.isBlank()) {
            return@withContext WebLoginHandoffResult.PrefilledWebLoginUrl(fallbackUrl)
        }

        val manager = AccountManager.get(context)
        val account = runCatching {
            manager.getAccountsByType(DeviceGoogleAccount.GOOGLE_ACCOUNT_TYPE)
                .firstOrNull { it.name.equals(cleanEmail, ignoreCase = true) }
        }.getOrNull() ?: Account(cleanEmail, DeviceGoogleAccount.GOOGLE_ACCOUNT_TYPE)

        val activity = context.findActivity()
        val bundle: Bundle? = withTimeoutOrNull(8_000L) {
            runCatching {
                val future = if (activity != null) {
                    manager.getAuthToken(
                        account,
                        WEB_LOGIN_TOKEN_TYPE,
                        null,
                        activity,
                        null,
                        null
                    )
                } else {
                    manager.getAuthToken(
                        account,
                        WEB_LOGIN_TOKEN_TYPE,
                        null,
                        false,
                        null,
                        null
                    )
                }
                future.result
            }.getOrNull()
        }

        val tokenUrl = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
            ?.trim()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (tokenUrl != null) {
            return@withContext WebLoginHandoffResult.AutoSessionUrl(tokenUrl)
        }

        val consentIntent: Intent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            bundle?.getParcelable(AccountManager.KEY_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle?.getParcelable(AccountManager.KEY_INTENT)
        }
        if (consentIntent != null) {
            return@withContext WebLoginHandoffResult.ConsentIntent(consentIntent)
        }

        WebLoginHandoffResult.PrefilledWebLoginUrl(fallbackUrl)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
