package com.smappsstudio.ads

abstract class SMAdCallback {
    open fun onAdLoaded() {}
    open fun onAdFailedToLoad(error: String) {}
    open fun onAdOpened() {}
    open fun onAdClosed() {}
    open fun onAdClicked() {}
    open fun onNextAction() {}
}
