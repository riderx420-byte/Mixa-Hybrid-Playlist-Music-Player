package com.example.data.innertube

import java.security.MessageDigest
import java.util.Locale

object InnerTubeAuthHelper {

    fun sanitizeCookie(rawCookie: String): String {
        val trimmed = rawCookie.trim()
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        if (trimmed.isBlank()) return ""
        return trimmed.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .joinToString("; ")
    }

    fun extractCookieValue(cookie: String, key: String): String? {
        if (cookie.isBlank()) return null
        return cookie.split(";")
            .asSequence()
            .map { it.trim() }
            .firstOrNull { part ->
                val eqIdx = part.indexOf('=')
                eqIdx > 0 && part.substring(0, eqIdx).trim() == key
            }
            ?.let { part ->
                val eqIdx = part.indexOf('=')
                part.substring(eqIdx + 1).trim().takeIf { it.isNotBlank() }
            }
    }

    fun extractSapisid(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        return extractCookieValue(cookie, "SAPISID")
            ?: extractCookieValue(cookie, "__Secure-3PAPISID")
            ?: extractCookieValue(cookie, "__Secure-1PAPISID")
    }

    fun hasValidSessionCookie(cookie: String?): Boolean {
        return !extractSapisid(cookie).isNullOrBlank()
    }

    fun generateSapisidHash(
        sapisid: String,
        origin: String = "https://music.youtube.com",
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): String {
        val input = "$timestampSeconds $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xFF)
        }
        return "SAPISIDHASH ${timestampSeconds}_${hex}"
    }

    fun buildAuthorizationHeader(
        cookie: String?,
        origin: String = "https://music.youtube.com",
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): String? {
        val sapisid = extractSapisid(cookie) ?: return null
        return generateSapisidHash(
            sapisid = sapisid,
            origin = origin,
            timestampSeconds = timestampSeconds
        )
    }

    fun buildAuthHeaders(
        cookie: String?,
        origin: String = "https://music.youtube.com",
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): Map<String, String> {
        val sanitized = cookie?.let(::sanitizeCookie).orEmpty()
        val auth = buildAuthorizationHeader(sanitized, origin, timestampSeconds) ?: return emptyMap()
        return mapOf(
            "Cookie" to sanitized,
            "Authorization" to auth,
            "X-Goog-AuthUser" to "0",
            "Origin" to origin,
            "X-Origin" to origin
        )
    }
}
