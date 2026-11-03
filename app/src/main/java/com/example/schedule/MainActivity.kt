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
import android.app.Activity
import android.widget.TextView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.schedule.ui.Pit
import com.example.schedule.ui.Pit.styled
import com.example.schedule.ui.PitPalette
import com.example.schedule.update.UpdateChecker

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

        // Opened via "Open with GP Schedule" on a .json file
        if (savedInstanceState == null) handleScheduleFileIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Race week only, at most once a day
        com.example.schedule.weather.WeatherWorker.enqueue(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleScheduleFileIntent(intent)
    }

    /* ---------------------------------------------------------- */
    /* SCHEDULE FILE (open-with from a file manager)              */
    /* ---------------------------------------------------------- */

    private fun handleScheduleFileIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        // Don't re-import if the activity is recreated with the same intent
        setIntent(Intent(this, MainActivity::class.java))

        ScheduleImporter.importFromUri(this, uri)
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
        SchedulerCoordinator.init(this)
    }

    /* ---------------------------------------------------------- */
    /* THEME                                                      */
    /* ---------------------------------------------------------- */

    private fun setupTheme() {
        val p = Pit.palette(this)
        applyWindowColors(this, p)
        findViewById<View>(R.id.mainRoot).setBackgroundColor(p.bg)
        findViewById<View>(R.id.bottomNav).setBackgroundColor(p.bg)
        findViewById<View>(R.id.navDivider).setBackgroundColor(p.line)
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
        val p = Pit.palette(this)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.adapter = RacePagerAdapter(this)

        val tabs = listOf(
            Triple(findViewById<View>(R.id.navSchedule), findViewById<TextView>(R.id.navScheduleText), findViewById<View>(R.id.navScheduleBar)),
            Triple(findViewById<View>(R.id.navSettings), findViewById<TextView>(R.id.navSettingsText), findViewById<View>(R.id.navSettingsBar))
        )

        fun select(position: Int) {
            tabs.forEachIndexed { i, (root, text, bar) ->
                val on = i == position
                text.styled(Pit.display(this), 14f, if (on) p.text else p.faint, 0.1f)
                bar.background = if (on) Pit.rounded(this, p.accent, 2f) else null
                root.isSelected = on
                root.contentDescription = text.text.toString() + if (on) ", selected" else ""
            }
        }

        tabs.forEachIndexed { i, (root, _, _) ->
            root.setOnClickListener { viewPager.currentItem = i }
        }

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = select(position)
            }
        )
        select(0)
    }

    companion object {
        /** Status / nav bar colors to match the screen */
        fun applyWindowColors(activity: Activity, p: PitPalette) {
            val w = activity.window
            @Suppress("DEPRECATION")
            w.statusBarColor = p.bg
            @Suppress("DEPRECATION")
            w.navigationBarColor = p.bg
            w.decorView.setBackgroundColor(p.bg)
            val light = p == Pit.LIGHT
            WindowInsetsControllerCompat(w, w.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}
