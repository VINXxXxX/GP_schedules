package com.example.schedule

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.schedule.ui.ThemePrefs
import com.example.schedule.update.UpdateChecker
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var motogpSwitch: SwitchMaterial
    private lateinit var sbkSwitch: SwitchMaterial

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val context = requireContext()
        val activity = requireActivity()
        val dark = ThemePrefs.isDark(context)

        // ------------------ VIEWS ------------------

        val versionText = view.findViewById<TextView>(R.id.versionText)
        val updateButton = view.findViewById<TextView>(R.id.checkUpdateButton)
        val themeContainer = view.findViewById<View>(R.id.themeToggleContainer)
        val themeSwitch = view.findViewById<SwitchMaterial>(R.id.themeSwitch)

        val motogpContainer = view.findViewById<View>(R.id.motogpNotifyContainer)
        val sbkContainer = view.findViewById<View>(R.id.sbkNotifyContainer)

        motogpSwitch = view.findViewById(R.id.motogpNotifySwitch)
        sbkSwitch = view.findViewById(R.id.sbkNotifySwitch)

        val motogpLabel = view.findViewById<TextView>(R.id.motogpNotifyLabel)
        val sbkLabel = view.findViewById<TextView>(R.id.sbkNotifyLabel)
        val lightModeLabel = view.findViewById<TextView>(R.id.lightModeLabel)

        // ------------------ LOAD FONTS ------------------

        var racingBold: Typeface
        var racingRegular: Typeface

        try {
            racingBold = Typeface.createFromAsset(context.assets, "fonts/racing_bold.ttf")
            racingRegular = Typeface.createFromAsset(context.assets, "fonts/racing_regular.ttf")
        } catch (_: Exception) {
            racingBold = Typeface.DEFAULT_BOLD
            racingRegular = Typeface.DEFAULT
        }

        // ------------------ THEME TOGGLE ------------------

        themeSwitch.isChecked = !dark
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ThemePrefs.setDark(context, !isChecked)
            activity.recreate()
        }

        // ------------------ STYLING ------------------

        styleSwitch(themeSwitch, dark)
        styleSwitch(motogpSwitch, dark)
        styleSwitch(sbkSwitch, dark)

        val bgRes =
            if (dark) R.drawable.bg_racing_button_dark
            else R.drawable.bg_racing_button_light

        updateButton.setBackgroundResource(bgRes)
        themeContainer.setBackgroundResource(bgRes)
        motogpContainer.setBackgroundResource(bgRes)
        sbkContainer.setBackgroundResource(bgRes)

        val textColor = if (dark) Color.WHITE else Color.BLACK

        versionText.apply {
            text = "APP VERSION  ${
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }"
            setTextColor(textColor)
            typeface = racingRegular
            letterSpacing = 0.08f
        }

        updateButton.apply {
            setTextColor(textColor)
            typeface = racingBold
            setOnClickListener {
                UpdateChecker.checkForUpdate(activity, manual = true)
            }
        }

        lightModeLabel.apply {
            setTextColor(textColor)
            typeface = racingBold
        }

        motogpLabel.apply {
            setTextColor(textColor)
            typeface = racingBold
        }

        sbkLabel.apply {
            setTextColor(textColor)
            typeface = racingBold
        }

        // ------------------ STATE INIT ------------------

        refreshNotificationToggles()

        // ------------------ CONTAINER CLICK HANDLING ------------------

        motogpContainer.setOnClickListener {
            if (NotificationPermissionHelper.notificationsDisabled(context)) {
                showNotificationPermissionDialog()
            } else {
                motogpSwitch.toggle()
            }
        }

        sbkContainer.setOnClickListener {
            if (NotificationPermissionHelper.notificationsDisabled(context)) {
                showNotificationPermissionDialog()
            } else {
                sbkSwitch.toggle()
            }
        }

        // ------------------ REAL STATE CHANGE ------------------

        motogpSwitch.setOnCheckedChangeListener { _, enabled ->
            NotificationPrefs.setMotoGP(context, enabled)
            NotificationScheduler.scheduleForNextRace(context)
        }

        sbkSwitch.setOnCheckedChangeListener { _, enabled ->
            NotificationPrefs.setSBK(context, enabled)
            NotificationScheduler.scheduleForNextRace(context)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationToggles()
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private fun refreshNotificationToggles() {
        val context = requireContext()
        val hasPermission =
            !NotificationPermissionHelper.notificationsDisabled(context)

        motogpSwitch.isEnabled = hasPermission
        sbkSwitch.isEnabled = hasPermission

        motogpSwitch.isChecked =
            hasPermission && NotificationPrefs.isMotoGPEnabled(context)

        sbkSwitch.isChecked =
            hasPermission && NotificationPrefs.isSBKEnabled(context)
    }

    private fun styleSwitch(
        switch: SwitchMaterial,
        dark: Boolean
    ) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )

        val thumbColors = intArrayOf(
            "#ec1010".toColorInt(), // checked
            if (dark) Color.WHITE else Color.BLACK // unchecked
        )

        val trackColors = intArrayOf(
            "#ec1010".toColorInt(), // checked
            "#808080".toColorInt()  // unchecked
        )

        switch.thumbTintList = ColorStateList(states, thumbColors)
        switch.trackTintList = ColorStateList(states, trackColors)
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable notifications")
            .setMessage(
                "Notifications are blocked. Enable them in system settings to receive MotoGP and SBK alerts."
            )
            .setPositiveButton("Open settings") { _, _ ->
                NotificationPermissionHelper.openSettings(requireActivity())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}