package com.newfeelcamera.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newfeelcamera.R
import com.newfeelcamera.ui.design.Accent
import com.newfeelcamera.ui.design.Background
import com.newfeelcamera.ui.design.Ink
import com.newfeelcamera.ui.design.InkDim
import com.newfeelcamera.ui.design.Rail

@Composable
fun TelemetryConsentDialog(
    isSaving: Boolean,
    errorMessageRes: Int?,
    onSave: (analyticsEnabled: Boolean, crashReportingEnabled: Boolean) -> Unit,
) {
    var analyticsEnabled by rememberSaveable { mutableStateOf(false) }
    var crashReportingEnabled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!isSaving) onSave(false, false) },
        title = { Text(stringResource(R.string.telemetry_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.telemetry_consent_description))
                TelemetryChoices(
                    analyticsEnabled = analyticsEnabled,
                    crashReportingEnabled = crashReportingEnabled,
                    onAnalyticsChanged = { analyticsEnabled = it },
                    onCrashReportingChanged = { crashReportingEnabled = it },
                    enabled = !isSaving,
                )
                TextButton(onClick = { openPrivacyPolicy(context) }) {
                    Text(stringResource(R.string.privacy_policy), color = Accent)
                }
                errorMessageRes?.let { Text(stringResource(it), color = Accent) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(analyticsEnabled, crashReportingEnabled) }, enabled = !isSaving) {
                Text(stringResource(R.string.telemetry_save_choices), color = Ink)
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(false, false) }, enabled = !isSaving) {
                Text(stringResource(R.string.telemetry_decline), color = Ink)
            }
        },
        containerColor = Background,
        titleContentColor = Ink,
        textContentColor = Ink,
    )
}

@Composable
internal fun TelemetryChoices(
    analyticsEnabled: Boolean,
    crashReportingEnabled: Boolean,
    onAnalyticsChanged: (Boolean) -> Unit,
    onCrashReportingChanged: (Boolean) -> Unit,
    enabled: Boolean,
) {
    TelemetryChoiceRow(
        title = stringResource(R.string.analytics_setting),
        description = stringResource(R.string.analytics_setting_description),
        checked = analyticsEnabled,
        onCheckedChange = onAnalyticsChanged,
        enabled = enabled,
    )
    TelemetryChoiceRow(
        title = stringResource(R.string.crash_reporting_setting),
        description = stringResource(R.string.crash_reporting_setting_description),
        checked = crashReportingEnabled,
        onCheckedChange = onCrashReportingChanged,
        enabled = enabled,
    )
}

@Composable
private fun TelemetryChoiceRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Medium)
            Text(description, color = InkDim, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, uncheckedTrackColor = Rail),
        )
    }
}
