package com.vibeactions.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.vibeactions.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play Billing for the one-time unlock ([PRODUCT_ID], a non-consumable in-app product).
 * Play is the source of truth: every [refresh] asks it what the account owns and mirrors that into
 * [License], so a purchase follows the Google account to a new phone and a refund locks again.
 * Nothing here runs on the full flavor (no paywall).
 */
@Singleton
class BillingManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _price = MutableStateFlow<String?>(null)
    /** Play's localized price, e.g. "$7.00" or "49,00 kr."; null until Play answers. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _pending = MutableStateFlow(false)
    /** A purchase is waiting for payment (e.g. cash at a shop); unlocks once Play confirms it. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off feedback for a snackbar/toast ("Purchase restored", errors). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Asks Play what this account owns and what the unlock costs. Call on every app resume. */
    fun refresh(announce: Boolean = false) {
        if (!BuildConfig.PAYWALL) return
        scope.launch {
            if (!connect()) {
                if (announce) _messages.tryEmit("Google Play isn't available right now. Try again later.")
                return@launch
            }
            loadDetails()
            val (code, purchases) = queryPurchases()
            // Only a definite answer changes the licence: offline or Play errors keep what we know.
            if (code != BillingClient.BillingResponseCode.OK) {
                if (announce) _messages.tryEmit("Couldn't reach Google Play. Try again later.")
                return@launch
            }
            val owned = handle(purchases)
            License.setUnlocked(context, owned)
            if (announce) _messages.tryEmit(if (owned) "TextFox is unlocked. Thank you!" else "No purchase found on this Google account.")
        }
    }

    /** Opens Play's purchase sheet. */
    fun launchPurchase(activity: Activity) {
        scope.launch {
            if (!connect()) { _messages.tryEmit("Google Play isn't available right now. Try again later."); return@launch }
            val product = details ?: loadDetails()
            if (product == null) { _messages.tryEmit("The purchase isn't available yet. Try again later."); return@launch }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())
                )
                .build()
            activity.runOnUiThread { client.launchBillingFlow(activity, params) }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                if (handle(purchases.orEmpty())) {
                    License.setUnlocked(context, true)
                    _messages.tryEmit("TextFox is unlocked. Thank you!")
                } else if (_pending.value) {
                    _messages.tryEmit("Payment pending. TextFox unlocks as soon as Google confirms it.")
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(announce = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _messages.tryEmit("The purchase didn't go through. Please try again.")
        }
    }

    /** Acknowledges new purchases (Play refunds unacknowledged ones after 3 days) and reports
     *  whether the unlock is owned. Updates [pending]. */
    private suspend fun handle(purchases: List<Purchase>): Boolean {
        val ours = purchases.filter { PRODUCT_ID in it.products }
        _pending.value = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        val bought = ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        bought.filterNot { it.isAcknowledged }.forEach { acknowledge(it.purchaseToken) }
        return bought.isNotEmpty()
    }

    private suspend fun loadDetails(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        val list = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { _, result ->
                if (cont.isActive) cont.resume(result.productDetailsList)
            }
        }
        val product = list.firstOrNull { it.productId == PRODUCT_ID }
        details = product
        product?.oneTimePurchaseOfferDetails?.formattedPrice?.let { _price.value = it }
        return product
    }

    private suspend fun queryPurchases(): Pair<Int, List<Purchase>> = suspendCancellableCoroutine { cont ->
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases -> if (cont.isActive) cont.resume(result.responseCode to purchases) }
    }

    private suspend fun acknowledge(token: String) = suspendCancellableCoroutine { cont ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) {
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    companion object {
        /** The in-app product in Play Console: one-time, non-consumable, 7 USD. */
        const val PRODUCT_ID = "textfox_unlock"
        /** Shown until Play reports the localized price. */
        const val FALLBACK_PRICE = "$7"
    }
}
