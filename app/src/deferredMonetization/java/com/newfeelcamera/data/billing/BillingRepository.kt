package com.newfeelcamera.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.newfeelcamera.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

sealed interface PurchaseRefreshState {
    data object NotStarted : PurchaseRefreshState
    data object Loading : PurchaseRefreshState
    data object Succeeded : PurchaseRefreshState
    data class Failed(val responseCode: Int) : PurchaseRefreshState
}

sealed interface UnlimitedAccessState {
    data object Unknown : UnlimitedAccessState
    data object NotPurchased : UnlimitedAccessState
    data object Pending : UnlimitedAccessState
    data object Active : UnlimitedAccessState
    data object VerificationFailed : UnlimitedAccessState
}

sealed interface UnlimitedProductState {
    data object NotLoaded : UnlimitedProductState
    data object Loading : UnlimitedProductState
    data class Available(val formattedPrice: String) : UnlimitedProductState
    data object Unavailable : UnlimitedProductState
    data class Failed(val responseCode: Int) : UnlimitedProductState
}

sealed interface PurchaseLaunchState {
    data object Idle : PurchaseLaunchState
    data object Loading : PurchaseLaunchState
    data class Failed(val responseCode: Int) : PurchaseLaunchState
}

@ActivityRetainedScoped
class BillingRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private enum class AcknowledgementState { InFlight, Succeeded }

    private val acknowledgementStates =
        ConcurrentHashMap<String, AcknowledgementState>()

    private val _refreshState =
        MutableStateFlow<PurchaseRefreshState>(PurchaseRefreshState.NotStarted)
    val refreshState = _refreshState.asStateFlow()

    private val _unlimitedAccessState =
        MutableStateFlow<UnlimitedAccessState>(UnlimitedAccessState.Unknown)
    val unlimitedAccessState = _unlimitedAccessState.asStateFlow()

    private val _productState =
        MutableStateFlow<UnlimitedProductState>(UnlimitedProductState.NotLoaded)
    val productState = _productState.asStateFlow()

    private val _purchaseLaunchState =
        MutableStateFlow<PurchaseLaunchState>(PurchaseLaunchState.Idle)
    val purchaseLaunchState = _purchaseLaunchState.asStateFlow()

    private val productRequestVersion = AtomicInteger()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        Log.d(
            TAG,
            "Purchase update: code=${billingResult.responseCode}, " +
                    "count=${purchases?.size ?: 0}"
        )

        if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refreshPurchases()
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.OK &&
            billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED
        ) {
            _purchaseLaunchState.value = PurchaseLaunchState.Failed(billingResult.responseCode)
        }

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            val matching = inspectUnlimitedPurchases(purchases)
            val completed = matching.filter(::isCompletedUnlimitedPurchase)
            val hasPending = matching.any {
                it.purchaseState == Purchase.PurchaseState.PENDING
            }

            when {
                completed.isNotEmpty() -> {
                    if (!processCompletedPurchases(completed)) {
                        refreshPurchases()
                    }
                }
                hasPending && _unlimitedAccessState.value != UnlimitedAccessState.Active -> {
                    _unlimitedAccessState.value = UnlimitedAccessState.Pending
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            refreshPurchases()
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if(billingClient.connectionState != BillingClient.ConnectionState.DISCONNECTED){
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                if(result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "onBillingSetupFinished: Billing connected")
                    queryPurchases()
                    queryProductDetails(productRequestVersion.get())
                } else {
                    _refreshState.value = PurchaseRefreshState.Failed(result.responseCode)
                    _productState.value = UnlimitedProductState.Failed(result.responseCode)
                    Log.w(TAG, "Billing setup failed: ${result.responseCode}, ${result.debugMessage}")
                }
            }
        })
    }

    fun refreshPurchases() {
        _refreshState.value = PurchaseRefreshState.Loading
        _productState.value = UnlimitedProductState.Loading
        val productVersion = productRequestVersion.incrementAndGet()
        if (billingClient.isReady){
            queryPurchases()
            queryProductDetails(productVersion)
        } else {
            connect()
        }
    }

    fun refreshProductDetails() {
        _productState.value = UnlimitedProductState.Loading
        val productVersion = productRequestVersion.incrementAndGet()
        if (billingClient.isReady) {
            queryProductDetails(productVersion)
        } else {
            connect()
        }
    }

    private fun queryProductDetails(requestVersion: Int) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(UNLIMITED_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (requestVersion != productRequestVersion.get()) return@queryProductDetailsAsync

            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _productState.value = UnlimitedProductState.Failed(result.responseCode)
                Log.w(TAG, "Product details query failed: ${result.responseCode}, ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val details = detailsResult.productDetailsList.firstOrNull {
                it.productId == UNLIMITED_PRODUCT_ID &&
                    it.productType == BillingClient.ProductType.INAPP
            }
            val price = details?.standardBuyOffer()?.formattedPrice?.takeIf(String::isNotBlank)

            _productState.value = if (price != null) {
                UnlimitedProductState.Available(price)
            } else {
                val unfetchedStatus = detailsResult.unfetchedProductList
                    .firstOrNull { it.productId == UNLIMITED_PRODUCT_ID }
                    ?.statusCode
                Log.w(TAG, "Unlimited product unavailable: status=$unfetchedStatus")
                UnlimitedProductState.Unavailable
            }
        }
    }

    private fun ProductDetails.standardBuyOffer(): ProductDetails.OneTimePurchaseOfferDetails? {
        val offers = oneTimePurchaseOfferDetailsList
        return if (offers.isNullOrEmpty()) {
            oneTimePurchaseOfferDetails
        } else {
            offers.firstOrNull {
                it.purchaseOptionId == STANDARD_PURCHASE_OPTION_ID &&
                    it.preorderDetails == null && it.rentalDetails == null
            } ?: offers.firstOrNull {
                it.preorderDetails == null && it.rentalDetails == null
            }
        }
    }

    fun launchUnlimitedPurchase(activity: Activity) {
        if (_purchaseLaunchState.value == PurchaseLaunchState.Loading) return
        if (_unlimitedAccessState.value == UnlimitedAccessState.Active ||
            _unlimitedAccessState.value == UnlimitedAccessState.Pending
        ) return

        if (!billingClient.isReady) {
            _purchaseLaunchState.value =
                PurchaseLaunchState.Failed(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            refreshPurchases()
            return
        }

        _purchaseLaunchState.value = PurchaseLaunchState.Loading
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(UNLIMITED_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        // Query again for the purchase: ProductDetails cached for display can become stale.
        try {
            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Purchase product query failed: ${result.responseCode}, ${result.debugMessage}")
                    _purchaseLaunchState.value = PurchaseLaunchState.Failed(result.responseCode)
                    return@queryProductDetailsAsync
                }

                val details = detailsResult.productDetailsList.firstOrNull {
                    it.productId == UNLIMITED_PRODUCT_ID &&
                        it.productType == BillingClient.ProductType.INAPP
                }
                val offerToken = details?.standardBuyOffer()?.offerToken
                    ?.takeIf(String::isNotBlank)
                if (details == null || offerToken == null) {
                    _purchaseLaunchState.value =
                        PurchaseLaunchState.Failed(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                    return@queryProductDetailsAsync
                }

                val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build()

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) {
                        _purchaseLaunchState.value = PurchaseLaunchState.Idle
                        return@runOnUiThread
                    }

                    val launchResult = try {
                        billingClient.launchBillingFlow(activity, flowParams)
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Unable to launch purchase flow", error)
                        _purchaseLaunchState.value =
                            PurchaseLaunchState.Failed(BillingClient.BillingResponseCode.ERROR)
                        return@runOnUiThread
                    }

                    _purchaseLaunchState.value = when (launchResult.responseCode) {
                        BillingClient.BillingResponseCode.OK,
                        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseLaunchState.Idle
                        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                            refreshPurchases()
                            PurchaseLaunchState.Idle
                        }
                        else -> {
                            Log.w(TAG, "Purchase flow launch failed: ${launchResult.responseCode}, ${launchResult.debugMessage}")
                            PurchaseLaunchState.Failed(launchResult.responseCode)
                        }
                    }
                }
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to query purchase product", error)
            _purchaseLaunchState.value =
                PurchaseLaunchState.Failed(BillingClient.BillingResponseCode.ERROR)
        }
    }

    private fun queryPurchases(){
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _refreshState.value = PurchaseRefreshState.Failed(result.responseCode)
                Log.w(TAG, "Purchase query failed: ${result.responseCode}, ${result.debugMessage}")
                return@queryPurchasesAsync
            }

            val matching = inspectUnlimitedPurchases(purchases)
            val completed = matching.filter(::isCompletedUnlimitedPurchase)

            when {
                completed.isNotEmpty() -> {
                    if (!processCompletedPurchases(completed)) {
                        _unlimitedAccessState.value = UnlimitedAccessState.VerificationFailed
                    }
                }
                matching.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> {
                    _unlimitedAccessState.value = UnlimitedAccessState.Pending
                }
                else -> {
                    _unlimitedAccessState.value = UnlimitedAccessState.NotPurchased
                }
            }

            _refreshState.value = PurchaseRefreshState.Succeeded
            Log.d(TAG, "Purchase query succeeded: count=${purchases.size}")
        }

    }

    private fun inspectUnlimitedPurchases(purchases: List<Purchase>): List<Purchase> {
        val matching = purchases.filter { UNLIMITED_PRODUCT_ID in it.products }
        Log.d(TAG, "Unlimited purchase matches=${matching.size}")

        matching.forEach { purchase ->
            val state = when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> "PURCHASED"
                Purchase.PurchaseState.PENDING -> "PENDING"
                else -> "OTHER"
            }
            Log.d(
                TAG,
                "Unlimited purchase state=$state, acknowledged=${purchase.isAcknowledged}"
            )
        }

        return matching
    }

    private fun isCompletedUnlimitedPurchase(purchase: Purchase): Boolean =
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            UNLIMITED_PRODUCT_ID in purchase.products &&
            purchase.packageName == context.packageName

    private fun hasValidPurchaseSignature(purchase: Purchase): Boolean {
        val publicKey = BuildConfig.PLAY_BILLING_PUBLIC_KEY
        if (publicKey.isBlank()) {
            Log.w(TAG, "Play Billing public key is not configured")
            return false
        }

        val valid = PurchaseSignatureVerifier.verify(
            base64PublicKey = publicKey,
            signedData = purchase.originalJson,
            base64Signature = purchase.signature,
        )
        if (!valid) {
            Log.w(TAG, "Purchase signature verification failed")
        }
        return valid
    }

    private fun processCompletedPurchases(purchases: List<Purchase>): Boolean {
        val verifiedPurchase = purchases.firstOrNull { purchase ->
            isCompletedUnlimitedPurchase(purchase) && purchase.purchaseToken.isNotBlank() && hasValidPurchaseSignature(purchase)
        } ?: return false

        _unlimitedAccessState.value = UnlimitedAccessState.Active
        acknowledgeIfNeeded(verifiedPurchase)
        return true
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        val token = purchase.purchaseToken
        if (token.isBlank()) {
            Log.w(TAG, "Purchase token is empty")
            return
        }
        if (purchase.isAcknowledged) {
            acknowledgementStates[token] = AcknowledgementState.Succeeded
            return
        }
        if (acknowledgementStates.putIfAbsent(
                token,
                AcknowledgementState.InFlight,
            ) != null
        ) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()

        try {
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    acknowledgementStates[token] = AcknowledgementState.Succeeded
                } else {
                    acknowledgementStates.remove(token, AcknowledgementState.InFlight)
                    Log.w(TAG, "Purchase acknowledgement failed: ${result.responseCode}")
                }
            }
        } catch (error: RuntimeException) {
            acknowledgementStates.remove(token, AcknowledgementState.InFlight)
            Log.e(TAG, "Unable to start purchase acknowledgement", error)
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "BillingRepository"
        private const val UNLIMITED_PRODUCT_ID = "feelcamera_unlimited_capture"
        private const val STANDARD_PURCHASE_OPTION_ID = "standard"
    }
}
