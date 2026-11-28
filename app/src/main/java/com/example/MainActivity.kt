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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.screens.ImsakiyeScreen
import com.example.ui.screens.LocationPickerScreen
import com.example.ui.screens.QiblaScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EzanVaktimTheme

object AppRoutes {
    const val HOME = "home"
    const val IMSAKIYE = "imsakiye"
    const val QIBLA = "qibla"
    const val SETTINGS = "settings"
    const val LOCATIONS = "locations"
}

private enum class PendingLocationTarget { NONE, DETECT_CITY, QIBLA_GPS }

private data class TopNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

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
    val customAudioStatus by viewModel.customAudioStatus.collectAsStateWithLifecycle()
    val isDetectingLocation by viewModel.isDetectingLocation.collectAsStateWithLifecycle()
    val locationDetectMessage by viewModel.locationDetectMessage.collectAsStateWithLifecycle()
    val locationPickerState by viewModel.locationPickerUiState.collectAsStateWithLifecycle()
    val monthlyPrayerDays by viewModel.monthlyPrayerDaysState.collectAsStateWithLifecycle()
    val qiblaState by viewModel.qiblaUiState.collectAsStateWithLifecycle()

    var pendingLocationTarget by remember { mutableStateOf(PendingLocationTarget.NONE) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.refreshPermissions()
        val granted = result.values.any { it }
        if (granted) {
            when (pendingLocationTarget) {
                PendingLocationTarget.QIBLA_GPS -> viewModel.refreshQiblaGps()
                PendingLocationTarget.DETECT_CITY, PendingLocationTarget.NONE -> {
                    viewModel.detectCurrentLocation {
                        if (currentRoute == AppRoutes.LOCATIONS) {
                            navController.popBackStack()
                        }
                    }
                }
            }
        }
        pendingLocationTarget = PendingLocationTarget.NONE
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissions.hasNotificationPermission
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val topNavItems = remember {
        listOf(
            TopNavItem(
                route = AppRoutes.HOME,
                labelRes = R.string.nav_prayer_times,
                selectedIcon = Icons.Filled.AccessTimeFilled,
                unselectedIcon = Icons.Outlined.AccessTime,
                testTag = "nav_home_tab"
            ),
            TopNavItem(
                route = AppRoutes.IMSAKIYE,
                labelRes = R.string.nav_imsakiye,
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                testTag = "nav_imsakiye_tab"
            ),
            TopNavItem(
                route = AppRoutes.QIBLA,
                labelRes = R.string.nav_qibla,
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                testTag = "nav_qibla_tab"
            ),
            TopNavItem(
                route = AppRoutes.SETTINGS,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings_tab"
            )
        )
    }

    val showBottomOrRailNav = currentRoute != AppRoutes.LOCATIONS

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showBottomOrRailNav && !useNavigationRail) {
                    NavigationBar {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
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
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
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
                            isDetectingLocation = isDetectingLocation,
                            locationDetectMessage = locationDetectMessage,
                            onOpenLocationPicker = {
                                viewModel.openLocationPicker()
                                navController.navigate(AppRoutes.LOCATIONS)
                            },
                            onDetectLocationClick = {
                                if (permissions.hasLocationPermission) {
                                    viewModel.detectCurrentLocation()
                                } else {
                                    pendingLocationTarget = PendingLocationTarget.DETECT_CITY
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            },
                            onDismissLocationMessage = { viewModel.clearLocationDetectMessage() },
                            onRefresh = { viewModel.refreshPrayerTimes() },
                            onTogglePrayerBell = { type, enabled ->
                                viewModel.togglePrayerEnabled(type, enabled)
                            },
                            onStopSound = { viewModel.stopSound() }
                        )
                    }

                    composable(AppRoutes.IMSAKIYE) {
                        ImsakiyeScreen(
                            location = homeState.location,
                            monthlyDays = monthlyPrayerDays,
                            isRefreshing = homeState.isRefreshing,
                            onRefresh = { viewModel.refreshPrayerTimes() }
                        )
                    }

                    composable(AppRoutes.QIBLA) {
                        QiblaScreen(
                            qiblaState = qiblaState,
                            hasLocationPermission = permissions.hasLocationPermission,
                            onRequestLocationAndRefresh = {
                                if (permissions.hasLocationPermission) {
                                    viewModel.refreshQiblaGps()
                                } else {
                                    pendingLocationTarget = PendingLocationTarget.QIBLA_GPS
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                    }

                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(
                            preferences = preferences,
                            permissions = permissions,
                            isSoundPlaying = isSoundPlaying,
                            hasBundledCustomEzanFile = viewModel.hasBundledCustomEzanFile,
                            customAudioStatus = customAudioStatus,
                            onSelectSoundType = { viewModel.selectSoundType(it) },
                            onTogglePrayer = { type, enabled ->
                                viewModel.togglePrayerEnabled(type, enabled)
                            },
                            onTogglePersistentCountdown = { enabled ->
                                viewModel.togglePersistentCountdown(enabled)
                            },
                            onPickCustomAudio = { uri -> viewModel.importCustomAudio(uri) },
                            onClearCustomAudio = { viewModel.clearCustomAudio() },
                            onTestNotification = { viewModel.sendTestNotification() },
                            onStopSound = { viewModel.stopSound() },
                            onRefreshPermissions = { viewModel.refreshPermissions() }
                        )
                    }

                    composable(AppRoutes.LOCATIONS) {
                        LocationPickerScreen(
                            uiState = locationPickerState,
                            isDetectingLocation = isDetectingLocation,
                            locationDetectMessage = locationDetectMessage,
                            onDetectLocationClick = {
                                if (permissions.hasLocationPermission) {
                                    viewModel.detectCurrentLocation {
                                        navController.popBackStack()
                                    }
                                } else {
                                    pendingLocationTarget = PendingLocationTarget.DETECT_CITY
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            },
                            onModeChange = { viewModel.setLocationPickerMode(it) },
                            onSearchQueryChange = { viewModel.updateLocationSearchQuery(it) },
                            onSearchVillagesNow = { viewModel.searchVillagesNow() },
                            onSwitchToVillageSearch = { viewModel.switchToVillageSearchWithQuery(it) },
                            onSelectCountry = { viewModel.selectCountry(it) },
                            onSelectCity = { viewModel.selectCity(it) },
                            onSelectDistrict = { district ->
                                viewModel.selectDistrict(district) {
                                    navController.popBackStack()
                                }
                            },
                            onSelectPlaceResult = { place ->
                                viewModel.selectPlaceSearchResult(place) {
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
