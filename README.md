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
  <img src="https://github.com/user-attachments/assets/7becd2d3-5559-4c82-bbf3-5e4a6bf82589" width="280" />
  <img src="https://github.com/user-attachments/assets/6beef6ee-2e79-4011-877d-7a71b87811f3" width="280" />
  <img src="https://github.com/user-attachments/assets/fbc412b6-75b3-42eb-a418-6ea00158c859" width="280" />
  <img src="https://github.com/user-attachments/assets/0d62c665-baa8-4981-b382-62096b38f06a" width="280" />
</p>
