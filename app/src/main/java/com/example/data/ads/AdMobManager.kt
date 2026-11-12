package com.example.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdMobManager {
    private const val TAG = "AdMobManager"

    // Official Google AdMob Test Ad Unit IDs (replace with your production IDs before Play Store release)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private const val INTERSTITIAL_COOLDOWN_MS = 90_000L

    @Volatile
    private var isInitialized = false

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var isLoadingInterstitial = false

    @Volatile
    private var lastInterstitialShownAt = 0L

    fun initialize(context: Context) {
        if (isInitialized) return
        runCatching {
            MobileAds.initialize(context.applicationContext) {
                isInitialized = true
                Log.d(TAG, "Google Mobile Ads initialized (Test Ads Mode)")
            }
        }.onFailure { err ->
            Log.w(TAG, "MobileAds initialization skipped: ${err.message}")
        }
    }

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true
        runCatching {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                context.applicationContext,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                        Log.d(TAG, "Test interstitial ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                        Log.w(TAG, "Test interstitial failed to load: ${error.message}")
                    }
                }
            )
        }.onFailure {
            isLoadingInterstitial = false
        }
    }

    fun showInterstitialIfReady(activity: Activity, onContinue: () -> Unit) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd
        if (ad == null || (now - lastInterstitialShownAt) < INTERSTITIAL_COOLDOWN_MS) {
            if (ad == null) preloadInterstitial(activity)
            onContinue()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastInterstitialShownAt = System.currentTimeMillis()
                preloadInterstitial(activity)
                onContinue()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preloadInterstitial(activity)
                onContinue()
            }
        }
        ad.show(activity)
    }
}
