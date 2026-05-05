package com.adwatch.feature.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AdState {
    data object Idle : AdState()
    data object Loading : AdState()
    data object Ready : AdState()
    data object Showing : AdState()
    data class Rewarded(val amount: Int, val type: String) : AdState()
    data class Error(val message: String) : AdState()
}

@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Production ad unit ID
        const val AD_UNIT_ID = "ca-app-pub-4215258396713221/9286614060"
    }

    private var rewardedAd: RewardedAd? = null

    private val _adState = MutableStateFlow<AdState>(AdState.Idle)
    val adState: StateFlow<AdState> = _adState.asStateFlow()

    fun loadAd() {
        if (_adState.value == AdState.Loading || _adState.value == AdState.Ready) return

        _adState.value = AdState.Loading

        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
                _adState.value = AdState.Error("Failed to load ad: ${adError.message}")
            }

            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                _adState.value = AdState.Ready
            }
        })
    }

    fun showAd(activity: Activity, onRewarded: (Int, String) -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            _adState.value = AdState.Error("Ad not loaded")
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                // Preload next ad
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _adState.value = AdState.Error("Failed to show ad: ${adError.message}")
            }

            override fun onAdShowedFullScreenContent() {
                _adState.value = AdState.Showing
            }
        }

        _adState.value = AdState.Showing
        ad.show(activity) { rewardItem ->
            val amount = rewardItem.amount
            val type = rewardItem.type
            _adState.value = AdState.Rewarded(amount, type)
            onRewarded(amount, type)
        }
    }

    fun resetState() {
        _adState.value = AdState.Idle
    }

    fun isAdReady(): Boolean = rewardedAd != null && _adState.value == AdState.Ready
}
