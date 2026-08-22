package com.smappsstudio.ads

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

object SMNativeAdHelper {

    /**
     * Populate ad assets into a NativeAdView container.
     */
    fun populateNativeAd(nativeAd: NativeAd, adView: NativeAdView) {
        adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        // Headline is guaranteed to be in every NativeAd
        (adView.headlineView as? TextView)?.text = nativeAd.headline

        // Media view configuration
        adView.mediaView?.mediaContent = nativeAd.mediaContent

        // Optional components
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        adView.setNativeAd(nativeAd)
    }

    /**
     * Inflates a custom layout and renders the NativeAd into a ViewGroup container.
     */
    fun populateNativeAdInContainer(
        activity: Activity,
        nativeAd: NativeAd,
        container: ViewGroup,
        layoutResId: Int
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        val adView = inflater.inflate(layoutResId, null) as NativeAdView
        populateNativeAd(nativeAd, adView)
        container.addView(adView)
        container.visibility = View.VISIBLE
    }

    /**
     * Inflates and renders native ad using the default small template.
     */
    fun populateDefaultSmallNative(activity: Activity, nativeAd: NativeAd, container: ViewGroup) {
        populateNativeAdInContainer(activity, nativeAd, container, R.layout.layout_native_small)
    }

    /**
     * Inflates and renders native ad using the default medium template.
     */
    fun populateDefaultMediumNative(activity: Activity, nativeAd: NativeAd, container: ViewGroup) {
        populateNativeAdInContainer(activity, nativeAd, container, R.layout.layout_native_medium)
    }
}
