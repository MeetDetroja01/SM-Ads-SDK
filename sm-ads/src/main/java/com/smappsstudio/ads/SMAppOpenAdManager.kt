package com.smappsstudio.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class SMAppOpenAdManager private constructor() : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    companion object {
        @Volatile
        private var instance: SMAppOpenAdManager? = null

        fun getInstance(): SMAppOpenAdManager {
            return instance ?: synchronized(this) {
                instance ?: SMAppOpenAdManager().also { instance = it }
            }
        }
    }

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null
    private var application: Application? = null

    // Set of Activity classes where App Open Ad should be disabled (e.g. Splash, Onboarding, Language)
    private val disabledActivities = mutableSetOf<Class<out Activity>>()

    // Gating variables
    private var adUnitId: String = ""
    private var isEnabled: Boolean = true

    /**
     * Initialize the App Open Manager in Application onCreate()
     */
    fun initialize(application: Application, placementKey: String) {
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val config = SMAdConfig.getPlacement(placementKey)
        this.adUnitId = config.id
        this.isEnabled = config.isEnable
    }

    /**
     * Disable App Open Ad for specific activities.
     */
    fun disableAppOpenForActivity(activityClass: Class<out Activity>) {
        disabledActivities.add(activityClass)
    }

    /**
     * Enable/Disable App Open Ad dynamically (e.g. if IAP is purchased)
     */
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    /**
     * Request an App Open Ad.
     */
    fun fetchAd() {
        if (isAdAvailable()) return
        if (adUnitId.isEmpty() || !isEnabled) return

        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            application ?: return,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle failure
                }
            }
        )
    }

    /**
     * Show the App Open Ad.
     */
    fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd) return
        if (!isAdAvailable()) {
            fetchAd()
            return
        }

        if (disabledActivities.contains(activity.javaClass)) {
            return
        }

        if (!isEnabled) return

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                fetchAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                fetchAd()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }
        }

        appOpenAd?.show(activity)
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - this.loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    // DefaultLifecycleObserver implementation for App Resume detection
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let {
            showAdIfAvailable(it)
        }
    }

    // ActivityLifecycleCallbacks implementation
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
