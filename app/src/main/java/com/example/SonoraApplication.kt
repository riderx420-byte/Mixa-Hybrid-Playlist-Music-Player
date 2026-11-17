package com.example

import android.app.Application
import com.example.data.extensions.ExtensionPackageManager
import com.example.data.extensions.ExtensionSecretsStore
import com.example.data.extensions.IsrcCrossMatchRouter
import com.example.data.extensions.QuickJsExtensionRuntime
import com.example.data.innertube.InnerTubeClient
import com.example.data.local.LocalMediaScanner
import com.example.data.local.SettingsPreferences
import com.example.data.local.db.SonoraDatabase
import com.example.data.lyrics.LyricsRepository
import com.example.data.repository.MusicRepository
import com.example.domain.model.PreferredStreamQuality
import com.example.player.AudioEffectsManager
import com.example.player.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SonoraApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var latestPreferredStreamQuality: PreferredStreamQuality = PreferredStreamQuality.AUTO_BEST

    @Volatile
    private var latestPreferOpus: Boolean = true

    val database: SonoraDatabase by lazy { SonoraDatabase.getInstance(this) }
    val innerTubeClient: InnerTubeClient by lazy { InnerTubeClient() }
    val localScanner: LocalMediaScanner by lazy { LocalMediaScanner(this) }
    val settingsPreferences: SettingsPreferences by lazy { SettingsPreferences(this) }
    val audioEffectsManager: AudioEffectsManager by lazy {
        AudioEffectsManager(settingsPreferences)
    }

    val extensionSecretsStore: ExtensionSecretsStore by lazy {
        ExtensionSecretsStore(this)
    }

    val extensionPackageManager: ExtensionPackageManager by lazy {
        ExtensionPackageManager(
            context = this,
            dao = database.sonoraDao(),
            okHttpClient = innerTubeClient.okHttpClient,
            secretsStore = extensionSecretsStore
        )
    }

    val quickJsExtensionRuntime: QuickJsExtensionRuntime by lazy {
        QuickJsExtensionRuntime(
            okHttpClient = innerTubeClient.okHttpClient,
            secretsStore = extensionSecretsStore
        )
    }

    val isrcCrossMatchRouter: IsrcCrossMatchRouter by lazy {
        IsrcCrossMatchRouter(
            packageManager = extensionPackageManager,
            runtime = quickJsExtensionRuntime,
            innerTubeClient = innerTubeClient,
            preferredQualityProvider = { latestPreferredStreamQuality },
            preferOpusProvider = { latestPreferOpus }
        )
    }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(
            context = this,
            okHttpClient = innerTubeClient.okHttpClient
        )
    }

    val repository: MusicRepository by lazy {
        MusicRepository(
            dao = database.sonoraDao(),
            localScanner = localScanner,
            innerTubeClient = innerTubeClient,
            settingsPreferences = settingsPreferences,
            extensionPackageManager = extensionPackageManager,
            extensionSecretsStore = extensionSecretsStore,
            isrcCrossMatchRouter = isrcCrossMatchRouter,
            lyricsRepository = lyricsRepository
        )
    }
    val playbackController: PlaybackController by lazy {
        PlaybackController(
            context = this,
            repository = repository,
            innerTubeClient = innerTubeClient,
            audioEffectsManager = audioEffectsManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settingsPreferences.settingsFlow.collect { settings ->
                latestPreferredStreamQuality = settings.preferredStreamQuality
                latestPreferOpus = settings.preferOpusStream
                innerTubeClient.updateSessionCookie(settings.ytMusicCookie)
            }
        }
    }
}
