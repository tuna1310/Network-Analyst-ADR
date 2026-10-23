package com.networkanalyst.app.data.service

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface cho hệ thống quảng cáo trong ứng dụng.
 * Hiện tại sử dụng [MockAdServiceImpl].
 *
 * ĐỂ TÍCH HỢP ADMOB THẬT:
 * 1. Thêm dependency trong app/build.gradle.kts:
 *    implementation("com.google.android.gms:play-services-ads:23.4.0")
 * 2. Khai báo App ID trong AndroidManifest.xml:
 *    <meta-data
 *        android:name="com.google.android.gms.ads.APPLICATION_ID"
 *        android:value="ca-app-pub-3940256099942544~3347511713" />
 * 3. Tạo AdMobAdServiceImpl kế thừa AdService và bind trong AppModule.
 */
interface AdService {
    val isBannerEnabled: StateFlow<Boolean>
    val interstitialSimulatedCount: StateFlow<Int>
    fun showInterstitial(activity: Activity?, onDismissed: () -> Unit)
    fun recordToolAction(activity: Activity?, onDismissed: (() -> Unit)? = null)
}

@Singleton
class MockAdServiceImpl @Inject constructor() : AdService {
    private val tag = "MockAdService"

    private val _isBannerEnabled = MutableStateFlow(true)
    override val isBannerEnabled: StateFlow<Boolean> = _isBannerEnabled.asStateFlow()

    private val _interstitialSimulatedCount = MutableStateFlow(0)
    override val interstitialSimulatedCount: StateFlow<Int> = _interstitialSimulatedCount.asStateFlow()

    private var actionCount = 0
    private val interstitialThreshold = 3

    override fun showInterstitial(activity: Activity?, onDismissed: () -> Unit) {
        _interstitialSimulatedCount.value += 1
        Log.d(tag, "[MOCK AD] Interstitial simulated: Action triggered ad #${_interstitialSimulatedCount.value}")
        // Với AdMob thật:
        // InterstitialAd.load(activity, adUnitId, adRequest, callback)
        // mInterstitialAd?.show(activity)
        onDismissed()
    }

    override fun recordToolAction(activity: Activity?, onDismissed: (() -> Unit)?) {
        actionCount++
        Log.d(tag, "[MOCK AD] Action recorded: $actionCount / $interstitialThreshold")
        if (actionCount >= interstitialThreshold) {
            actionCount = 0
            showInterstitial(activity) {
                onDismissed?.invoke()
            }
        } else {
            onDismissed?.invoke()
        }
    }
}
