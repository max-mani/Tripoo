package com.manikandan.tripoo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.manikandan.tripoo.databinding.ActivityMainBinding
import com.manikandan.tripoo.notifications.NotificationConstants

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** When already on Home, navigation args do not refresh; consume this in [com.manikandan.tripoo.ui.home.HomeFragment]. */
    @Volatile
    var pendingOpenTripId: String? = null
        private set

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, maxOf(nav.bottom, ime.bottom))
            insets
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationLaunchIntent(intent)
    }

    /**
     * Warm start: user tapped a notification while the app was in the back stack.
     * Cold start is handled by [com.manikandan.tripoo.ui.splash.SplashFragment] using the same intent extra.
     */
    fun handleNotificationLaunchIntent(intent: Intent?) {
        val i = intent ?: return
        val tripId = i.getStringExtra(NotificationConstants.EXTRA_OPEN_TRIP_ID)?.trim().orEmpty()
        if (tripId.isEmpty()) return

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: return
        val nav = navHost.navController
        val dest = nav.currentDestination?.id ?: return

        i.removeExtra(NotificationConstants.EXTRA_OPEN_TRIP_ID)

        when (dest) {
            R.id.tripDashboardFragment -> {
                val args = Bundle().apply { putString("tripId", tripId) }
                nav.navigate(R.id.action_dashboard_to_home, args)
            }
            R.id.homeFragment -> {
                pendingOpenTripId = tripId
            }
            else -> {
                // Put back so Splash or a later screen can still read once.
                i.putExtra(NotificationConstants.EXTRA_OPEN_TRIP_ID, tripId)
            }
        }
    }

    fun consumePendingOpenTripId(): String? {
        val t = pendingOpenTripId
        pendingOpenTripId = null
        return t
    }
}
