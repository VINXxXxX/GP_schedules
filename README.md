# MotoGP & SBK Schedule Widgets 🏁

Android application that provides **MotoGP and WorldSBK race schedules**
via **home screen widgets** and an **in-app race calendar**.

The widgets automatically update at midnight, after reboot, and on time/timezone changes.

---

## ✨ Features

- 📅 Upcoming MotoGP & SBK race schedules
- ⏱ Local timezone session timings
- 🕛 Automatic midnight refresh
- 🔄 Updates after reboot & time change
- 🧩 Resizable home screen widgets
- 🎴 Interactive race cards with flip animation
- 📱 Clean Material UI design

---

## 🧩 Widgets

- **MotoGP Widget** – Shows upcoming MotoGP race & sessions
- **SBK Widget** – Shows upcoming WorldSBK race & sessions

Widgets intelligently switch to the **next race after race weekend**.

---

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI:** XML, Material Components
- **Architecture:** AppWidgetProvider + WorkManager + AlarmManager
- **Data:** Local JSON assets
- **Background Tasks:**  
  - AlarmManager (exact midnight updates)  
  - WorkManager (fallback & reliability)

---

## 🔄 Update Logic

- Midnight exact alarm (Android 12+ supported)
- Device reboot handling
- Time & timezone change handling
- Manual force refresh supported

