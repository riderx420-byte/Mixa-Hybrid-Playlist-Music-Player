package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.ui.components.AddHiResStreamSheet
import com.example.ui.components.ExtensionManagerSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.MixaSplashOverlay
import com.example.ui.components.YouTubeAccountSheet
import com.example.ui.theme.MixaHaptics
import com.example.ui.navigation.EqualizerRoute
import com.example.ui.navigation.ExploreRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.PlaylistDetailRoute
import com.example.ui.navigation.PlaylistsRoute
import com.example.ui.navigation.RadioRoute
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingSheet
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.QueueBottomSheet
import com.example.ui.screens.RadioScreen

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun SonoraApp(viewModel: SonoraViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val libraryState by viewModel.libraryUiState.collectAsStateWithLifecycle()
    val exploreState by viewModel.exploreUiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsUiState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val accountStatusMessage by viewModel.accountStatusMessage.collectAsStateWithLifecycle()
    val streamProbeState by viewModel.streamProbeUiState.collectAsStateWithLifecycle()
    val extensionRegistries by viewModel.extensionRegistries.collectAsStateWithLifecycle()
    val installedExtensions by viewModel.installedExtensions.collectAsStateWithLifecycle()
    val extensionUiState by viewModel.extensionUiState.collectAsStateWithLifecycle()

    val view = LocalView.current
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showAddHiResStreamSheet by remember { mutableStateOf(false) }
    var showExtensionManagerSheet by remember { mutableStateOf(false) }

    val topDestinations = remember {
        listOf(
            TopLevelDestination(
                route = LibraryRoute,
                labelRes = R.string.nav_library,
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                tag = "nav_library"
            ),
            TopLevelDestination(
                route = ExploreRoute,
                labelRes = R.string.nav_explore,
                selectedIcon = Icons.Rounded.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                tag = "nav_explore"
            ),
            TopLevelDestination(
                route = RadioRoute,
                labelRes = R.string.nav_radio,
                selectedIcon = Icons.Rounded.Radio,
                unselectedIcon = Icons.Outlined.Radio,
                tag = "nav_radio"
            ),
            TopLevelDestination(
                route = PlaylistsRoute,
                labelRes = R.string.nav_playlists,
                selectedIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic,
                tag = "nav_playlists"
            ),
            TopLevelDestination(
                route = EqualizerRoute,
                labelRes = R.string.nav_studio_eq,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                tag = "nav_studio_eq"
            )
        )
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavRail) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        if (playbackState.currentTrack != null) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onExpand = { showNowPlaying = true },
                                onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                                onSkipNext = { viewModel.playbackController.skipNext() },
                                onSkipPrevious = { viewModel.playbackController.skipPrevious() },
                                onOpenQueue = { showQueueSheet = true }
                            )
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = when (dest.route) {
                                    is LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                    is ExploreRoute -> currentDestination?.hasRoute<ExploreRoute>() == true
                                    is RadioRoute -> currentDestination?.hasRoute<RadioRoute>() == true
                                    is PlaylistsRoute -> currentDestination?.hasRoute<PlaylistsRoute>() == true ||
                                        currentDestination?.hasRoute<PlaylistDetailRoute>() == true
                                    is EqualizerRoute -> currentDestination?.hasRoute<EqualizerRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        MixaHaptics.confirm(view)
                                        navigateTopLevel(dest.route)
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    ),
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = when (dest.route) {
                                is LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                is ExploreRoute -> currentDestination?.hasRoute<ExploreRoute>() == true
                                is RadioRoute -> currentDestination?.hasRoute<RadioRoute>() == true
                                is PlaylistsRoute -> currentDestination?.hasRoute<PlaylistsRoute>() == true ||
                                    currentDestination?.hasRoute<PlaylistDetailRoute>() == true
                                is EqualizerRoute -> currentDestination?.hasRoute<EqualizerRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    MixaHaptics.confirm(view)
                                    navigateTopLevel(dest.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = LibraryRoute,
                        enterTransition = { fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) },
                        exitTransition = { fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<LibraryRoute> {
                            LibraryScreen(
                                uiState = libraryState,
                                playbackState = playbackState,
                                favoriteIds = favoriteIds,
                                playlists = playlists,
                                onSelectTab = viewModel::selectLibraryTab,
                                onSelectFolder = viewModel::selectFolder,
                                onScanLocalMedia = viewModel::scanLocalMedia,
                                onImportAudioUri = viewModel::importLocalAudioUri,
                                onPlayTrackList = viewModel::playTrackList,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onStartRadio = viewModel::startRadioFromTrack,
                                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                                onCreatePlaylistAndAdd = { name, track ->
                                    viewModel.createPlaylist(name, initialTracks = listOf(track))
                                },
                                onClearBanner = viewModel::clearLibraryBanner,
                                isYoutubeSignedIn = userSettings.isYoutubeSignedIn,
                                onOpenAccountSheet = { showAccountSheet = true },
                                onOpenAddHiResStream = { showAddHiResStreamSheet = true },
                                onDeleteDirectStream = viewModel::deleteDirectStream
                            )
                        }

                        composable<ExploreRoute> {
                            ExploreScreen(
                                uiState = exploreState,
                                searchHistory = searchHistory,
                                playbackState = playbackState,
                                favoriteIds = favoriteIds,
                                playlists = playlists,
                                isYoutubeSignedIn = userSettings.isYoutubeSignedIn,
                                onQueryChanged = viewModel::onSearchQueryChanged,
                                onSubmitSearch = viewModel::submitSearch,
                                onSelectFilter = viewModel::selectSearchFilter,
                                onSelectSource = viewModel::selectExploreSource,
                                onDeleteHistoryItem = viewModel::deleteSearchHistoryItem,
                                onClearHistory = viewModel::clearSearchHistory,
                                onRefreshFeatured = viewModel::loadExploreFeatured,
                                onPlayTrackList = viewModel::playTrackList,
                                onStartRadio = viewModel::startRadioFromTrack,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                                onCreatePlaylistAndAdd = { name, track ->
                                    viewModel.createPlaylist(name, initialTracks = listOf(track))
                                },
                                onOpenExtensionManager = { showExtensionManagerSheet = true }
                            )
                        }

                        composable<RadioRoute> {
                            RadioScreen(
                                stations = libraryState.directStreamTracks,
                                playbackState = playbackState,
                                favoriteIds = favoriteIds,
                                playlists = playlists,
                                onPlayTrackList = viewModel::playTrackList,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                                onCreatePlaylistAndAdd = { name, track ->
                                    viewModel.createPlaylist(name, initialTracks = listOf(track))
                                },
                                onOpenAddHiResStream = { showAddHiResStreamSheet = true },
                                onDeleteDirectStream = viewModel::deleteDirectStream
                            )
                        }

                        composable<PlaylistsRoute> {
                            PlaylistsScreen(
                                playlists = playlists,
                                onOpenPlaylist = { id ->
                                    navController.navigate(PlaylistDetailRoute(id))
                                },
                                onPlayPlaylist = { tracks ->
                                    viewModel.playTrackList(tracks, 0)
                                },
                                onCreatePlaylist = { name, desc ->
                                    viewModel.createPlaylist(name, desc)
                                },
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onAddPlaylistToQueue = viewModel::addTracksToQueue
                            )
                        }

                        composable<PlaylistDetailRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<PlaylistDetailRoute>()
                            val detailPlaylist by viewModel.observePlaylist(route.playlistId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            val combinedCandidates = remember(
                                libraryState.localTracks,
                                libraryState.directStreamTracks,
                                libraryState.cachedYoutubeTracks,
                                libraryState.favoriteTracks
                            ) {
                                (libraryState.localTracks + libraryState.directStreamTracks + libraryState.cachedYoutubeTracks + libraryState.favoriteTracks)
                                    .distinctBy { it.id }
                            }
                            PlaylistDetailScreen(
                                playlist = detailPlaylist,
                                availableTracks = combinedCandidates,
                                playbackState = playbackState,
                                favoriteIds = favoriteIds,
                                allPlaylists = playlists,
                                onBack = { navController.popBackStack() },
                                onPlayTracks = viewModel::playTrackList,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onStartRadio = viewModel::startRadioFromTrack,
                                onAddTrackToThisPlaylist = { track ->
                                    viewModel.addTrackToPlaylist(route.playlistId, track)
                                },
                                onAddTrackToOtherPlaylist = viewModel::addTrackToPlaylist,
                                onCreatePlaylistAndAdd = { name, track ->
                                    viewModel.createPlaylist(name, initialTracks = listOf(track))
                                },
                                onRemoveTrack = { trackId ->
                                    viewModel.removeTrackFromPlaylist(route.playlistId, trackId)
                                },
                                onMoveTrack = { tracks, from, to ->
                                    viewModel.moveTrackInPlaylist(route.playlistId, tracks, from, to)
                                },
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onAddTracksToQueue = viewModel::addTracksToQueue
                            )
                        }

                        composable<EqualizerRoute> {
                            val enabledNames = installedExtensions.filter { it.isEnabled }.map { it.name }
                            val extSummary = when {
                                enabledNames.isNotEmpty() ->
                                    "${enabledNames.joinToString(", ")} • ${enabledNames.size} active"
                                installedExtensions.isNotEmpty() ->
                                    "${installedExtensions.size} installed (disabled)"
                                extensionRegistries.isNotEmpty() ->
                                    "${extensionRegistries.size} registry feed(s) synced"
                                else -> "Add Mornye .sflx feeds (Qobuz, Deezer, Apple Music)"
                            }
                            EqualizerScreen(
                                eqState = equalizerState,
                                userSettings = userSettings,
                                onToggleEq = viewModel::setEqEnabled,
                                onSelectPreset = viewModel::applyEqPreset,
                                onBandLevelChanged = viewModel::setEqBandLevel,
                                onBassBoostChanged = viewModel::setBassBoost,
                                onToggleAutoRadio = viewModel::setAutoRadioSetting,
                                onTogglePreferOpus = viewModel::setPreferOpusSetting,
                                onToggleAutoSkipBroken = viewModel::setAutoSkipBrokenTracks,
                                onSelectPreferredStreamQuality = viewModel::setPreferredStreamQuality,
                                onToggleDirectFloatPcmBypass = viewModel::setDirectFloatPcmBypassEq,
                                onSelectCrossfadeDuration = viewModel::setCrossfadeDurationSeconds,
                                onClearStreamCache = viewModel::clearStreamCache,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onSelectThemeAccent = viewModel::setThemeAccent,
                                onToggleDynamicColor = viewModel::setUseDynamicColor,
                                onSelectCornerStyle = viewModel::setCornerStyle,
                                onSelectBackdropStyle = viewModel::setPlayerBackdropStyle,
                                onOpenAccountSheet = { showAccountSheet = true },
                                activeExtensionsSummary = extSummary,
                                hasActiveExtensions = enabledNames.isNotEmpty(),
                                onOpenExtensionManager = { showExtensionManagerSheet = true }
                            )
                        }
                    }

                    if (useNavRail && playbackState.currentTrack != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(bottom = 8.dp)
                        ) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onExpand = { showNowPlaying = true },
                                onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                                onSkipNext = { viewModel.playbackController.skipNext() },
                                onSkipPrevious = { viewModel.playbackController.skipPrevious() },
                                onOpenQueue = { showQueueSheet = true }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying && playbackState.currentTrack != null,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            )
        ) {
            NowPlayingSheet(
                playbackState = playbackState,
                favoriteIds = favoriteIds,
                playlists = playlists,
                equalizerState = equalizerState,
                directFloatPcmBypassEq = userSettings.directFloatPcmBypassEq,
                isYoutubeSignedIn = userSettings.isYoutubeSignedIn,
                onDismiss = { showNowPlaying = false },
                onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                onSkipNext = { viewModel.playbackController.skipNext() },
                onSkipPrevious = { viewModel.playbackController.skipPrevious() },
                onSeekTo = { viewModel.playbackController.seekTo(it) },
                onToggleShuffle = { viewModel.playbackController.toggleShuffle() },
                onCycleRepeat = { viewModel.playbackController.cycleRepeatMode() },
                onToggleFavorite = viewModel::toggleFavorite,
                onStartRadio = viewModel::startRadioFromTrack,
                onOpenQueue = { showQueueSheet = true },
                onOpenEqualizer = {
                    showNowPlaying = false
                    navigateTopLevel(EqualizerRoute)
                },
                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                onCreatePlaylistAndAdd = { name, track ->
                    viewModel.createPlaylist(name, initialTracks = listOf(track))
                },
                onRetryStream = { viewModel.retryStream() },
                onSwitchStreamFormat = { viewModel.retryStreamWithHls() },
                onSwitchStreamVariant = viewModel::switchActiveStreamVariant,
                onToggleDirectPcmBypass = viewModel::setDirectFloatPcmBypassEq,
                onOpenAccountSheet = {
                    showNowPlaying = false
                    showAccountSheet = true
                },
                onJumpToQueueIndex = { idx -> viewModel.playbackController.skipToQueueIndex(idx) },
                onMoveQueueItem = { from, to -> viewModel.playbackController.moveQueueItem(from, to) },
                onRemoveQueueItem = { idx -> viewModel.playbackController.removeQueueItem(idx) },
                onToggleAutoRadio = viewModel::setAutoRadioSetting,
                onSaveQueueAsPlaylist = viewModel::saveCurrentQueueAsPlaylist,
                lyricsState = lyricsState,
                onRefreshLyrics = viewModel::refreshCurrentTrackLyrics,
                onImportLrcUri = viewModel::importLrcForCurrentTrack,
                onNudgeLyricsOffsetMs = viewModel::nudgeLyricsSyncOffset
            )
        }

        if (showQueueSheet) {
            QueueBottomSheet(
                playbackState = playbackState,
                onDismiss = { showQueueSheet = false },
                onJumpToIndex = { idx -> viewModel.playbackController.skipToQueueIndex(idx) },
                onMoveItem = { from, to -> viewModel.playbackController.moveQueueItem(from, to) },
                onRemoveItem = { idx -> viewModel.playbackController.removeQueueItem(idx) },
                onToggleAutoRadio = viewModel::setAutoRadioSetting,
                onSaveQueueAsPlaylist = viewModel::saveCurrentQueueAsPlaylist
            )
        }

        if (showAccountSheet) {
            YouTubeAccountSheet(
                settings = userSettings,
                statusMessage = accountStatusMessage,
                onDismiss = { showAccountSheet = false },
                onSelectPhoneAccount = viewModel::selectDeviceGoogleAccount,
                onSubmitCookie = { cookie, email ->
                    viewModel.saveYouTubeAccountCookie(cookie, email)
                },
                onSignOut = viewModel::signOutYouTubeAccount,
                onClearStatus = viewModel::clearAccountStatus
            )
        }

        if (showAddHiResStreamSheet) {
            AddHiResStreamSheet(
                probeState = streamProbeState,
                onDismiss = { showAddHiResStreamSheet = false },
                onProbeStream = viewModel::probeDirectStreamUrl,
                onSaveTrack = viewModel::saveAndPlayProbedStream,
                onResetProbe = viewModel::resetStreamProbe
            )
        }

        if (showExtensionManagerSheet) {
            ExtensionManagerSheet(
                registries = extensionRegistries,
                installedExtensions = installedExtensions,
                uiState = extensionUiState,
                onDismiss = { showExtensionManagerSheet = false },
                onAddRegistry = viewModel::addAndSyncRegistry,
                onSyncAllRegistries = viewModel::syncAllRegistries,
                onRemoveRegistry = viewModel::removeRegistry,
                onInstallFromRegistry = viewModel::installRegistryExtension,
                onImportLocalSflx = viewModel::installLocalExtensionUri,
                onToggleExtensionEnabled = viewModel::setExtensionEnabled,
                onUninstallExtension = viewModel::uninstallExtension,
                onReadExtensionSettings = viewModel::getExtensionSettings,
                onSaveExtensionSetting = viewModel::saveExtensionSetting,
                onClearMessages = viewModel::clearExtensionUiMessages
            )
        }

        MixaSplashOverlay(
            visible = showSplash,
            onFinished = { showSplash = false }
        )
    }
}
