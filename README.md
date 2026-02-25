# MotoGP & SBK Schedule Widgets 🏁

Android application providing **MotoGP** and **WorldSBK** race schedules
through **home screen widgets** and an **in-app race calendar**.

Built with a focus on:

* Accuracy
* Reliability
* Clean racing-focused UI
* Zero ads. Zero tracking.

---

## ✨ Features

* 📅 Upcoming MotoGP & WorldSBK race schedules
* ⏱ Automatic local timezone conversion
* 🕛 Exact midnight refresh (next race auto-switch)
* 🔄 Auto re-sync after reboot & time/timezone changes
* 🧩 Resizable home screen widgets
* 🎴 Interactive race cards with flip animation
* 🌗 Manual Light / Dark theme toggle (Dark default)
* 🔔 In-app Check for Updates (GitHub Releases)
* 🎨 Racing-style Material UI with custom fonts
* 🛡 Race Week Guard (prevents outdated race notifications)

---

## 🔔 Smart Notification System

* Session-based race alerts
* Exact alarm scheduling (AlarmManager)
* Android 13+ notification permission handling
* Boot-safe rescheduling
* Time & timezone change recovery
* Duplicate notification prevention
* Battery optimization guidance prompt

Notifications are designed to be reliable even under aggressive background restrictions.

---

## 🧩 Widgets

### MotoGP Widget

Displays:

* Upcoming MotoGP race
* All weekend sessions
* Auto-switches after race weekend ends

### SBK Widget

Displays:

* Upcoming WorldSBK race
* Weekend sessions
* Automatic race progression

Widgets intelligently refresh:

* At midnight
* After reboot
* After time/timezone change
* Via manual refresh

---

## 🎨 UI & Theming

* Theme-aware gradient backgrounds
* Racing-style buttons with ripple effects
* Adaptive outlines and toggle styling
* Improved light mode readability
* Consistent racing fonts across the app
* Instant theme switch without color glitches

---

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI:** XML + Material Components
* **Architecture:**

  * AppWidgetProvider
  * AlarmManager (exact scheduling)
  * WorkManager (fallback reliability layer)
* **Data Source:** Local JSON assets
* **Receivers:**

  * Boot completed
  * Time change
  * Timezone change

Designed for maximum scheduling resilience across Android versions.

---

## 🔄 Update Logic

* Exact midnight alarm (Android 12+ compliant)
* Automatic rescheduling after reboot
* Time & timezone change detection
* Manual force refresh supported
* In-app update check via GitHub Releases

---

## 🔒 Privacy

* No ads
* No analytics
* No tracking
* No user data collection
* No unnecessary permissions

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
