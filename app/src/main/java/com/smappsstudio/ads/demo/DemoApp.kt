package com.smappsstudio.ads.demo

import android.app.Application
import com.smappsstudio.ads.SMAdManager
import com.smappsstudio.ads.SMAppOpenAdManager

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SM Ads SDK in Debug mode
        SMAdManager.initialize(this, isDebug = true)

        // Initialize App Open Ad Manager with placement key "open_resume"
        SMAppOpenAdManager.getInstance().initialize(this, "open_resume")

        // Exclude SplashActivity from showing App Open Ads on Resume
        SMAppOpenAdManager.getInstance().disableAppOpenForActivity(SplashActivity::class.java)
    }
}
