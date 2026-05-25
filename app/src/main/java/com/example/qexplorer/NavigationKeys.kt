package com.example.qexplorer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Dashboard : NavKey
@Serializable data class Explorer(
    val path: String = "",
    val category: String = "",
    val isPicker: Boolean = false
) : NavKey
@Serializable data object ProviderScreenView : NavKey
@Serializable data object Settings : NavKey
@Serializable data class Editor(val path: String) : NavKey
@Serializable data class PhotoViewer(
    val path: String,
    val directoryPath: String = "",
    val category: String = "",
    val sortBy: String = "name",
    val sortAsc: Boolean = true
) : NavKey
@Serializable data class VideoPlayer(val path: String) : NavKey
@Serializable data object WifiManager : NavKey
@Serializable data object Trash : NavKey
