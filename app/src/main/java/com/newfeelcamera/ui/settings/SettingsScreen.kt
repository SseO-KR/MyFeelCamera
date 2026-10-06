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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.billing.PurchaseRefreshState
import com.newfeelcamera.data.billing.PurchaseLaunchState
import com.newfeelcamera.data.billing.UnlimitedAccessState
import com.newfeelcamera.data.billing.UnlimitedProductState
import com.newfeelcamera.ui.design.Accent
import com.newfeelcamera.ui.design.Background
import com.newfeelcamera.ui.design.Ink
import com.newfeelcamera.ui.design.InkDim
import com.newfeelcamera.ui.design.Rail

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
                Text(text = "뒤로", color = if (uiState.isSaving) InkDim else Ink)
            }
            Text(
                text = "설정",
                modifier = Modifier.align(Alignment.Center),
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (uiState.isLoading) {
            Text(
                text = "설정을 불러오는 중입니다.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                color = InkDim,
                fontSize = 14.sp,
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column {
                    Text(
                        text = "시작 필름",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "카메라를 열 때 선택할 필름",
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
                                text = film.displayName,
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
                            text = "격자 표시",
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "프리뷰에 3×3 구도선을 표시",
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
                        text = "무제한 촬영",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val productMessage = when (unlimitedAccessState) {
                        UnlimitedAccessState.Active -> "무제한 촬영을 사용 중입니다."
                        UnlimitedAccessState.Pending -> "결제 승인을 기다리고 있습니다."
                        UnlimitedAccessState.VerificationFailed ->
                            "구매 확인에 실패했습니다. 구매 내역을 다시 확인해 주세요."
                        UnlimitedAccessState.Unknown -> "구매 내역을 확인하는 중입니다."
                        UnlimitedAccessState.NotPurchased -> when (unlimitedProductState) {
                            UnlimitedProductState.NotLoaded,
                            UnlimitedProductState.Loading -> "상품 정보를 확인하는 중입니다."
                            is UnlimitedProductState.Available ->
                                "한 번 결제하면 촬영 횟수 제한이 해제됩니다."
                            UnlimitedProductState.Unavailable ->
                                "현재 구매 가능한 상품 정보를 확인할 수 없습니다."
                            is UnlimitedProductState.Failed ->
                                "상품 정보를 불러오지 못했습니다."
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
                            Text("상품 정보 다시 확인", color = Accent)
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
                                text = "무제한 촬영 구매 · ${unlimitedProductState.formattedPrice}",
                                color = Accent,
                            )
                        }
                    }
                    if (unlimitedAccessState == UnlimitedAccessState.VerificationFailed) {
                        TextButton(onClick = onRetryPurchaseRefresh) {
                            Text("구매 내역 다시 확인", color = Accent)
                        }
                    }
                    if (unlimitedAccessState == UnlimitedAccessState.NotPurchased) {
                        when (purchaseLaunchState) {
                            PurchaseLaunchState.Idle -> Unit
                            PurchaseLaunchState.Loading -> Text(
                                text = "결제 화면을 준비하는 중입니다.",
                                color = InkDim,
                                fontSize = 13.sp,
                            )
                            is PurchaseLaunchState.Failed -> Text(
                                text = "결제를 진행하지 못했습니다. 다시 시도해 주세요. (${purchaseLaunchState.responseCode})",
                                color = Accent,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if(purchaseRefreshState is PurchaseRefreshState.Failed){
                    Column {
                        Text(
                            text = "구매 내역을 확인하지 못했습니다.",
                            color = InkDim,
                            fontSize = 13.sp,
                        )
                        TextButton(
                            onClick = onRetryPurchaseRefresh,
                        ) {
                            Text("다시 시도", color = Accent)
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = Accent,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
