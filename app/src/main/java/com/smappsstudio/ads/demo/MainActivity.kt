package com.smappsstudio.ads.demo

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smappsstudio.ads.SMAdCallback
import com.smappsstudio.ads.SMAdManager
import com.smappsstudio.ads.SMNativeAdHelper

class MainActivity : AppCompatActivity() {

    private lateinit var bannerContainer: FrameLayout
    private lateinit var nativeContainer: FrameLayout
    private lateinit var cbPremium: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bannerContainer = findViewById(R.id.banner_ad_container)
        nativeContainer = findViewById(R.id.native_ad_container)
        cbPremium = findViewById(R.id.cb_premium)

        // 1. Preload Interstitial
        SMAdManager.preloadInterstitialAd(this, "inter_details")

        // 2. Load Banner and Native
        loadAds()

        // 3. Handle premium checkbox
        cbPremium.setOnCheckedChangeListener { _, isChecked ->
            SMAdManager.setPremiumUser(isChecked)
            if (isChecked) {
                // Remove loaded ads from screen immediately
                bannerContainer.removeAllViews()
                nativeContainer.removeAllViews()
                Toast.makeText(this, "Premium Enabled: Ads Removed", Toast.LENGTH_SHORT).show()
            } else {
                // Reload ads
                loadAds()
                Toast.makeText(this, "Premium Disabled: Loading Ads", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Interstitial Show trigger
        findViewById<Button>(R.id.btn_show_inter).setOnClickListener {
            SMAdManager.showInterstitialAd(
                activity = this,
                placementKey = "inter_details",
                callback = object : SMAdCallback() {
                    override fun onAdClosed() {
                        Toast.makeText(this@MainActivity, "Interstitial Closed! Moving forward.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun loadAds() {
        // Load Adaptive Banner
        SMAdManager.loadBanner(
            activity = this,
            container = bannerContainer,
            placementKey = "banner_home",
            isCollapsible = false
        )

        // Load Native Ad
        SMAdManager.loadNativeAd(
            context = this,
            placementKey = "native_home",
            onLoaded = { nativeAd ->
                SMNativeAdHelper.populateDefaultSmallNative(this, nativeAd, nativeContainer)
            },
            onFailed = { error ->
                Toast.makeText(this, "Native Ad load failed: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
