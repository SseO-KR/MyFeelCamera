plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// 설정 파일이 있는 개발 환경과 모든 Release 빌드에 운영 모니터링 SDK를 포함한다.
// 설정 파일이 없는 공개 소스의 Debug 빌드만 no-op 구현을 사용한다.
val firebaseConfigured = file("google-services.json").isFile
val firebaseDebugCollectionEnabled = firebaseConfigured &&
    providers.gradleProperty("firebaseDebugCollectionEnabled")
        .map(String::toBoolean)
        .getOrElse(false)
// v1 무료 배포에는 이전 결제·촬영 한도 코드를 포함하지 않는다.
val monetizationCodeEnabled = providers.gradleProperty("monetizationCodeEnabled")
    .map(String::toBoolean)
    .getOrElse(false)

if (firebaseConfigured) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
}

// Firebase 설정이 없는 환경에서도 Debug 빌드로 카메라 기능을 개발할 수 있다.
android.sourceSets.named("main") {
    kotlin.directories.add(
        if (firebaseConfigured) "src/firebase/java" else "src/noFirebase/java"
    )
    if (monetizationCodeEnabled) {
        kotlin.directories.add("src/deferredMonetization/java")
    }
}
if (monetizationCodeEnabled) {
    android.sourceSets.named("test") {
        kotlin.directories.add("src/deferredMonetizationTest/java")
    }
}

android {
    namespace = "com.sseo.feelcamera"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sseo.feelcamera"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())

        if (monetizationCodeEnabled) {
            val playBillingPublicKey = providers.gradleProperty("playBillingPublicKey")
                .map(String::trim)
                .getOrElse("")
            buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"$playBillingPublicKey\"")
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "TELEMETRY_ALLOWED", firebaseDebugCollectionEnabled.toString())
        }
        release {
            buildConfigField("boolean", "TELEMETRY_ALLOWED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9.0+ built-in Kotlin: 별도 kotlinOptions{} DSL이 없다.
    // JVM target은 compileOptions의 sourceCompatibility/targetCompatibility를 따라간다.

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    if (firebaseConfigured) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }

    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    if (monetizationCodeEnabled) {
        implementation(libs.billing.ktx)
    }
    implementation(libs.play.app.update)
}

// 모니터링이 빠진 AAB가 실수로 배포되지 않도록 Release 빌드에서 확인한다.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(firebaseConfigured) {
            "Release builds require app/google-services.json for Firebase Analytics and Crashlytics."
        }
    }
}
