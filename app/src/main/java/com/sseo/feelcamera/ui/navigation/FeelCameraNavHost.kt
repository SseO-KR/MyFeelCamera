package com.sseo.feelcamera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sseo.feelcamera.ui.camera.CameraRoute
import com.sseo.feelcamera.ui.settings.SettingsRoute

private const val CameraDestination = "camera"
private const val SettingsDestination = "settings"

@Composable
fun FeelCameraNavHost(
    modifier: Modifier = Modifier,
    registerVolumeShutterHandler: (() -> Unit) -> (() -> Unit),
    onCameraPermissionHandled: () -> Unit,
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
                onCameraPermissionHandled = onCameraPermissionHandled,
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
