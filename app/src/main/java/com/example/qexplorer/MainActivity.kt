package com.example.qexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.qexplorer.data.ThemeSettings
import com.example.qexplorer.theme.QExplorerTheme

import android.content.Intent
import androidx.navigation3.runtime.NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ThemeSettings preference store
        ThemeSettings.init(this)

        val action = intent?.action
        val isPicker = action == Intent.ACTION_GET_CONTENT || action == Intent.ACTION_OPEN_DOCUMENT
        val startDestination: NavKey = if (isPicker) ProviderScreenView else Dashboard

        enableEdgeToEdge()
        setContent {
            val themeMode by ThemeSettings.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemInDarkTheme() // System
            }

            QExplorerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(startDestination = startDestination)
                }
            }
        }
    }
}
