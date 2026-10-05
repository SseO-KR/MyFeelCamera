package com.newfeelcamera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.newfeelcamera.ui.camera.CameraRoute
import com.newfeelcamera.ui.settings.SettingsRoute

private const val CameraDestination = "camera"
private const val SettingsDestination = "settings"

@Composable
fun FeelCameraNavHost(
    modifier: Modifier = Modifier,
    registerVolumeShutterHandler: (() -> Unit) -> (() -> Unit),
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CameraDestination,
        modifier = modifier,
    ) {
        composable(CameraDestination) {
            CameraRoute(
                registerVolumeShutterHandler = registerVolumeShutterHandler,
                onOpenSettings = {
                    navController.navigate(SettingsDestination) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(SettingsDestination) {
            SettingsRoute(
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}
