# MotoGP Schedule Widgets 🏁

Android application providing **MotoGP** race schedules
through **home screen widgets** and an **in-app race calendar**.

Ships with the **2027 MotoGP season**. New seasons can be loaded from a JSON
file on the phone — no app update needed.

Built with a focus on:

* Accuracy
* Reliability
* Clean racing-focused UI
* Zero ads. Zero tracking.

---

## ✨ Features

* 📅 Upcoming MotoGP race schedule (2027 built in)
* 📂 **Import a new season from a JSON file** (Settings → Import schedule)
* ⏱ Automatic local timezone conversion
* 🕛 Exact midnight refresh (next race auto-switch)
* 🔄 Auto re-sync after reboot & time/timezone changes
* 🧩 Resizable home screen widgets (standard + compact)
* 🎴 Interactive race cards with flip animation
* 🌗 Manual Light / Dark theme toggle (Dark default)
* 🔔 In-app Check for Updates (GitHub Releases)
* 🎨 Racing-style Material UI with custom fonts
* 🛡 Race Week Guard (prevents outdated race notifications)

---

## 📂 Loading a new season

1. Put the schedule `.json` file on your phone (Downloads, Drive, anywhere).
2. Open the app → **Settings** → **IMPORT SCHEDULE (JSON)** and pick the file.
   You can also tap the file in a file manager and choose **Open with GP Schedule**.
3. Check the summary (season, rounds, first and last race) and tap **Import**.

The list, both widgets and notifications switch over immediately. If the file has
a problem, the app says what's wrong and keeps the current schedule.
**USE BUILT-IN SCHEDULE** removes the imported file and goes back to the bundled one.

When every race in the loaded schedule is over, the widgets show **END** —
that's the cue to import next season's file.

### File format

Same as `app/src/main/assets/motogp_races.json`:

```json
{
  "races": [
    {
      "name": "Buriram Grand Prix",
      "location": "Buriram, Thailand",
      "track": "Chang International Circuit",
      "round": 1,
      "category": "MotoGP",
      "race": "2027-03-05",
      "sessions": [
        { "sessionName": "fp1",    "sessionTime": "09:15 AM", "dayOffset": 0 },
        { "sessionName": "fp2",    "sessionTime": "01:30 PM", "dayOffset": 0 },
        { "sessionName": "fp3",    "sessionTime": "08:40 AM", "dayOffset": 1 },
        { "sessionName": "q1",     "sessionTime": "09:20 AM", "dayOffset": 1 },
        { "sessionName": "q2",     "sessionTime": "09:45 AM", "dayOffset": 1 },
        { "sessionName": "sprint", "sessionTime": "01:30 PM", "dayOffset": 1 },
        { "sessionName": "race",   "sessionTime": "01:30 PM", "dayOffset": 2 }
      ]
    }
  ]
}
```

| Field | Rule |
|---|---|
| `race` | Friday of the race weekend, `yyyy-MM-dd` |
| `sessionTime` | **IST (Asia/Kolkata)**, `hh:mm AM/PM` — the app converts to the phone's timezone |
| `dayOffset` | Days after `race` the session falls on, in IST (0 = Friday). Optional; guessed from the session name if missing |
| `sessionName` | `fp1`, `fp2`, `sq` (Sprint Qualifying), `fp3`, `q1`, `q2`, `sprint`, `race`. Labels follow the season: before 2027 `fp2` shows as PR and `fp3` as FP2; from 2027 they show as FP2 and FP3. Unknown names still work and show as written |
| `label` | Optional display name for a session (max 8 chars), overrides the built-in one |
| `duration` | Optional length in minutes; used to know when a session is over (defaults: FP1 45, FP2 30, SQ 15, FP3 20, Q 15, Sprint 30, GP 50) |
| `category` | Optional, defaults to `MotoGP`. Anything else is ignored |
| `track`, `slug`, `localeKey` | Optional |

---

## 🔔 Smart Notification System

* Race-week (Monday) and daily session alerts
* Exact alarm scheduling (AlarmManager)
* Android 13+ notification permission handling
* Boot-safe rescheduling
* Time & timezone change recovery
* Duplicate notification prevention
* Old alarms cleared automatically when a new schedule is imported
* Battery optimization guidance prompt

---

## 🧩 Widgets

### MotoGP Widget (4×2, resizable)

* Round, dates, country, circuit
* **Hero timer**: within 24 h of the next session a live ticking countdown
  (e.g. `NEXT · SPRINT QUALI 2:14:05`); during a session `● LIVE` and time since it started;
  further out, whole days (`3 DAYS`, `TOMORROW`)
* Weekend split into your local days (up to 4 columns) — finished sessions fade,
  the live one is red, the next one is bold, today's header is red
* Season progress bar
* Three layouts by height (short / medium / tall), picked by the launcher on Android 12+

### MotoGP Compact Widget (2×2, resizable)

* Round, city, dates, hero timer, the active day's sessions, season progress

Widgets redraw on their own at the exact moments they change (24 h before a session,
session start, session end, midnight) using one self-rescheduling exact alarm,
plus after reboot, time/timezone change, schedule import, and on tap.

---

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI:** XML + Material Components
* **Architecture:**
  * `RaceRepository` — single data source (imported file → bundled asset)
  * AppWidgetProvider
  * AlarmManager (exact scheduling)
  * WorkManager (fallback reliability layer)
* **Data Source:** JSON (bundled asset, or user-imported file in app storage)

---

## 🔒 Privacy

* No ads
* No analytics
* No tracking
* No user data collection
* File import uses the system file picker — no storage permission

Core functionality does not require internet access.

---

## 📦 Download

👉 **Latest APK:**
[https://github.com/VINXxXxX/GP_schedules/releases](https://github.com/VINXxXxX/GP_schedules/releases)

> ⚠️ Not available on Google Play Store
> Users must enable **Install unknown apps** to install the APK.

---

## 📷 Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/933d2a7c-df3a-4fe6-b3f0-c7f107144f68" width="280" />
  <img src="https://github.com/user-attachments/assets/69d72523-4a50-4d9c-948b-c9062fe82c7c" width="280" />
  <img src="https://github.com/user-attachments/assets/9671c391-129e-4e57-b589-cbcefcef5c4a" width="280" />
  <img src="https://github.com/user-attachments/assets/641e61a2-14ff-4b23-998e-608480c976ef" width="280" />
  <img src="https://github.com/user-attachments/assets/70572747-e789-451f-8a33-fe6701d858f6" width="280" />
  <img src="https://github.com/user-attachments/assets/f5efd31f-1a5f-46fe-ad13-4d092caffdcb" width="280" />
</p>
