# 🏋️ FitLife — All-in-One Fitness Tracker for Android

**FitLife** is a modern, feature-rich Android fitness application built entirely with **Kotlin** and **Jetpack Compose**. It brings daily activity overview, step tracking, workout management, live GPS cycling tracking, calorie logging, diet planning, user profiling, and wellbeing monitoring into a single, beautifully designed Material 3 interface.

---

## ✨ Key Features

### 🏠 Today Dashboard
- **Consolidated Daily Hub**: View all your vital health metrics in one single dashboard
- Live step progress ring with goal achievement percentage
- Calorie burn balance (Consumed vs TDEE Target & Exercise Burn)
- Quick hydration logging (+1 glass counter)
- Current workout routine status and quick action shortcut
- Profile avatar shortcut for instant user settings access

### 🚶 Step Tracking
- **Real-time hardware step counting** via `StepCounterService` (foreground service)
- Automatic calorie burn, distance, and active-minutes estimation
- Daily step goal with animated circular progress ring
- 30-day step history with interactive bar charts
- Streak tracking (consecutive days of goal achievement)
- Manual step entry with date selection dialog
- Bi-directional sync with **Google Health Connect** using best-of-source (`maxOf`) sync logic

### 🏋️ Workout Management
- Create custom workouts with multiple exercises (sets × reps × weight)
- Pre-built workout templates: Push Day, Pull Day, Leg Day, Morning Yoga, 5K Run
- Workout categories: Strength, Cardio, HIIT, Yoga, Flexibility, Custom
- Active workout runner with set checklists and configurable rest timers (30s / 60s / 90s)
- **1RM Estimator**: Auto-calculates One-Rep Max using the Brzycki formula
- Track workout completion, duration, and MET-based calorie burn
- Workout sessions auto-sync to Health Connect (`ExerciseSessionRecord`)

### 🚴 Outdoor & Indoor Cycling Tracker
- **Real-time GPS tracking** via `CyclingService` (foreground service)
- Live speed gauge (km/h), max speed, distance (km), duration stopwatch, and elevation gain
- **Smart Auto-Pause**: Automatically pauses tracking when stationary (>5 seconds at < 1 km/h)
- **Interactive Route Map**: Renders live route polylines using `GoogleMapsView`
- Saves trip summaries to `TripEntity` and route coordinates to `TrackPointEntity` in Room DB

### 🥗 Calorie & Macro Tracking
- Log food by meal (Breakfast, Lunch, Dinner, Snack)
- **100+ built-in food database** covering proteins, dairy, grains, fruits, vegetables, legumes, nuts, oils, snacks, drinks, and fast food
- Live network search with **Open Food Facts REST API** integration
- **ML Kit Barcode Scanner**: Instant product lookup via camera barcode scanning
- Real-time macro dashboard: protein, carbs, fat with progress bars
- Adjustable serving sizes with auto-scaled macros
- TDEE (Total Daily Energy Expenditure) auto-calculated from profile

### 🥗 Diet Planning
- Create and manage multiple diet plans with calorie and macro targets
- Quick-start templates: Fat Loss (-500 kcal), Muscle Gain (+300 kcal), Maintenance, Keto
- Custom macro percentage allocator (Protein / Carbs / Fat)
- Activate/deactivate plans — active plan automatically drives daily calorie goals across the app

### 🧘 Wellbeing Dashboard
- **Sleep tracking**: log bed time, wake time, duration, and quality rating (1–5 stars); 14-day visual history
- **Water intake**: glass-by-glass counter with hydration reminders every 2 hours
- **Mood & stress logging**: emoji-based mood selector (1–5 scale) and stress slider (1–10) with journal notes
- **Mindfulness timer**: track daily meditation sessions with ambient background sound toggle

### 👤 Profile & Personalization
- Guided 4-step onboarding wizard (Welcome → Profile → Goals → Daily Step Target)
- Collects: name, date of birth, sex, height, weight, weight goal, activity level
- Calculates personalized TDEE using the **Mifflin-St Jeor equation**
- Interactive daily step goal slider (1,000–25,000 steps)
- **Data Export & Privacy**: Export health data to JSON file or perform a complete data reset

### 🔔 Smart Notifications
- **Step reminder** at 7:00 PM if you're below 80% of your daily step goal
- **Water reminder** every 2 hours if you've logged fewer than 4 glasses
- **Live status notifications**: Silent foreground notification showing live steps and GPS cycling telemetry

---

## 🏗️ Architecture

FitLife follows the **Clean MVVM (Model-View-ViewModel)** architecture pattern with unidirectional data flow:

```
┌──────────────────────────────────────────────────────────┐
│                     UI Layer                             │
│  Jetpack Compose Screens + Reusable Components           │
│  (Today, Steps, Workout, Cycling, Calories, Profile)     │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│                  ViewModel Layer                         │
│  FitnessViewModel & CyclingViewModel (@HiltViewModel)    │
│  UI State Flows: StepsUiState, WorkoutUiState, etc.     │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│                 Repository Layer                         │
│  FitnessRepository  ←→  HealthConnectManager             │
│  FoodRepository (local DB + Open Food Facts API)         │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│          Data Layer & Services / Workers                 │
│  Room Database (fitlife_v1.db - 10 Entities · 9 DAOs)    │
│  Foreground Services (StepCounterService, CyclingService)│
│  WorkManager (DailySyncWorker, StepReminder, Water)      │
└──────────────────────────────────────────────────────────┘
```

### Dependency Injection

All dependencies are wired through **Dagger Hilt**:
- `AppModule` provides the Room database and all 9 DAOs as singletons
- `FoodRepository`, `FitnessRepository`, and `HealthConnectManager` use `@Inject constructor()`
- Background workers use `@HiltWorker` with `@AssistedInject`

---

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| **Language** | Kotlin 1.9.24 |
| **UI Framework** | Jetpack Compose (BOM 2024.09) |
| **Design System** | Material 3 (Material You) with dynamic color & adaptive icons |
| **Navigation** | Navigation Compose 2.8.1 |
| **DI** | Dagger Hilt 2.48 |
| **Local Database** | Room 2.6.1 (10 Entities / 9 DAOs) |
| **Health Data** | Health Connect Client 1.0.0-alpha11 |
| **Location & Maps** | FusedLocationProviderClient & Google Maps Compose |
| **Vision API** | Google ML Kit Barcode Scanning |
| **Background Work** | WorkManager 2.9.1 |
| **Preferences** | DataStore Preferences 1.1.1 & SharedPreferences |
| **Charts** | Vico (Compose M3) 1.15.0 |
| **Image Loading** | Coil Compose 2.7.0 |
| **Permissions** | Accompanist Permissions 0.36.0 |
| **Async** | Kotlin Coroutines 1.8.1 |
| **Food API** | Open Food Facts REST API (free, no API key needed) |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 35 (Android 15) |
| **Java Target** | JVM 17 |
| **Build System** | Gradle KTS (AGP 8.13.2) |

---

## 📁 Project Structure

```
FitLife_Android/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/fitlife/app/
│       │   ├── FitLifeApp.kt              # Application class (Hilt + WorkManager init)
│       │   ├── MainActivity.kt            # Single activity, navigation host, 5-tab bottom bar
│       │   ├── di/
│       │   │   └── AppModule.kt           # Hilt module — DB + 9 DAO providers
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   └── Database.kt        # Room DB, 10 entities, 9 DAOs
│       │   │   ├── healthconnect/
│       │   │   │   └── HealthConnectManager.kt  # Read/write Health Connect data
│       │   │   ├── food/
│       │   │   │   └── FoodRepository.kt  # 100-item local DB + Open Food Facts API
│       │   │   └── repository/
│       │   │       └── FitnessRepository.kt  # Central data coordinator
│       │   ├── service/
│       │   │   ├── StepCounterService.kt  # Foreground service (hardware pedometer)
│       │   │   └── CyclingService.kt      # Foreground service (GPS speed, distance, route)
│       │   ├── worker/
│       │   │   └── Workers.kt             # DailySyncWorker, StepReminder, WaterReminder
│       │   └── ui/
│       │       ├── theme/
│       │       │   └── Theme.kt           # Material 3 color schemes (light + dark)
│       │       ├── components/
│       │       │   ├── Components.kt      # Reusable composables (RingProgress, BarChart, etc.)
│       │       │   └── GoogleMapsView.kt  # Google Maps route polyline composable
│       │       ├── viewmodel/
│       │       │   ├── FitnessViewModel.kt # Shared ViewModel for all screens
│       │       │   └── CyclingViewModel.kt # Telemetry & route recording ViewModel
│       │       └── screens/
│       │           ├── OnboardingScreen.kt   # 4-step setup wizard
│       │           ├── PermissionScreen.kt   # Runtime permission requests
│       │           ├── TodayScreen.kt        # Consolidated daily Overview dashboard
│       │           ├── StepsScreen.kt        # Step counter + 30-day history chart
│       │           ├── WorkoutScreen.kt      # Workout builder + active session runner
│       │           ├── CyclingScreen.kt      # Live GPS speed gauge, distance, route map
│       │           ├── CaloriesScreen.kt     # Food logging, barcode scan, macro tracking
│       │           ├── DietScreen.kt         # Diet plan management
│       │           ├── WellbeingScreen.kt    # Sleep, water, mood, meditation
│       │           └── ProfileScreen.kt      # TDEE recalculation, goals, data export
│       └── res/                              # Drawables, mipmaps, themes, adaptive icons
├── build.gradle.kts                          # Root build config
├── settings.gradle.kts                       # Project settings
└── gradle.properties                         # Gradle build flags
```

---

## 📊 Database Schema

The Room database (`fitlife_v1.db`) contains **10 entities**:

| Entity | Description | Key Fields |
|---|---|---|
| `DailyStepsEntity` | Daily step records | date, steps, caloriesBurned, distanceMeters, activeMinutes, syncedWithFit |
| `WorkoutEntity` | Workout sessions | name, category, date, durationMinutes, caloriesBurned, completed, templateId |
| `ExerciseEntity` | Individual exercises within a workout | workoutId, name, sets, repsPerSet, weightKg, durationSeconds |
| `FoodLogEntity` | Individual food log entries | date, mealType, foodName, calories, proteinG, carbsG, fatG, servingGrams, barcode |
| `DietPlanEntity` | Diet plan definitions | name, goal, dailyCalorieTarget, proteinTargetG, carbsTargetG, fatTargetG, isActive |
| `SleepLogEntity` | Sleep records | date, bedtimeHour, bedtimeMinute, wakeHour, wakeMinute, durationMinutes, qualityRating |
| `WellbeingLogEntity` | Daily wellbeing metrics | date, moodScore, stressLevel, waterGlasses, meditationMinutes, notes |
| `UserProfileEntity` | User profile & goals | name, dateOfBirth, sex, heightCm, weightKg, dailyStepGoal, dailyCalorieGoal, dailyWaterGoal, weightGoal, activityLevel, onboardingComplete |
| `TripEntity` | GPS cycling trip records | id, startTime, endTime, distanceMeters, durationMs, avgSpeed |
| `TrackPointEntity` | Individual GPS route coordinates | id, tripId, lat, lng, altitude, speed, accuracy, time |

---

## 🔌 Health Connect Integration

FitLife integrates with [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) to read and write:

| Data Type | Read | Write |
|---|:---:|:---:|
| Steps | ✅ | ✅ |
| Active Calories Burned | ✅ | ✅ |
| Exercise Sessions | ✅ | ✅ |
| Nutrition | ✅ | ✅ |
| Sleep Sessions | ✅ | ✅ |
| Heart Rate | ✅ | — |
| Weight | ✅ | ✅ |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17** or higher
- **Android SDK** with API level 35 installed
- A physical device or emulator running **Android 8.0+ (API 26+)**
- [Health Connect app](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed on the device (for health data sync)

### Build & Run

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/fitlife.git
   cd fitlife/FitLife_Android
   ```

2. **Open in Android Studio**
   - File → Open → select the `FitLife_Android` directory

3. **Sync Gradle**
   - Android Studio will automatically download all dependencies

4. **Run on device**
   - Select a connected device or emulator
   - Click **Run ▶** or press `Shift + F10`

5. **Build APK (release)**
   ```bash
   ./gradlew assembleRelease
   ```
   The signed APK will be at `app/release/FitLife-release.apk`

### Permissions

On first launch, the app requests runtime permissions for:
- **Activity Recognition** — hardware step counting
- **Location (Fine/Coarse)** — real-time GPS speed and cycling route mapping
- **Health Connect** — syncing steps, workouts, nutrition, and sleep
- **Notifications** — daily step nudges and hydration alerts

---

## 🎨 Theming & Icons

FitLife uses **Material 3 / Material You** with:
- **Dynamic Color** support on Android 12+ (colors adapt to wallpaper)
- **Light & Dark mode** with pure black (`#000000`) background optimization
- Custom accent colors: FitLife Lime (`#CCFF00`), Teal (`#00897B`), Blue (`#1565C0`)
- **Adaptive Icon Suite**: Vector background, foreground logo, and Android 13+ monochrome themed launcher icon (`ic_launcher_monochrome.xml`)

---

## 🔧 Background Services & Workers

| Service / Worker | Schedule | Purpose |
|---|---|---|
| `StepCounterService` | Foreground Service (`FOREGROUND_SERVICE_DATA_SYNC`) | Hardware pedometer sensor listener & DB updates |
| `CyclingService` | Foreground Service | Real-time GPS location tracking, speed gauge, route mapping |
| `DailySyncWorker` | Periodic (Every 1 hour) | Background step sync with Health Connect |
| `StepReminderWorker` | Daily at 7:00 PM | Sends notification nudge if step progress < 80% |
| `WaterReminderWorker` | Periodic (Every 2 hours) | Sends hydration alert if water intake < 4 glasses |

---

## 📋 App Flow

```
Launch
  │
  ├─ First Time? → Permission Screen → Onboarding Wizard (4 steps) → Main App
  │
  └─ Returning User → Permission Check → Main App
                                              │
                                              ▼
                                    ┌───────────────────┐
                                    │    Bottom Nav     │
                                    ├─────┬─────┬───────┤
                                    │Today│Steps│Workout│
                                    │ 🏠  │ 🚶  │  🏋️   │
                                    ├─────┼─────┼───────┤
                                    │Cycle│Cals │Profile│
                                    │ 🚴  │ 🥗  │  👤   │
                                    └─────┴─────┴───────┘
```

---

## 📄 License

This project is developed as a personal/educational fitness application.

---

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

<p align="center">
  Built with ❤️ using Kotlin & Jetpack Compose
</p>
