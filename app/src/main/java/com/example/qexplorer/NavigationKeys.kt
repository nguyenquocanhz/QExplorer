package com.example.qexplorer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Dashboard : NavKey
@Serializable data class Explorer(val path: String = "", val category: String = "") : NavKey
@Serializable data object Settings : NavKey
@Serializable data class Editor(val path: String) : NavKey
