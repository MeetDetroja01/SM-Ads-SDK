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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object SMAdManager {

    @JvmStatic
    var isFullScreenAdShowing = false

    private var isPremium = false
    private var isMobileAdsInitialized = false
    private var lastInterstitialShowTime = 0L
    private val interstitialAds = mutableMapOf<String, InterstitialAd>()
    private val clickCounters = mutableMapOf<String, Int>()
    private val rewardedAds = mutableMapOf<String, RewardedAd>()
    private var minIntervalBetweenInterstitials = 30 * 1000L // 30 seconds default
    private var globalClickThreshold = 0
    private var loadingDialog: android.app.Dialog? = null
    private var dotsHandler: Handler? = null
    private var adLoadingDelayMs = 1200L
    private var adLoadingText = "Loading Ads"

    @JvmStatic
    fun setAdLoadingTime(delayMs: Long) {
        this.adLoadingDelayMs = delayMs
    }

    @JvmStatic
    fun getAdLoadingTime(): Long = adLoadingDelayMs

    @JvmStatic
    fun setAdLoadingText(text: String) {
        this.adLoadingText = text
    }

    @JvmStatic
    fun getAdLoadingText(): String = adLoadingText

    @JvmStatic
    fun showLoadingDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        dismissLoadingDialog()

        val dialog = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        
        try {
            val view = android.view.LayoutInflater.from(activity).inflate(R.layout.sm_dialog_loading, null)
            val textView = view.findViewById<android.widget.TextView>(R.id.sm_loading_text)
            val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.sm_loading_progress)
            progressBar?.indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3B82F6"))

            dialog.setContentView(view)
            dialog.setCancelable(false)
            dialog.show()
            loadingDialog = dialog

            val baseText = adLoadingText
            var dotCount = 0
            val handler = Handler(Looper.getMainLooper())
            dotsHandler = handler
            val dotsRunnable = object : Runnable {
                override fun run() {
                    if (loadingDialog?.isShowing == true) {
                        dotCount = (dotCount % 3) + 1
                        textView?.text = "$baseText${".".repeat(dotCount)}"
                        handler.postDelayed(this, 350)
                    }
                }
            }
            handler.post(dotsRunnable)
        } catch (e: Exception) {
            showFallbackLoadingDialog(activity, dialog)
        }
    }

    private fun showFallbackLoadingDialog(activity: Activity, dialog: android.app.Dialog) {
        val layout = android.widget.RelativeLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            gravity = android.view.Gravity.CENTER
        }
        
        val density = activity.resources.displayMetrics.density
        val cardLayout = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val padH = (36 * density).toInt()
            val padV = (28 * density).toInt()
            setPadding(padH, padV, padH, padV)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 18 * density
                setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#E2E8F0"))
            }
            background = bg
            elevation = 10 * density
        }

        val progressBar = android.widget.ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3B82F6"))
        }

        val textView = android.widget.TextView(activity).apply {
            text = "$adLoadingText..."
            setTextColor(android.graphics.Color.parseColor("#1E293B"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, (16 * density).toInt(), 0, 0)
        }

        cardLayout.addView(progressBar)
        cardLayout.addView(textView)
        layout.addView(cardLayout)

        dialog.setContentView(layout)
        dialog.setCancelable(false)
        try {
            dialog.show()
            loadingDialog = dialog

            val baseText = adLoadingText
            var dotCount = 0
            val handler = Handler(Looper.getMainLooper())
            dotsHandler = handler
            val dotsRunnable = object : Runnable {
                override fun run() {
                    if (loadingDialog?.isShowing == true) {
                        dotCount = (dotCount % 3) + 1
                        textView.text = "$baseText${".".repeat(dotCount)}"
                        handler.postDelayed(this, 350)
                    }
                }
            }
            handler.post(dotsRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun dismissLoadingDialog() {
        try {
            dotsHandler?.removeCallbacksAndMessages(null)
            dotsHandler = null
            loadingDialog?.let {
                if (it.isShowing) {
                    it.dismiss()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            loadingDialog = null
        }
    }

    /**
     * Initialize SDK and parse JSON configs (AdMob initialization is deferred to consent gathering)
     */
    fun initialize(context: Context, isDebug: Boolean, fileName: String? = null) {
        SMAdConfig.initialize(context, isDebug, fileName)
    }

    /**
     * Initialize SDK with attribution and marketing configurations
     */
    fun initialize(config: SMAppConfig, fileName: String? = null) {
        SMAdConfig.initialize(config.context, config.isDebug, fileName)

        // 1. Initialize Adjust SDK
        if (config.adjustToken.isNotEmpty()) {
            val environment = if (config.isDebug) {
                com.adjust.sdk.AdjustConfig.ENVIRONMENT_SANDBOX
            } else {
                com.adjust.sdk.AdjustConfig.ENVIRONMENT_PRODUCTION
            }
            val adjustConfig = com.adjust.sdk.AdjustConfig(config.context, config.adjustToken, environment)
            
            val application = config.context.applicationContext as? android.app.Application
            application?.let { app ->
                app.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: android.app.Activity) {
                        com.adjust.sdk.Adjust.onResume()
                    }

                    override fun onActivityPaused(activity: android.app.Activity) {
                        com.adjust.sdk.Adjust.onPause()
                    }

                    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
                    override fun onActivityStarted(activity: android.app.Activity) {}
                    override fun onActivityStopped(activity: android.app.Activity) {}
                    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
                    override fun onActivityDestroyed(activity: android.app.Activity) {}
                })
            }
            com.adjust.sdk.Adjust.onCreate(adjustConfig)
        }

        // 2. Initialize Facebook Core SDK
        if (config.facebookClientToken.isNotEmpty()) {
            com.facebook.FacebookSdk.setClientToken(config.facebookClientToken)
            if (config.facebookAppId.isNotEmpty()) {
                com.facebook.FacebookSdk.setApplicationId(config.facebookAppId)
            }
            com.facebook.FacebookSdk.sdkInitialize(config.context)
            
            val application = config.context.applicationContext as? android.app.Application
            application?.let { app ->
                com.facebook.appevents.AppEventsLogger.activateApp(app)
            }
        }
    }

    /**
     * Initialize Mobile Ads SDK safely if consent permits or is completed
     */
    @JvmStatic
    fun initMobileAds(context: Context) {
        if (isMobileAdsInitialized) return
        MobileAds.initialize(context) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for ((adapterClass, status) in statusMap) {
                android.util.Log.d("SMAdManager", "Mediation Adapter: $adapterClass, State: ${status.initializationState}")
            }
        }
        isMobileAdsInitialized = true
    }

    /**
     * Gather user consent for GDPR using Google UMP SDK
     */
    @JvmStatic
    fun setupGDPR(activity: Activity, onConsentGatheringFinished: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity
                ) { formError ->
                    if (consentInformation.canRequestAds()) {
                        initMobileAds(activity)
                    }
                    onConsentGatheringFinished()
                }
            },
            { requestConsentError ->
                if (consentInformation.canRequestAds()) {
                    initMobileAds(activity)
                }
                onConsentGatheringFinished()
            }
        )
    }

    /**
     * Disable ads if user purchased premium (No Ads)
     */
    fun setPremiumUser(premium: Boolean) {
        this.isPremium = premium
        SMAppOpenAdManager.getInstance().setEnabled(!premium)
    }

    /**
     * Set minimum interval between interstitial ads in seconds (Default is 30 seconds)
     */
    @JvmStatic
    fun setMinIntervalBetweenInterstitials(seconds: Int) {
        this.minIntervalBetweenInterstitials = seconds * 1000L
    }

    /**
     * Get minimum interval between interstitial ads in seconds
     */
    @JvmStatic
    fun getMinIntervalBetweenInterstitials(): Int = (minIntervalBetweenInterstitials / 1000L).toInt()

    /**
     * Check if the minimum interval between interstitial ads has passed
     */
    @JvmStatic
    fun isInterstitialIntervalPassed(): Boolean {
        return (System.currentTimeMillis() - lastInterstitialShowTime) >= minIntervalBetweenInterstitials
    }

    /**
     * Get remaining cooldown seconds before the next interstitial ad can be shown (0 if ready)
     */
    @JvmStatic
    fun getRemainingInterstitialIntervalSeconds(): Long {
        val diff = minIntervalBetweenInterstitials - (System.currentTimeMillis() - lastInterstitialShowTime)
        return if (diff > 0) (diff + 999) / 1000L else 0L
    }

    /**
     * Get timestamp in millis when the last interstitial or full-screen ad was shown/dismissed
     */
    @JvmStatic
    fun getLastInterstitialShowTime(): Long = lastInterstitialShowTime

    /**
     * Reset interstitial interval cooldown so an interstitial ad can show immediately
     */
    @JvmStatic
    fun resetInterstitialInterval() {
        lastInterstitialShowTime = 0L
    }

    /**
     * Set global click threshold for all interstitial ads
     * @param threshold 0 = show every click, 1 = show every 2nd click (alternate), 2 = show every 3rd click, etc.
     */
    fun setGlobalClickThreshold(threshold: Int) {
        this.globalClickThreshold = threshold
    }

    /**
     * Load and Show Splash Interstitial Ad with safe timeout
     */
    fun loadSplashInterstitialAd(
        activity: Activity,
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        timeoutMs: Long = 15000,
        delayMs: Long = 1000,
        callback: SMAdCallback
    ) {
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey ?: "").id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey ?: "").isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(activity) || finalAdUnitId.isEmpty()) {
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
                dismissLoadingDialog()
                callback.onNextAction()
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            activity,
            finalAdUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    handler.removeCallbacks(timeoutRunnable)
                    
                    interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = false
                            lastInterstitialShowTime = System.currentTimeMillis()
                            if (!isNextActionCalled) {
                                isNextActionCalled = true
                                callback.onNextAction()
                            }
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = false
                            if (!isNextActionCalled) {
                                isNextActionCalled = true
                                callback.onNextAction()
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = true
                            lastInterstitialShowTime = System.currentTimeMillis()
                        }
                    }

                    if (!isNextActionCalled) {
                        activity.runOnUiThread {
                            showLoadingDialog(activity)
                            Handler(Looper.getMainLooper()).postDelayed({
                                val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                                    activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                                } else {
                                    !activity.isFinishing && !activity.isDestroyed
                                }

                                if (isActivityResumed && !isNextActionCalled) {
                                    isFullScreenAdShowing = true
                                    try {
                                        interstitialAd.show(activity)
                                    } catch (e: Exception) {
                                        dismissLoadingDialog()
                                        if (!isNextActionCalled) {
                                            isNextActionCalled = true
                                            callback.onNextAction()
                                        }
                                    }
                                } else {
                                    dismissLoadingDialog()
                                    if (!isNextActionCalled) {
                                        isNextActionCalled = true
                                        callback.onNextAction()
                                    }
                                }
                            }, adLoadingDelayMs)
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handler.removeCallbacks(timeoutRunnable)
                    dismissLoadingDialog()
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
    fun preloadInterstitialAd(
        context: Context,
        placementKey: String,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        callback: SMAdCallback? = null
    ) {
        initMobileAds(context)
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey).id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey).isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(context) || finalAdUnitId.isEmpty()) {
            callback?.onAdFailedToLoad("Premium user, disabled, offline or empty adUnitId")
            return
        }

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            finalAdUnitId,
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
        if (isFullScreenAdShowing) {
            callback.onAdClosed()
            return
        }

        // Policy: Prevent showing ads too frequently (minimum 30 seconds interval between interstitial ads)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShowTime < minIntervalBetweenInterstitials) {
            callback.onAdClosed()
            return
        }

        val ad = interstitialAds[placementKey]
        val config = SMAdConfig.getPlacement(placementKey)

        if (isPremium || !config.isEnable || ad == null) {
            callback.onAdClosed()
            return
        }

        // Global click counter gating
        val currentCount = (clickCounters[placementKey] ?: 0) + 1
        clickCounters[placementKey] = currentCount

        if (currentCount <= globalClickThreshold) {
            callback.onAdClosed()
            return
        }

        // Reset counter when threshold is met and ad is about to be shown
        clickCounters[placementKey] = 0

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                dismissLoadingDialog()
                isFullScreenAdShowing = false
                interstitialAds.remove(placementKey)
                lastInterstitialShowTime = System.currentTimeMillis()
                callback.onAdClosed()
                preloadInterstitialAd(activity, placementKey) // Auto reload next ad
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                dismissLoadingDialog()
                isFullScreenAdShowing = false
                interstitialAds.remove(placementKey)
                callback.onAdClosed()
            }

            override fun onAdShowedFullScreenContent() {
                dismissLoadingDialog()
                isFullScreenAdShowing = true
                lastInterstitialShowTime = System.currentTimeMillis()
                callback.onAdOpened()
            }
        }

        // Show loading dialog on Main UI thread with a small delay before the ad renders
        activity.runOnUiThread {
            showLoadingDialog(activity)
            Handler(Looper.getMainLooper()).postDelayed({
                val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                    activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                } else {
                    !activity.isFinishing && !activity.isDestroyed
                }

                if (isActivityResumed) {
                    isFullScreenAdShowing = true
                    try {
                        ad.show(activity)
                    } catch (e: Exception) {
                        dismissLoadingDialog()
                        callback.onAdClosed()
                    }
                } else {
                    dismissLoadingDialog()
                    callback.onAdClosed()
                }
            }, adLoadingDelayMs)
        }
    }

    /**
     * Show Interstitial Ad using a click counter threshold
     * @param clickThreshold 0 = show every click, 1 = show every 2nd click (alternate), 2 = show every 3rd click, etc.
     */
    fun showInterstitialAdWithCounter(
        activity: Activity,
        placementKey: String,
        clickThreshold: Int,
        callback: SMAdCallback
    ) {
        val currentCount = (clickCounters[placementKey] ?: 0) + 1
        clickCounters[placementKey] = currentCount

        if (currentCount > clickThreshold) {
            clickCounters[placementKey] = 0
            showInterstitialAd(activity, placementKey, callback)
        } else {
            callback.onAdClosed()
        }
    }

    /**
     * Directly load and show an Interstitial Ad on demand (e.g. on button click).
     * If an ad for the placement is already preloaded, it will show it immediately.
     * Otherwise, it displays the loading dialog, loads the ad with a timeout, and shows it.
     */
    @JvmStatic
    fun loadAndShowInterstitialAd(
        activity: Activity,
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        timeoutMs: Long = 8000L,
        callback: SMAdCallback
    ) {
        initMobileAds(activity)
        val finalPlacementKey = placementKey ?: adUnitId ?: "inter_default"
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(finalPlacementKey).id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(finalPlacementKey).isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(activity) || finalAdUnitId.isEmpty() || isFullScreenAdShowing) {
            callback.onAdClosed()
            return
        }

        // Policy: Prevent showing ads too frequently (minimum 30 seconds interval between interstitial ads)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShowTime < minIntervalBetweenInterstitials) {
            callback.onAdClosed()
            return
        }

        // If an ad is already cached/preloaded for this placement, show it directly!
        val cachedAd = interstitialAds.remove(finalPlacementKey)
        if (cachedAd != null) {
            cachedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    dismissLoadingDialog()
                    isFullScreenAdShowing = false
                    lastInterstitialShowTime = System.currentTimeMillis()
                    callback.onAdClosed()
                    preloadInterstitialAd(activity, finalPlacementKey, finalAdUnitId, finalIsEnabled)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    dismissLoadingDialog()
                    isFullScreenAdShowing = false
                    callback.onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {
                    dismissLoadingDialog()
                    isFullScreenAdShowing = true
                    lastInterstitialShowTime = System.currentTimeMillis()
                    callback.onAdOpened()
                }
            }

            activity.runOnUiThread {
                showLoadingDialog(activity)
                Handler(Looper.getMainLooper()).postDelayed({
                    val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                        activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                    } else {
                        !activity.isFinishing && !activity.isDestroyed
                    }

                    if (isActivityResumed) {
                        isFullScreenAdShowing = true
                        try {
                            cachedAd.show(activity)
                        } catch (e: Exception) {
                            dismissLoadingDialog()
                            callback.onAdClosed()
                        }
                    } else {
                        dismissLoadingDialog()
                        callback.onAdClosed()
                    }
                }, adLoadingDelayMs)
            }
            return
        }

        var isActionCompleted = false
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()

        activity.runOnUiThread {
            showLoadingDialog(activity)
        }

        val timeoutRunnable = Runnable {
            if (!isActionCompleted) {
                isActionCompleted = true
                dismissLoadingDialog()
                callback.onAdClosed()
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            activity,
            finalAdUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    handler.removeCallbacks(timeoutRunnable)

                    // Re-check interval & full-screen state in case another ad showed while loading
                    val now = System.currentTimeMillis()
                    if (now - lastInterstitialShowTime < minIntervalBetweenInterstitials || isFullScreenAdShowing) {
                        interstitialAds[finalPlacementKey] = interstitialAd
                        dismissLoadingDialog()
                        if (!isActionCompleted) {
                            isActionCompleted = true
                            callback.onAdClosed()
                        }
                        return
                    }

                    interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = false
                            lastInterstitialShowTime = System.currentTimeMillis()
                            if (!isActionCompleted) {
                                isActionCompleted = true
                                callback.onAdClosed()
                            }
                            preloadInterstitialAd(activity, finalPlacementKey, finalAdUnitId, finalIsEnabled)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = false
                            if (!isActionCompleted) {
                                isActionCompleted = true
                                callback.onAdClosed()
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = true
                            lastInterstitialShowTime = System.currentTimeMillis()
                            callback.onAdOpened()
                        }
                    }

                    if (!isActionCompleted) {
                        activity.runOnUiThread {
                            val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                                activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                            } else {
                                !activity.isFinishing && !activity.isDestroyed
                            }

                            if (isActivityResumed && !isActionCompleted) {
                                val elapsed = System.currentTimeMillis() - startTime
                                val remainingDelay = (adLoadingDelayMs - elapsed).coerceAtLeast(0L)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        isFullScreenAdShowing = true
                                        interstitialAd.show(activity)
                                    } catch (e: Exception) {
                                        dismissLoadingDialog()
                                        if (!isActionCompleted) {
                                            isActionCompleted = true
                                            callback.onAdClosed()
                                        }
                                    }
                                }, remainingDelay)
                            } else {
                                dismissLoadingDialog()
                                if (!isActionCompleted) {
                                    isActionCompleted = true
                                    callback.onAdClosed()
                                }
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handler.removeCallbacks(timeoutRunnable)
                    dismissLoadingDialog()
                    if (!isActionCompleted) {
                        isActionCompleted = true
                        callback.onAdClosed()
                    }
                }
            }
        )
    }

    /**
     * Preload standard Rewarded Ad
     */
    fun preloadRewardedAd(
        context: Context,
        placementKey: String,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        callback: SMAdCallback? = null
    ) {
        initMobileAds(context)
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey).id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey).isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(context) || finalAdUnitId.isEmpty()) {
            callback?.onAdFailedToLoad("Premium user, disabled, offline or empty adUnitId")
            return
        }

        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            finalAdUnitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAds[placementKey] = ad
                    callback?.onAdLoaded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    callback?.onAdFailedToLoad(loadAdError.message)
                }
            }
        )
    }

    /**
     * Show Preloaded Rewarded Ad with safety loading dialog warning
     */
    fun showRewardedAd(
        activity: Activity,
        placementKey: String,
        callback: SMAdCallback,
        onUserEarnedReward: (RewardItem) -> Unit
    ) {
        val ad = rewardedAds[placementKey]
        val config = SMAdConfig.getPlacement(placementKey)

        if (isPremium || !config.isEnable || ad == null) {
            callback.onAdClosed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                dismissLoadingDialog()
                isFullScreenAdShowing = false
                rewardedAds.remove(placementKey)
                lastInterstitialShowTime = System.currentTimeMillis()
                callback.onAdClosed()
                preloadRewardedAd(activity, placementKey) // Auto reload next ad
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                dismissLoadingDialog()
                isFullScreenAdShowing = false
                rewardedAds.remove(placementKey)
                callback.onAdClosed()
            }

            override fun onAdShowedFullScreenContent() {
                dismissLoadingDialog()
                isFullScreenAdShowing = true
                lastInterstitialShowTime = System.currentTimeMillis()
                callback.onAdOpened()
            }
        }

        // Show loading dialog on Main UI thread with a small delay before the ad renders
        activity.runOnUiThread {
            showLoadingDialog(activity)
            Handler(Looper.getMainLooper()).postDelayed({
                val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                    activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                } else {
                    !activity.isFinishing && !activity.isDestroyed
                }

                if (isActivityResumed) {
                    isFullScreenAdShowing = true
                    try {
                        ad.show(activity) { rewardItem ->
                            onUserEarnedReward(rewardItem)
                        }
                    } catch (e: Exception) {
                        dismissLoadingDialog()
                        callback.onAdClosed()
                    }
                } else {
                    dismissLoadingDialog()
                    callback.onAdClosed()
                }
            }, adLoadingDelayMs)
        }
    }

    /**
     * Directly load and show a Rewarded Ad on demand (e.g. on button click).
     * If an ad for the placement is already preloaded, it will show it immediately.
     * Otherwise, it displays the loading dialog, loads the ad with a timeout, and shows it.
     */
    @JvmStatic
    fun loadAndShowRewardedAd(
        activity: Activity,
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        timeoutMs: Long = 8000L,
        callback: SMAdCallback,
        onUserEarnedReward: (RewardItem) -> Unit
    ) {
        initMobileAds(activity)
        val finalPlacementKey = placementKey ?: adUnitId ?: "reward_default"
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(finalPlacementKey).id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(finalPlacementKey).isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(activity) || finalAdUnitId.isEmpty()) {
            callback.onAdClosed()
            return
        }

        // If an ad is already cached/preloaded for this placement, show it directly!
        val cachedAd = rewardedAds.remove(finalPlacementKey)
        if (cachedAd != null) {
            cachedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    dismissLoadingDialog()
                    isFullScreenAdShowing = false
                    lastInterstitialShowTime = System.currentTimeMillis()
                    callback.onAdClosed()
                    preloadRewardedAd(activity, finalPlacementKey, finalAdUnitId, finalIsEnabled)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    dismissLoadingDialog()
                    isFullScreenAdShowing = false
                    callback.onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {
                    dismissLoadingDialog()
                    isFullScreenAdShowing = true
                    lastInterstitialShowTime = System.currentTimeMillis()
                    callback.onAdOpened()
                }
            }

            activity.runOnUiThread {
                showLoadingDialog(activity)
                Handler(Looper.getMainLooper()).postDelayed({
                    val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                        activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                    } else {
                        !activity.isFinishing && !activity.isDestroyed
                    }

                    if (isActivityResumed) {
                        isFullScreenAdShowing = true
                        try {
                            cachedAd.show(activity) { rewardItem ->
                                onUserEarnedReward(rewardItem)
                            }
                        } catch (e: Exception) {
                            dismissLoadingDialog()
                            callback.onAdClosed()
                        }
                    } else {
                        dismissLoadingDialog()
                        callback.onAdClosed()
                    }
                }, adLoadingDelayMs)
            }
            return
        }

        var isActionCompleted = false
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()

        activity.runOnUiThread {
            showLoadingDialog(activity)
        }

        val timeoutRunnable = Runnable {
            if (!isActionCompleted) {
                isActionCompleted = true
                dismissLoadingDialog()
                callback.onAdClosed()
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        val request = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            finalAdUnitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    handler.removeCallbacks(timeoutRunnable)

                    rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = false
                            lastInterstitialShowTime = System.currentTimeMillis()
                            if (!isActionCompleted) {
                                isActionCompleted = true
                                callback.onAdClosed()
                            }
                            preloadRewardedAd(activity, finalPlacementKey, finalAdUnitId, finalIsEnabled)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = false
                            if (!isActionCompleted) {
                                isActionCompleted = true
                                callback.onAdClosed()
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            dismissLoadingDialog()
                            isFullScreenAdShowing = true
                            lastInterstitialShowTime = System.currentTimeMillis()
                            callback.onAdOpened()
                        }
                    }

                    if (!isActionCompleted) {
                        activity.runOnUiThread {
                            val isActivityResumed = if (activity is androidx.lifecycle.LifecycleOwner) {
                                activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                            } else {
                                !activity.isFinishing && !activity.isDestroyed
                            }

                            if (isActivityResumed && !isActionCompleted) {
                                val elapsed = System.currentTimeMillis() - startTime
                                val remainingDelay = (adLoadingDelayMs - elapsed).coerceAtLeast(0L)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        isFullScreenAdShowing = true
                                        rewardedAd.show(activity) { rewardItem ->
                                            onUserEarnedReward(rewardItem)
                                        }
                                    } catch (e: Exception) {
                                        dismissLoadingDialog()
                                        if (!isActionCompleted) {
                                            isActionCompleted = true
                                            callback.onAdClosed()
                                        }
                                    }
                                }, remainingDelay)
                            } else {
                                dismissLoadingDialog()
                                if (!isActionCompleted) {
                                    isActionCompleted = true
                                    callback.onAdClosed()
                                }
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handler.removeCallbacks(timeoutRunnable)
                    dismissLoadingDialog()
                    if (!isActionCompleted) {
                        isActionCompleted = true
                        callback.onAdClosed()
                    }
                }
            }
        )
    }

    /**
     * Load Banner Ad (Normal or Collapsible) into a container
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        isCollapsible: Boolean = false,
        callback: SMAdCallback? = null
    ) {
        initMobileAds(activity)
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey ?: "").id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey ?: "").isEnable
        container.removeAllViews()

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(activity) || finalAdUnitId.isEmpty()) {
            container.visibility = ViewGroup.GONE
            callback?.onAdFailedToLoad("Premium user, disabled, offline or empty adUnitId")
            return
        }

        val adView = AdView(activity).apply {
            this.adUnitId = finalAdUnitId
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
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (String) -> Unit
    ) {
        initMobileAds(context)
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey ?: "").id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey ?: "").isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(context) || finalAdUnitId.isEmpty()) {
            onFailed("Premium, disabled, offline or empty adUnitId")
            return
        }

        val adLoader = AdLoader.Builder(context, finalAdUnitId)
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
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        isCollapsible: Boolean = false,
        callback: SMAdCallback? = null
    ) {
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey ?: "").id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey ?: "").isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(activity) || finalAdUnitId.isEmpty()) {
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
            adUnitId = finalAdUnitId,
            isEnabled = finalIsEnabled,
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
        placementKey: String? = null,
        adUnitId: String? = null,
        isEnabled: Boolean? = null,
        layoutResId: Int,
        callback: SMAdCallback? = null
    ) {
        val finalAdUnitId = adUnitId ?: SMAdConfig.getPlacement(placementKey ?: "").id
        val finalIsEnabled = isEnabled ?: SMAdConfig.getPlacement(placementKey ?: "").isEnable

        if (isPremium || !finalIsEnabled || !isNetworkAvailable(activity) || finalAdUnitId.isEmpty()) {
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
            adUnitId = finalAdUnitId,
            isEnabled = finalIsEnabled,
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

    // Direct Ad Unit ID & Enabled state Overloads (No PlacementKey required)
    @JvmStatic
    @JvmOverloads
    fun loadSplashInterstitialAd(
        activity: Activity,
        adUnitId: String,
        isEnabled: Boolean,
        timeoutMs: Long = 15000,
        delayMs: Long = 1000,
        callback: SMAdCallback
    ) {
        SMAdConfig.registerPlacement(adUnitId, adUnitId, isEnabled)
        loadSplashInterstitialAd(activity, adUnitId, adUnitId, isEnabled, timeoutMs, delayMs, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun preloadInterstitialAd(
        context: Context,
        adUnitId: String,
        isEnabled: Boolean,
        callback: SMAdCallback? = null
    ) {
        SMAdConfig.registerPlacement(adUnitId, adUnitId, isEnabled)
        preloadInterstitialAd(context, adUnitId, adUnitId, isEnabled, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun preloadRewardedAd(
        context: Context,
        adUnitId: String,
        isEnabled: Boolean,
        callback: SMAdCallback? = null
    ) {
        SMAdConfig.registerPlacement(adUnitId, adUnitId, isEnabled)
        preloadRewardedAd(context, adUnitId, adUnitId, isEnabled, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        isEnabled: Boolean,
        isCollapsible: Boolean = false,
        callback: SMAdCallback? = null
    ) {
        loadBanner(activity, container, null, adUnitId, isEnabled, isCollapsible, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun loadBannerWithShimmer(
        activity: Activity,
        container: ViewGroup,
        shimmer: com.facebook.shimmer.ShimmerFrameLayout,
        adUnitId: String,
        isEnabled: Boolean,
        isCollapsible: Boolean = false,
        callback: SMAdCallback? = null
    ) {
        loadBannerWithShimmer(activity, container, shimmer, null, adUnitId, isEnabled, isCollapsible, callback)
    }

    @JvmStatic
    fun loadNativeAd(
        context: Context,
        adUnitId: String,
        isEnabled: Boolean,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (String) -> Unit
    ) {
        loadNativeAd(context, null, adUnitId, isEnabled, onLoaded, onFailed)
    }

    @JvmStatic
    @JvmOverloads
    fun loadNativeWithShimmer(
        activity: Activity,
        container: ViewGroup,
        shimmer: com.facebook.shimmer.ShimmerFrameLayout,
        adUnitId: String,
        isEnabled: Boolean,
        layoutResId: Int,
        callback: SMAdCallback? = null
    ) {
        loadNativeWithShimmer(activity, container, shimmer, null, adUnitId, isEnabled, layoutResId, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun loadAndShowInterstitialAd(
        activity: Activity,
        placementKey: String,
        callback: SMAdCallback,
        timeoutMs: Long = 8000L
    ) {
        loadAndShowInterstitialAd(activity, placementKey, null, null, timeoutMs, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun loadAndShowInterstitialAd(
        activity: Activity,
        adUnitId: String,
        isEnabled: Boolean,
        callback: SMAdCallback,
        timeoutMs: Long = 8000L
    ) {
        SMAdConfig.registerPlacement(adUnitId, adUnitId, isEnabled)
        loadAndShowInterstitialAd(activity, adUnitId, adUnitId, isEnabled, timeoutMs, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun loadAndShowRewardedAd(
        activity: Activity,
        placementKey: String,
        callback: SMAdCallback,
        timeoutMs: Long = 8000L,
        onUserEarnedReward: (RewardItem) -> Unit
    ) {
        loadAndShowRewardedAd(activity, placementKey, null, null, timeoutMs, callback, onUserEarnedReward)
    }

    @JvmStatic
    @JvmOverloads
    fun loadAndShowRewardedAd(
        activity: Activity,
        adUnitId: String,
        isEnabled: Boolean,
        callback: SMAdCallback,
        timeoutMs: Long = 8000L,
        onUserEarnedReward: (RewardItem) -> Unit
    ) {
        SMAdConfig.registerPlacement(adUnitId, adUnitId, isEnabled)
        loadAndShowRewardedAd(activity, adUnitId, adUnitId, isEnabled, timeoutMs, callback, onUserEarnedReward)
    }
}
