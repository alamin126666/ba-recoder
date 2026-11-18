package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.RecorderStatus
import com.example.presentation.draw.DrawToolsScreen
import com.example.presentation.draw.DrawViewModel
import com.example.presentation.gallery.GalleryScreen
import com.example.presentation.gallery.GalleryViewModel
import com.example.presentation.home.HomeScreen
import com.example.presentation.home.HomeViewModel
import com.example.presentation.player.VideoPlayerScreen
import com.example.presentation.player.VideoPlayerViewModel
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.SettingsViewModel
import com.example.presentation.splash.SplashOverlay
import com.example.service.DrawOverlayService
import com.example.ui.components.CountdownOverlay
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.theme.BAScreenRecordTheme
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val DRAW = "draw"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{recordingId}"

    fun player(id: String): String = "player/$id"
}

private data class NavTab(
    val route: String,
    val label: String,
    val iconRes: Int,
    val testTag: String
)

private val bottomTabs = listOf(
    NavTab(Routes.HOME, "Home", R.drawable.ic_home, "nav_home"),
    NavTab(Routes.GALLERY, "Gallery", R.drawable.ic_gallery, "nav_gallery"),
    NavTab(Routes.DRAW, "Draw", R.drawable.ic_draw_pen, "nav_draw"),
    NavTab(Routes.SETTINGS, "Settings", R.drawable.ic_settings, "nav_settings")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BARecorderApp).container

        setContent {
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(container.settingsDataStore)
            )
            val config by settingsVm.config.collectAsStateWithLifecycle()

            BAScreenRecordTheme(themeMode = config.themeMode) {
                BAScreenRecordRoot(
                    container = container,
                    settingsViewModel = settingsVm
                )
            }
        }
    }
}

@Composable
private fun BAScreenRecordRoot(
    container: AppContainer,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showOverlayRationale by remember { mutableStateOf(false) }
    var showMicRationale by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var pendingOverlayAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val homeVm: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            repository = container.repository,
            settingsDataStore = container.settingsDataStore,
            fileManager = container.fileManager,
            recordManager = container.screenRecordManager
        )
    )
    val galleryVm: GalleryViewModel = viewModel(
        factory = GalleryViewModel.Factory(
            repository = container.repository,
            fileManager = container.fileManager
        )
    )
    val drawVm: DrawViewModel = viewModel(
        factory = DrawViewModel.Factory(
            settingsDataStore = container.settingsDataStore,
            fileManager = container.fileManager,
            recordManager = container.screenRecordManager
        )
    )

    val homeState by homeVm.uiState.collectAsStateWithLifecycle()
    val galleryState by galleryVm.uiState.collectAsStateWithLifecycle()
    val thumbnails by galleryVm.thumbnails.collectAsStateWithLifecycle()
    val drawState by drawVm.uiState.collectAsStateWithLifecycle()
    val config by settingsViewModel.config.collectAsStateWithLifecycle()

    // MediaProjection Launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val dataIntent = result.data!!
            homeVm.startWithCountdown(config.countdownSeconds) {
                homeVm.startProjectionService(
                    context = context,
                    resultCode = result.resultCode,
                    data = dataIntent,
                    enableMic = config.recordMic
                )
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Screen capture permission is required to record.")
            }
        }
    }

    // Mic Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone denied — recording video without mic audio.")
            }
        }
        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    // Notification Permission Launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed smoothly whether granted or denied
    }

    // Overlay Settings Launcher
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            pendingOverlayAction?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Overlay permission not granted.")
            }
        }
        pendingOverlayAction = null
    }

    fun launchScreenCaptureFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (config.recordMic) {
            val micGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!micGranted) {
                showMicRationale = true
                return
            }
        }

        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    fun handleRecordButtonTap() {
        when (homeState.runtime.status) {
            RecorderStatus.RECORDING, RecorderStatus.PAUSED -> {
                homeVm.stopRecording(context)
            }
            RecorderStatus.COUNTDOWN -> {
                homeVm.cancelCountdown()
            }
            RecorderStatus.IDLE -> {
                if ((config.showFloatingBubble || config.showDrawToolbarByDefault) &&
                    !Settings.canDrawOverlays(context)
                ) {
                    pendingOverlayAction = { launchScreenCaptureFlow() }
                    showOverlayRationale = true
                } else {
                    launchScreenCaptureFlow()
                }
            }
        }
    }

    fun handleLiveDrawToggle() {
        if (Settings.canDrawOverlays(context)) {
            drawVm.toggleLiveOverlay(context)
        } else {
            pendingOverlayAction = { DrawOverlayService.start(context) }
            showOverlayRationale = true
        }
    }

    LaunchedEffect(homeState.runtime.lastError) {
        homeState.runtime.lastError?.let { err ->
            snackbarHostState.showSnackbar(err)
            homeVm.clearError()
        }
    }

    LaunchedEffect(galleryState.message) {
        galleryState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            galleryVm.clearMessage()
        }
    }

    LaunchedEffect(drawState.statusMessage) {
        drawState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            drawVm.clearMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPlayerRoute = currentDestination?.route == Routes.PLAYER

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail && !isPlayerRoute) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            bottomTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(id = tab.iconRes),
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldPrimary.copy(alpha = 0.14f)
                                    )
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
                    if (useRail && !isPlayerRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            bottomTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(id = tab.iconRes),
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag),
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldPrimary.copy(alpha = 0.14f)
                                    )
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                uiState = homeState,
                                onRecordAction = { handleRecordButtonTap() },
                                onPauseResume = { homeVm.togglePauseResume(context) },
                                onToggleDrawOverlay = { handleLiveDrawToggle() },
                                onCycleResolution = { homeVm.cycleResolution() },
                                onCycleFps = { homeVm.cycleFps() },
                                onToggleMic = { homeVm.toggleMic() },
                                onToggleDrawDefault = { homeVm.toggleDrawToolbarDefault() },
                                onOpenSettings = {
                                    navController.navigate(Routes.SETTINGS) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenGallery = {
                                    navController.navigate(Routes.GALLERY) {
                                        launchSingleTop = true
                                    }
                                },
                                onPlayRecording = { rec ->
                                    navController.navigate(Routes.player(rec.id))
                                }
                            )
                        }

                        composable(Routes.GALLERY) {
                            GalleryScreen(
                                uiState = galleryState,
                                thumbnails = thumbnails,
                                onRequestThumbnail = { galleryVm.loadThumbnailIfNeeded(it) },
                                onSearchChange = galleryVm::onSearchChange,
                                onSortSelected = galleryVm::onSortSelected,
                                onFilterSelected = galleryVm::onFilterSelected,
                                onToggleSelect = galleryVm::toggleSelection,
                                onClearSelection = galleryVm::clearSelection,
                                onPlayRecording = { rec ->
                                    navController.navigate(Routes.player(rec.id))
                                },
                                onRenameRecording = galleryVm::renameRecording,
                                onDeleteRecording = galleryVm::deleteRecording,
                                onDeleteSelected = galleryVm::deleteSelected,
                                onShareRecording = { rec ->
                                    galleryVm.createShareIntent(listOf(rec))?.let { intent ->
                                        context.startActivity(intent)
                                    }
                                },
                                onShareSelected = {
                                    galleryVm.createShareSelectedIntent()?.let { intent ->
                                        context.startActivity(intent)
                                    }
                                },
                                onStartRecordingCta = {
                                    navController.navigate(Routes.HOME) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(Routes.DRAW) {
                            DrawToolsScreen(
                                uiState = drawState,
                                onSelectTool = drawVm::selectTool,
                                onSelectColor = drawVm::selectColor,
                                onSelectStrokeWidth = drawVm::selectStrokeWidth,
                                onSelectStamp = drawVm::selectStamp,
                                onHistoryChanged = drawVm::updateHistory,
                                onToggleLiveOverlay = { handleLiveDrawToggle() },
                                onSaveSketch = drawVm::saveSketch
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                config = config,
                                viewModel = settingsViewModel,
                                onRequestOverlayPermission = {
                                    pendingOverlayAction = null
                                    showOverlayRationale = true
                                },
                                onRequestMicPermission = { showMicRationale = true },
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        showNotificationRationale = true
                                    }
                                }
                            )
                        }

                        composable(
                            route = Routes.PLAYER,
                            arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
                        ) { entry ->
                            val recordingId = entry.arguments?.getString("recordingId") ?: ""
                            val playerVm: VideoPlayerViewModel = viewModel(
                                key = "player_$recordingId",
                                factory = VideoPlayerViewModel.Factory(recordingId, container.repository)
                            )
                            val playerState by playerVm.uiState.collectAsStateWithLifecycle()

                            VideoPlayerScreen(
                                uiState = playerState,
                                onBack = { navController.popBackStack() },
                                onShare = {
                                    playerVm.createShareIntent()?.let { context.startActivity(it) }
                                },
                                onTrim = { startMs, endMs ->
                                    playerVm.trimVideo(startMs, endMs) {}
                                },
                                onMessageShown = playerVm::clearMessage
                            )
                        }
                    }
                }
            }
        }

        // Countdown Overlay
        if (homeState.runtime.status == RecorderStatus.COUNTDOWN) {
            CountdownOverlay(
                secondsRemaining = homeState.runtime.countdownRemaining,
                onCancel = { homeVm.cancelCountdown() }
            )
        }

        // Animated Cold-Start Splash Overlay
        SplashOverlay(
            visible = showSplash,
            onFinished = { showSplash = false }
        )
    }

    // Permission Rationale Dialogs
    if (showOverlayRationale) {
        PermissionRationaleDialog(
            title = "Overlay Permission",
            message = context.getString(R.string.permission_overlay_rationale),
            iconRes = R.drawable.ic_draw_pen,
            confirmLabel = "Allow Overlay",
            onConfirm = {
                showOverlayRationale = false
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlaySettingsLauncher.launch(intent)
            },
            onDismiss = {
                showOverlayRationale = false
                // Still allow recording without floating bubble if user prefers
                val action = pendingOverlayAction
                pendingOverlayAction = null
                if (action != null && homeState.runtime.status == RecorderStatus.IDLE) {
                    launchScreenCaptureFlow()
                }
            }
        )
    }

    if (showMicRationale) {
        PermissionRationaleDialog(
            title = "Microphone Permission",
            message = context.getString(R.string.permission_audio_rationale),
            iconRes = R.drawable.ic_mic,
            confirmLabel = "Continue",
            onConfirm = {
                showMicRationale = false
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = {
                showMicRationale = false
                val projectionManager =
                    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        )
    }

    if (showNotificationRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionRationaleDialog(
            title = "Notification Permission",
            message = context.getString(R.string.permission_notification_rationale),
            iconRes = R.drawable.ic_notification,
            confirmLabel = "Allow",
            onConfirm = {
                showNotificationRationale = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { showNotificationRationale = false }
        )
    }
}
