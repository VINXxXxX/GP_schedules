package com.example.schedule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.work.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scheduleDailyWidgetUpdate()

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        viewPager.adapter = RacePagerAdapter(this)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_motogp -> viewPager.currentItem = 0
                R.id.nav_sbk -> viewPager.currentItem = 1
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = if (position == 0) R.id.nav_motogp else R.id.nav_sbk
            }
        })
    }

    private fun scheduleDailyWidgetUpdate() {
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(currentTime)) {
                add(Calendar.DAY_OF_MONTH, 1) // Tomorrow at 12:00 AM
            }
        }

        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val dailyWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag("widget_daily_update")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_widget_update",
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            dailyWork
        )
    }
}