package com.newfeelcamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newfeelcamera.BuildConfig
import com.newfeelcamera.R
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.settings.ShutterSoundLevel
import com.newfeelcamera.data.billing.PurchaseRefreshState
import com.newfeelcamera.data.billing.PurchaseLaunchState
import com.newfeelcamera.data.billing.UnlimitedAccessState
import com.newfeelcamera.data.billing.UnlimitedProductState
import com.newfeelcamera.ui.design.Accent
import com.newfeelcamera.ui.design.Background
import com.newfeelcamera.ui.design.Ink
import com.newfeelcamera.ui.design.InkDim
import com.newfeelcamera.ui.design.Rail
import com.newfeelcamera.ui.labelRes

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    onPurchaseUnlimited: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val purchaseRefreshState by viewModel.purchaseRefreshState.collectAsStateWithLifecycle()
    val unlimitedProductState by viewModel.unlimitedProductState.collectAsStateWithLifecycle()
    val unlimitedAccessState by viewModel.unlimitedAccessState.collectAsStateWithLifecycle()
    val purchaseLaunchState by viewModel.purchaseLaunchState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        purchaseRefreshState = purchaseRefreshState,
        unlimitedProductState = unlimitedProductState,
        unlimitedAccessState = unlimitedAccessState,
        purchaseLaunchState = purchaseLaunchState,
        onPurchaseUnlimited = onPurchaseUnlimited,
        onRetryPurchaseRefresh = viewModel::retryPurchaseRefresh,
        onRetryProductDetails = viewModel::retryProductDetails,
        onStartFilmSelected = viewModel::setStartFilm,
        onGridChanged = viewModel::setShowGrid,
        onShutterSoundLevelSelected = viewModel::setShutterSoundLevel,
        onBackClick = onBackClick,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    purchaseRefreshState: PurchaseRefreshState,
    unlimitedProductState: UnlimitedProductState,
    unlimitedAccessState: UnlimitedAccessState,
    purchaseLaunchState: PurchaseLaunchState,
    onPurchaseUnlimited: () -> Unit,
    onRetryPurchaseRefresh: () -> Unit,
    onRetryProductDetails: () -> Unit,
    onStartFilmSelected: (FilmPreset) -> Unit,
    onGridChanged: (Boolean) -> Unit,
    onShutterSoundLevelSelected: (ShutterSoundLevel) -> Unit,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TextButton(
                onClick = onBackClick,
                enabled = !uiState.isSaving,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text(text = stringResource(R.string.back), color = if (uiState.isSaving) InkDim else Ink)
            }
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.align(Alignment.Center),
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
                Column {
                    Text(
                        text = stringResource(R.string.start_film),
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
                                .clickable(enabled = !uiState.isSaving) { onStartFilmSelected(film) }
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
                        .clickable(enabled = !uiState.isSaving) {
                            onGridChanged(!uiState.showGrid)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
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

                Column {
                    Text(
                        text = stringResource(R.string.shutter_sound),
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (uiState.mustPlayShutterSound) {
                        Text(
                            text = stringResource(R.string.shutter_sound_required),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.shutter_sound_description),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                        ShutterSoundLevel.entries.forEach { level ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !uiState.isSaving) {
                                        onShutterSoundLevelSelected(level)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = uiState.shutterSoundLevel == level,
                                    onClick = null,
                                    enabled = !uiState.isSaving,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Accent,
                                        unselectedColor = InkDim,
                                    ),
                                )
                                Text(
                                    text = stringResource(level.labelRes),
                                    modifier = Modifier.padding(start = 12.dp),
                                    color = Ink,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }

                Column {
                    Text(
                        text = stringResource(R.string.unlimited_captures),
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val productMessage = when (unlimitedAccessState) {
                        UnlimitedAccessState.Active -> stringResource(R.string.unlimited_active)
                        UnlimitedAccessState.Pending -> stringResource(R.string.purchase_pending)
                        UnlimitedAccessState.VerificationFailed ->
                            stringResource(R.string.purchase_verification_failed)
                        UnlimitedAccessState.Unknown -> stringResource(R.string.purchase_checking)
                        UnlimitedAccessState.NotPurchased -> when (unlimitedProductState) {
                            UnlimitedProductState.NotLoaded,
                            UnlimitedProductState.Loading -> stringResource(R.string.product_loading)
                            is UnlimitedProductState.Available ->
                                stringResource(R.string.unlimited_description)
                            UnlimitedProductState.Unavailable ->
                                stringResource(R.string.product_unavailable)
                            is UnlimitedProductState.Failed ->
                                stringResource(R.string.product_load_failed)
                        }
                    }
                    Text(
                        text = productMessage,
                        color = InkDim,
                        fontSize = 13.sp,
                    )
                    if (unlimitedProductState is UnlimitedProductState.Unavailable ||
                        unlimitedProductState is UnlimitedProductState.Failed
                    ) {
                        TextButton(onClick = onRetryProductDetails) {
                            Text(stringResource(R.string.retry_product), color = Accent)
                        }
                    }
                    if (unlimitedAccessState == UnlimitedAccessState.NotPurchased &&
                        unlimitedProductState is UnlimitedProductState.Available
                    ) {
                        TextButton(
                            onClick = onPurchaseUnlimited,
                            enabled = !uiState.isSaving &&
                                purchaseLaunchState != PurchaseLaunchState.Loading,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.buy_unlimited,
                                    unlimitedProductState.formattedPrice,
                                ),
                                color = Accent,
                            )
                        }
                    }
                    if (unlimitedAccessState == UnlimitedAccessState.VerificationFailed) {
                        TextButton(onClick = onRetryPurchaseRefresh) {
                            Text(stringResource(R.string.retry_purchases), color = Accent)
                        }
                    }
                    if (unlimitedAccessState == UnlimitedAccessState.NotPurchased) {
                        when (purchaseLaunchState) {
                            PurchaseLaunchState.Idle -> Unit
                            PurchaseLaunchState.Loading -> Text(
                                text = stringResource(R.string.purchase_preparing),
                                color = InkDim,
                                fontSize = 13.sp,
                            )
                            is PurchaseLaunchState.Failed -> Text(
                                text = stringResource(
                                    R.string.purchase_launch_failed,
                                    purchaseLaunchState.responseCode,
                                ),
                                color = Accent,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if(purchaseRefreshState is PurchaseRefreshState.Failed){
                    Column {
                        Text(
                            text = stringResource(R.string.purchase_refresh_failed),
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                        TextButton(
                            onClick = onRetryPurchaseRefresh,
                        ) {
                            Text(stringResource(R.string.retry), color = Accent)
                        }
                    }
                }

                uiState.errorMessageRes?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        color = Accent,
                        fontSize = 13.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.current_version), color = InkDim, fontSize = 13.sp)
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
