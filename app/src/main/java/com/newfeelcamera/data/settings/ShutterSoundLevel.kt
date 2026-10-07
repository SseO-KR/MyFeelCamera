package com.newfeelcamera.data.settings

enum class ShutterSoundLevel(
    val displayName: String,
    val volume: Float,
) {
    OFF("끔", 0f),
    LOW("작게", 0.3f),
    MEDIUM("보통", 0.65f),
    HIGH("크게", 1f),
}
