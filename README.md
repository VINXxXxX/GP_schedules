# MotoGP & SBK Schedule Widgets 🏁

Android application that provides **MotoGP and WorldSBK race schedules**
via **home screen widgets** and an **in-app race calendar**.

The app focuses on **accuracy, reliability, and a clean racing-focused UI**, with no ads or tracking.

---

## ✨ Features

- 📅 Upcoming MotoGP & WorldSBK race schedules  
- ⏱ Local timezone session timings  
- 🕛 Automatic midnight refresh  
- 🔄 Updates after reboot & time/timezone changes  
- 🧩 Resizable home screen widgets  
- 🎴 Interactive race cards with flip animation  
- 🌗 Manual **Light / Dark theme toggle** (Dark default)  
- 🔔 In-app **Check for Updates** via GitHub Releases  
- 📱 Clean racing-style Material UI with custom fonts  

---

## 🧩 Widgets

- **MotoGP Widget** – Shows upcoming MotoGP race & sessions  
- **SBK Widget** – Shows upcoming WorldSBK race & sessions  

Widgets intelligently switch to the **next race after a race weekend completes**.

---

## 🎨 UI & Theming

- Racing-style gradients and buttons  
- Theme-aware card backgrounds and text colors  
- Consistent custom racing fonts across the app  

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
- In-app update checks via GitHub Releases  

---

## 🔒 Privacy

- No ads  
- No analytics  
- Open-source  

---

## 📦 Download

👉 [Download the latest beta APK](https://github.com/VINXxXxX/GP_schedules/releases)

> ⚠️ This app is distributed via GitHub Releases and is **not available on Google Play Store**.  
> Users must enable **Install unknown apps** to install the APK.

---

## 📷 Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/e94e3358-b658-4553-a156-c934b9c5df75" width="280" />
  <img src="https://github.com/user-attachments/assets/7b4730d5-4e08-4cfa-86ba-715b8e217d6e" width="280" />
  <img src="https://github.com/user-attachments/assets/f08852a8-e088-49fb-ad63-6dad4844fc15" width="280" />
</p>
