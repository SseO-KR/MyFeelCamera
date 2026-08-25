package com.newfeelcamera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.newfeelcamera.ui.camera.CameraRoute

private const val CameraDestination = "camera"

@Composable
fun FeelCameraNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CameraDestination,
        modifier = modifier,
    ) {
        composable(CameraDestination) {
            CameraRoute()
        }
    }
}
