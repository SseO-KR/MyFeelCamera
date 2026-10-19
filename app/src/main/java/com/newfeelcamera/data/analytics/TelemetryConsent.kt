package com.newfeelcamera.data.analytics

data class TelemetryConsent(
    val hasMadeChoice: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = false,
) {
    val allowsAnalytics: Boolean get() = hasMadeChoice && analyticsEnabled
    val allowsCrashReporting: Boolean get() = hasMadeChoice && crashReportingEnabled
}

interface TelemetryCollectionController {
    val canRecordAnalyticsEvents: Boolean
    fun applyConsent(consent: TelemetryConsent)
}
