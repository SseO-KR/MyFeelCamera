plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// 공개 저장소에는 JSON을 포함하지 않는다. 파일이 없으면 Firebase 없이 빌드한다.
val firebaseEnabled = providers.gradleProperty("firebaseEnabled")
    .map(String::toBoolean)
    .getOrElse(true) && file("google-services.json").isFile
val firebaseDebugCollectionEnabled = firebaseEnabled &&
    providers.gradleProperty("firebaseDebugCollectionEnabled")
        .map(String::toBoolean)
        .getOrElse(false)

if (firebaseEnabled) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
}

val playBillingPublicKey = providers.gradleProperty("playBillingPublicKey")
    .map(String::trim)
    .getOrElse("")

android {
    namespace = "com.newfeelcamera"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.newfeelcamera"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"$playBillingPublicKey\"")
    }

    buildTypes {
        debug {
            manifestPlaceholders["firebaseCollectionEnabled"] = firebaseDebugCollectionEnabled.toString()
        }
        release {
            manifestPlaceholders["firebaseCollectionEnabled"] = firebaseEnabled.toString()
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
    if (firebaseEnabled) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }

    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.core.ktx)
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
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    implementation(libs.billing.ktx)
}
