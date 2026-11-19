package com.chronos.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chronos.app.data.repository.AppSettings
import com.chronos.app.ui.ChronosViewModelFactory
import com.chronos.app.ui.navigation.ChronosNavHost
import com.chronos.app.ui.navigation.Dest
import com.chronos.app.ui.navigation.bottomItems
import com.chronos.app.ui.theme.ChronosTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = chronosContainer
        lifecycleScope.launch { container.repository.ensureSeedCategories() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            ChronosTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                ChronosAppRoot(ChronosViewModelFactory(container))
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}

@Composable
private fun ChronosAppRoot(factory: ChronosViewModelFactory) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val topLevelRoutes = bottomItems.map { it.dest.route }.toSet()
    val showScaffold = currentRoute in topLevelRoutes
    val showFab = currentRoute in setOf(Dest.Home.route, Dest.Calendar.route, Dest.Events.route)

    Scaffold(
        bottomBar = {
            if (showScaffold) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.dest.route,
                            onClick = {
                                if (currentRoute != item.dest.route) {
                                    navController.navigate(item.dest.route) {
                                        popUpTo(Dest.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { androidx.compose.material3.Text(item.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { navController.navigate(Dest.EditEvent.build()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Novo agendamento")
                }
            }
        },
    ) { padding ->
        ChronosNavHost(
            navController = navController,
            factory = factory,
            modifier = Modifier.padding(padding),
        )
    }
}
