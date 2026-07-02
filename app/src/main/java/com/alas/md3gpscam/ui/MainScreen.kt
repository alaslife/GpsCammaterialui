package com.alas.md3gpscam.ui

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.alas.md3gpscam.ui.screens.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()

    // Permission State Tracker
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    )

    val cameraGranted = permissionsState.permissions.find { it.permission == Manifest.permission.CAMERA }?.status?.isGranted == true
    val locationGranted = permissionsState.permissions.find { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }?.status?.isGranted == true ||
                          permissionsState.permissions.find { it.permission == Manifest.permission.ACCESS_COARSE_LOCATION }?.status?.isGranted == true

    val essentialPermissionsGranted = cameraGranted && locationGranted

    if (essentialPermissionsGranted) {
        // Main App Layout
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Determine if bottom bar / FAB should be visible (hidden on Camera view)
        val showBars = currentRoute != "camera"

        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = showBars,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        val items = listOf(
                            Triple("home", Icons.Rounded.Home, "Home"),
                            Triple("gallery", Icons.Rounded.PhotoLibrary, "Gallery"),
                            Triple("map", Icons.Rounded.Map, "Map"),
                            Triple("templates", Icons.Rounded.Style, "Designs"),
                            Triple("settings", Icons.Rounded.Settings, "Settings")
                        )
                        items.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = {
                                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showBars && currentRoute == "home",
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("camera")
                        },
                        icon = { Icon(Icons.Rounded.Camera, contentDescription = "Capture") },
                        text = { Text("Capture") },
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 1.dp
                        )
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (showBars) innerPadding.calculateBottomPadding() else 0.dp),
                    enterTransition = {
                        val fromRoute = initialState.destination.route
                        val toRoute = targetState.destination.route
                        if (fromRoute == "camera" || toRoute == "camera") {
                            fadeIn(animationSpec = tween(300))
                        } else {
                            val order = listOf("home", "gallery", "map", "templates", "settings", "privacy")
                            val fromIndex = order.indexOf(fromRoute)
                            val toIndex = order.indexOf(toRoute)
                            val direction = if (fromIndex < toIndex)
                                AnimatedContentTransitionScope.SlideDirection.Left
                            else
                                AnimatedContentTransitionScope.SlideDirection.Right
                            slideIntoContainer(direction, animationSpec = spring())
                        }
                    },
                    exitTransition = {
                        val fromRoute = initialState.destination.route
                        val toRoute = targetState.destination.route
                        if (fromRoute == "camera" || toRoute == "camera") {
                            fadeOut(animationSpec = tween(200))
                        } else {
                            val order = listOf("home", "gallery", "map", "templates", "settings", "privacy")
                            val fromIndex = order.indexOf(fromRoute)
                            val toIndex = order.indexOf(toRoute)
                            val direction = if (fromIndex < toIndex)
                                AnimatedContentTransitionScope.SlideDirection.Left
                            else
                                AnimatedContentTransitionScope.SlideDirection.Right
                            slideOutOfContainer(direction, animationSpec = spring())
                        }
                    }
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
                            onNavigateToCamera = { navController.navigate("camera") },
                            onNavigateToGallery = { navController.navigate("gallery") },
                            onNavigateToMap = { navController.navigate("map") },
                            onNavigateToTemplates = { navController.navigate("templates") },
                            onPhotoClick = { photo ->
                                if (photo.isVideo) {
                                    val encodedPath = java.net.URLEncoder.encode(photo.filePath, "UTF-8")
                                    navController.navigate("videoPlayer/$encodedPath")
                                } else {
                                    viewModel.setSelectedPhotoForGallery(photo)
                                    navController.navigate("gallery")
                                }
                            }
                        )
                    }

                    composable("gallery") {
                        GalleryScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onPhotoSelected = {},
                            onPlayVideo = { photo ->
                                val encodedPath = java.net.URLEncoder.encode(photo.filePath, "UTF-8")
                                navController.navigate("videoPlayer/$encodedPath")
                            }
                        )
                    }

                    composable("map") {
                        MapScreen(
                            viewModel = viewModel,
                            onPhotoSelected = { photo ->
                                navController.navigate("gallery")
                            }
                        )
                    }

                    composable("templates") {
                        TemplatesScreen(viewModel = viewModel)
                    }

                    composable("settings") {
                        SettingsScreen(viewModel = viewModel) {
                            navController.navigate("privacy")
                        }
                    }

                    composable("privacy") {
                        PrivacyScreen {
                            navController.popBackStack()
                        }
                    }

                    composable("camera") {
                        CameraScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onPhotoCaptured = { photo ->
                                navController.popBackStack()
                                navController.navigate("gallery")
                            }
                        )
                    }

                    composable("videoPlayer/{videoUri}") { backStackEntry ->
                        val videoUri = backStackEntry.arguments?.getString("videoUri")
                        if (videoUri != null) {
                            val decodedUri = java.net.URLDecoder.decode(videoUri, "UTF-8")
                            VideoPlayerScreen(
                                videoUri = decodedUri,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Welcome and Permissions request display
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = "Security",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "GPS Camera",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "To overlay location metadata onto your photos and record geotagged videos in real-time, this application requires access to your camera, GPS, and microphone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                FilledTonalButton(
                    onClick = {
                        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        permissionsState.launchMultiplePermissionRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Grant Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

