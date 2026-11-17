package com.example.data.innertube

import org.json.JSONObject
import java.util.Locale

data class StreamResolution(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String,
    val codecLabel: String,
    val bitrateKbps: Int,
    val expiresAtMs: Long,
    val userAgent: String,
    val isHls: Boolean = false,
    val clientLabel: String = "INNERTUBE"
) {
    fun isValid(nowMs: Long = System.currentTimeMillis()): Boolean {
        return streamUrl.isNotBlank() && nowMs < (expiresAtMs - 120_000L)
    }
}

data class PlayabilityResult(
    val status: String,
    val reason: String? = null
) {
    val isPlayable: Boolean get() = status.equals("OK", ignoreCase = true)
    val requiresSignIn: Boolean
        get() = status.equals("LOGIN_REQUIRED", ignoreCase = true) ||
            (reason?.contains("Sign in", ignoreCase = true) == true) ||
            (reason?.contains("age", ignoreCase = true) == true)
}

object InnerTubeClients {
    const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
    const val YOUTUBE_API_URL = "https://www.youtube.com/youtubei/v1/"

    data class ClientSpec(
        val clientName: String,
        val clientVersion: String,
        val clientIdHeader: String,
        val userAgent: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null,
        val embedUrl: String? = null,
        val requiresWebOrigin: Boolean = false
    )

    val WEB_REMIX = ClientSpec(
        clientName = "WEB_REMIX",
        clientVersion = "1.20250414.01.00",
        clientIdHeader = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
        requiresWebOrigin = true
    )

    val ANDROID_VR_161 = ClientSpec(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientIdHeader = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = 32
    )

    val ANDROID_VR = ClientSpec(
        clientName = "ANDROID_VR",
        clientVersion = "1.60.19",
        clientIdHeader = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = 32
    )

    val ANDROID_VR_LEGACY = ClientSpec(
        clientName = "ANDROID_VR",
        clientVersion = "1.43.32",
        clientIdHeader = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip",
        osName = "Android",
        osVersion = "12",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = 32
    )

    val IOS = ClientSpec(
        clientName = "IOS",
        clientVersion = "20.10.4",
        clientIdHeader = "5",
        userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        osName = "iPhone",
        osVersion = "18.3.2.22D82",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2"
    )

    val ANDROID_MOBILE = ClientSpec(
        clientName = "ANDROID",
        clientVersion = "20.10.38",
        clientIdHeader = "3",
        userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 15; en_US; Pixel 8 Build/AP3A.241105.007) gzip",
        osName = "Android",
        osVersion = "15",
        deviceMake = "Google",
        deviceModel = "Pixel 8",
        androidSdkVersion = 35
    )

    val ANDROID_TESTSUITE = ClientSpec(
        clientName = "ANDROID_TESTSUITE",
        clientVersion = "1.9",
        clientIdHeader = "30",
        userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 13; en_US) gzip",
        osName = "Android",
        osVersion = "13",
        androidSdkVersion = 33
    )

    val ANDROID_MUSIC = ClientSpec(
        clientName = "ANDROID_MUSIC",
        clientVersion = "8.10.51",
        clientIdHeader = "21",
        userAgent = "com.google.android.apps.youtube.music/8.10.51 (Linux; U; Android 14; Pixel 8) gzip",
        osName = "Android",
        osVersion = "14",
        deviceMake = "Google",
        deviceModel = "Pixel 8",
        androidSdkVersion = 34
    )

    val TVHTML5_EMBED = ClientSpec(
        clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        clientVersion = "2.0",
        clientIdHeader = "85",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        embedUrl = "https://www.youtube.com",
        requiresWebOrigin = true
    )

    val TVHTML5_STANDARD = ClientSpec(
        clientName = "TVHTML5",
        clientVersion = "7.20250414.13.00",
        clientIdHeader = "7",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        requiresWebOrigin = true
    )

    val WEB_EMBEDDED = ClientSpec(
        clientName = "WEB_EMBEDDED_PLAYER",
        clientVersion = "1.20250414.01.00",
        clientIdHeader = "56",
        userAgent = WEB_REMIX.userAgent,
        embedUrl = "https://www.youtube.com",
        requiresWebOrigin = true
    )

    fun userAgentForClientTag(clientTag: String?): String? {
        return when (clientTag?.uppercase(Locale.US)) {
            "ANDROID_VR" -> ANDROID_VR_161.userAgent
            "ANDROID" -> ANDROID_MOBILE.userAgent
            "IOS" -> IOS.userAgent
            "ANDROID_TESTSUITE" -> ANDROID_TESTSUITE.userAgent
            "ANDROID_MUSIC" -> ANDROID_MUSIC.userAgent
            "WEB", "WEB_REMIX", "MWEB", "WEB_EMBEDDED_PLAYER", "WEB_CREATOR" -> WEB_REMIX.userAgent
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> TVHTML5_EMBED.userAgent
            else -> null
        }
    }

    fun isWebOriginStreamUrl(url: String): Boolean {
        return when (extractClientTag(url)?.uppercase(Locale.US)) {
            "WEB", "WEB_REMIX", "MWEB", "WEB_EMBEDDED_PLAYER", "WEB_CREATOR",
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> true
            else -> false
        }
    }

    fun extractClientTag(url: String): String? {
        val queryMatch = Regex("""[?&]c=([A-Z0-9_]+)""").find(url)
        if (queryMatch != null) return queryMatch.groupValues[1]
        val pathMatch = Regex("""/c/([A-Z0-9_]+)/""").find(url)
        return pathMatch?.groupValues?.getOrNull(1)
    }

    fun userAgentForStreamUrl(url: String): String? {
        return userAgentForClientTag(extractClientTag(url))
    }

    fun buildContextJson(
        spec: ClientSpec,
        visitorData: String? = null,
        gl: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US",
        hl: String = Locale.getDefault().language.takeIf { it.length == 2 } ?: "en"
    ): JSONObject {
        val clientObj = JSONObject().apply {
            put("clientName", spec.clientName)
            put("clientVersion", spec.clientVersion)
            put("gl", gl)
            put("hl", hl)
            spec.osName?.let { put("osName", it) }
            spec.osVersion?.let { put("osVersion", it) }
            spec.deviceMake?.let { put("deviceMake", it) }
            spec.deviceModel?.let { put("deviceModel", it) }
            spec.androidSdkVersion?.let { put("androidSdkVersion", it) }
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData)
            }
        }
        return JSONObject().apply {
            put("client", clientObj)
            spec.embedUrl?.let { url ->
                put("thirdParty", JSONObject().put("embedUrl", url))
            }
        }
    }
}
