package com.example.data.extensions

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores per-extension user-entered credentials (e.g. ARL, API token, region, custom settings).
 * Uses AndroidKeyStore AES-GCM encryption at rest when available on device, with clean fallback
 * for JVM/Robolectric unit tests where AndroidKeyStore is absent.
 */
class ExtensionSecretsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setSetting(extensionId: String, key: String, value: String, isSecret: Boolean) {
        val storageKey = composeKey(extensionId, key)
        if (value.isEmpty()) {
            prefs.edit().remove(storageKey).apply()
            return
        }
        val storedValue = if (isSecret) {
            encryptWithKeystore(value) ?: "plain:${value}"
        } else {
            value
        }
        prefs.edit().putString(storageKey, storedValue).apply()
    }

    fun getSetting(extensionId: String, key: String, defaultValue: String = ""): String {
        val raw = prefs.getString(composeKey(extensionId, key), null) ?: return defaultValue
        return when {
            raw.startsWith(ENC_PREFIX) -> decryptWithKeystore(raw) ?: defaultValue
            raw.startsWith("plain:") -> raw.removePrefix("plain:")
            else -> raw
        }
    }

    fun getAllSettings(extensionId: String): Map<String, String> {
        val prefix = "${extensionId}::"
        val out = linkedMapOf<String, String>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is String) {
                val settingKey = k.removePrefix(prefix)
                out[settingKey] = when {
                    v.startsWith(ENC_PREFIX) -> decryptWithKeystore(v).orEmpty()
                    v.startsWith("plain:") -> v.removePrefix("plain:")
                    else -> v
                }
            }
        }
        return out
    }

    fun clearExtensionSettings(extensionId: String) {
        val prefix = "${extensionId}::"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun composeKey(extensionId: String, key: String): String = "${extensionId}::$key"

    private fun encryptWithKeystore(plaintext: String): String? {
        return runCatching {
            val secretKey = getOrCreateAesKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            "$ENC_PREFIX$ivB64:$dataB64"
        }.getOrNull()
    }

    private fun decryptWithKeystore(payload: String): String? {
        return runCatching {
            val body = payload.removePrefix(ENC_PREFIX)
            val parts = body.split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getOrCreateAesKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateAesKey(): SecretKey? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "sonora_extension_settings"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sonora_ext_secrets_aes_v1"
        const val ENC_PREFIX = "enc_v1:"
    }
}
