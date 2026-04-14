package com.manikandan.tripoo.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.manikandan.tripoo.R
import java.util.Calendar
import java.util.Locale

/**
 * Interstitial when leaving an active trip flow for the all-trips dashboard.
 * At most one successful show per local calendar day (SharedPreferences).
 */
object TripExitInterstitialHelper {

    private const val PREFS_NAME = "tripoo_ads"
    private const val PREF_KEY_DAY = "trip_exit_interstitial_last_day"

    @Volatile
    private var prepared: InterstitialAd? = null

    @Volatile
    private var loading = false

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    @JvmStatic
    fun canShowToday(context: Context): Boolean {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY_DAY, null) ?: return true
        return stored != todayKey()
    }

    private fun markShownToday(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_DAY, todayKey())
            .apply()
    }

    /** Loads the next interstitial in the background if the daily cap has not been reached. */
    @JvmStatic
    fun preload(context: Context) {
        if (!canShowToday(context)) return
        if (prepared != null || loading) return
        val activity = context as? Activity ?: return
        loading = true
        val unitId = context.getString(R.string.admob_interstitial_trip_exit)
        InterstitialAd.load(
            activity,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    prepared = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loading = false
                    prepared = null
                }
            }
        )
    }

    /**
     * If [tripId] is blank, runs [navigate] immediately.
     * Otherwise may show an interstitial (once per day), then runs [navigate].
     */
    @JvmStatic
    fun navigateToTripDashboard(activity: Activity, tripId: String?, navigate: Runnable) {
        if (tripId.isNullOrBlank()) {
            navigate.run()
            return
        }
        if (!canShowToday(activity)) {
            navigate.run()
            return
        }
        val ad = prepared
        if (ad == null) {
            navigate.run()
            preload(activity)
            return
        }
        prepared = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                markShownToday(activity)
                navigate.run()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                navigate.run()
                preload(activity)
            }
        }
        ad.show(activity)
    }
}
