package com.newfeelcamera.data.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.newfeelcamera.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTelemetryCollectionController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TelemetryCollectionController {
    @Volatile
    override var canRecordAnalyticsEvents: Boolean = false
        private set

    private var initialized = false
    private var previousConsent: TelemetryConsent? = null

    override fun applyConsent(consent: TelemetryConsent) {
        val effectiveConsent = if (BuildConfig.TELEMETRY_ALLOWED) consent else TelemetryConsent()
        if (previousConsent == effectiveConsent) return
        canRecordAnalyticsEvents = false

        // 첫 실행·모두 거부한 상태에서는 Firebase를 초기화하지 않는다.
        if (!initialized && !effectiveConsent.allowsAnalytics && !effectiveConsent.allowsCrashReporting) {
            previousConsent = effectiveConsent
            return
        }
        if (!initialized) {
            checkNotNull(FirebaseApp.initializeApp(context)) { "Firebase configuration is missing." }
            initialized = true
        }

        val analytics = FirebaseAnalytics.getInstance(context)
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (effectiveConsent.allowsAnalytics) FirebaseAnalytics.ConsentStatus.GRANTED
                    else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
        analytics.setAnalyticsCollectionEnabled(effectiveConsent.allowsAnalytics)
        if (previousConsent?.allowsAnalytics == true && !effectiveConsent.allowsAnalytics) {
            analytics.resetAnalyticsData()
        }

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(effectiveConsent.allowsCrashReporting)
        // SDK의 수집 중단은 다음 프로세스 시작부터 적용된다. 거부 시 미전송 보고서도 삭제한다.
        if (!effectiveConsent.allowsCrashReporting) crashlytics.deleteUnsentReports()
        canRecordAnalyticsEvents = effectiveConsent.allowsAnalytics
        previousConsent = effectiveConsent
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryCollectionModule {
    @Binds
    abstract fun bindController(implementation: FirebaseTelemetryCollectionController): TelemetryCollectionController
}
