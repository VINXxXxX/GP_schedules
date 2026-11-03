package com.example.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.schedule.data.RaceJsonParser
import com.example.schedule.data.RaceRepository
import com.example.schedule.ui.Pit
import com.example.schedule.ui.Pit.styled
import com.example.schedule.ui.PitPalette
import com.example.schedule.ui.ThemePrefs
import com.example.schedule.update.UpdateChecker
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings, Pit Wall style. Built in code so every row shares one look.
 * Sections: status · notifications · which sessions · extras · appearance · schedule · about
 */
class SettingsFragment : Fragment() {

    private lateinit var p: PitPalette
    private lateinit var list: LinearLayout

    // System file picker → no storage permission needed
    private val pickScheduleFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null && isAdded) {
                ScheduleImporter.importFromUri(requireActivity(), uri) { render() }
            }
        }

    private val scheduleListener: () -> Unit = { if (isAdded) render() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        p = Pit.palette(requireContext())
        list = view.findViewById(R.id.settingsList)
        RaceRepository.addListener(scheduleListener)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    override fun onDestroyView() {
        RaceRepository.removeListener(scheduleListener)
        super.onDestroyView()
    }

    // ==================================================================
    // RENDER
    // ==================================================================

    private fun render() {
        val ctx = context ?: return
        val scrollY = (list.parent as? View)?.scrollY ?: 0
        list.removeAllViews()

        list.addView(TextView(ctx).apply {
            text = "SETTINGS"
            styled(Pit.display(ctx), 32f, p.text, 0.02f)
            setPadding(Pit.dp(ctx, 4f), 0, 0, Pit.dp(ctx, 16f))
        })

        renderStatus(ctx)
        renderAlerts(ctx)
        renderSessionTypes(ctx)
        renderExtras(ctx)
        renderAppearance(ctx)
        renderSchedule(ctx)
        renderAbout(ctx)

        (list.parent as? View)?.post { (list.parent as? View)?.scrollTo(0, scrollY) }
    }

    // ---------------- status ----------------

    private fun renderStatus(ctx: Context) {
        val blocked = NotificationPermissionHelper.notificationsDisabled(ctx)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        val batteryOk = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)

        val (msg, warn, action) = when {
            blocked -> Triple("Notifications are blocked · tap to allow", true, ::fixNotifications)
            !exactOk -> Triple("Exact alarms are off — alerts may be late · tap to allow", true, ::openExactAlarmSettings)
            !batteryOk -> Triple("Battery optimisation may delay alerts · tap to allow", true, ::openBatterySettings)
            else -> Triple("Notifications, exact alarms and battery: all OK", false, null)
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Pit.pressable(ctx, if (warn) p.liveTint else p.surface, 16f, Pit.ripple(p))
            setPadding(Pit.dp(ctx, 14f), Pit.dp(ctx, 12f), Pit.dp(ctx, 14f), Pit.dp(ctx, 12f))
            minimumHeight = Pit.dp(ctx, 48f)
            if (action != null) setOnClickListener { action() }
        }
        row.addView(View(ctx).apply { background = Pit.oval(if (warn) p.accent else 0xFF22C55E.toInt()) },
            LinearLayout.LayoutParams(Pit.dp(ctx, 8f), Pit.dp(ctx, 8f)))
        row.addView(TextView(ctx).apply {
            text = msg
            styled(Pit.bodyMedium, 13f, if (warn) p.text else p.muted)
            setPadding(Pit.dp(ctx, 10f), 0, 0, 0)
        })
        list.addView(row, block())
    }

    // ---------------- notifications ----------------

    private fun renderAlerts(ctx: Context) {
        sectionLabel(ctx, "NOTIFICATIONS")
        val card = card(ctx)

        val enabled = NotificationPrefs.isMotoGPEnabled(ctx)
        card.addView(switchRow(ctx, "Notifications", "Race week · today's sessions · before a session", enabled) { on ->
            NotificationPrefs.setMotoGP(ctx, on)
            if (on && NotificationPermissionHelper.notificationsDisabled(ctx)) fixNotifications()
            changed()
        })

        divider(ctx, card)
        card.addView(caption(ctx, "How long before a session"))
        val lead = NotificationPrefs.leadMinutes(ctx)
        card.addView(segmented(
            ctx,
            NotificationPrefs.LEAD_OPTIONS.map { if (it == 60) "1 hr" else "$it min" },
            NotificationPrefs.LEAD_OPTIONS.indexOf(lead).coerceAtLeast(0),
            accent = true
        ) { i ->
            NotificationPrefs.setLeadMinutes(ctx, NotificationPrefs.LEAD_OPTIONS[i])
            changed()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Pit.dp(ctx, 44f)).apply {
            bottomMargin = Pit.dp(ctx, 14f)
        })
        card.alpha = if (enabled) 1f else 0.6f
    }

    private fun renderSessionTypes(ctx: Context) {
        sectionLabel(ctx, "REMIND ME BEFORE")
        val types = NotificationPrefs.types(ctx)

        val group = ChipGroup(ctx).apply {
            chipSpacingHorizontal = Pit.dp(ctx, 6f)
            chipSpacingVertical = Pit.dp(ctx, 6f)
        }
        NotificationPrefs.TYPES.forEach { t ->
            val on = t in types
            group.addView(chip(ctx, (if (on) "✓ " else "") + NotificationPrefs.typeLabel(t), on) {
                val next = types.toMutableSet().apply { if (on) remove(t) else add(t) }
                NotificationPrefs.setTypes(ctx, next)
                NotificationPrefs.clearOverrides(ctx)
                changed()
            })
        }
        list.addView(group, block(bottom = 8f))

        // Presets
        val presets = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Everything" to NotificationPrefs.PRESET_ALL,
            "Quali + races" to NotificationPrefs.PRESET_QUALI_RACES,
            "Races only" to NotificationPrefs.PRESET_RACES
        ).forEach { (label, set) ->
            val active = types == set
            presets.addView(TextView(ctx).apply {
                text = label
                gravity = Gravity.CENTER
                styled(if (active) Pit.bodyBold else Pit.bodyMedium, 12f, if (active) p.accentText else p.muted)
                background = Pit.pressable(ctx, p.bg, 10f, Pit.ripple(p), if (active) p.accent else p.surface2)
                setPadding(Pit.dp(ctx, 10f), 0, Pit.dp(ctx, 10f), 0)
                setOnClickListener {
                    NotificationPrefs.setTypes(ctx, set)
                    NotificationPrefs.clearOverrides(ctx)
                    changed()
                }
            }, LinearLayout.LayoutParams(0, Pit.dp(ctx, 40f), 1f).apply { marginEnd = Pit.dp(ctx, 6f) })
        }
        list.addView(presets, block())
    }

    private fun renderExtras(ctx: Context) {
        val card = card(ctx)
        card.addView(switchRow(ctx, "Race week", "Monday of race week · the whole weekend",
            NotificationPrefs.weekendPreview(ctx)) { on ->
            NotificationPrefs.setWeekendPreview(ctx, on); changed()
        })
        divider(ctx, card)
        card.addView(switchRow(ctx, "Today's sessions", "Each weekend morning · every session that day",
            NotificationPrefs.dailySchedule(ctx)) { on ->
            NotificationPrefs.setDailySchedule(ctx, on); changed()
        })
        divider(ctx, card)
        card.addView(linkRow(ctx, "Send test notifications", "›", p.text, p.accentText) {
            when {
                NotificationPermissionHelper.notificationsDisabled(ctx) -> fixNotifications()
                RaceNotificationReceiver.sendTest(ctx) ->
                    android.widget.Toast.makeText(ctx, "Sent: before-session reminder, today's sessions and race week", android.widget.Toast.LENGTH_SHORT).show()
                else ->
                    android.widget.Toast.makeText(ctx, "No upcoming sessions to test with", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ---------------- appearance ----------------

    private fun renderAppearance(ctx: Context) {
        sectionLabel(ctx, "APPEARANCE")
        val card = card(ctx)
        card.addView(TextView(ctx).apply {
            text = "Theme"
            styled(Pit.body, 15f, p.text)
            setPadding(0, Pit.dp(ctx, 14f), 0, Pit.dp(ctx, 10f))
        })
        val modes = ThemePrefs.Mode.values()
        card.addView(segmented(ctx, listOf("Dark", "Light", "System"), modes.indexOf(ThemePrefs.mode(ctx))) { i ->
            if (modes[i] != ThemePrefs.mode(ctx)) {
                ThemePrefs.setMode(ctx, modes[i])
                activity?.recreate()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Pit.dp(ctx, 42f)).apply {
            bottomMargin = Pit.dp(ctx, 14f)
        })
    }

    // ---------------- schedule ----------------

    @SuppressLint("SetTextI18n")
    private fun renderSchedule(ctx: Context) {
        sectionLabel(ctx, "SCHEDULE")
        val card = card(ctx)
        val races = RaceRepository.getRaces(ctx)
        val imported = RaceRepository.source(ctx) == RaceRepository.Source.IMPORTED

        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Pit.dp(ctx, 60f)
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val desc = if (races.isEmpty()) "Nothing loaded" else RaceJsonParser.describe(races)
        texts.addView(TextView(ctx).apply {
            text = desc.substringBefore(" · ").let { if (races.isEmpty()) it else "$it season" }
            styled(Pit.body, 15f, p.text)
        })
        texts.addView(TextView(ctx).apply {
            text = (desc.substringAfter(" · ", "").lowercase() + " · " + if (imported) "imported" else "built-in").trimStart(' ', '·')
            styled(Pit.body, 12f, p.muted)
            setPadding(0, Pit.dp(ctx, 3f), 0, 0)
        })
        info.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        info.addView(TextView(ctx).apply {
            text = "ACTIVE"
            styled(Pit.bodyBold, 11f, p.soft, 0.08f)
            background = Pit.rounded(ctx, p.surface2, 6f)
            setPadding(Pit.dp(ctx, 8f), Pit.dp(ctx, 4f), Pit.dp(ctx, 8f), Pit.dp(ctx, 4f))
        })
        card.addView(info)

        // ---- official times (auto) ----
        divider(ctx, card)
        val checked = com.example.schedule.data.ScheduleSync.lastChecked(ctx)
        val src = when (com.example.schedule.data.ScheduleSync.lastSource(ctx)) {
            com.example.schedule.data.ScheduleSync.Source.GITHUB -> "GitHub"
            com.example.schedule.data.ScheduleSync.Source.MOTOGP -> "MotoGP"
            else -> null
        }
        val ago = if (checked == 0L) "not checked yet" else {
            val m = (System.currentTimeMillis() - checked) / 60_000L
            "checked " + when {
                m < 1 -> "just now"
                m < 60 -> "$m min ago"
                m < 48 * 60 -> "${m / 60} h ago"
                else -> "${m / 1440} days ago"
            } + (src?.let { " · $it" } ?: "")
        }
        val sync = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Pit.dp(ctx, 58f)
            isClickable = true
            isFocusable = true
            background = Pit.pressable(ctx, p.surface, 0f, Pit.ripple(p))
        }
        val syncTexts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        syncTexts.addView(TextView(ctx).apply { text = "Official session times"; styled(Pit.body, 15f, p.text) })
        syncTexts.addView(TextView(ctx).apply {
            text = "Auto-updated · $ago"
            styled(Pit.body, 12f, p.muted)
            setPadding(0, Pit.dp(ctx, 3f), 0, 0)
        })
        sync.addView(syncTexts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sync.addView(TextView(ctx).apply { text = "Check now"; styled(Pit.bodyBold, 13f, p.accentText) })
        sync.setOnClickListener {
            android.widget.Toast.makeText(ctx, "Checking official times…", android.widget.Toast.LENGTH_SHORT).show()
            val app = ctx.applicationContext
            Thread {
                val r = com.example.schedule.data.ScheduleSync.sync(app)
                if (r.changed) SchedulerCoordinator.onScheduleChanged(app)
                activity?.runOnUiThread {
                    val msg = when {
                        !r.ok -> "Couldn't reach GitHub or MotoGP — times unchanged"
                        r.changed -> "Updated: ${r.moved} session time${if (r.moved == 1) "" else "s"} differ from the built-in schedule"
                        else -> "All times are up to date"
                    }
                    android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_LONG).show()
                    if (isAdded) render()
                }
            }.start()
        }
        card.addView(sync)

        divider(ctx, card)
        card.addView(linkRow(ctx, "Import schedule (JSON)", "›", p.text, p.accentText) {
            pickScheduleFile.launch(arrayOf("application/json", "text/plain", "*/*"))
        })
        if (imported) {
            divider(ctx, card)
            card.addView(linkRow(ctx, "Use built-in schedule", "›", p.muted, p.muted) {
                ScheduleImporter.confirmReset(requireActivity()) { render() }
            })
        }
    }

    // ---------------- about ----------------

    private fun renderAbout(ctx: Context) {
        sectionLabel(ctx, "ABOUT")
        val card = card(ctx)
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        card.addView(linkRow(ctx, "Check for updates", "v$version", p.text, p.muted) {
            UpdateChecker.checkForUpdate(requireActivity(), manual = true)
        })
    }

    // ==================================================================
    // ACTIONS
    // ==================================================================

    /** Any alert setting changed: rebuild alarms and redraw */
    private fun changed() {
        val ctx = context ?: return
        NotificationScheduler.rescheduleAll(ctx)
        render()
    }

    private fun fixNotifications() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationPermissionHelper.isBlocked(act)) {
            ActivityCompat.requestPermissions(act, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
        } else {
            NotificationPermissionHelper.openSettings(act)
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${requireContext().packageName}")))
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${requireContext().packageName}")))
        }
    }

    // ==================================================================
    // BUILDING BLOCKS
    // ==================================================================

    private fun block(bottom: Float = 18f) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Pit.dp(requireContext(), bottom)
        }

    private fun sectionLabel(ctx: Context, text: String) {
        list.addView(TextView(ctx).apply {
            this.text = text
            styled(Pit.bodyBold, 11f, p.muted, 0.16f)
            setPadding(Pit.dp(ctx, 4f), 0, 0, Pit.dp(ctx, 8f))
        })
    }

    private fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Pit.rounded(ctx, p.surface, 20f)
            setPadding(Pit.dp(ctx, 16f), Pit.dp(ctx, 2f), Pit.dp(ctx, 16f), Pit.dp(ctx, 2f))
        }
        list.addView(c, block())
        return c
    }

    private fun divider(ctx: Context, parent: LinearLayout) {
        parent.addView(View(ctx).apply { setBackgroundColor(p.surface2) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Pit.dp(ctx, 1f)))
    }

    private fun caption(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        styled(Pit.body, 12f, p.muted)
        setPadding(0, Pit.dp(ctx, 12f), 0, Pit.dp(ctx, 8f))
    }

    private fun switchRow(ctx: Context, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Pit.dp(ctx, 58f)
            isClickable = true
            isFocusable = true
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(ctx).apply { text = title; styled(Pit.body, 15f, p.text) })
        if (sub != null) texts.addView(TextView(ctx).apply {
            text = sub
            styled(Pit.body, 12f, p.muted)
            setPadding(0, Pit.dp(ctx, 3f), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val sw = SwitchMaterial(ctx).apply {
            isChecked = checked
            contentDescription = title
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), p.muted))
            trackTintList = ColorStateList(states, intArrayOf(p.accent, p.surface2))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    private fun segmented(ctx: Context, labels: List<String>, selected: Int, accent: Boolean = false, onPick: (Int) -> Unit): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Pit.rounded(ctx, p.bg, 14f)
            setPadding(Pit.dp(ctx, 4f), Pit.dp(ctx, 4f), Pit.dp(ctx, 4f), Pit.dp(ctx, 4f))
        }
        labels.forEachIndexed { i, label ->
            val on = i == selected
            bar.addView(TextView(ctx).apply {
                text = label
                gravity = Gravity.CENTER
                styled(if (on) Pit.bodyBold else Pit.body, 13f, if (on) (if (accent) p.onAccent else p.text) else p.muted)
                background = if (on) Pit.rounded(ctx, if (accent) p.accent else p.surface2, 10f) else null
                isClickable = true
                isFocusable = true
                contentDescription = label + if (on) ", selected" else ""
                setOnClickListener { if (!on) onPick(i) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = Pit.dp(ctx, 4f)
            })
        }
        return bar
    }

    private fun chip(ctx: Context, label: String, on: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = Pit.dp(ctx, 40f)
        styled(if (on) Pit.bodyBold else Pit.body, 13f, if (on) p.onAccent else p.muted)
        background = Pit.pressable(ctx, if (on) p.accent else p.surface2, 12f, Pit.ripple(p))
        setPadding(Pit.dp(ctx, 12f), 0, Pit.dp(ctx, 12f), 0)
        isClickable = true
        contentDescription = "${label.removePrefix("✓ ")}, ${if (on) "on" else "off"}"
        setOnClickListener { onClick() }
    }

    private fun linkRow(ctx: Context, title: String, trailing: String, titleColor: Int, trailingColor: Int, onClick: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Pit.dp(ctx, 52f)
            isClickable = true
            isFocusable = true
            background = Pit.pressable(ctx, p.surface, 0f, Pit.ripple(p))
            setOnClickListener { onClick() }
        }
        row.addView(TextView(ctx).apply { text = title; styled(Pit.body, 15f, titleColor) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(ctx).apply { text = trailing; styled(Pit.bodyMedium, if (trailing == "›") 18f else 12f, trailingColor) })
        return row
    }
}
