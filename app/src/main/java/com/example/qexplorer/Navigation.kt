package com.example.qexplorer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.qexplorer.ui.DashboardScreen
import com.example.qexplorer.ui.ExplorerScreen
import com.example.qexplorer.ui.SettingsScreen
import com.example.qexplorer.ui.EditorScreen
import com.example.qexplorer.ui.PhotoViewerScreen
import com.example.qexplorer.ui.VideoPlayerScreen
import com.example.qexplorer.ui.WifiManagerScreen
import com.example.qexplorer.ui.TrashScreen
import com.example.qexplorer.ui.ProviderScreenView
import androidx.navigation3.runtime.NavKey

@Composable
fun MainNavigation(startDestination: NavKey = Dashboard) {
  val backStack = rememberNavBackStack(startDestination)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<ProviderScreenView> {
          ProviderScreenView(
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Dashboard> {
          DashboardScreen(
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Explorer> { key ->
          ExplorerScreen(
            path = key.path,
            category = key.category,
            isPicker = key.isPicker,
            onNavigate = { navKey -> backStack.add(navKey) },
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Settings> {
          SettingsScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Editor> { key ->
          EditorScreen(
            filePath = key.path,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<PhotoViewer> { key ->
          PhotoViewerScreen(
            filePath = key.path,
            directoryPath = key.directoryPath,
            category = key.category,
            sortBy = key.sortBy,
            sortAsc = key.sortAsc,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<VideoPlayer> { key ->
          VideoPlayerScreen(
            filePath = key.path,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<WifiManager> {
          WifiManagerScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Trash> {
          TrashScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
