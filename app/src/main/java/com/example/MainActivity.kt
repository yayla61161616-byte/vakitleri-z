package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.PrayerViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LocationPickerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EzanVaktimTheme

object AppRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LOCATIONS = "locations"
}

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels {
        val app = application as EzanVaktimApp
        PrayerViewModel.factory(applicationContext, app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EzanVaktimTheme {
                EzanVaktimRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun EzanVaktimRoot(viewModel: PrayerViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.HOME

    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsUiState.collectAsStateWithLifecycle()
    val isSoundPlaying by viewModel.isSoundPlaying.collectAsStateWithLifecycle()
    val locationPickerState by viewModel.locationPickerUiState.collectAsStateWithLifecycle()

    // Request POST_NOTIFICATIONS smoothly on Android 13+ if not yet granted
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissions.hasNotificationPermission
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val showBottomOrRailNav = currentRoute != AppRoutes.LOCATIONS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showBottomOrRailNav && !useNavigationRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == AppRoutes.HOME,
                            onClick = {
                                navController.navigate(AppRoutes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == AppRoutes.HOME) {
                                        Icons.Filled.AccessTimeFilled
                                    } else {
                                        Icons.Outlined.AccessTime
                                    },
                                    contentDescription = stringResource(R.string.nav_prayer_times)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_prayer_times)) },
                            modifier = Modifier.testTag("nav_home_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == AppRoutes.SETTINGS,
                            onClick = {
                                navController.navigate(AppRoutes.SETTINGS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == AppRoutes.SETTINGS) {
                                        Icons.Filled.Settings
                                    } else {
                                        Icons.Outlined.Settings
                                    },
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            modifier = Modifier.testTag("nav_settings_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showBottomOrRailNav && useNavigationRail) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentRoute == AppRoutes.HOME,
                            onClick = {
                                navController.navigate(AppRoutes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == AppRoutes.HOME) {
                                        Icons.Filled.AccessTimeFilled
                                    } else {
                                        Icons.Outlined.AccessTime
                                    },
                                    contentDescription = stringResource(R.string.nav_prayer_times)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_prayer_times)) }
                        )
                        NavigationRailItem(
                            selected = currentRoute == AppRoutes.SETTINGS,
                            onClick = {
                                navController.navigate(AppRoutes.SETTINGS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == AppRoutes.SETTINGS) {
                                        Icons.Filled.Settings
                                    } else {
                                        Icons.Outlined.Settings
                                    },
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_settings)) }
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppRoutes.HOME) {
                        HomeScreen(
                            uiState = homeState,
                            onOpenLocationPicker = {
                                viewModel.openLocationPicker()
                                navController.navigate(AppRoutes.LOCATIONS)
                            },
                            onRefresh = { viewModel.refreshPrayerTimes() },
                            onTogglePrayerBell = { type, enabled ->
                                viewModel.togglePrayerEnabled(type, enabled)
                            },
                            onStopSound = { viewModel.stopSound() }
                        )
                    }

                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(
                            preferences = preferences,
                            permissions = permissions,
                            isSoundPlaying = isSoundPlaying,
                            hasCustomEzanFile = viewModel.hasCustomEzanFile,
                            onSelectSoundType = { viewModel.selectSoundType(it) },
                            onTogglePrayer = { type, enabled ->
                                viewModel.togglePrayerEnabled(type, enabled)
                            },
                            onTestNotification = { viewModel.sendTestNotification() },
                            onStopSound = { viewModel.stopSound() },
                            onRefreshPermissions = { viewModel.refreshPermissions() }
                        )
                    }

                    composable(AppRoutes.LOCATIONS) {
                        LocationPickerScreen(
                            uiState = locationPickerState,
                            onSearchQueryChange = { viewModel.updateLocationSearchQuery(it) },
                            onSelectCountry = { viewModel.selectCountry(it) },
                            onSelectCity = { viewModel.selectCity(it) },
                            onSelectDistrict = { district ->
                                viewModel.selectDistrict(district) {
                                    navController.popBackStack()
                                }
                            },
                            onStepBack = { viewModel.navigateLocationStepBack() },
                            onExit = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
