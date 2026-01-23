package com.example.schedule

import android.annotation.SuppressLint
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
    private lateinit var motogpContainer: View
    private lateinit var sbkContainer: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val context = requireContext()
        val dark = ThemePrefs.isDark(context)

        // ------------------ VIEWS ------------------
        val versionText = view.findViewById<TextView>(R.id.versionText)
        val updateButton = view.findViewById<TextView>(R.id.checkUpdateButton)
        val themeContainer = view.findViewById<View>(R.id.themeToggleContainer)
        val themeSwitch = view.findViewById<SwitchMaterial>(R.id.themeSwitch)

        motogpContainer = view.findViewById(R.id.motogpNotifyContainer)
        sbkContainer = view.findViewById(R.id.sbkNotifyContainer)

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
            requireActivity().recreate()
        }

        // ------------------ SWITCH STYLING ------------------
        styleSwitch(themeSwitch, dark)
        styleSwitch(motogpSwitch, dark)
        styleSwitch(sbkSwitch, dark)

        // ------------------ BACKGROUNDS ------------------
        val bgRes =
            if (dark) R.drawable.bg_racing_button_dark
            else R.drawable.bg_racing_button_light

        updateButton.setBackgroundResource(bgRes)
        themeContainer.setBackgroundResource(bgRes)
        motogpContainer.setBackgroundResource(bgRes)
        sbkContainer.setBackgroundResource(bgRes)

        // ------------------ TEXT COLORS + FONTS ------------------
        val textColor = if (dark) Color.WHITE else Color.BLACK

        versionText.setTextColor(textColor)
        versionText.typeface = racingRegular
        versionText.letterSpacing = 0.08f

        updateButton.setTextColor(textColor)
        updateButton.typeface = racingBold

        lightModeLabel.setTextColor(textColor)
        lightModeLabel.typeface = racingBold

        motogpLabel.setTextColor(textColor)
        motogpLabel.typeface = racingBold

        sbkLabel.setTextColor(textColor)
        sbkLabel.typeface = racingBold

        // ------------------ VERSION ------------------
        val versionName =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        versionText.text = "APP VERSION  $versionName"

        // ------------------ UPDATE BUTTON ------------------
        updateButton.setOnClickListener {
            UpdateChecker.checkForUpdate(requireActivity(), manual = true)
        }

        // ------------------ NOTIFICATION TOGGLES ------------------
        refreshNotificationToggles()

        motogpSwitch.setOnCheckedChangeListener { _, enabled ->
            NotificationPrefs.setMotoGP(context, enabled)
            NotificationScheduler.scheduleForNextRace(context)
        }

        sbkSwitch.setOnCheckedChangeListener { _, enabled ->
            NotificationPrefs.setSBK(context, enabled)
            NotificationScheduler.scheduleForNextRace(context)
        }

        // ------------------ PERMISSION HANDLING ------------------

        motogpContainer.setOnClickListener {
            if (!NotificationPermissionHelper.hasPermission(context)) {
                showNotificationPermissionDialog()
            }
        }

        sbkContainer.setOnClickListener {
            if (!NotificationPermissionHelper.hasPermission(context)) {
                showNotificationPermissionDialog()
            }
        }

        // Intercept switch taps BEFORE Android blocks them
        motogpSwitch.setOnTouchListener { v, _ ->
            if (!NotificationPermissionHelper.hasPermission(context)) {
                showNotificationPermissionDialog()
                v.performClick()
                true
            } else {
                false
            }
        }

        sbkSwitch.setOnTouchListener { v, _ ->
            if (!NotificationPermissionHelper.hasPermission(context)) {
                showNotificationPermissionDialog()
                v.performClick()
                true
            } else {
                false
            }
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
        val hasPermission = NotificationPermissionHelper.hasPermission(context)

        motogpSwitch.isEnabled = hasPermission
        sbkSwitch.isEnabled = hasPermission

        motogpSwitch.isChecked =
            hasPermission && NotificationPrefs.isMotoGPEnabled(context)

        sbkSwitch.isChecked =
            hasPermission && NotificationPrefs.isSBKEnabled(context)

        val alpha = if (hasPermission) 1f else 0.5f
        motogpSwitch.alpha = alpha
        sbkSwitch.alpha = alpha
    }

    private fun styleSwitch(
        switch: SwitchMaterial,
        dark: Boolean
    ) {
        switch.thumbTintList =
            android.content.res.ColorStateList.valueOf(
                if (dark) Color.WHITE else Color.BLACK
            )

        switch.trackTintList =
            android.content.res.ColorStateList.valueOf("#ec1010".toColorInt())
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable notifications")
            .setMessage(
                "Enable notifications in system settings to receive MotoGP and SBK race alerts."
            )
            .setPositiveButton("Open settings") { _, _ ->
                NotificationPermissionHelper.openSettings(requireActivity())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
