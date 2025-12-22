package com.example.schedule

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.schedule.ui.ThemePrefs
import com.example.schedule.update.UpdateChecker
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val dark = ThemePrefs.isDark(requireContext())

        // ---- Views ----
        val versionText = view.findViewById<TextView>(R.id.versionText)
        val updateButton = view.findViewById<TextView>(R.id.checkUpdateButton)
        val toggleContainer = view.findViewById<View>(R.id.themeToggleContainer)
        val themeSwitch = view.findViewById<SwitchMaterial>(R.id.themeSwitch)

        // ---- Toggle state ----
        themeSwitch.isChecked = !dark
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ThemePrefs.setDark(requireContext(), !isChecked)
            requireActivity().recreate()
        }

        if (dark) {
            // DARK MODE
            themeSwitch.thumbTintList =
                android.content.res.ColorStateList.valueOf(Color.WHITE)

            themeSwitch.trackTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#ec1010") // red with alpha
                )
        } else {
            // LIGHT MODE
            themeSwitch.thumbTintList =
                android.content.res.ColorStateList.valueOf(Color.BLACK)

            themeSwitch.trackTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#ec1010") // subtle dark track
                )
        }

        // ---- Button background (THIS is the key part) ----
        val bgRes =
            if (dark) R.drawable.bg_racing_button_dark
            else R.drawable.bg_racing_button_light

        updateButton.setBackgroundResource(bgRes)
        toggleContainer.setBackgroundResource(bgRes)

        // ---- Text colors ----
        val textColor = if (dark) Color.WHITE else Color.BLACK
        updateButton.setTextColor(textColor)
        versionText.setTextColor(textColor)
        val lightModeLabel = view.findViewById<TextView>(R.id.lightModeLabel)
        lightModeLabel.setTextColor(textColor)



        // ---- Fonts ----
        var racingBold: Typeface
        var racingRegular: Typeface

        try {
            racingBold = Typeface.createFromAsset(
                requireContext().assets,
                "fonts/racing_bold.ttf"
            )
            racingRegular = Typeface.createFromAsset(
                requireContext().assets,
                "fonts/racing_regular.ttf"
            )
        } catch (_: Exception) {
            racingBold = Typeface.DEFAULT_BOLD
            racingRegular = Typeface.DEFAULT
        }

        updateButton.typeface = racingBold
        versionText.typeface = racingRegular
        lightModeLabel.typeface = racingBold


        // ---- Version text ----
        val pm = requireContext().packageManager
        val pkg = requireContext().packageName
        val versionName = pm.getPackageInfo(pkg, 0).versionName

        versionText.text = "APP VERSION  $versionName"
        versionText.letterSpacing = 0.08f

        // ---- Update click ----
        updateButton.setOnClickListener {
            UpdateChecker.checkForUpdate(requireActivity(), manual = true)
        }
    }
}
