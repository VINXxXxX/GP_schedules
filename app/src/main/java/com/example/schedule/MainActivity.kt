package com.example.schedule

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.schedule.ui.ThemePrefs
import com.example.schedule.update.UpdateChecker
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationChannels.create(this)

        setContentView(R.layout.activity_main)

        UpdateChecker.checkForUpdate(this, manual = false)

        setupTheme()
        requestNotificationPermissionIfNeeded()
        setupUI()
        maybePromptBatteryOptimization()
    }

    /* ---------------------------------------------------------- */
    /* NOTIFICATION FLOW                                          */
    /* ---------------------------------------------------------- */

    private fun requestNotificationPermissionIfNeeded() {

        // Ask permission if needed
        NotificationPermissionHelper.maybeRequest(this)

        // If already granted, schedule immediately
        if (!NotificationPermissionHelper.notificationsDisabled(this)) {
            scheduleNotifications()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 2001) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                scheduleNotifications()
            }
        }
    }

    private fun scheduleNotifications() {
        NotificationScheduler.scheduleForNextRace(this)
        SchedulerCoordinator.init(this)
    }

    /* ---------------------------------------------------------- */
    /* THEME                                                      */
    /* ---------------------------------------------------------- */

    private fun setupTheme() {
        val gradient = findViewById<View>(R.id.gradientBackground)
        val isDark = ThemePrefs.isDark(this)

        gradient.setBackgroundResource(
            if (isDark) R.drawable.widget_gradient_dark
            else R.drawable.widget_gradient_light
        )
    }

    /* ---------------------------------------------------------- */
    /* BATTERY OPTIMIZATION                                       */
    /* ---------------------------------------------------------- */

    @SuppressLint("BatteryLife")
    private fun maybePromptBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager

        if (!pm.isIgnoringBatteryOptimizations(packageName)
            && BatteryPrompt.shouldShow(this)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Allow background updates")
                .setMessage(
                    "To keep race notifications and widgets accurate, " +
                            "please allow background activity."
                )
                .setPositiveButton("Allow") { _, _ ->
                    BatteryPrompt.markShown(this)
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                .setNegativeButton("Later") { _, _ ->
                    BatteryPrompt.markShown(this)
                }
                .show()
        }
    }

    /* ---------------------------------------------------------- */
    /* UI                                                         */
    /* ---------------------------------------------------------- */

    private fun setupUI() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        viewPager.adapter = RacePagerAdapter(this)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_motogp -> viewPager.currentItem = 0
                R.id.nav_sbk -> viewPager.currentItem = 1
                R.id.nav_settings -> viewPager.currentItem = 2
            }
            true
        }

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    bottomNav.selectedItemId = when (position) {
                        0 -> R.id.nav_motogp
                        1 -> R.id.nav_sbk
                        else -> R.id.nav_settings
                    }
                }
            }
        )
    }
}
