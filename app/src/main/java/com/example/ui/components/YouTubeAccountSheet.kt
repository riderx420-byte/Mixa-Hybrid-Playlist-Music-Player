package com.example.ui.components

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.innertube.DeviceGoogleAccount
import com.example.data.innertube.GoogleDeviceAccountHelper
import com.example.data.innertube.InnerTubeAuthHelper
import com.example.data.innertube.WebLoginHandoffResult
import com.example.data.local.UserAudioSettings
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable
import kotlinx.coroutines.launch

private const val CLEAN_MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAccountSheet(
    settings: UserAudioSettings,
    statusMessage: String?,
    onDismiss: () -> Unit,
    onSelectPhoneAccount: (String) -> Unit,
    onSubmitCookie: (cookie: String, email: String?) -> Unit,
    onSignOut: () -> Unit,
    onClearStatus: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    var visibleDeviceAccounts by remember { mutableStateOf<List<DeviceGoogleAccount>>(emptyList()) }
    var selectedEmail by remember(settings.ytSelectedEmail) {
        mutableStateOf(settings.ytSelectedEmail)
    }
    var activeWebLoginUrl by remember {
        mutableStateOf(GoogleDeviceAccountHelper.buildPrefilledLoginUrl(settings.ytSelectedEmail))
    }
    var webLoginNonce by remember { mutableIntStateOf(0) }
    var showWebLogin by remember { mutableStateOf(false) }
    var isLaunchingPhoneSso by remember { mutableStateOf(false) }
    var localSsoHint by remember { mutableStateOf<String?>(null) }
    var showManualCookie by remember { mutableStateOf(false) }
    var manualCookieInput by remember { mutableStateOf("") }
    var webProgress by remember { mutableIntStateOf(0) }

    fun refreshVisibleAccounts() {
        val fetched = GoogleDeviceAccountHelper.getVisibleGoogleAccounts(context)
        val saved = settings.ytSelectedEmail?.trim()?.takeIf { it.isNotBlank() }
        visibleDeviceAccounts = buildList {
            addAll(fetched)
            if (saved != null && none { it.email.equals(saved, ignoreCase = true) }) {
                add(DeviceGoogleAccount(email = saved))
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val email = selectedEmail
        isLaunchingPhoneSso = false
        if (result.resultCode == Activity.RESULT_OK && !email.isNullOrBlank()) {
            MixaHaptics.confirm(view)
            localSsoHint = "Completing sign-in for $email…"
            scope.launch {
                when (val handoff = GoogleDeviceAccountHelper.requestYouTubeWebLoginHandoff(context, email)) {
                    is WebLoginHandoffResult.AutoSessionUrl -> {
                        activeWebLoginUrl = handoff.url
                        webLoginNonce++
                        showWebLogin = true
                    }
                    else -> {
                        activeWebLoginUrl = GoogleDeviceAccountHelper.buildPrefilledLoginUrl(email)
                        webLoginNonce++
                        showWebLogin = true
                    }
                }
            }
        } else {
            activeWebLoginUrl = GoogleDeviceAccountHelper.buildPrefilledLoginUrl(email)
            webLoginNonce++
            showWebLogin = true
        }
    }

    fun startPhoneAccountSignIn(email: String) {
        MixaHaptics.confirm(view)
        selectedEmail = email
        onSelectPhoneAccount(email)
        isLaunchingPhoneSso = true
        localSsoHint = "Connecting phone Google account $email…"
        scope.launch {
            val handoff = GoogleDeviceAccountHelper.requestYouTubeWebLoginHandoff(context, email)
            isLaunchingPhoneSso = false
            when (handoff) {
                is WebLoginHandoffResult.AutoSessionUrl -> {
                    localSsoHint = "Auto-authenticating YouTube Music as $email…"
                    activeWebLoginUrl = handoff.url
                    webLoginNonce++
                    showWebLogin = true
                }
                is WebLoginHandoffResult.ConsentIntent -> {
                    localSsoHint = "Confirm device account permission for $email"
                    consentLauncher.launch(handoff.intent)
                }
                is WebLoginHandoffResult.PrefilledWebLoginUrl -> {
                    localSsoHint = "Pre-filled $email — enter password or passkey if prompted"
                    activeWebLoginUrl = handoff.url
                    webLoginNonce++
                    showWebLogin = true
                }
            }
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            refreshVisibleAccounts()
            if (pickedEmail != null) {
                startPhoneAccountSignIn(pickedEmail)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshVisibleAccounts()
    }

    ModalBottomSheet(
        onDismissRequest = {
            MixaHaptics.gestureEnd(view)
            onClearStatus()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        if (settings.isYoutubeSignedIn) tokens.emeraldAccent else primary,
                                        tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (settings.isYoutubeSignedIn) {
                                Icons.Rounded.VerifiedUser
                            } else {
                                Icons.Rounded.AccountCircle
                            },
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Google & YouTube Music",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (settings.isYoutubeSignedIn) {
                                "Authenticated session active"
                            } else {
                                "Connect an account signed in on this phone"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.isYoutubeSignedIn) {
                                tokens.emeraldAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                IconButton(
                    onClick = {
                        MixaHaptics.tap(view)
                        onClearStatus()
                        onDismiss()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_account_sheet_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close account sheet")
                }
            }

            val banner = statusMessage ?: localSsoHint
            if (!banner.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(tokens.chipCornerRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (settings.isYoutubeSignedIn) {
                Surface(
                    shape = RoundedCornerShape(tokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = settings.ytAccountLabel ?: selectedEmail ?: "YouTube Music Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Your Google / YouTube Music session stays encrypted locally on this device and signs InnerTube streams with SAPISIDHASH.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    MixaHaptics.tap(view)
                                    accountPickerLauncher.launch(
                                        GoogleDeviceAccountHelper.buildGoogleAccountPickerIntent(selectedEmail)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("switch_phone_google_account_button")
                            ) {
                                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Switch Account", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = {
                                    MixaHaptics.reject(view)
                                    runCatching {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                    }
                                    onSignOut()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("yt_sign_out_button")
                            ) {
                                Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out", maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                // Phone Google Accounts Fetch & One-Tap Card
                Surface(
                    shape = RoundedCornerShape(tokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Google Accounts on This Phone",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    MixaHaptics.tap(view)
                                    refreshVisibleAccounts()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh device Google accounts",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = "Pick an account signed into Android so Mixa can auto-fill or hand off your phone Google session without typing your email.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (visibleDeviceAccounts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                visibleDeviceAccounts.forEach { account ->
                                    val isChosen = account.email.equals(selectedEmail, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(tokens.chipCornerRadius),
                                        color = if (isChosen) {
                                            primary.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                                            .hapticClickable(kind = MixaHapticKind.CONFIRM) {
                                                startPhoneAccountSignIn(account.email)
                                            }
                                            .testTag("phone_google_account_${account.email}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AccountCircle,
                                                    contentDescription = null,
                                                    tint = if (isChosen) primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Column {
                                                    Text(
                                                        text = account.email,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "Tap to connect with YouTube Music",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (isChosen) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                MixaHaptics.confirm(view)
                                accountPickerLauncher.launch(
                                    GoogleDeviceAccountHelper.buildGoogleAccountPickerIntent(selectedEmail)
                                )
                            },
                            enabled = !isLaunchingPhoneSso,
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_phone_google_account_button")
                        ) {
                            if (isLaunchingPhoneSso) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting Phone Account…")
                            } else {
                                Icon(Icons.Rounded.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (visibleDeviceAccounts.isEmpty()) {
                                        "Fetch Accounts from Phone"
                                    } else {
                                        "Choose Another Phone Account"
                                    }
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                MixaHaptics.tap(view)
                                activeWebLoginUrl = GoogleDeviceAccountHelper.buildPrefilledLoginUrl(selectedEmail)
                                webLoginNonce++
                                showWebLogin = !showWebLogin
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("yt_google_signin_button")
                        ) {
                            Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (showWebLogin) "Hide Sign-In Window" else "Open Google Sign-In Browser")
                        }
                    }
                }

                AnimatedVisibility(visible = showWebLogin) {
                    Surface(
                        shape = RoundedCornerShape(tokens.cardCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedEmail?.let { "Signing in as $it" } ?: "Complete Google sign-in below",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        MixaHaptics.confirm(view)
                                        val cookie = readYoutubeCookies()
                                        if (InnerTubeAuthHelper.hasValidSessionCookie(cookie)) {
                                            showWebLogin = false
                                            onSubmitCookie(cookie, selectedEmail)
                                        }
                                    }
                                ) {
                                    Text("Sync Cookie")
                                }
                            }
                            if (webProgress in 1..99) {
                                LinearProgressIndicator(
                                    progress = { webProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            YouTubeWebLoginBox(
                                targetUrl = activeWebLoginUrl,
                                reloadNonce = webLoginNonce,
                                onProgressChanged = { webProgress = it },
                                onCookieCaptured = { cookie ->
                                    MixaHaptics.confirm(view)
                                    showWebLogin = false
                                    onSubmitCookie(cookie, selectedEmail)
                                }
                            )
                        }
                    }
                }

                // Manual Cookie Paste Option
                Surface(
                    shape = RoundedCornerShape(tokens.cardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hapticClickable { showManualCookie = !showManualCookie },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Paste Session Cookie Manually",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = {
                                    MixaHaptics.tap(view)
                                    showManualCookie = !showManualCookie
                                }
                            ) {
                                Icon(
                                    imageVector = if (showManualCookie) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Toggle manual cookie input"
                                )
                            }
                        }

                        AnimatedVisibility(visible = showManualCookie) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "If Google restricts WebView login for your account: sign in to music.youtube.com in a desktop browser, open DevTools (F12) → Network, select any /youtubei/ request, and paste its Cookie header here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextField(
                                    value = manualCookieInput,
                                    onValueChange = { manualCookieInput = it },
                                    placeholder = { Text("SAPISID=...; __Secure-3PAPISID=...; SID=...") },
                                    maxLines = 4,
                                    shape = RoundedCornerShape(tokens.chipCornerRadius),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_cookie_input")
                                )
                                FilledTonalButton(
                                    onClick = {
                                        MixaHaptics.confirm(view)
                                        onSubmitCookie(manualCookieInput, selectedEmail)
                                    },
                                    enabled = manualCookieInput.isNotBlank(),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("save_manual_cookie_button")
                                ) {
                                    Text("Verify & Save Cookie")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebLoginBox(
    targetUrl: String,
    reloadNonce: Int,
    onProgressChanged: (Int) -> Unit,
    onCookieCaptured: (String) -> Unit
) {
    var captured by remember(reloadNonce) { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = CLEAN_MOBILE_CHROME_UA
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (captured || url.isNullOrBlank()) return
                        if (url.contains("music.youtube.com") || url.contains("www.youtube.com")) {
                            val cookie = readYoutubeCookies()
                            if (InnerTubeAuthHelper.hasValidSessionCookie(cookie)) {
                                captured = true
                                cookieManager.flush()
                                onCookieCaptured(cookie)
                            }
                        }
                    }
                }
                tag = targetUrl
                loadUrl(targetUrl)
            }
        },
        update = { webView ->
            if (webView.tag != "$targetUrl#$reloadNonce") {
                webView.tag = "$targetUrl#$reloadNonce"
                webView.loadUrl(targetUrl)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(14.dp))
    )
}

private fun readYoutubeCookies(): String {
    val cm = CookieManager.getInstance()
    val music = cm.getCookie("https://music.youtube.com").orEmpty()
    val yt = cm.getCookie("https://www.youtube.com").orEmpty()
    val combined = listOf(music, yt)
        .filter { it.isNotBlank() }
        .joinToString("; ")
    return InnerTubeAuthHelper.sanitizeCookie(combined)
}
