package com.manikandan.tripoo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.manikandan.tripoo.notifications.FanoutTripNotificationListener
import com.manikandan.tripoo.notifications.NotificationConstants
import com.manikandan.tripoo.notifications.TripDeadlineWorker

class TripooApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        createTripNotificationChannel()

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                FanoutTripNotificationListener.start(this, user.uid)
                TripDeadlineWorker.schedule(this)
            } else {
                FanoutTripNotificationListener.stop()
                TripDeadlineWorker.cancel(this)
            }
        }
    }

    private fun createTripNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NotificationConstants.CHANNEL_TRIP_UPDATES_ID,
            getString(R.string.notification_channel_trip_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_trip_desc)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
