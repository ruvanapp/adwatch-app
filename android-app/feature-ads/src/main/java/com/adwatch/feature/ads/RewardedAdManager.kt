package com.adwatch.feature.ads

import android.app.Activity
import android.content.Context
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AdState {
    object Idle : AdState()
    object Loading : AdState()
    object Ready : AdState()
    object Showing : AdState()
    data class Rewarded(val amount: Int) : AdState()
    data class Error(val message: String) : AdState()
}

@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Replace with your AppLovin MAX rewarded ad unit ID from dashboard.applovin.com
        // Go to: Mediation → Manage → Ad Units → Create Ad Unit (Rewarded)
        const val AD_UNIT_ID = "YOUR_REWARDED_AD_UNIT_ID"
    }

    private val _adState = MutableStateFlow<AdState>(AdState.Idle)
    val adState: StateFlow<AdState> = _adState.asStateFlow()

    private var rewardedAd: MaxRewardedAd? = null
    private var pendingRewardCallback: ((Int, String) -> Unit)? = null

    private val listener = object : MaxRewardedAdListener {
        override fun onAdLoaded(ad: MaxAd) {
            _adState.value = AdState.Ready
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            _adState.value = AdState.Error(error.message)
        }

        override fun onAdDisplayed(ad: MaxAd) {
            _adState.value = AdState.Showing
        }

        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
            _adState.value = AdState.Error(error.message)
        }

        override fun onAdHidden(ad: MaxAd) {
            _adState.value = AdState.Idle
            // Preload next ad automatically
            rewardedAd?.loadAd()
        }

        override fun onAdClicked(ad: MaxAd) {}

        override fun onRewardedVideoStarted(ad: MaxAd) {}

        override fun onRewardedVideoCompleted(ad: MaxAd) {}

        override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
            _adState.value = AdState.Rewarded(reward.amount)
            pendingRewardCallback?.invoke(
                reward.amount,
                reward.label.ifBlank { "credits" }
            )
            pendingRewardCallback = null
        }
    }

    fun loadAd(activity: Activity) {
        _adState.value = AdState.Loading
        val ad = MaxRewardedAd.getInstance(AD_UNIT_ID, activity)
        ad.setListener(listener)
        rewardedAd = ad
        ad.loadAd()
    }

    fun showAd(activity: Activity, onRewarded: (Int, String) -> Unit) {
        pendingRewardCallback = onRewarded
        // AppLovin MAX uses the Activity passed to getInstance() — no Activity needed here
        rewardedAd?.showAd()
    }

    fun resetState() {
        _adState.value = AdState.Idle
        pendingRewardCallback = null
    }

    val isAdReady: Boolean
        get() = rewardedAd?.isReady == true
}
