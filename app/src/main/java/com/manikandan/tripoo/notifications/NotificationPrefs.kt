package com.manikandan.tripoo.notifications

import android.content.Context

object NotificationPrefs {
    private const val PREFS = "tripoo_profile_prefs"
    private const val KEY_NOTIFY = "notifications_on"

    @JvmStatic
    fun areTripNotificationsEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFY, true)
}
