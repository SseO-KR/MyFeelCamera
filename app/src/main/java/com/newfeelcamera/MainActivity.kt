package com.newfeelcamera

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.newfeelcamera.data.billing.BillingRepository
import com.newfeelcamera.ui.navigation.FeelCameraNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var volumeShutterHandler: (() -> Unit)? = null

    @Inject
    lateinit var billingRepository: BillingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeelCameraNavHost(
                registerVolumeShutterHandler = ::registerVolumeShutterHandler,
            )
        }
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
        billingRepository.refreshPurchases()
    }

    override fun onDestroy() {
        if(!isChangingConfigurations){
            billingRepository.close()
        }
        super.onDestroy()
    }
}
