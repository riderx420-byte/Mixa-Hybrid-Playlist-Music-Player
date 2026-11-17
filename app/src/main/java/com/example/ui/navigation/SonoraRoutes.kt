package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
object ExploreRoute

@Serializable
object RadioRoute

@Serializable
object PlaylistsRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: Long)

@Serializable
object EqualizerRoute
