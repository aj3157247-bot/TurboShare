package com.fastsend.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class PremiumManager(
    context: Context,
    private val onPremiumChanged: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit
) {
    companion object {
        const val PREMIUM_ID = "turboshare_premium"
        const val MONTHLY_ID = "turboshare_pro_monthly"
    }

    private val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)
    private val billing = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        prefs.edit().putBoolean("premium", true).apply()
                        onPremiumChanged(true)
                        if (!purchase.isAcknowledged) acknowledge(purchase)
                    }
                }
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                onMessage(result.debugMessage.ifBlank { "Purchase could not be completed" })
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var product: ProductDetails? = null
    var isPremium: Boolean = prefs.getBoolean("premium", false)
        private set

    fun connect() {
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restore()
                }
            }
            override fun onBillingServiceDisconnected() {
                onMessage("Play Billing disconnected")
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PREMIUM_ID).setProductType(BillingClient.ProductType.INAPP).build()))
            .build()
        billing.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) product = details.firstOrNull()
        }
    }

    fun buy(activity: Activity) {
        val p = product
        if (p == null) {
            onMessage("Premium is not configured in Play Console yet")
            return
        }
        val offer = p.oneTimePurchaseOfferDetails
        if (offer == null) {
            onMessage("Premium offer is unavailable")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p).build())
            ).build()
        billing.launchBillingFlow(activity, params)
    }

    fun restore() {
        billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases.any { it.products.contains(PREMIUM_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                prefs.edit().putBoolean("premium", true).apply()
                isPremium = true
                onPremiumChanged(true)
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        billing.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { }
    }

    fun close() = billing.endConnection()
}
