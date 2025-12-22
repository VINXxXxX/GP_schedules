package com.example.schedule

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.schedule.ui.ThemePrefs
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    @SuppressLint("BatteryLife", "UseKtx")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(android.R.id.content)

        val gradient = findViewById<View>(R.id.gradientBackground)

        val isDark = ThemePrefs.isDark(this)

        gradient.setBackgroundResource(
            if (isDark) R.drawable.widget_gradient_dark
            else R.drawable.widget_gradient_light
        )


        // Schedule alarms & work ONCE
        SchedulerCoordinator.init(this)
        BatteryOptimizationHelper.maybeShowPrompt(this)


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

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = when (position) {
                    0 -> R.id.nav_motogp
                    1 -> R.id.nav_sbk
                    else -> R.id.nav_settings
                }
            }
        })

        // ---------- BATTERY OPTIMIZATION PROMPT (ONE-TIME) ----------
        val pm = getSystemService(POWER_SERVICE) as PowerManager

        if (!pm.isIgnoringBatteryOptimizations(packageName)
            && BatteryPrompt.shouldShow(this)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Allow background updates")
                .setMessage(
                    "To keep race countdown widgets updated daily, " +
                            "please allow background activity. This is required by Android."
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
}
