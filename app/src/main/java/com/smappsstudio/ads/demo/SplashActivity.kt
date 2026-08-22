package com.smappsstudio.ads.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smappsstudio.ads.SMAdCallback
import com.smappsstudio.ads.SMAdManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Load Splash Interstitial and move to MainActivity on completion
        SMAdManager.loadSplashInterstitialAd(
            activity = this,
            placementKey = "inter_splash",
            timeoutMs = 8000, // Safe timeout (8 seconds)
            delayMs = 1500,   // Delay before transitioning if ad is disabled
            callback = object : SMAdCallback() {
                override fun onNextAction() {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        )
    }
}
