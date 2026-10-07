package com.newfeelcamera

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.newfeelcamera.data.billing.BillingRepository
import com.newfeelcamera.ui.design.Accent
import com.newfeelcamera.ui.design.Background
import com.newfeelcamera.ui.design.Ink
import com.newfeelcamera.ui.design.InkDim
import com.newfeelcamera.ui.navigation.FeelCameraNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.net.toUri

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var volumeShutterHandler: (() -> Unit)? = null
    private var cameraPermissionHandled by mutableStateOf(false)
    private var updateAvailable by mutableStateOf(false)
    private var updateCheckInFlight = false
    private var updatePromptHandled = false

    @Inject
    lateinit var billingRepository: BillingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatePromptHandled = savedInstanceState?.getBoolean(UPDATE_PROMPT_HANDLED) ?: false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent {
            FeelCameraNavHost(
                registerVolumeShutterHandler = ::registerVolumeShutterHandler,
                onPurchaseUnlimited = { billingRepository.launchUnlimitedPurchase(this) },
                onCameraPermissionHandled = { cameraPermissionHandled = true },
            )
            if (cameraPermissionHandled && updateAvailable && !updatePromptHandled) {
                AlertDialog(
                    onDismissRequest = ::dismissUpdatePrompt,
                    title = { Text("새 버전이 있습니다") },
                    text = { Text("플레이스토어에서 업데이트할 수 있습니다.") },
                    confirmButton = {
                        TextButton(onClick = {
                            dismissUpdatePrompt()
                            openPlayStore()
                        }) {
                            Text("업데이트", color = Accent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = ::dismissUpdatePrompt) {
                            Text("나중에", color = InkDim)
                        }
                    },
                    containerColor = Background,
                    titleContentColor = Ink,
                    textContentColor = InkDim,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(UPDATE_PROMPT_HANDLED, updatePromptHandled)
        super.onSaveInstanceState(outState)
    }

    private fun dismissUpdatePrompt() {
        updatePromptHandled = true
        updateAvailable = false
    }

    private fun checkForUpdate() {
        if (updatePromptHandled || updateAvailable || updateCheckInFlight) return
        updateCheckInFlight = true
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnCompleteListener { task ->
            updateCheckInFlight = false
            if (!isDestroyed && task.isSuccessful &&
                task.result.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            ) {
                updateAvailable = true
            }
        }
    }

    private fun openPlayStore() {
        val uri = "https://play.google.com/store/apps/details?id=$packageName".toUri()
        val playStoreIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")
        if (tryStartActivity(playStoreIntent)) return
        if (!tryStartActivity(Intent(Intent.ACTION_VIEW, uri))) {
            Toast.makeText(this, "플레이스토어를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryStartActivity(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun registerVolumeShutterHandler(handler: () -> Unit): () -> Unit {
        volumeShutterHandler = handler
        return {
            if (volumeShutterHandler === handler) {
                volumeShutterHandler = null
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = volumeShutterHandler
        val isVolumeKey =
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (handler != null && isVolumeKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                handler()
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        billingRepository.refreshPurchases()
        checkForUpdate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        if(!isChangingConfigurations){
            billingRepository.close()
        }
        super.onDestroy()
    }

    private companion object {
        const val UPDATE_PROMPT_HANDLED = "update_prompt_handled"
    }
}
