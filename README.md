# 🏋️ FitLife: All-in-One Fitness Tracker for Android

**FitLife** is an Android fitness app written in **Kotlin** with **Jetpack Compose** and Material 3. It covers steps, **GPS cycling**, workouts, nutrition, diet plans and wellbeing in one app. Everything is stored on the device, and Health Connect sync is optional.

> **v2.0** brings a full redesign based on the *FitLife UI Redesign System* (Stitch), a new **cycling tracker**, and a security hardening pass. See [What's new in 2.0](#-whats-new-in-20).

---

## ✨ Features

### 🏠 Today
- A greeting and readiness line, a **streak chip** and a **daily-progress ring** that combines steps, water and calories
- Tiles for Steps, Active Burn, Water and Calories Remaining
- A **Next Workout** card with a Start button, plus one-tap water logging and last night's sleep

### 🚶 Steps & Activity
- A hardware step-counter foreground service (counts deltas, so a reboot won't drop or double-count steps)
- Goal ring, distance and calories burned, a 7-day **Activity Trend** chart and recent history with goal ticks
- **Personalised for each person** (`data/steps/StepMath.kt`, unit-tested). Nothing is a fixed number for everyone:
  - **Distance** = steps × *your* stride, which is height × 0.415 (men) or × 0.413 (women)
  - **Pace** = stride × 100 steps/min, so a longer stride means a faster walk
  - **Active kcal** = (walking MET for that pace − 1) × *your* resting burn rate (revised Harris-Benedict BMR from weight, height, age and sex) × walking time
  - The Activity screen shows what the numbers are based on ("71 cm stride · 70 kg · 30 y") with a link to edit them
  - Changing height, weight, sex or birth year recalculates every stored day; the step counts themselves are never changed
- **Manual step entry** sheet with a date picker and +500 / +1,000 / +2,500 presets
- Two-way **Health Connect** sync

### 🚴 Cycling (new)
- **Live GPS ride recording** in a location foreground service. It keeps recording with the screen off, and the notification has Pause / Resume and Open actions.
- **Active Ride HUD** ("Trip Mode"): large speed readout, distance, moving time, GPS status, live route trace, climb and grade, average and max speed, live speed sparkline, kcal/hr, and a **Lock HUD** mode
- **Cycling dashboard**: daily km goal ring, a "This week" chart and ride history
- **Ride summary**: route trace, elevation profile, the full stat grid and 🏆 personal-best callouts (longest ride, fastest average). Deleting a ride can be undone.
- Rides are written to Health Connect as *Biking* exercise sessions
- How the ride engine works (`data/ride/`, pure Kotlin, unit-tested):
  - Drops GPS fixes worse than 30 m accuracy, ignores jitter under 3 m and rejects "teleport" glitches faster than 108 km/h
  - Smooths speed with an EMA, **auto-pauses** after 8 s below 2 km/h and resumes at 4 km/h
  - Counts climbing on smoothed altitude with 3 m hysteresis, so barometer and GPS noise doesn't add phantom climbing
  - Estimates calories from speed-based MET values (Compendium of Physical Activities) × body weight × moving time
  - Never bridges distance across a manual pause

### 💪 Workout Planner
- **Today / Templates** tabs, a weekly goal strip (M–S) and a *Scheduled Today* card with exercise check-off
- Templates: Upper Body Hypertrophy, Legs & Glutes Power, Push Day, Pull Day, Morning Yoga, 5K Run
- **Design Routine** sheet with title, duration, category (Strength / HIIT / Cardio / Yoga) and an exercise picker
- Deleting a routine shows a confirmation dialog, and an **Undo** snackbar lets you restore it

### 🥗 Nutrition & Macros
- Active-plan chip, **calories remaining** hero card, protein / carbs / fat bars and meals grouped by Breakfast / Lunch / Dinner / Snacks
- **Add Food** sheet:
  - Searches the 97-item offline database and **Open Food Facts**
  - Each result shows a source badge ("Verified" or "OpenFoodFacts")
  - Serving units follow the food: grams for per-100 g foods, ×portions for foods sold in portions
- Logging and removing food can both be undone
- **Diet plans** with templates (Fat Loss, Muscle Gain, Maintenance, Keto) computed from *your* TDEE; the active plan sets your calorie and macro targets

### 🧘 Wellbeing
- Sleep log with real time pickers, a water counter with a goal, mood and stress, meditation minutes (partial minutes count) and insights

### 🔔 Notifications
- Step reminder at 7 PM if you're under 80% of your goal
- Water reminder that compares what you've drunk with how far through the day you are (quiet hours are 22:00–08:00)
- Tapping any notification opens the app

---

## 🎨 Design: "FitLife UI Redesign System"

The UI follows the two design systems from the Stitch project:

| | **Kinetic Clarity** (light) | **Night Ride** (dark) |
|---|---|---|
| Primary | `#2563EB` blue | `#CCFF00` lime |
| Secondary | `#06B6D4` cyan | `#00F5FF` cyan |
| Tertiary | `#10B981` emerald | `#FF9900` orange |
| Type | Plus Jakarta Sans | Space Grotesk (headlines) · Geist (body) · JetBrains Mono (labels) |

- The theme follows the system or can be set in **Profile → Appearance** (System / Light / Dark).
- Dynamic colour is **off on purpose** so the brand palette stays consistent.
- Each metric keeps the same accent colour on every screen (steps, burn, water, sleep, ride, workout, macros). See `ui/theme/Color.kt`.
- The app is edge-to-edge (required on Android 15). Fonts are bundled under the OFL licence (`FitLife_Android/third_party_licenses/fonts/`).

Navigation uses **5 tabs**: **Today · Activity · Workout · Ride · Nutrition**. Wellbeing, Diet Plans and Profile open from Today, and the ride HUD and summary are full-screen routes.

---

## 🔐 Security & Privacy (2.0 hardening)

| Issue | Fix |
|---|---|
| `android:allowBackup="true"`: health data, profile and food logs could be pulled with `adb backup` or copied to cloud backup | `allowBackup="false"` plus explicit `backup_rules.xml` / `data_extraction_rules.xml` that exclude the database and preferences |
| Cleartext HTTP allowed | `network_security_config.xml` blocks cleartext and trusts only system CAs. Open Food Facts is called over HTTPS only. |
| Food API: no timeouts or response size limit, unescaped query | Connect/read timeouts, a 1 MB response cap, URL-encoded queries, sanitised and length-limited fields, and NaN/negative nutrient values dropped |
| Unused `CAMERA` permission | Removed |
| Step service used the `dataSync` FGS type (rejected on Android 14+, and time-limited on 15) | Now uses `health` (steps) and `location` (rides), each with its matching permission |
| **Release artifacts (`app/release/*.dm`, `output-metadata.json`) committed to git** | Removed from the repo. `.gitignore` now blocks `app/release/`, `*.jks`, `*.keystore` and `keystore.properties` |
| Notification PendingIntents | Explicit intents with `FLAG_IMMUTABLE` |

> ⚠️ Release artifacts were committed in the past, so they are still in git history. If a signing key was ever committed, consider it compromised and rotate it.

**Not done yet** (planned): encrypting the Room database with SQLCipher, and turning on R8 minification for release builds.

---

## 🏗️ Architecture

MVVM, Hilt and Room, with a single-activity Navigation Compose host.

```
UI (Compose screens + ui/components)         ── TodayScreen, ActivityScreen, CyclingScreen, ActiveRideScreen…
   │
ViewModels                                    ── FitnessViewModel (app data) · RideViewModel (live ride + history)
   │
Repositories / engines                        ── FitnessRepository · FoodRepository · HealthConnectManager
   │                                              RideTracker + RideMath (pure Kotlin ride engine)
Data & services                               ── Room (fitlife_v1.db, schema v2) · StepCounterService
                                                  RideTrackingService (platform LocationManager, no Play Services) · WorkManager
```

### Database (Room, schema v2)

| Entity | Purpose |
|---|---|
| `DailyStepsEntity` | Daily steps, kcal, distance, active minutes |
| `WorkoutEntity` / `ExerciseEntity` | Routines and their exercises |
| `FoodLogEntity` | Logged foods per meal |
| `DietPlanEntity` | Calorie and macro plans (one active) |
| `SleepLogEntity` / `WellbeingLogEntity` | Sleep, water, mood, stress, meditation |
| `UserProfileEntity` | Profile, goals (incl. daily ride km), theme |
| **`RideEntity`** *(new)* | Ride stats + downsampled route (≤ 600 points) |

`MIGRATION_1_2` adds the `rides` table and the new profile columns, so existing users keep their data.

---

## 🛠️ Tech Stack

| | |
|---|---|
| Language | Kotlin 1.9.24 (JVM 17) |
| UI | Jetpack Compose (BOM 2024.09), Material 3, Navigation Compose |
| DI / DB | Hilt 2.48 · Room 2.6.1 |
| Health | Health Connect 1.0.0-alpha11 |
| Background | Foreground services (health, location) · WorkManager |
| Charts | Custom Compose `Canvas` (bar, route, elevation, sparkline). No chart library. |
| Tests | JUnit 4 (`app/src/test`) |
| SDK | min 26 · target 35 |

---

## 📁 Project Structure

```
FitLife_Android/app/src/
├── main/java/com/fitlife/app/
│   ├── MainActivity.kt                 # Nav host, 5-tab bar, onboarding/permission gates, snackbars
│   ├── data/
│   │   ├── local/Database.kt           # Room entities, DAOs, migrations
│   │   ├── repository/FitnessRepository.kt
│   │   ├── food/FoodRepository.kt      # Offline foods + hardened Open Food Facts client
│   │   ├── healthconnect/HealthConnectManager.kt
│   │   ├── ride/RideMath.kt, RideTracker.kt   # Ride engine
│   │   └── steps/StepMath.kt           # Personalised stride / distance / kcal
│   ├── service/StepCounterService.kt, RideTrackingService.kt
│   ├── worker/Workers.kt               # Sync + reminders
│   └── ui/
│       ├── theme/                      # Color.kt, Type.kt, Theme.kt
│       ├── components/                 # Components.kt, Charts.kt
│       ├── viewmodel/                  # FitnessViewModel.kt, RideViewModel.kt
│       └── screens/                    # Today, Activity, Workout, Cycling, ActiveRide, RideSummary,
│                                       # Nutrition, DietPlans, Wellbeing, Profile, Onboarding, Permission
├── main/res/                           # fonts, xml security configs, icons, themes
└── test/java/com/fitlife/app/data/      # ride/RideMathTest, ride/RideTrackerTest, steps/StepMathTest
```

---

## 🚀 Getting Started

1. Open `FitLife_Android/` in Android Studio (Ladybug or newer, JDK 17, SDK 35).
2. Let Gradle sync, then run on a device with Android 8.0 or later. For rides, a real device with GPS works best.
3. Run the unit tests: `./gradlew :app:testDebugUnitTest`

### Permissions requested
- **Activity recognition**: step counting
- **Location (while using the app)**: only while a ride is recording
- **Notifications**: the live step/ride notification and reminders
- **Health Connect**: optional sync

### Release signing
Keep keystores and `keystore.properties` **out of git** (they are ignored). Supply signing config locally or through CI secrets.

---

## 📄 License

Personal/educational project. The bundled fonts are under the SIL Open Font License 1.1 (see `FitLife_Android/third_party_licenses/fonts/`).
