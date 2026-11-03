# Changes

## v2.6.0 — official times sync, 2026 + 2027 built in

Backup of the previous source: `backup_before_sync_2026-11-03.tgz`.

- **Built-in seasons**: `assets/motogp_2026.json` and `assets/motogp_2027.json` (2027 format:
  FP1 · FP2 · SQ on Friday, predicted SQ / sighting times). The app shows the first season that
  still has a weekend to come, so it moves to 2027 by itself after Valencia. An imported file
  still wins for its own season.
- **Official session times, automatic** (`data/ScheduleSync.kt`, `data/ScheduleSyncWorker.kt`):
  every 4 h when online (and on app open if the last check is over an hour old) the phone and
  TV apps read `schedule/motogp_<year>.json` from the GP_schedules GitHub repo; if GitHub
  can't be reached they read MotoGP's timetable feed directly. Differences are stored as
  corrections on top of the season (your files stay untouched), then widgets, TV channel and
  alerts are rebuilt. Sanity checks: same weekend only; moves over 3 h are ignored unless the
  session was TBA / predicted (SQ, sighting laps).
- Session page: "MOVED · WAS SAT 1:30 PM" under a session whose time changed.
- Settings → Schedule: "Official session times · checked 2 h ago · GitHub" + **Check now**.
- SQ shown as "SQ" in the alert chips and notifications.
- **GitHub side** (push these with the project):
  `.github/workflows/sync-schedule.yml` (runs every 3 h + "Run workflow" button),
  `tools/sync_schedule.py` (standard-library Python), `schedule/motogp_2026.json`,
  `schedule/motogp_2027.json`. The workflow commits updated JSON with the repo's own token.


## v2.5.0 — TV screen cleanup, widget timing fix, three notifications only

Backup of the previous source: `backup_before_tv_cleanup_2026-10-30.tgz`.

- **Widget timing (phone + TV + channel row)**: the redraw at each session start / end no
  longer depends on one alarm. `SessionScheduler` now sets (1) the exact alarm, (2) a
  WorkManager backup 5 s after the same moment and (3) a redraw every 15 min during a race
  weekend (`WidgetTickWorker.kt`). Fixes the countdown running into negatives (-57:18)
  on phones / TVs that drop background alarms.
- **TV widget**: tap = refresh (no longer opens the app).
- **TV screen**: read-only weekend + one bottom bar for the remote
  (◀ PREVIOUS ROUND · THIS WEEKEND · NEXT ROUND ▶ … SETTINGS); focused button turns white.
  Overscan-safe margins, condensed type for tiles, "TODAY" as a pill (no wrapping),
  one-line notes, status card with the live / next timer.
- **Notifications — only three kinds**:
  1. Race week: Monday 09:00 of race week, whole weekend by day
  2. Today's sessions: each weekend day at 07:00 (or 1 h before the first session if earlier)
  3. Before a session: N minutes before each session you pick
  Live card, race-day countdown card and night-session options are gone; old channels are
  deleted on start.


## v2.4.0 — Android TV / Google TV: TV widget, home-screen channel row, TV screen

Two apps from one project (product flavors). In Android Studio open **Build Variants** and pick:
- `phoneDebug` → **GP Schedule** (`com.example.schedule`, same as before: icon, 4×2 widget, alerts)
- `tvDebug` → **GP Schedule TV** (`com.example.schedule.tv`: TV app list, TV widget, channel row)

**Sync Gradle** (new library: `androidx.tvprovider:tvprovider:1.1.0`), **Build → Clean Project**,
then run `tvDebug` on the TV (`adb connect <tv-ip>`). On the TV, uninstall the earlier combined
build (`com.example.schedule`) so only GP Schedule TV is left. Each app keeps its own schedule:
import a new season on the TV through SETTINGS.
Shared code stays in `src/main`; `src/phone/AndroidManifest.xml` and `src/tv/AndroidManifest.xml`
only declare what each app shows. A backup of the previous source is in
`backup_before_tv_widget_2026-10-29.tgz` in the project root.

### TV widget (launchers that host Android widgets, e.g. ATV Launcher)
- Wide Pit Wall card: round chip, city, country · dates · track, track weather.
- Status line + ticking timer: `● FP1 LIVE · NEXT PR 12:30 PM · ENDS IN 00:25:12`,
  `NEXT SPR · SAT 12:30 PM · STARTS IN 03:14:05`, or `3 DAYS` further out.
- One tile per session (up to 10): label, day (TODAY / FRI…), local time.
  Live = red tile with ●, next = red outline, done = faded, Sprint / GP labels red.
- Footer: next round and date, round x / y. Tap → TV schedule screen.
- Size-aware: when the widget is too narrow for readable tiles (< ~62 dp per session) it shows
  one line per day instead: `FRI   FP1 8:15a  ·  PR 12:30p` (done faded, live red ●, next bold).
  In ATV Launcher, give the widget row a bigger tile size to get the tiles.
- `tv/TvWidget.kt`, `tv/TvWidgetUi.kt`, `layout/widget_tv.xml`.

### Channel row "MotoGP Weekend" (launchers that show app channels)
- Standard Android TV preview channel, so it works with the stock Android TV home,
  Monet (custom media row → GP Schedule), Projectivy and other launchers that read TvProvider.
  Google TV's own home screen doesn't show third-party rows.
- One 16:9 card per session drawn in the Pit Wall style (`tv/TvCardArt.kt`):
  big label, day + local time, NEXT / LIVE / DONE pill, round watermark.
  Order: live / next first, then the rest of the weekend, finished sessions last.
- Cards carry no countdowns, so they're never stale; they're rewritten on every refresh
  (session start / end, midnight, import, boot, time-zone change). Images are cached and
  only re-drawn when a card changes. `tv/TvChannel.kt`, served by `tv/TvArtProvider.kt`.
- Published right after install on Android TV (`tv/TvInitReceiver.kt`, INITIALIZE_PROGRAMS)
  and every time the TV screen opens.

### TV screen (`tv/TvScheduleActivity.kt`)
- "GP Schedule" banner in the TV app list (LEANBACK_LAUNCHER, `drawable-xhdpi/tv_banner.png`).
- Left: round, city, country, track, live / next status with a per-second timer.
  Right: sessions in day columns, D-pad focus (focused tile grows + white outline),
  opens on the live / next session (or the card you clicked).
- PREV / NEXT buttons (also CH+/CH− and media skip keys) browse the season.
  SETTINGS opens the phone UI for import / theme / alerts.

### Other changes
- `WidgetUpdater`: copes with TVs that have no widget support (AppWidgetManager is null there),
  refreshes the TV widget + channel, and keeps the exact refresh alarm on while any of them exists.
- Manifests: phone flavor = LAUNCHER icon + 4×2 widget; TV flavor = leanback (required),
  TV banner, EPG permissions, TV widget, channel receiver + image provider.

Version: `versionCode 7`, `versionName "2.4.0"`.

## v2.3.0 — circuit cards, race-day countdown, edit times, track weather

- **Circuit card** on each round's page (assets/circuits.json, offline): length, corners
  (left/right), longest straight, opened year, and the live local time at the track.
- **Race-day countdown**: silent lock-screen card from 4 h before each weekend day's first
  session — "Next: Sprint" with a countdown; steps aside while a session is live and comes
  back for the next one. Settings → Race-day countdown.
- **Edit times in the app**: tap a session on the round page → Change time / Mark as TBA.
  Saved as an imported schedule; Settings → Use built-in schedule undoes all edits.
- **Track weather (the one online feature)**: Open-Meteo, free, no key. Only from Monday of
  race week to the end of the weekend, fetched at most once a day (on app open, widget tap or
  the midnight job, when there's a network) and cached. Shows on the widget header, the home
  card and the round page (Fri/Sat/Sun: icon, high/low, rain chance).
- Built-in schedule is now 2026; `schedules/` has the 2026 file and the 2027 file with
  predicted Sprint Qualifying + sighting laps.
- Widget: 4×3 by default, bigger session text, lighter greys, AM/PM, tap = refresh.


## v2.2.0 — Pit Wall redesign, 2027 format, notifications

Copy over the project, then **Build → Clean Project** and run. Remove and re-add the
widget once so the launcher picks up the new layout. A backup of the previous source is
in `backup_before_redesign_2026-10-28.tgz` in the project root.

### Removed in this version (already deleted)
```
MGPWidgetCompact.kt, widget_mgp_compact.xml, mgp_widget_compact_info.xml   (2×2 widget)
RaceSelector.kt, RaceWeekGuard.kt                                         (old notification logic)
ui/ThemeApplier.kt, ui/ThemeButtonApplier.kt, ui/ThemeColors.kt           (replaced by ui/Pit.kt)
item_race.xml, item_session.xml, bottom_nav_menu.xml, bottom_nav_text_selector.xml,
bottom_nav_item_bg.xml, widget_gradient_dark/light.xml, bg_racing_button_dark/light.xml
```

### Look (ui/Pit.kt)
- One palette for the whole app: carbon black, one red, racing font for headings.
  Theme: Dark / Light / System (Settings → Appearance).
- Custom bottom bar: SCHEDULE | SETTINGS with a red bar under the active tab.

### Home
- Season header with one segment per round (done / current / to come).
- "Next up" card: big city + round watermark, 4-box countdown (days/hrs/min/sec, ticks
  every second), live state shows time left, first/next session line.
- Upcoming rounds list, finished rounds faded at the bottom. Tap any round → its sessions.

### Session page (RaceDetailActivity) — replaces the flip card
- Day tabs in your local days, timeline per day, live session with progress bar.
- Each session: state (live / in 4h 15m / done), time · length, what it decides
  (2027: FP2 practice only, Sprint Qualifying sets the Sprint grid, Q1 all riders → top 12,
  Q2 sets the GP grid, sighting laps before the GP), and a bell to switch its alert.
- Sessions with `"sessionTime": "TBA"` are shown as TBA and get no alert until a time is set.

### Notifications (no action buttons — tap opens that round)
- Reminder N minutes before each chosen session, with a countdown in the notification.
- Live card while a session runs (ongoing, silent, time left + next session).
- Weekend preview at 20:00 the evening before: whole schedule by day.
- Night sessions (00:00–06:30): Ring / Silent / Skip.
- Settings: master switch, 5/15/30/60 min, which sessions (FP1…Grand Prix) with presets
  Everything / Quali + races / Races only, and a status line for permission, exact alarms
  and battery optimisation.
- New channels: Session reminders, Live session, Weekend preview, Night reminders.
- Alarms are set for the current and next round and rebuilt on app open, boot, time-zone
  change, schedule import and at midnight.

### Widget (4×2 only)
- Race week: round, country, dates, alert setting (bell + lead time), LIVE chip,
  live "ends in" or next "starts in" ticking timer, all sessions by local day (live ●,
  done faded, Sprint/GP bold, TBA sessions listed), next round, season progress.
- Off week: NEXT Rxx, days to the first session, key times (FP1 · Sprint · GP),
  the three rounds after it.
- Tap → that round's session page.

### JSON
- `"sessionTime": "TBA"` (or TBC / TBD) is now accepted.

---


This zip is cumulative: it contains everything from v2.0.0 and v2.1.0.
Copy it over the project (same paths, overwrite), delete the files below, then
**Build → Clean Project** and run. After installing, remove and re-add the widgets
once so the launcher picks up the new layouts and sizes.

## Delete these files

```
app/src/main/java/com/example/schedule/SBKWidget.kt
app/src/main/java/com/example/schedule/SBKWidgetCompact.kt
app/src/main/assets/sbk_races.json
app/src/main/res/layout/widget_sbk.xml
app/src/main/res/layout/widget_sbk_compact.xml
app/src/main/res/xml/sbk_widget_info.xml
app/src/main/res/xml/sbk_widget_compact_info.xml
app/src/main/res/drawable/widget_gradient_sbk.xml
app/src/main/res/drawable/countdown_rounded_sbk.xml
app/src/main/res/drawable/widget_gradient_mgp.xml        (old widget background, now unused)
app/src/main/res/drawable/countdown_rounded_mgp.xml      (old countdown badge, now unused)
app/src/main/races.json                                  (old unused 2025 sample)
```

From the project root (macOS/Linux/Git Bash):

```
rm app/src/main/java/com/example/schedule/SBKWidget.kt \
   app/src/main/java/com/example/schedule/SBKWidgetCompact.kt \
   app/src/main/assets/sbk_races.json \
   app/src/main/res/layout/widget_sbk.xml \
   app/src/main/res/layout/widget_sbk_compact.xml \
   app/src/main/res/xml/sbk_widget_info.xml \
   app/src/main/res/xml/sbk_widget_compact_info.xml \
   app/src/main/res/drawable/widget_gradient_sbk.xml \
   app/src/main/res/drawable/countdown_rounded_sbk.xml \
   app/src/main/res/drawable/widget_gradient_mgp.xml \
   app/src/main/res/drawable/countdown_rounded_mgp.xml \
   app/src/main/races.json
```

---

## v2.1.0 — widget redesign, live session timer, 2027 session format

### Widgets
- New look for both widgets: carbon-black card with a red glow, red round chip
  (`R01`), condensed type, status chip (`● LIVE`, `WEEKEND`, `RACE WEEK`, `FINISHED`),
  season progress bar.
- **Hero timer** (`widget/WidgetUi.kt`):
  - next session within 24 h → ticking countdown (`NEXT · SPRINT QUALI 2:14:05`).
    It uses the widget Chronometer, so it ticks on the home screen without any updates
  - session running → `● LIVE · SPRINT` and time since it started
  - further out → `3 DAYS` / `TOMORROW` plus day and time
  - season over → `DONE`, with a hint to import the next season
- **Per-session states**: the weekend is split by your *local* days (up to 4 columns,
  since overseas rounds can spill into Thursday or Monday). Finished sessions fade, the live
  one is red with ●, the next one is bold, today's header is red.
- **Responsive layouts**: large widget has short / medium / tall layouts, compact has
  with or without the session list. Android 12+ picks per size. Older versions use the
  reported height.
- Both widgets are resizable. Tap still refreshes with a short vibration.

### Exact refresh (SessionScheduler.kt)
- One alarm, always set for the next moment the widget changes: 24 h before a session,
  session start, session end, or local midnight. When it fires the widgets redraw and the
  next alarm is set. It's cancelled when no widgets are on the home screen.
- `USE_EXACT_ALARM` permission (auto-granted on Android 13+, fine for a sideloaded personal
  app) so changes land on the minute without asking.

### 2027 session format
- `data/SessionCatalog.kt` holds every session name in one place. Labels follow the season:
  before 2027 `fp2` = PR and `fp3` = FP2; from 2027 `fp2` = FP2 and `fp3` = FP3. New `sq`
  (Sprint Qualifying, Friday). Aliases like `sprint_quali`, `Sprint Qualifying`, `spr`
  and `gp` are normalised on import.
- Optional per-session `"label"` and `"duration"` in the JSON for anything new.
- Race cards and daily notifications now place and pick sessions by day (`dayOffset`)
  instead of fixed names, so new sessions land in the right place automatically.
- `data/WeekendTimeline.kt` turns IST times into real instants and works out live, next,
  done, day groups and the next refresh moment. Covered by `WeekendTimelineTest`.

**Your bundled 2027 file has no Sprint Qualifying yet.** When official times are out, add one
per round, e.g.:

```json
{ "sessionName": "sq", "sessionTime": "06:10 PM", "dayOffset": 0 }
```

Then import the file from Settings. No rebuild needed.

Version: `versionCode 4`, `versionName "2.1.0"`.

---

## v2.0.0 — MotoGP only, 2027 season, JSON import

- SBK removed everywhere (tabs, widgets, notifications, settings, manifest).
- 2027 MotoGP schedule bundled.
- `data/RaceRepository.kt`: one source of race data. An imported file wins over the bundled asset.
- Settings → **IMPORT SCHEDULE (JSON)**: validates the file, shows a summary to confirm, then
  updates the list, widgets and notifications. **USE BUILT-IN SCHEDULE** reverts.
  Also "Open with GP Schedule" from a file manager.
- Old notification alarms are cleared on import; the notification switch cancels alarms when turned off.
