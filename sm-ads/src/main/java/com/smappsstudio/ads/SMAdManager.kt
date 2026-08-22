package com.smappsstudio.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd

object SMAdManager {

    @JvmStatic
    var isFullScreenAdShowing = false

    private var isPremium = false
    private var lastInterstitialShowTime = 0L
    private val interstitialAds = mutableMapOf<String, InterstitialAd>()
    private var minIntervalBetweenInterstitials = 30 * 1000L // 30 seconds default

    /**
     * Initialize SDK and parse JSON configs
     */
    fun initialize(context: Context, isDebug: Boolean, fileName: String? = null) {
        MobileAds.initialize(context) {}
        SMAdConfig.initialize(context, isDebug, fileName)
    }

    /**
     * Disable ads if user purchased premium (No Ads)
     */
    fun setPremiumUser(premium: Boolean) {
        this.isPremium = premium
        SMAppOpenAdManager.getInstance().setEnabled(!premium)
    }

    /**
     * Set minimum interval between interstitial ads to protect UX
     */
    fun setMinIntervalBetweenInterstitials(seconds: Int) {
        this.minIntervalBetweenInterstitials = seconds * 1000L
    }

    /**
     * Load and Show Splash Interstitial Ad with safe timeout
     */
    fun loadSplashInterstitialAd(
        activity: Activity,
        placementKey: String,
        timeoutMs: Long = 15000,
        delayMs: Long = 1000,
        callback: SMAdCallback
    ) {
        val config = SMAdConfig.getPlacement(placementKey)

        if (isPremium || !config.isEnable || !isNetworkAvailable(activity)) {
            Handler(Looper.getMainLooper()).postDelayed({
                callback.onNextAction()
            }, delayMs)
            return
        }

        var isNextActionCalled = false
        val handler = Handler(Looper.getMainLooper())
        
        // Timeout runnable
        val timeoutRunnable = Runnable {
            if (!isNextActionCalled) {
                isNextActionCalled = true
                callback.onNextAction()
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            activity,
            config.id,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    handler.removeCallbacks(timeoutRunnable)
                    
                    interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            isFullScreenAdShowing = false
                            if (!isNextActionCalled) {
                                isNextActionCalled = true
                                callback.onNextAction()
                            }
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                            isFullScreenAdShowing = false
                            if (!isNextActionCalled) {
                                isNextActionCalled = true
                                callback.onNextAction()
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            isFullScreenAdShowing = true
                        }
                    }

                    if (!isNextActionCalled) {
                        isFullScreenAdShowing = true
                        interstitialAd.show(activity)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handler.removeCallbacks(timeoutRunnable)
                    if (!isNextActionCalled) {
                        isNextActionCalled = true
                        callback.onNextAction()
                    }
                }
            }
        )
    }

    /**
     * Preload standard Interstitial Ad
     */
    fun preloadInterstitialAd(context: Context, placementKey: String, callback: SMAdCallback? = null) {
        val config = SMAdConfig.getPlacement(placementKey)
        if (isPremium || !config.isEnable || !isNetworkAvailable(context)) {
            callback?.onAdFailedToLoad("Premium user, disabled or no network")
            return
        }

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            config.id,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    interstitialAds[placementKey] = interstitialAd
                    callback?.onAdLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    callback?.onAdFailedToLoad(loadAdError.message)
                }
            }
        )
    }

    /**
     * Show Preloaded Interstitial Ad with safety delay interval check
     */
    fun showInterstitialAd(activity: Activity, placementKey: String, callback: SMAdCallback) {
        val ad = interstitialAds[placementKey]
        val config = SMAdConfig.getPlacement(placementKey)

        if (isPremium || !config.isEnable || ad == null) {
            callback.onAdClosed()
            return
        }

        // Policy: Prevent showing ads too frequently
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShowTime < minIntervalBetweenInterstitials) {
            callback.onAdClosed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isFullScreenAdShowing = false
                interstitialAds.remove(placementKey)
                lastInterstitialShowTime = System.currentTimeMillis()
                callback.onAdClosed()
                preloadInterstitialAd(activity, placementKey) // Auto reload next ad
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                isFullScreenAdShowing = false
                interstitialAds.remove(placementKey)
                callback.onAdClosed()
            }

            override fun onAdShowedFullScreenContent() {
                isFullScreenAdShowing = true
                callback.onAdOpened()
            }
        }

        isFullScreenAdShowing = true
        ad.show(activity)
    }

    /**
     * Load Banner Ad (Normal or Collapsible) into a container
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        placementKey: String,
        isCollapsible: Boolean = false,
        callback: SMAdCallback? = null
    ) {
        val config = SMAdConfig.getPlacement(placementKey)
        container.removeAllViews()

        if (isPremium || !config.isEnable || !isNetworkAvailable(activity)) {
            container.visibility = ViewGroup.GONE
            callback?.onAdFailedToLoad("Premium user, disabled or no network")
            return
        }

        val adView = AdView(activity).apply {
            adUnitId = config.id
            setAdSize(getAdaptiveAdSize(activity))
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                container.removeAllViews()
                container.addView(adView)
                container.visibility = ViewGroup.VISIBLE
                callback?.onAdLoaded()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                container.visibility = ViewGroup.GONE
                callback?.onAdFailedToLoad(loadAdError.message)
            }

            override fun onAdClicked() {
                callback?.onAdClicked()
            }
        }

        val requestBuilder = AdRequest.Builder()
        if (isCollapsible) {
            val extras = Bundle().apply {
                putString("collapsible", "bottom") // default collapsible behavior
            }
            requestBuilder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }

        adView.loadAd(requestBuilder.build())
    }

    /**
     * Load Native Ad
     */
    fun loadNativeAd(
        context: Context,
        placementKey: String,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val config = SMAdConfig.getPlacement(placementKey)

        if (isPremium || !config.isEnable || !isNetworkAvailable(context)) {
            onFailed("Premium, disabled or no internet connection")
            return
        }

        val adLoader = AdLoader.Builder(context, config.id)
            .forNativeAd { nativeAd ->
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    onFailed(loadAdError.message)
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Load Banner Ad with Shimmer frame layout support
     */
    fun loadBannerWithShimmer(
        activity: Activity,
        container: ViewGroup,
        shimmer: com.facebook.shimmer.ShimmerFrameLayout,
        placementKey: String,
        isCollapsible: Boolean = false,
        callback: SMAdCallback? = null
    ) {
        val config = SMAdConfig.getPlacement(placementKey)
        if (isPremium || !config.isEnable || !isNetworkAvailable(activity)) {
            shimmer.stopShimmer()
            shimmer.visibility = android.view.View.GONE
            container.visibility = android.view.View.GONE
            callback?.onAdFailedToLoad("Premium, disabled or offline")
            return
        }

        shimmer.visibility = android.view.View.VISIBLE
        shimmer.startShimmer()
        container.visibility = android.view.View.GONE

        loadBanner(
            activity = activity,
            container = container,
            placementKey = placementKey,
            isCollapsible = isCollapsible,
            callback = object : SMAdCallback() {
                override fun onAdLoaded() {
                    shimmer.stopShimmer()
                    shimmer.visibility = android.view.View.GONE
                    container.visibility = android.view.View.VISIBLE
                    callback?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: String) {
                    shimmer.stopShimmer()
                    shimmer.visibility = android.view.View.GONE
                    container.visibility = android.view.View.GONE
                    callback?.onAdFailedToLoad(error)
                }

                override fun onAdClicked() {
                    callback?.onAdClicked()
                }
            }
        )
    }

    /**
     * Load Native Ad with Shimmer frame layout support
     */
    fun loadNativeWithShimmer(
        activity: Activity,
        container: ViewGroup,
        shimmer: com.facebook.shimmer.ShimmerFrameLayout,
        placementKey: String,
        layoutResId: Int,
        callback: SMAdCallback? = null
    ) {
        val config = SMAdConfig.getPlacement(placementKey)
        if (isPremium || !config.isEnable || !isNetworkAvailable(activity)) {
            shimmer.stopShimmer()
            shimmer.visibility = android.view.View.GONE
            container.visibility = android.view.View.GONE
            callback?.onAdFailedToLoad("Premium, disabled or offline")
            return
        }

        shimmer.visibility = android.view.View.VISIBLE
        shimmer.startShimmer()
        container.visibility = android.view.View.GONE

        loadNativeAd(
            context = activity,
            placementKey = placementKey,
            onLoaded = { nativeAd ->
                shimmer.stopShimmer()
                shimmer.visibility = android.view.View.GONE
                container.visibility = android.view.View.VISIBLE
                SMNativeAdHelper.populateNativeAdInContainer(activity, nativeAd, container, layoutResId)
                callback?.onAdLoaded()
            },
            onFailed = { error ->
                shimmer.stopShimmer()
                shimmer.visibility = android.view.View.GONE
                container.visibility = android.view.View.GONE
                callback?.onAdFailedToLoad(error)
            }
        )
    }

    // Helper functions
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun getAdaptiveAdSize(activity: Activity): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = android.util.DisplayMetrics()
        display.getMetrics(outMetrics)
        val density = outMetrics.density
        var adWidthPixels = outMetrics.widthPixels.toFloat()
        if (adWidthPixels == 0f) {
            adWidthPixels = outMetrics.widthPixels.toFloat()
        }
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
}
