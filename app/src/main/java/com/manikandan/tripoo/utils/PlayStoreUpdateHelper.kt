package com.manikandan.tripoo.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.manikandan.tripoo.R

/**
 * Uses Play In-App Updates to detect whether a newer version exists on Google Play,
 * then shows a Tripoo-themed dialog that opens the store listing.
 */
object PlayStoreUpdateHelper {

    private const val PREFS_NAME = "tripoo_play_update_prompt"
    private const val KEY_SNOOZE_UNTIL_MS = "snooze_until_ms"
    private const val SNOOZE_MS = 24L * 60 * 60 * 1000

    fun attach(activity: AppCompatActivity) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var started = false

            override fun onStart(owner: LifecycleOwner) {
                if (started) return
                started = true
                maybePrompt(activity)
            }
        })
    }

    private fun maybePrompt(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_SNOOZE_UNTIL_MS, 0L) > System.currentTimeMillis()) return

        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener

                MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Tripoo_MaterialAlertDialog)
                    .setTitle(R.string.update_available_title)
                    .setMessage(R.string.update_available_message)
                    .setPositiveButton(R.string.update_available_confirm) { _, _ ->
                        openPlayStore(activity)
                    }
                    .setNegativeButton(R.string.update_available_later) { _, _ ->
                        prefs.edit().putLong(KEY_SNOOZE_UNTIL_MS, System.currentTimeMillis() + SNOOZE_MS).apply()
                    }
                    .show()
            }
    }

    private fun openPlayStore(context: Context) {
        val pkg = context.packageName
        val marketUri = Uri.parse("market://details?id=$pkg")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        val intent = Intent(Intent.ACTION_VIEW, marketUri)
        if (context !is AppCompatActivity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW, webUri)
            if (context !is AppCompatActivity) web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(web)
        }
    }
}
