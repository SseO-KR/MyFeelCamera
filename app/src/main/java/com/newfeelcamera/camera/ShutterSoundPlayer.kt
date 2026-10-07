package com.newfeelcamera.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaActionSound
import android.media.SoundPool
import android.util.Log
import androidx.camera.core.CameraInfo
import com.newfeelcamera.R
import com.newfeelcamera.data.settings.ShutterSoundLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject

class ShutterSoundPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : Closeable {
    private val systemSound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
    }
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundId: Int
    @Volatile private var soundLoaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            soundLoaded = status == 0
            if (status != 0) Log.w(TAG, "Shutter sound could not be loaded: $status")
        }
        soundId = soundPool.load(context, R.raw.shutter_click, 1)
    }

    fun play(level: ShutterSoundLevel) {
        if (CameraInfo.mustPlayShutterSound()) {
            systemSound.play(MediaActionSound.SHUTTER_CLICK)
            return
        }

        if (level == ShutterSoundLevel.OFF) return

        if (!soundLoaded ||
            soundPool.play(soundId, level.volume, level.volume, 1, 0, 1f) == 0
        ) {
            systemSound.play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    override fun close() {
        soundPool.release()
        systemSound.release()
    }

    private companion object {
        const val TAG = "ShutterSoundPlayer"
    }
}
