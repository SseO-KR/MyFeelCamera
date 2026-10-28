package com.sseo.feelcamera.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sseo.feelcamera.BuildConfig
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.settings.ShutterSoundLevel
import com.sseo.feelcamera.ui.design.Accent
import com.sseo.feelcamera.ui.design.Background
import com.sseo.feelcamera.ui.design.Ink
import com.sseo.feelcamera.ui.design.InkDim
import com.sseo.feelcamera.ui.design.Rail
import com.sseo.feelcamera.ui.labelRes

private const val PRIVACY_POLICY_URL =
    "https://curly-crowberry-6e3.notion.site/FeelCamera-3f4a6411e1a4805f968df6a70c04a331"

internal fun openPrivacyPolicy(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.privacy_policy_open_failed, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.privacy_policy_open_failed, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreen(
        uiState = uiState,
        onStartFilmSelected = viewModel::setStartFilm,
        onGridChanged = viewModel::setShowGrid,
        onShutterSoundMutedChanged = viewModel::setShutterSoundMuted,
        onShutterSoundLevelSelected = viewModel::setShutterSoundLevel,
        onAnalyticsChanged = { enabled ->
            viewModel.setTelemetryConsent(enabled, uiState.telemetryConsent.crashReportingEnabled)
        },
        onCrashReportingChanged = { enabled ->
            viewModel.setTelemetryConsent(uiState.telemetryConsent.analyticsEnabled, enabled)
        },
        onPrivacyPolicyClick = { openPrivacyPolicy(context) },
        onBackClick = onBackClick,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onStartFilmSelected: (FilmPreset) -> Unit,
    onGridChanged: (Boolean) -> Unit,
    onShutterSoundMutedChanged: (Boolean) -> Unit,
    onShutterSoundLevelSelected: (ShutterSoundLevel) -> Unit,
    onAnalyticsChanged: (Boolean) -> Unit,
    onCrashReportingChanged: (Boolean) -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.isSaving) { }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBackClick,
                enabled = !uiState.isSaving,
            ) {
                Text(text = stringResource(R.string.back), color = if (uiState.isSaving) InkDim else Ink)
            }
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.weight(1f).semantics { heading() },
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        uiState.errorMessageRes?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = Accent,
                fontSize = 13.sp,
            )
        }

        if (uiState.isLoading) {
            Text(
                text = stringResource(R.string.settings_loading),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                color = InkDim,
                fontSize = 14.sp,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    Text(
                        text = stringResource(R.string.start_film),
                        modifier = Modifier.semantics { heading() },
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.start_film_description),
                        color = InkDim,
                        fontSize = 13.sp,
                    )
                    FilmPreset.entries.forEach { film ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = uiState.startFilm == film,
                                    enabled = !uiState.isSaving,
                                    role = Role.RadioButton,
                                    onClick = { onStartFilmSelected(film) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = uiState.startFilm == film,
                                onClick = null,
                                enabled = !uiState.isSaving,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Accent,
                                    unselectedColor = InkDim,
                                ),
                            )
                            Text(
                                text = stringResource(film.labelRes),
                                modifier = Modifier.padding(start = 12.dp),
                                color = Ink,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = uiState.showGrid,
                            enabled = !uiState.isSaving,
                            role = Role.Switch,
                            onValueChange = onGridChanged,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.show_grid),
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.show_grid_description),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = uiState.showGrid,
                        onCheckedChange = null,
                        enabled = !uiState.isSaving,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent,
                            uncheckedTrackColor = Rail,
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = uiState.isShutterSoundMuted,
                            enabled = !uiState.isSaving,
                            role = Role.Switch,
                            onValueChange = onShutterSoundMutedChanged,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.silent_capture),
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                if (uiState.usesSystemShutterSound) R.string.system_shutter_sound_description
                                else R.string.silent_capture_description,
                            ),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = uiState.isShutterSoundMuted,
                        onCheckedChange = null,
                        enabled = !uiState.isSaving,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent,
                            uncheckedTrackColor = Rail,
                        ),
                    )
                }

                if (!uiState.usesSystemShutterSound) {
                    Column(modifier = Modifier.selectableGroup()) {
                        Text(
                            text = stringResource(R.string.shutter_sound),
                            modifier = Modifier.semantics { heading() },
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.shutter_sound_description),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                        ShutterSoundLevel.entries.filter { it != ShutterSoundLevel.OFF }.forEach { level ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = uiState.shutterSoundLevel == level,
                                        enabled = !uiState.isSaving && !uiState.isShutterSoundMuted,
                                        role = Role.RadioButton,
                                        onClick = { onShutterSoundLevelSelected(level) },
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = uiState.shutterSoundLevel == level,
                                    onClick = null,
                                    enabled = !uiState.isSaving && !uiState.isShutterSoundMuted,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Accent,
                                        unselectedColor = InkDim,
                                    ),
                                )
                                Text(
                                    text = stringResource(level.labelRes),
                                    modifier = Modifier.padding(start = 12.dp),
                                    color = if (uiState.isShutterSoundMuted) InkDim else Ink,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }

                if (BuildConfig.FIREBASE_CONFIGURED && BuildConfig.TELEMETRY_ALLOWED) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.telemetry_title),
                            modifier = Modifier.semantics { heading() },
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        TelemetryChoices(
                            analyticsEnabled = uiState.telemetryConsent.analyticsEnabled,
                            crashReportingEnabled = uiState.telemetryConsent.crashReportingEnabled,
                            onAnalyticsChanged = onAnalyticsChanged,
                            onCrashReportingChanged = onCrashReportingChanged,
                            enabled = !uiState.isSaving,
                        )
                        Text(
                            text = stringResource(R.string.crash_reporting_restart_notice),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onPrivacyPolicyClick)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.privacy_policy),
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.privacy_policy_description),
                        color = InkDim,
                        fontSize = 13.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.current_version),
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        color = InkDim,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = InkDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
