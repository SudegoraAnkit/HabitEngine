# ⚡ HabitEngine ⚡

> **Quantify Your Daily Energy, Habits, and Discipline into Clean, Immutable Data. Designed for Devs, Built Offline-First.**

HabitEngine is a highly optimized, beautifully designed, distraction-free, and privacy-focused habit loop tracker and productivity logger. It is tailored specifically for developers, scientists, and high-performance individuals who want to stop setting vague resolutions and start tracking progress with computer-science precision.

| Live Status | Platform | Build System | Database | Architecture | License |
|---|---|---|---|---|---|
| ![Active Dev](https://img.shields.io/badge/Status-Active-brightgreen) | Android (SDK 24+) | Gradle (Kotlin DSL) | SQLite + Jetpack Room | MVVM + Clean Architecture | MIT |

---

## 📸 App Preview

Here is a glimpse of the minimalist, dark-mode dashboard with its customizable widgets:

```
+-------------------------------------------------------------+
|  ⚡ HabitEngine                   [ FAQ & Settings ] [ 🌍 EN ]|
|  "The dopamine loop of execution."                           |
+-------------------------------------------------------------+
|  [🎯 TODAY'S RADAR]                                         |
|  ■ Health   (3/4) [████████████░░░] 75%                      |
|  ■ Professional (2/2) [███████████████] 100%                 |
|  ■ Personal (1/3) [█████░░░░░░░░░░] 33%                      |
|  ■ Family   (2/2) [███████████████] 100%                 |
+-------------------------------------------------------------+
|  [🔥 HEATWAVE STREAK: 12 DAYS] [⚡ EFFICIENCY: 82%]           |
+-------------------------------------------------------------+
|  [➕ ADD NEW HABIT]                                          |
|  [📂 COGNITIVE LOGGER - REALTIME TERMINAL]                  |
+-------------------------------------------------------------+
```
*(Screenshot path reference: `/app/src/main/res/drawable/app_preview.png`)*

---

## 🎯 App Purpose

Traditional habit trackers are filled with intrusive ads, monthly subscriptions, and cloud synchronization that compromises personal privacy. HabitEngine is different. 

Its **primary purpose** is to provide a fully offline, zero-friction, highly customizable visual space where you can create micro-triggers, track them consistently using flexible schedules, and maintain high visual cognitive reinforcement (Dopamine Sparkles & Heatwave Streak Indicators).

### 🎁 Value to the User

1. **Psychologically Backed Habit Loop**: Follows the *Cue ➔ Action ➔ Reward* blueprint. Setup actions tied to existing triggers (e.g., "When I sit down with my morning coffee, I will write 1 file").
2. **Four Dimensional Life Balance**: Forces you to stay balanced across 4 essential areas: **Health**, **Professional**, **Personal**, and **Family**. No more burning out in one area while losing another.
3. **Interactive Cognitive Logger (Activity Terminal)**: Register raw workflow, focuses, ambient states, or mental state logs on-the-fly. Keep an exact data catalog of your daily focus and distractions.
4. **Absolute Privacy & Control**: 100% offline. Features instant, high-speed CSV or JSON database backup & import right inside the app settings. No telemetry, no external servers.

### 🛠️ Value to the Developer

1. **Pragmatic Material 3 Compose Layout**: Highly complex yet fully reactive Jetpack Compose setup demonstrating custom canvas particle systems, animated expandable dialogs, multi-language system state, and clean, modern themes.
2. **Modern Architecture**: Demonstrates explicit division of concerns using Model-View-ViewModel (MVVM) and Android Clean Architecture.
3. **Local DB with Room + KSP**: Exemplary database implementation of relations, destructive fallback sandbox strategies, and transaction safety.
4. **Agile Localization**: Modular enum-based dictionary structure (`AppLanguage`) supporting 6 major global developer languages seamlessly.

---

## 🚀 Getting Started

### 📋 Prerequisites

- **Android Studio Koala / Ladybug** (or later)
- **JDK 17 or JDK 21** configured in your Gradle environment
- **Android Device / Emulator** running SDK 24 (Nougat) or higher

### ⚙️ Compilation & Build

Clone this repository and open the project inside Android Studio:

```bash
# Clone the open-source repository
git clone https://github.com/ankitrai-dev/habitengine.git
cd habitengine

# Clean and Build project
gradle clean assembleDebug
```

The compiled APK will be output beautifully at:
`app/build/outputs/HabitEngineApk/HabitEngine_1.1.0.apk`

---

## 📄 Open Source & License

This project is fully open source under the developer-friendly **[MIT License](LICENSE)**. 

### Why is HabitEngine Open Source?
We believe that personal growth tools should be transparent, personal, and owned entirely by the builder. Developers worldwide can audit the code, submit pull requests to improve performance, add tracking widgets, or fork/re-design the engine according to their specific productivity methodology.

---

*Handcrafted with ♥️ by **Ankit Rai** and the open source community to empower developers to execute, one habit loop at a time.*
