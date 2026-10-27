# FitLife — UI Inventory & Redesign Reference

> **Purpose.** This document lists **every user-facing element** of the FitLife Android app as it exists in code today (branch `master` @ `48c1b82`). Use it to plan a UI redesign. It covers what the app shows, where each value comes from, how the user interacts with it, and what currently works poorly.
>
> **Source of truth.** Everything here was taken from the Kotlin/Compose source in `FitLife_Android/app/src/main/`. Where the README disagrees with the code, the code wins, and the difference is called out (⚠️).

---

## Table of Contents

1. [App at a Glance](#1-app-at-a-glance)
2. [Information Architecture & Navigation](#2-information-architecture--navigation)
3. [Current Design System (Tokens)](#3-current-design-system-tokens)
4. [Global Shell & System Surfaces](#4-global-shell--system-surfaces)
5. [Screen-by-Screen Inventory](#5-screen-by-screen-inventory)
   - 5.1 [Onboarding (4 steps)](#51-onboarding-wizard)
   - 5.2 [Permissions](#52-permissions-screen)
   - 5.3 [Steps](#53-steps-tab)
   - 5.4 [Workout](#54-workout-tab)
   - 5.5 [Calories](#55-calories-tab)
   - 5.6 [Diet](#56-diet-tab)
   - 5.7 [Wellbeing](#57-wellbeing-tab)
6. [Reusable Component Library](#6-reusable-component-library)
7. [Data Available to the UI](#7-data-available-to-the-ui)
8. [Business Rules & Calculations Shown in the UI](#8-business-rules--calculations-shown-in-the-ui)
9. [Static Content (Templates, Presets, Food DB)](#9-static-content)
10. [Feedback, Messages & Notifications (Full Copy Deck)](#10-feedback-messages--notifications)
11. [UX Audit — Current Issues](#11-ux-audit--current-issues)
12. [Redesign Opportunities](#12-redesign-opportunities)
13. [Designer Deliverables Checklist](#13-designer-deliverables-checklist)
14. [Appendix — Source File Map](#14-appendix--source-file-map)

---

## 1. App at a Glance

| Attribute | Value |
|---|---|
| App name | **FitLife** |
| Package | `com.fitlife.app` · version `1.0` |
| Platform | Android 8.0+ (minSdk 26), targetSdk 35 |
| UI toolkit | Jetpack Compose + Material 3 (Material You) |
| Architecture | Single Activity · single shared `FitnessViewModel` · Room DB · Hilt DI |
| Theme | Light + Dark (follows system). **Dynamic color is ON by default** on Android 12+ |
| Orientation / form factor | Phone portrait only (no tablet/landscape layouts) |
| Languages | English only, hard-coded strings (only `app_name` is in `strings.xml`) |
| Accounts / login | None. All data is local to the device |
| Network | Only used for Open Food Facts search and Health Connect sync |

**Five feature pillars (= five bottom-nav tabs):**

| # | Tab | Purpose in one line |
|---|---|---|
| 1 | **Steps** | Live step count from the phone sensor, daily goal ring, 7-day chart, streak, manual entry |
| 2 | **Workout** | Log workouts (name, category, duration, kcal, exercises); history; 6 templates |
| 3 | **Calories** | Food log by meal, calorie ring vs goal, macro bars, food search (local + online) |
| 4 | **Diet** | Create / activate diet plans with calorie + macro targets; today's adherence |
| 5 | **Wellbeing** | Sleep log, mood + stress check-in, water glasses, meditation timer, weekly insights |

---

## 2. Information Architecture & Navigation

### 2.1 App flow

```
App launch
  │
  ├── profile.onboardingComplete == false ──► ONBOARDING (4 steps) ──► saves profile
  │                                                                     │
  ├── permissionsDone == false (in memory only, resets every launch) ◄──┘
  │        └──► PERMISSIONS screen
  │               • auto-skips if all runtime permissions are already granted
  │               • "Grant Permissions" → system dialog → continue if Activity Recognition granted
  │               • "Skip for now" → continue
  │               • starts StepCounterService
  │
  └── MAIN APP  (Scaffold + bottom NavigationBar)
         ├── Steps      ── [Dialog]  Log Steps Manually
         ├── Workout    ── Tabs: Today | History | Templates ── [Bottom sheet] New Workout
         ├── Calories   ── [Bottom sheet] Log Food (with live search dropdown)
         ├── Diet       ── [Bottom sheet] Create Diet Plan
         └── Wellbeing  ── [Bottom sheet] Log Sleep  (+ inline meditation timer)
```

### 2.2 Sitemap (every surface in the app)

| Level | Surface | Type | File |
|---|---|---|---|
| Pre-app | Onboarding · Step 1 Welcome | Full screen | `OnboardingScreen.kt` |
| Pre-app | Onboarding · Step 2 Profile | Full screen | `OnboardingScreen.kt` |
| Pre-app | Onboarding · Step 3 Goal & Activity | Full screen | `OnboardingScreen.kt` |
| Pre-app | Onboarding · Step 4 Step Goal | Full screen | `OnboardingScreen.kt` |
| Pre-app | Permissions | Full screen | `PermissionScreen.kt` |
| Tab 1 | Steps | Tab screen | `StepsScreen.kt` |
| Tab 1 › | Log Steps Manually | `AlertDialog` | `StepsScreen.kt` |
| Tab 2 | Workout (Today / History / Templates) | Tab screen with `TabRow` | `WorkoutScreen.kt` |
| Tab 2 › | New Workout | `ModalBottomSheet` (92% height) | `WorkoutScreen.kt` |
| Tab 3 | Calories | Tab screen | `CaloriesScreen.kt` |
| Tab 3 › | Log Food | `ModalBottomSheet` (92% height) | `CaloriesScreen.kt` |
| Tab 4 | Diet | Tab screen | `DietScreen.kt` |
| Tab 4 › | Create Diet Plan | `ModalBottomSheet` | `DietScreen.kt` |
| Tab 5 | Wellbeing | Tab screen | `WellbeingScreen.kt` |
| Tab 5 › | Log Sleep | `ModalBottomSheet` | `WellbeingScreen.kt` |
| System | Foreground "step tracker" notification | Notification | `StepCounterService.kt` |
| System | Step reminder (7 PM) | Notification | `Workers.kt` |
| System | Water reminder (every 2 h) | Notification | `Workers.kt` |
| System | Snackbar messages | Snackbar (root) | `MainActivity.kt` |

**Not present (no surface exists):** Profile / Settings, Home / Dashboard summary, workout detail, food detail, plan edit, history/calendar views beyond 7 days, achievements, about / privacy, splash screen.

### 2.3 Navigation mechanics

- Bottom nav is a Material 3 `NavigationBar` (`tonalElevation = 4.dp`) with 5 `NavigationBarItem`s, always showing labels.
- Navigation Compose is a dependency but **not used**. Tabs switch through a `remember { mutableStateOf<Screen>() }` and a `when` block. Because of this:
  - Each tab screen is torn down when you leave it, so scroll position, the selected Workout sub-tab, and a **running meditation timer are lost**.
  - The selected tab resets to Steps after rotation or process death.
  - The system Back button exits the app from any tab.
- Each tab screen has its **own nested `Scaffold`** (top bar + FAB) inside the root `Scaffold` (bottom bar + snackbar host).

| Tab | Route | Label | Icon (Material Outlined) |
|---|---|---|---|
| 1 | `steps` | Steps | `DirectionsWalk` |
| 2 | `workout` | Workout | `FitnessCenter` |
| 3 | `calories` | Calories | `LocalFireDepartment` |
| 4 | `diet` | Diet | `Restaurant` |
| 5 | `wellbeing` | Wellbeing | `FavoriteBorder` |

---

## 3. Current Design System (Tokens)

### 3.1 Color

**Brand constants** (`Theme.kt`). These are defined but **not used by any screen**:

| Token | Hex | Swatch intent |
|---|---|---|
| `FitBlue` | `#1E88E5` | Primary brand |
| `FitTeal` | `#00897B` | Secondary |
| `FitOrange` | `#F4511E` | Energy / calories |
| `FitGreen` | `#43A047` | Success |
| `FitPurple` | `#7B1FA2` | Wellbeing |
| `FitRed` | `#E53935` | Alert |

**Fallback Light scheme** (used on Android < 12, or when dynamic color is disabled):

| Role | Hex |
|---|---|
| primary | `#1565C0` |
| onPrimary | `#FFFFFF` |
| primaryContainer | `#DEECFD` |
| secondary | `#00695C` |
| tertiary | `#F57F17` |
| background | `#F4F6F9` |
| surface | `#FFFFFF` |
| surfaceVariant | `#EEF2F8` |
| onBackground / onSurface | `#0D1B2A` |
| outline | `#BCC8D8` |

**Fallback Dark scheme:**

| Role | Hex |
|---|---|
| primary | `#90CAF9` |
| onPrimary | `#0D2137` |
| primaryContainer | `#1565C0` |
| secondary | `#80CBC4` |
| tertiary | `#FFCC02` |
| background | `#0E1621` |
| surface | `#182130` |
| surfaceVariant | `#1E2D40` |
| onBackground / onSurface | `#E3EDF8` |
| outline | `#2F4460` |
| error | `#EF9A9A` |

**Other:** launcher icon background is `#1565C0`. The status bar color follows `colorScheme.background`.

**Semantic color usage across screens.** Colors are assigned per screen, not from a shared metric palette:

| Meaning | Color role used | Where |
|---|---|---|
| Steps / main progress | `primary` | Steps ring, bars, FAB |
| Calories burned | `error` | Steps quick-stat, Calories "Burned" row |
| Distance | `secondary` | Steps quick-stat |
| Active minutes | `tertiary` | Steps quick-stat |
| Goal met (history row) | `tertiary` | Steps history ring + check |
| Food calories ring | `secondary` | Calories ring, food search kcal |
| Protein | `primary` | Calories macros, Diet |
| Carbs | `tertiary` | Calories macros, Diet |
| Fat | `error` | Calories macros, Diet |
| Workout categories | Cardio=`secondary`, HIIT=`error`, Yoga=`tertiary`, others=`primary` | Workout card |
| Sleep | `tertiary` | Wellbeing sleep chart |
| Meditation | `secondary` | Timer |
| Goal line on charts | `error` @ 40% alpha | `BarChart` |
| FAB container | Steps=`primary`, Workout=`tertiary`, Calories=`secondary`, Diet=`tertiary` | per-screen |

> ⚠️ With dynamic color on (the default on most current devices), the colors above come from the user's wallpaper. The brand palette is effectively **never shown**, and colors such as `error` (used for "fat" and "calories burned") can look like warnings.

### 3.2 Typography

- Uses the **default Material 3 `Typography()`** (Roboto). No custom font or type scale.
- Many components override `fontSize` with raw `sp` values. **Steps** and **Calories** use 16–18 sp overrides heavily; the other screens don't.

| Style used | Typical usage |
|---|---|
| `displayLarge` + ExtraBold | Step count in the main ring |
| `displayMedium` Bold / Light | Onboarding step-goal value; meditation countdown |
| `headlineMedium` | Steps top-bar title (ExtraBold); onboarding page titles; Calories ring number; Log Food title |
| `headlineSmall` | Calories title; sheet titles (New Workout, Create Diet Plan, Log Sleep); quick-stat values; history step count |
| `titleLarge` | Section headers on Steps; `StatCard` value |
| `titleMedium` | Goal label in ring; history row date; `CalRow` values |
| `titleSmall` SemiBold | `SectionCard` titles; "Your Plans"; "Exercises" |
| `bodyLarge` / `bodyMedium` / `bodySmall` | Lists, descriptions, metadata |
| `labelMedium` / `labelSmall` | Field group labels, chart labels (overridden to **9 sp**) |

Font weights in use: Light, Medium, SemiBold, Bold, ExtraBold.

### 3.3 Shape (corner radius)

There is no shape scale; values are hard-coded per component.

| Radius | Used for |
|---|---|
| 2 dp | Onboarding progress segments |
| 4 dp | Bar chart bars |
| 8 dp | Water glass tiles |
| 10 dp | Icon tiles in cards, food summary card |
| 12 dp | `StatCard`, mood tiles, search dropdown bottom corners |
| 14 dp | Workout / Plan / Permission / Goal cards, onboarding & permission buttons |
| 16 dp | `SectionCard` |
| 20 dp | Calories ring card, Steps history row, `StreakBadge` |
| 24 dp | Steps chart card, app logo tile |
| 32 dp | Steps main progress card |
| 50% / Circle | Progress bars, feature icons |

### 3.4 Spacing & sizing

- Screen content padding: **16 dp** for tabs, **24 dp** for onboarding, **28 dp** for permissions, **20 dp** horizontal in sheets.
- Vertical gap between list items: Steps **24 dp**; Workout **12 dp**; Calories / Diet / Wellbeing **14 dp**.
- Every tab list ends with an **80 dp spacer** so the FAB doesn't cover content.
- Ring sizes: Steps 240 dp / 18 dp stroke; Calories 150 dp / 16 dp; history mini-ring 52 dp / 5 dp; `RingProgress` default 180 dp / 18 dp.
- Primary buttons: 52 dp tall on Permissions; default elsewhere. Some trailing buttons are **32 dp tall** (below the 48 dp touch-target guideline).

### 3.5 Elevation

`Card` defaults are used mostly. Exceptions: Steps main card at 0 dp, `StatCard` at tonal 1 dp, food search dropdown at 6 dp, NavigationBar at tonal 4 dp.

### 3.6 Iconography

**Material Icons (Outlined)** used:
`DirectionsWalk`, `FitnessCenter`, `LocalFireDepartment`, `Restaurant`, `FavoriteBorder`, `Favorite`, `Sync`, `Add`, `Map`, `Timer`, `Check`, `CheckCircle`, `CalendarToday`, `Delete`, `Close`, `Search`, `EditNote`, `TrendingUp`, `TrendingDown`, `Save`, `Notifications`.

**Emoji are also used as icons:**

| Emoji | Meaning / place |
|---|---|
| 🔥 ⚖️ 💪 | Goal cards (Lose / Maintain / Gain) |
| 🎉 | "Start FitLife 🎉" button |
| ☀️ 🌤 🌙 🍎 | Meal headers: Breakfast / Lunch / Dinner / Snack |
| 🔥 💪 🌾 🥑 | Food entry summary: kcal / protein / carbs / fat |
| 🌐 | "online" result from Open Food Facts |
| 😞 😕 😐 😊 😁 | Mood 1–5 |
| 😴 😪 😐 😊 😁 | Sleep quality 1–5 |
| 💧 | Water glass tiles |
| 🔥 | Streak badge |
| 👣 💪 📋 🥗 | Empty states |
| 😊 😤 💧 🧘 🌙 | Weekly insights rows |

**Logo:** The in-app logo is the letter **"F"** (40–44 sp Bold, primary) on a `primaryContainer` rounded square (80–88 dp, 24 dp radius). The **launcher icon** is a white generic "person" silhouette (head and shoulders) on `#1565C0`. The two don't match each other.

### 3.7 Motion

| Animation | Spec |
|---|---|
| `RingProgress` fill | `tween(1000, FastOutSlowInEasing)` |
| Steps main ring | `animateFloatAsState` default spring |
| `AnimatedProgressBar` | `tween(800)` |
| `BarChart` bars | `tween(600)` per bar |
| Onboarding page change | Slide in from right + fade / slide out to left + fade (`AnimatedContent`) |
| Food search dropdown | `AnimatedVisibility` default (expand + fade) |
| Bottom sheets / dialogs | Material defaults |

---

## 4. Global Shell & System Surfaces

### 4.1 Main shell (`MainActivity.kt → MainApp`)

```
┌──────────────────────────────────────┐
│ (status bar – colored = background)  │
├──────────────────────────────────────┤
│  [ per-tab TopAppBar ]               │
│                                      │
│  [ per-tab scrolling content ]       │
│                                      │
│                       [ per-tab FAB ]│
│  [ Snackbar (root host) ]            │
├──────────────────────────────────────┤
│ 🚶Steps 🏋Workout 🔥Calories 🍴Diet ♡Wellbeing │  ← NavigationBar
└──────────────────────────────────────┘
```

| Element | Details |
|---|---|
| Root `Scaffold` | Holds `snackbarHost` and `bottomBar` |
| NavigationBar | 5 items, label `FontWeight.Medium`, icon + label always visible |
| Snackbar | `SnackbarDuration.Short`, fed by `vm.message` (SharedFlow). Full list in §10 |
| Step service | `StepCounterService.start()` is called when MainApp first composes |

### 4.2 Android notifications

| Notification | Channel (id / name) | Title | Body | Trigger |
|---|---|---|---|---|
| Live step tracker (ongoing, silent) | `fitlife_steps_service` / "Step Tracking" | `FitLife — Step Tracker` | `12,345 steps today` | While the foreground service runs |
| Step reminder | `fitlife_steps` / "Step Reminders" | `Keep moving! 🚶` | `4,210 steps done — 5,790 to go!` | Daily at ~7 PM, if steps are below 80% of goal |
| Water reminder | `fitlife_reminders` / "Reminders" | `Stay hydrated! 💧` | `Don't forget to drink water today.` | Every 2 h, if fewer than 4 glasses logged |

There are no notification actions (e.g. "+1 glass") and no deep links into specific tabs.

### 4.3 Runtime permissions declared

| Permission | Requested in UI? |
|---|---|
| `ACTIVITY_RECOGNITION` | ✅ Permissions screen |
| `POST_NOTIFICATIONS` (Android 13+) | ✅ Permissions screen |
| Health Connect (steps, HR, calories, sleep, nutrition, exercise, weight) | ❌ No UI to request them; sync only works if granted elsewhere |
| `CAMERA` | ❌ Declared for future barcode scanning; never requested |

---

## 5. Screen-by-Screen Inventory

Each screen section has: **purpose → wireframe → element table → states → overlays → data bindings → issues**.

---

### 5.1 Onboarding Wizard

**File:** `ui/screens/OnboardingScreen.kt`  
**Shown when:** `profile.onboardingComplete == false` (first launch).  
**Container:** full-screen `Box` (background color) → `Column` (padding 24 dp).

#### Persistent chrome (all 4 steps)

| Element | Component | Details |
|---|---|---|
| Progress indicator | 4 × `Box` segments | 4 dp tall, 2 dp radius, 8 dp gap. Current = **32 dp wide**, others 16 dp. Completed and current = `primary`, upcoming = `surfaceVariant` |
| Page content | `AnimatedContent` | Horizontal slide + fade |
| Back button | `OutlinedButton` "Back" | Hidden on step 1. Half width when visible |
| Continue button | `Button` "Continue" → on last step "Start FitLife 🎉" | Full width on step 1, half width otherwise. SemiBold |

There is **no validation** anywhere in the wizard. Any field can be left empty, and defaults are used silently.

#### Step 1 — Welcome

```
          ━━━━ ── ── ──
            ┌──────┐
            │  F   │   ← 88dp logo tile
            └──────┘
       Welcome to FitLife
  Your all-in-one fitness companion.
 Steps · Workouts · Calories · Diet · Wellbeing

 (◯) Step Counter
     Track steps with Google Health Connect
 (◯) Workout Planner
     Log exercises and track progress
 (◯) Calorie & Diet
     Monitor nutrition and macro targets
 (◯) Digital Wellbeing
     Sleep, mood, water and mindfulness

 [            Continue            ]
```

| Element | Copy / content |
|---|---|
| Logo tile | "F", 44 sp Bold, primary on primaryContainer, 88 dp, 24 dp radius |
| Title | **Welcome to FitLife** (headlineMedium Bold) |
| Subtitle | "Your all-in-one fitness companion.\nSteps · Workouts · Calories · Diet · Wellbeing" |
| Feature rows ×4 (`FeatureRow`) | 40 dp circle icon (primaryContainer) + title + subtitle: `DirectionsWalk` Step Counter / "Track steps with Google Health Connect" · `FitnessCenter` Workout Planner / "Log exercises and track progress" · `Restaurant` Calorie & Diet / "Monitor nutrition and macro targets" · `Favorite` Digital Wellbeing / "Sleep, mood, water and mindfulness" |

#### Step 2 — Profile ("Tell us about you")

```
 Tell us about you
 We'll personalise your goals and calorie targets.

 [ Your name                       ]
 [ Age        ]   Sex
                  (Male) (Female)
 [ Height (cm)]   [ Weight (kg)   ]
```

| Field | Component | Keyboard | Default if empty |
|---|---|---|---|
| Your name | `OutlinedTextField` single-line | Text | "Friend" |
| Age | `OutlinedTextField` | Number | 25 → stored as DOB `YYYY-01-01` |
| Sex | 2 × `FilterChip`: Male / Female | — | Male (preselected) |
| Height (cm) | `OutlinedTextField` | Number | 170 |
| Weight (kg) | `OutlinedTextField` | Decimal | 70 |

#### Step 3 — Goal ("Your fitness goal")

```
 Your fitness goal
 We'll set your daily calorie target accordingly.

 ┌─────────────────────────────────────┐
 │ 🔥  Lose Weight                       │
 │     Calorie deficit · burn more…   ✓ │
 └─────────────────────────────────────┘
 ┌ ⚖️  Maintain Weight ─────────────────┐
 ┌ 💪  Build Muscle ────────────────────┐

 Activity level
 (Sedentary) (Light) (Moderate) (Active)
```

| Element | Details |
|---|---|
| Goal cards ×3 (clickable `Card`, 14 dp radius) | `lose` 🔥 **Lose Weight** · "Calorie deficit · burn more than you eat"<br>`maintain` ⚖️ **Maintain Weight** · "Stay balanced · eat at your TDEE" *(default)*<br>`gain` 💪 **Build Muscle** · "Calorie surplus · fuel your gains" |
| Selected card state | Outlined border + `primaryContainer` @ 40% fill + `CheckCircle` icon (primary) |
| Activity level | 4 × `FilterChip` in a single non-scrolling `Row`: Sedentary / Light / **Moderate** (default) / Active |

> ⚠️ The data model also supports `very_active` (×1.9), but there is no chip for it. Four chips in one row may overflow on narrow phones.

#### Step 4 — Daily step goal

```
        Daily step goal
 You can change this anytime in your profile.

            10,000
         steps per day
 ●━━━━━━━━━━━━━━━━○─────────────────
 3,000                         25,000

 (5,000) (7,500) (10,000) (15,000)

 [   Back   ]  [ Start FitLife 🎉  ]
```

| Element | Details |
|---|---|
| Value | `displayMedium` Bold primary, formatted `%,d` |
| Slider | Range **3,000–25,000**, 43 steps (= 500-step increments), default 10,000 |
| Quick-pick | 4 × `AssistChip`: 5,000 / 7,500 / 10,000 / 15,000 |
| Finish | Builds `UserProfileEntity`, computes TDEE → `dailyCalorieGoal`, sets `onboardingComplete = true` |

> ⚠️ The copy says "change this anytime in your profile", but **no profile screen exists**. The README says the slider goes to 1,000; the code minimum is 3,000.

---

### 5.2 Permissions Screen

**File:** `ui/screens/PermissionScreen.kt` · Shown after onboarding and on **every** launch (its state is not persisted). It auto-continues if permissions are already granted, which can cause a brief flash.

```
            ┌──────┐
            │  F   │  80dp
            └──────┘
        Permissions needed
 FitLife needs these to track your steps
 automatically and send reminders.

 ┌──────────────────────────────────────┐
 │ [🚶] Physical Activity             ✓ │
 │      Required to count your steps…   │
 └──────────────────────────────────────┘
 ┌──────────────────────────────────────┐
 │ [🔔] Notifications                    │  (Android 13+ only)
 │      For step goal reminders, water…  │
 └──────────────────────────────────────┘

 [ ✓  Grant Permissions ]   (52dp, 14dp radius)
          Skip for now
```

| Element | Copy / behavior |
|---|---|
| Title | **Permissions needed** (headlineSmall Bold) |
| Body | "FitLife needs these to track your steps automatically and send reminders." |
| `PermissionItem` – Physical Activity | Icon `DirectionsWalk`; "Required to count your steps automatically using the phone sensor." |
| `PermissionItem` – Notifications | Icon `Notifications`; "For step goal reminders, water reminders and weekly summaries." *(⚠️ there are no weekly summaries)* |
| Item granted state | Card tint `primaryContainer` @ 30%, icon tile primaryContainer, icon primary, trailing `CheckCircle` |
| Item default state | Card `surface`, icon tile `surfaceVariant`, icon `onSurfaceVariant` |
| Grant Permissions | `Button` with `CheckCircle` icon → system permission dialog. Continues only if Activity Recognition is granted |
| Skip for now | `TextButton` (onSurfaceVariant) → continues without permissions |

**Missing states:** permanently denied ("Open settings"), Health Connect not installed or not permitted, rationale after the first denial.

---

### 5.3 Steps Tab

**File:** `ui/screens/StepsScreen.kt` · **State:** `StepsUiState`

```
┌──────────────────────────────────────┐
│ Step Tracker                    (⟳) │ ← sync only if Health Connect available
│ Tuesday, 27 October                  │
├──────────────────────────────────────┤
│ ╭──────────────────────────────────╮ │
│ │          ◜‾‾‾‾‾‾‾‾◝              │ │
│ │        ◜    🚶      ◝            │ │  240dp ring
│ │       │   7,842      │           │ │
│ │        ◟ GOAL: 10,000◞           │ │
│ │          ◟________◞              │ │
│ │                                  │ │
│ │   🔥 313   │  🗺 5.98  │  ⏱ 78  │ │
│ │    kcal    │    km     │   min   │ │
│ ╰──────────────────────────────────╯ │
│                                      │
│ Activity Overview                    │
│ Last 7 days                          │
│ ┌──────────────────────────────────┐ │
│ │ ▂  ▅  ▇  ▃  ▆  ▁  ▄   ---goal--- │ │  160dp
│ │ We Th Fr Sa Su Mo Tu              │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Recent History          [🔥 3 day streak] │
│ ┌──────────────────────────────────┐ │
│ │ (◔) Today          7,842         │ │
│ │     313 kcal · 5.98 km           │ │
│ └──────────────────────────────────┘ │
│  … up to 7 rows                      │
│                       [ + Log Steps ]│ ← Extended FAB
└──────────────────────────────────────┘
```

#### Elements

| # | Element | Component | Content / data | Interaction |
|---|---|---|---|---|
| 1 | Title | `TopAppBar` title | **Step Tracker** (headlineMedium ExtraBold) | — |
| 2 | Date | Text | `EEEE, d MMMM` e.g. "Tuesday, 27 October" | — |
| 3 | Sync button | `FilledTonalIconButton` + `Sync` icon (28 dp) | Visible only if `healthConnectAvailable`. While syncing, shows a 24 dp spinner and is disabled | `vm.syncSteps()` → snackbar |
| 4 | Main progress card | `Card` 32 dp radius, surfaceVariant @ 30%, 0 elevation | — | — |
| 4a | Progress ring | 2 stacked `CircularProgressIndicator`s, 240 dp, 18 dp, round cap | Track = outlineVariant @ 40%; progress = primary; `todaySteps / stepGoal` (fill capped at 100%) | — |
| 4b | Ring center | Icon + number + label | `DirectionsWalk` 40 dp; `todaySteps` (displayLarge ExtraBold, `%,d`); "GOAL: 10,000" (titleMedium Bold) | — |
| 4c | Quick stats ×3 (`QuickStat`) | Icon + value + unit, separated by `VerticalDivider`s | 🔥 `todayCaloriesBurned` **kcal** (error) · 🗺 `todayDistanceKm` (2 dp) **km** (secondary) · ⏱ `todayActiveMinutes` **min** (tertiary) | — |
| 5 | Section header | `SectionHeader` | **Activity Overview** / "Last 7 days" | — |
| 6 | Weekly chart | `BarChart` in an outlined card (24 dp radius), 160 dp tall | 7 bars, oldest→today, labels Mo–Su; goal line drawn; max = max(best day, goal) | — |
| 7 | Section header | `SectionHeader` | **Recent History** | — |
| 8 | Streak badge | `StreakBadge` | "🔥 N day streak"; shown only if streak > 0 | — |
| 9 | History rows (`ModernStepRow`) | Outlined `Surface` (20 dp radius) | 52 dp mini-ring (tertiary + ✓ if goal met, else primary) · date ("Today" / "Yesterday" / "Mon, 26 Oct") · "313 kcal · 5.98 km" · steps (headlineSmall ExtraBold primary) | Clickable with **no action** (ripple only) |
| 10 | FAB | `ExtendedFloatingActionButton` primary | `Add` icon + "Log Steps" (18 sp) | Opens dialog |

#### States

| State | What renders |
|---|---|
| No history | `EmptyState` 👣 "No steps logged" / "Start walking to see your history here!" |
| Goal exceeded | Ring stays at 100%; nothing extra is shown |
| Syncing | Spinner in sync button |
| Health Connect unavailable | Sync button hidden, with no explanation |

#### Overlay — "Log Steps Manually" (`AlertDialog`)

| Element | Details |
|---|---|
| Title | **Log Steps Manually** (headlineSmall Bold) |
| Steps field | `OutlinedTextField`, digits only, prefix icon `DirectionsWalk`, label "Steps" (18 sp) |
| Date field | `OutlinedTextField` **free text**, prefilled with today `YYYY-MM-DD`, prefix `CalendarToday`, label "Date (YYYY-MM-DD)". No date picker and no validation |
| Confirm | `Button` "Save Activity" (only acts if steps > 0) |
| Dismiss | `TextButton` "Cancel" |
| Behavior | Steps are **added** to the existing value for that date (not replaced) |

---

### 5.4 Workout Tab

**File:** `ui/screens/WorkoutScreen.kt` · **State:** `WorkoutUiState`

```
┌──────────────────────────────────────┐
│ Workout Planner                      │
├──────────────────────────────────────┤
│   Today   │  History  │  Templates   │ ← TabRow
├──────────────────────────────────────┤
│ ┌───────────────┐ ┌───────────────┐  │  (Today & History only)
│ │  This Week    │ │  Total Time   │  │
│ │      3        │ │     145m      │  │
│ │   workouts    │ │   all time    │  │
│ └───────────────┘ └───────────────┘  │
│ ┌──────────────────────────────────┐ │
│ │ [🏋] Push Day A (Strength)    ✓ │ │
│ │      Today · 45 min · 320 kcal 🗑│ │
│ └──────────────────────────────────┘ │
│                    [ 🏋 New Workout ]│
└──────────────────────────────────────┘
```

#### Elements

| # | Element | Component | Content / data | Interaction |
|---|---|---|---|---|
| 1 | Title | `TopAppBar` | **Workout Planner** (Bold) | — |
| 2 | Sub-tabs | `TabRow` | Today · History · Templates | Switch list (state lost when leaving the tab) |
| 3 | Summary | 2 × `StatCard` | "This Week" `weeklyCount` "workouts" (primary) · "Total Time" `${totalMinutes}m` "all time" (secondary) | — |
| 4 | Workout card (`WorkoutCard`) | `Card` 14 dp | 44 dp icon tile (category color @ 12%) with `FitnessCenter` icon (**same icon for every category**); name (bodyLarge SemiBold); category `AssistChip` (22 dp tall); meta line "Today · 45 min · 320 kcal · notes" | ✓ `CheckCircle` (tertiary) → complete + sync to Health Connect, shown only when `!completed`. 🗑 `Delete` (error 70%) → delete immediately, with **no confirm or undo** |
| 5 | Template card (`TemplateCard`) | `Card` 14 dp | Name (bodyLarge SemiBold); category (labelSmall primary); first 3 exercises joined by " · " + "…" | `Button` "Use" → opens New Workout sheet prefilled |
| 6 | FAB | Extended, tertiary | `FitnessCenter` + "New Workout" | Opens empty sheet |

#### States

| Sub-tab | Empty state |
|---|---|
| Today | 💪 "No workouts today" / "Tap + to log your first session" |
| History | 📋 "No workouts yet" / "Start logging to see your history" |
| Templates | Always shows 6 templates (§9.1) |

#### Overlay — "New Workout" (`ModalBottomSheet`, 92% height)

| Element | Component | Details |
|---|---|---|
| Title | Text | **New Workout** (headlineSmall Bold) |
| Workout name | `OutlinedTextField` | Default on save: "Workout" |
| Category | `FilterChip` row | **Only the first 4 are shown**: Strength / Cardio / HIIT / Yoga. "Flexibility" and "Custom" can't be picked |
| Duration (min) | `OutlinedTextField` digits | Half width |
| Calories | `OutlinedTextField` digits | Half width; entered manually (no estimate) |
| Notes (optional) | `OutlinedTextField` | Shown in card meta line |
| Exercises header | Text + `TextButton` "+ Add" | Adds an empty row |
| Exercise row | "1." + `OutlinedTextField` "Exercise" + `Close` icon (error) | **Name only.** The DB has sets / reps / weight / duration / rest fields, but the UI doesn't capture them. Template text like "Bench Press 3×8" is saved as the name |
| Actions | `OutlinedButton` Cancel · `Button` **Save Workout** | Saved with `completed = true` and today's date (date can't be picked) |

> ⚠️ Saved workouts are always `completed = true`, so the "Complete" ✓ button never appears in practice. There is no workout detail view, so saved exercises are never shown again.

---

### 5.5 Calories Tab

**File:** `ui/screens/CaloriesScreen.kt` · **State:** `CalorieUiState`

```
┌──────────────────────────────────────┐
│ Calorie Counter                   (+)│
│ Tuesday, 27 October                  │
├──────────────────────────────────────┤
│ ┌──────────────────────────────────┐ │
│ │  ◜‾‾‾‾◝     Goal     2,150 kcal  │ │
│ │ │ 1,240│    Burned     320 kcal  │ │
│ │ │ kcal │    ─────────────────    │ │
│ │  ◟____◞     Net        920 kcal  │ │
│ └──────────────────────────────────┘ │
│ ┌ Macros Today ────────────────────┐ │
│ │ Protein          62g / 150g      │ │
│ │ ███████░░░░░░░░                  │ │
│ │ Carbs …   Fat …                  │ │
│ └──────────────────────────────────┘ │
│ ┌ ☀️ Breakfast           [+ Add] ─┐ │
│ │ 420 kcal                          │ │
│ │ Oats (dry, 40g)        150 kcal ✕│ │
│ │ P:5g · C:27g · F:3g · 40g         │ │
│ └──────────────────────────────────┘ │
│ ┌ 🌤 Lunch ┐ ┌ 🌙 Dinner ┐ ┌ 🍎 Snack ┐ │
│                       [ 🍴 Log Food ]│
└──────────────────────────────────────┘
```

#### Elements

| # | Element | Component | Content / data | Interaction |
|---|---|---|---|---|
| 1 | Title + date | `TopAppBar` | **Calorie Counter** (headlineSmall Bold) + date | — |
| 2 | Top-bar add | `IconButton` `Add` 28 dp | — | Opens Log Food with meal = **Breakfast** |
| 3 | Summary card | `Card` 20 dp | — | — |
| 3a | Ring | `RingProgress` 150 dp / 16 dp, secondary | `totalCalories` (headlineMedium Bold secondary) + "kcal"; progress = eaten / goal | — |
| 3b | Rows (`CalRow`) | Label + value + unit | **Goal** `calorieGoal` (onSurface) · **Burned** `burnedCalories` (error) · divider · **Net** `netCalories` (error if > goal, else tertiary) | — |
| 4 | Macros | `SectionCard` "Macros Today" + 3 × `MacroRow` | Protein `/150g` (primary) · Carbs `/200g` (tertiary) · Fat `/65g` (error) | — |
| 5 | Meal sections ×4 | `SectionCard` | Title "☀️ Breakfast" / "🌤 Lunch" / "🌙 Dinner" / "🍎 Snack"; subtitle "N kcal"; trailing `TextButton` "+ Add" (16 sp) | Add → sheet with that meal preselected |
| 5a | Food row (`FoodRow`) | Row + divider | Name (bodyLarge Medium); "P:5g · C:27g · F:3g · 40g"; "150 kcal" (SemiBold) | `Close` ✕ → delete immediately, **no undo** |
| 5b | Empty meal | Text | "Nothing logged yet" | — |
| 6 | FAB | Extended, secondary | `Restaurant` + "Log Food" (18 sp) | Opens sheet with meal = Breakfast |

> ⚠️ **Macro targets are hard-coded** (150/200/65 g) and ignore the active diet plan. The **calorie goal comes from the profile TDEE**, not the active diet plan, so the Calories and Diet tabs can show different targets for the same day. "Burned" counts **workouts only**; calories from steps are left out.

#### Overlay — "Log Food" (`ModalBottomSheet`, 92% height)

```
 Log Food
 (Breakfast) (Lunch) (Dinner) (Snack)
 [ Type food name…                🔍 ]
 ┌──────────────────────────────────┐   ← dropdown (≤10 results)
 │ Chicken Breast (grilled…  165 kcal│
 │ P:31g C:0g F:3g /100g             │
 │ Chicken nuggets (Brand)   296 kcal│
 │ P:15g C:18g F:18g /100g  🌐 online│
 └──────────────────────────────────┘
 [ Serving size (g)            100 g ]
 Nutritional values (auto-filled · editable)
 [ Calories ] [ Protein g ]
 [ Carbs g  ] [ Fat g     ]
 ┌  🔥165   💪31.0g   🌾0.0g   🥑3.6g ┐  ← shown when kcal entered
 [  Cancel  ]  [  Log Food  ]
```

| Element | Details |
|---|---|
| Title | **Log Food** (headlineMedium Bold) |
| Meal selector | 4 × `FilterChip` (16 sp) in one row; preselected from where the sheet was opened |
| Search field | `OutlinedTextField` "Type food name…" (18 sp); trailing `Search` icon, or a 20 dp spinner while the API runs |
| Search logic | ≥ 2 chars → instant local results; ≥ 3 chars + **350 ms debounce** → Open Food Facts API; merged, de-duplicated, **max 10** |
| Dropdown row | Name (1 line); "P:xg C:xg F:xg /100g"; kcal (Bold secondary); "🌐 online" tag for API results |
| Dropdown container | `Card`, bottom corners 12 dp, elevation 6 dp, dividers @ 15% |
| Serving size | `OutlinedTextField` digits, suffix "g", default **100**. Changing it rescales all macros (× g/100) |
| Helper text | "Nutritional values (auto-filled · editable)" |
| Macro fields | Calories (digits), Protein g, Carbs g, Fat g (decimal); 2 × 2 grid |
| Preview card | primaryContainer @ 35%, 10 dp: 🔥 kcal · 💪 protein · 🌾 carbs · 🥑 fat |
| Actions | `OutlinedButton` Cancel · `Button` **Log Food** (does nothing if kcal is empty, with no error shown) |

**Missing states:** no results, offline / API error, recent and frequent foods, barcode scan (camera permission is declared but unused).

> ⚠️ Some local foods are defined per unit ("Egg (whole, 1 large)", "Whey Protein (1 scoop 30g)", "Milk (whole, 250ml)"). The UI still scales them as if values were per 100 g, so their numbers come out wrong.

---

### 5.6 Diet Tab

**File:** `ui/screens/DietScreen.kt` · **State:** `DietUiState`

```
┌──────────────────────────────────────┐
│ Diet Planner                      (+)│
├──────────────────────────────────────┤
│ ┌ Active Plan              (Fatloss)┐│
│ │ Summer Cut                         ││
│ │ ┌────┐┌────┐┌────┐┌────┐           ││
│ │ │1500││180g││120g││ 50g│ ← 4 StatCards
│ │ │kcal││Prot││Carb││Fat │           ││
│ │ └────┘└────┘└────┘└────┘           ││
│ │ Today's Progress                   ││
│ │ Calories  1240g / 1500g  ████░     ││  ← ⚠️ "g" unit on calories
│ │ Protein / Carbs / Fat bars         ││
│ └────────────────────────────────────┘│
│ ┌ 📉 On track! -260 kcal vs target ─┐ │
│ │    Keep it up! 💪                  │ │
│ └────────────────────────────────────┘ │
│ Your Plans                            │
│ ┌ Summer Cut  [Active]          🗑 ┐  │
│ │ 1500 kcal · P:180g · C:120g · F:50g │
│ ┌ Bulk           [Activate]     🗑 ┐  │
│                      [ 📝 Create Plan ]│
└──────────────────────────────────────┘
```

#### Elements

| # | Element | Component | Content / data | Interaction |
|---|---|---|---|---|
| 1 | Title | `TopAppBar` | **Diet Planner** | — |
| 2 | Top-bar add | `IconButton` `Add` "Create plan" | — | Opens sheet (same as FAB) |
| 3 | Active plan card | `SectionCard` "Active Plan" | Subtitle = plan name or "No active plan"; trailing `AssistChip` with raw goal key capitalized ("Fatloss", "Muscle", "Keto", "Maintain", "Custom") | Chip is not interactive |
| 3a | Targets | 4 × `StatCard` | Calories `kcal` (secondary) · Protein (primary) · Carbs (tertiary) · Fat (error) | — |
| 3b | Progress | "Today's Progress" + 4 × `MacroRow` | Calories / Protein / Carbs / Fat, current vs target | — |
| 4 | Status banner | `Card` 14 dp | `diff = todayCalories − target`: **> +100** → errorContainer + `TrendingUp` "You're N kcal over today's target"; **< −300** → primaryContainer + `TrendingDown` "You're N kcal under today's target"; otherwise surfaceVariant + `TrendingDown` "On track! ±N kcal vs target". Subline is always "Keep it up! 💪" | — |
| 5 | "Your Plans" | Text titleSmall SemiBold | — | — |
| 6 | Plan card (`PlanCard`) | `Card` 14 dp | Name; "Active" `AssistChip` if active; "1500 kcal · P:180g · C:120g · F:50g". Active = outlined border + primaryContainer @ 30% | `TextButton` "Activate" (inactive only) · 🗑 Delete (**no confirm**) |
| 7 | FAB | Extended, tertiary | `EditNote` + "Create Plan" | Opens sheet |

#### States

| State | Render |
|---|---|
| No active plan | `EmptyState` 🥗 "No plan active" / "Create a diet plan to track your macro targets" inside the Active Plan card; banner hidden |
| No plans | `EmptyState` 📋 "No plans yet" / "Tap + to create your first diet plan" |

> ⚠️ `MacroRow` always appends "g", so the Calories row reads "1240g / 1500g". Four `StatCard`s in one row are very cramped. "Keep it up! 💪" is shown even when the user is over target.

#### Overlay — "Create Diet Plan" (`ModalBottomSheet`)

| Element | Details |
|---|---|
| Title | **Create Diet Plan** |
| Plan name | `OutlinedTextField`; default "My Plan" |
| Template | 5 × `FilterChip` in a **non-scrolling row**: Custom / Fat Loss / Muscle / Keto / Maintain (likely overflows). Picking one fills the fields below (§9.2) |
| Daily calories | Digits, default 2000 |
| Protein (g) / Carbs (g) / Fat (g) | 3 fields in one row, defaults 150 / 200 / 65 |
| Actions | Cancel · **Save & Activate** (a new plan always becomes active) |

There is no macro-percentage view, no check that macros add up to the calories, no suggestion based on TDEE, and no way to edit an existing plan.

---

### 5.7 Wellbeing Tab

**File:** `ui/screens/WellbeingScreen.kt` · **State:** `WellbeingUiState` · No FAB.

```
┌──────────────────────────────────────┐
│ Wellbeing                            │
├──────────────────────────────────────┤
│ ┌ Sleep                     [+ Log]┐ │
│ │ 7h 30m last night                │ │
│ │ [Last Night][7-Day Avg][Entries] │ │
│ │ Last 7 nights                    │ │
│ │ ▅ ▆ ▇ ▅ ▃ ▆ ▇  --8h--            │ │
│ └──────────────────────────────────┘ │
│ ┌ Mood Check-in ───────────────────┐ │
│ │ [😞][😕][😐][😊][😁]             │ │
│ │ Stress level: 5/10               │ │
│ │ ●━━━━━━━━○──────────             │ │
│ │ [ 💾 Save Check-in ]             │ │
│ └──────────────────────────────────┘ │
│ ┌ Hydration               [+ Glass]┐ │
│ │ 3 / 8 glasses                    │ │
│ │ ██████░░░░░░░░░                  │ │
│ │ [💧][💧][💧][  ][  ][  ][  ][  ] │ │
│ └──────────────────────────────────┘ │
│ ┌ Meditation ──────────────────────┐ │
│ │ Today: 10 min                    │ │
│ │ [ 5m ][ 10m ][ 15m ][ 20m ]      │ │
│ └──────────────────────────────────┘ │
│ ┌ Weekly Insights ─────────────────┐ │
│ │ 😊 Avg mood            3.8/5     │ │
│ │ 😤 Avg stress          4.2/10    │ │
│ │ 💧 Avg water           5.1 glasses/day │
│ │ 🧘 Meditation          35 min this week│
│ │ 🌙 Sleep entries       6 nights logged │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

#### Elements

| Section | Element | Details | Interaction |
|---|---|---|---|
| Top bar | Title | **Wellbeing** | — |
| **Sleep** | `SectionCard` | Subtitle "7h 30m last night" or "No sleep logged today" | Trailing `Button` "+ Log" (**32 dp tall**) → Log Sleep sheet |
| | 3 × `StatCard` | "Last Night" `Nh` (tertiary) · "7-Day Avg" `N.Nh` (primary; actually an average of up to 14 entries) · "Entries" N "logged" (secondary). "—" when empty | — |
| | Chart | "Last 7 nights" + `BarChart` 80 dp, max 10, goal line at **8 h**, tertiary. Values are **whole hours** (7h50 shows as 7) | — |
| **Mood Check-in** | `MoodSelector` | 5 emoji tiles, 52 dp, 12 dp radius; selected = primaryContainer | Tap selects |
| | Stress | "Stress level: N/10" + `Slider` 1–10 (integer steps) | Drag |
| | Save | Full-width `Button` with `Save` icon "Save Check-in" | `vm.logMood()`. ⚠️ **No snackbar or confirmation**, and nothing on screen shows that today's check-in was saved |
| **Hydration** | `SectionCard` | Subtitle "N / 8 glasses" | Trailing `FilledTonalButton` "+ Glass" (32 dp) → +1, max 12 |
| | Progress | `AnimatedProgressBar` 10 dp, primary | — |
| | Glass tiles | 8 square tiles with 💧; filled = primaryContainer | **Not tappable**, and there is no way to remove a glass |
| **Meditation** | Idle | Subtitle "Today: N min"; 4 × `OutlinedButton` 5m / 10m / 15m / 20m | Starts countdown |
| | Running | Countdown "MM:SS" (displayMedium Light secondary) + `LinearProgressIndicator` + `OutlinedButton` "Stop" | Stop **discards** progress (only completed sessions are logged). Switching tabs kills the timer |
| **Weekly Insights** | `SectionCard` + 5 × `InsightRow` | 😊 Avg mood x.x/5 · 😤 Avg stress x.x/10 · 💧 Avg water x.x glasses/day · 🧘 Meditation N min this week · 🌙 Sleep entries N nights logged. Values show "—" when no data | — |

> ⚠️ The README calls these "AI-generated insights"; they are plain averages. The water goal is hard-coded to 8, even though `UserProfileEntity.dailyWaterGoal` exists.

#### Overlay — "Log Sleep" (`ModalBottomSheet`)

| Element | Details |
|---|---|
| Title | **Log Sleep** |
| Bedtime | Two `OutlinedTextField`s "HH" / "MM" (digits, max 2), default 23:00 |
| Wake up | Two `OutlinedTextField`s "HH" / "MM", default 07:00 |
| Quality | 5 × `FilterChip`: "😴 1", "😪 2", "😐 3", "😊 4" (default), "😁 5" |
| Notes (optional) | `OutlinedTextField` max 2 lines (saved but **never displayed**) |
| Actions | Cancel · **Save Sleep** (duration auto-calculated, wraps past midnight; always saved under **today's** date) |

> ⚠️ Time entry uses 4 raw text fields instead of a time picker. Sleep quality is saved but never shown anywhere.

---

## 6. Reusable Component Library

All in `ui/components/Components.kt` unless noted.

| Component | Signature (key params) | Visual | Used in |
|---|---|---|---|
| `RingProgress` | `progress 0–1, size=180dp, strokeWidth=18dp, trackColor, progressColor, content` | Canvas arc from 12 o'clock, round caps, 1 s animation | Calories |
| `AnimatedProgressBar` | `progress, color, trackColor, height=8dp` | Pill-shaped bar, 800 ms | MacroRow, Hydration |
| `StatCard` | `label, value, unit, color` | 12 dp tonal surface; label (labelSmall) / value (titleLarge Bold, colored) / unit | Workout, Diet, Wellbeing |
| `SectionCard` | `title, subtitle, trailing, content` | 16 dp `Card`, 16 dp padding; header row + 12 dp gap + content | Calories, Diet, Wellbeing |
| `MacroRow` | `label, current, target, color` | Label left, "Xg / Yg" right, progress bar below | Calories, Diet |
| `BarChart` | `values: List<label,value>, maxValue, highlightColor, goalValue` | Canvas bars at 70% width, full-height track behind each bar, horizontal goal line, 9 sp labels | Steps, Wellbeing |
| `EmptyState` | `emoji, title, subtitle` | Centered 44 sp emoji + title + subtitle, 32 dp padding | All tabs |
| `StreakBadge` | `days` | Pill (20 dp) tertiaryContainer: "🔥 N day streak" | Steps |
| `MoodSelector` | `selected, onSelect` | 5 × 52 dp emoji tiles | Wellbeing |

**Screen-private components** (good candidates to move into the shared library):

| Component | File | Description |
|---|---|---|
| `FeatureRow` | Onboarding | Circle icon + title + subtitle |
| `WelcomeStep`, `ProfileStep`, `GoalStep`, `StepGoalStep` | Onboarding | Wizard pages |
| `PermissionItem` | Permissions | Icon tile + text + granted check |
| `QuickStat` | Steps | Icon + big value + unit |
| `SectionHeader` | Steps | titleLarge ExtraBold + subtitle |
| `ModernStepRow` | Steps | Mini-ring history row |
| `AddStepsDialog` | Steps | Dialog |
| `WorkoutCard`, `TemplateCard`, `AddWorkoutSheet` | Workout | — |
| `CalRow`, `FoodRow`, `AddFoodSheet` | Calories | — |
| `PlanCard`, `CreatePlanSheet` | Diet | — |
| `InsightRow`, `LogSleepSheet` | Wellbeing | — |

**Duplication to consolidate:** Steps draws its rings with `CircularProgressIndicator` while Calories uses `RingProgress`. There are 3 different "icon tile" implementations, 2 different section-header styles (`SectionHeader` vs the `SectionCard` title), and two copies of the date formatter (`formatDate` / `formatDateLocal`).

---

## 7. Data Available to the UI

These are all the fields designers can show. Fields marked **🆕 unused** are stored or available but **not shown anywhere today**, which makes them opportunities.

### 7.1 UI state (ViewModel)

| State | Fields |
|---|---|
| `StepsUiState` | `todaySteps`, `todayCaloriesBurned`, `todayDistanceKm`, `todayActiveMinutes`, `stepGoal`, `streakDays`, `weekHistory` (7 of 30 loaded days), `isSyncing`, `healthConnectAvailable` |
| `WorkoutUiState` | `todayWorkouts`, `allWorkouts`, `weeklyCount`, `totalMinutes` |
| `CalorieUiState` | `todayFoods`, `totalCalories`, `calorieGoal`, `burnedCalories`, `netCalories`, `proteinG`, `carbsG`, `fatG`, `byMeal` |
| `DietUiState` | `activePlan`, `allPlans`, `todayCalories`, `todayProtein`, `todayCarbs`, `todayFat` |
| `WellbeingUiState` | `todaySleep`, `recentSleep` (14), `avgSleepHours`, `todayWellbeing`, `weekWellbeing` (7), `meditationMinutes` |
| `ProfileUiState` | `profile`, **`bmi` 🆕 unused**, **`tdee` 🆕 unused**, `onboardingComplete` |

### 7.2 Database entities (Room)

| Entity | Fields (🆕 = never displayed) |
|---|---|
| `DailyStepsEntity` | date, steps, caloriesBurned, distanceMeters, activeMinutes, syncedWithFit 🆕 |
| `WorkoutEntity` | id, date, name, category, durationMinutes, caloriesBurned, notes, completed, templateId 🆕 |
| `ExerciseEntity` 🆕 (whole entity never shown) | workoutId, name, sets, repsPerSet, weightKg, durationSeconds, restSeconds, notes |
| `FoodLogEntity` | date, mealType, foodName, calories, proteinG, carbsG, fatG, servingGrams, barcode 🆕 |
| `DietPlanEntity` | name, goal, dailyCalorieTarget, proteinTargetG, carbsTargetG, fatTargetG, isActive, createdAt 🆕 |
| `SleepLogEntity` | date, bedtime H:M 🆕, wake H:M 🆕, durationMinutes, qualityRating 🆕, notes 🆕 |
| `WellbeingLogEntity` | date, moodScore, stressLevel, waterGlasses, meditationMinutes, notes 🆕 |
| `UserProfileEntity` | name 🆕, dateOfBirth 🆕, sex 🆕, heightCm 🆕, weightKg 🆕, dailyStepGoal, dailyCalorieGoal, dailyWaterGoal 🆕, weightGoal 🆕, activityLevel 🆕 |

### 7.3 Health Connect capabilities

`HealthConnectManager` already contains these functions, but no UI uses them:

- `readAvgHeartRateForDate()` → **average heart rate** 🆕
- `readSleepForDate()` → sleep from wearables 🆕
- `readActiveCaloriesForDate()` → active calories 🆕
- `getDailySummary()` → combined daily summary 🆕
- Weight read/write permissions declared → **weight tracking** 🆕

---

## 8. Business Rules & Calculations Shown in the UI

| Value | Formula / rule | Shown on |
|---|---|---|
| Today's steps | Hardware `TYPE_STEP_COUNTER` minus midnight offset; saved to Room on each sensor event | Steps, notification |
| Calories from steps | `steps × 0.04` kcal | Steps |
| Distance | `steps × 0.762 m` (fixed stride, ignores height) | Steps |
| Active minutes | `steps ÷ 100` | Steps |
| Streak | Consecutive days ending **today** with steps ≥ goal (max 61). Today counts only after the goal is reached | Steps badge |
| Step reminder | At ~19:00, if steps < 80% of goal | Notification |
| TDEE / calorie goal | BMR (**Harris-Benedict revised**: M = 88.36 + 13.4w + 4.8h − 5.7a; F = 447.6 + 9.2w + 3.1h − 4.3a) × activity (1.2 / 1.375 / 1.55 / 1.725 / 1.9), then −500 (lose), +300 (gain); minimum 1,200 kcal. ⚠️ The README says Mifflin-St Jeor | Calories "Goal" |
| BMI | `weight / (height m)²` | **Not shown** |
| Burned (Calories tab) | Sum of today's workout kcal only | Calories |
| Net | `eaten − burned`; red if > goal | Calories |
| Diet status banner | diff > +100 → over; diff < −300 → under; else on track | Diet |
| Food scaling | All macros × (serving g / 100) | Log Food |
| Sleep duration | wake − bed (+1440 min if negative) | Wellbeing |
| Water | +1 per tap, max 12, goal 8 | Wellbeing |
| Water reminder | Every 2 h if glasses < 4 | Notification |
| Meditation | Logged only when the countdown finishes (whole minutes) | Wellbeing |
| Weekly workouts | Workouts dated within the last 7 days | Workout |

---

## 9. Static Content

### 9.1 Workout templates

| Name | Category | Exercises |
|---|---|---|
| Push Day A | Strength | Bench Press 3×8 · Overhead Press 3×10 · Tricep Dips 3×12 · Lateral Raises 3×15 |
| Pull Day A | Strength | Deadlift 3×5 · Barbell Row 3×8 · Bicep Curls 3×12 · Face Pulls 3×15 |
| Leg Day | Strength | Squat 4×6 · Leg Press 3×10 · Romanian Deadlift 3×10 · Calf Raises 4×15 |
| HIIT Blast | HIIT | Burpees 3×12 · Jump Rope 3×60s · Mountain Climbers 3×20 · Box Jumps 3×10 |
| Morning Yoga | Yoga | Sun Salutation · Warrior I & II · Downward Dog · Child's Pose |
| 5K Run | Cardio | 5 min warm-up walk · 5 km steady run · 5 km steady run ⚠️ *(duplicated line)* |

**Workout categories:** Strength, Cardio, HIIT, Yoga, Flexibility, Custom (the UI exposes only the first 4).

### 9.2 Diet plan templates

| Key | Label | kcal | Protein g | Carbs g | Fat g |
|---|---|---|---|---|---|
| `custom` | Custom | (keeps current values; defaults 2000 / 150 / 200 / 65) | | | |
| `fatloss` | Fat Loss | 1500 | 180 | 120 | 50 |
| `muscle` | Muscle | 2800 | 220 | 280 | 80 |
| `maintain` | Maintain | 2200 | 160 | 230 | 70 |
| `keto` | Keto | 1800 | 160 | 25 | 140 |

### 9.3 Local food database

About **97 items** in `FoodRepository.kt`, grouped by: Proteins · Dairy · Grains & Carbs · Fruits · Vegetables · Legumes · Nuts & Seeds · Oils & Fats · Snacks & Processed · Drinks · Fast Food / Restaurant. Each item has name (with portion in the name), kcal, protein, carbs, fat. **There are no images, no category metadata in the UI, and no browse view.** Online results come from Open Food Facts (per 100 g).

### 9.4 Scales and presets

| Scale | Values |
|---|---|
| Mood (1–5) | 😞 😕 😐 😊 😁 |
| Sleep quality (1–5) | 😴 😪 😐 😊 😁 |
| Stress | 1–10 slider |
| Activity level | sedentary · light · moderate · active · (very_active, not in the UI) |
| Weight goal | lose · maintain · gain |
| Sex | Male · Female |
| Meals | Breakfast · Lunch · Dinner · Snack |
| Meditation presets | 5 · 10 · 15 · 20 min |
| Step goal presets | 5,000 · 7,500 · 10,000 · 15,000 (slider 3,000–25,000) |

---

## 10. Feedback, Messages & Notifications

### 10.1 Snackbar copy (all of it)

| Trigger | Message |
|---|---|
| Profile saved *(function exists, no UI calls it)* | `Profile saved` |
| Manual steps added | `Added 2,500 steps` |
| Sync with result | `Synced N steps from Google Fit ✓` ⚠️ *(says Google Fit; the app uses Health Connect)* |
| Sync, nothing new | `Nothing new to sync` |
| Workout saved | `<Workout name> saved! 💪` |
| Workout completed | `Workout synced to Health Connect ✓` |
| Workout deleted | `Workout deleted` |
| Food logged | `<Food name> logged ✓` |
| Food deleted | `Food removed` |
| Diet plan saved | `<Plan name> activated ✓` |
| Plan activated | `Plan activated` |
| Plan deleted | `Plan deleted` |
| Sleep logged | `Sleep logged 🌙` |
| Water added | `💧 Water logged!` |
| Meditation completed | `🧘 N min meditation logged!` |
| Mood saved | *(none)* ⚠️ |

None of the snackbars have an action (e.g. **Undo** after a delete).

### 10.2 Empty states

| Where | Emoji | Title | Subtitle |
|---|---|---|---|
| Steps history | 👣 | No steps logged | Start walking to see your history here! |
| Workout · Today | 💪 | No workouts today | Tap + to log your first session |
| Workout · History | 📋 | No workouts yet | Start logging to see your history |
| Diet · Active plan | 🥗 | No plan active | Create a diet plan to track your macro targets |
| Diet · Plans | 📋 | No plans yet | Tap + to create your first diet plan |
| Calories · each meal | — | Nothing logged yet | — |
| Wellbeing · Sleep | — | No sleep logged today (subtitle) | — |

### 10.3 Missing feedback states (need design)

- Loading / skeleton states (none exist; data appears as it streams in from Room).
- Error states: API failure, Health Connect errors, sensor not available on the device.
- Offline indicator for food search.
- Form validation errors (none in any form).
- Delete confirmation or undo.
- Goal-reached celebration (steps, water, calories).

---

## 11. UX Audit — Current Issues

Severity: 🔴 high · 🟠 medium · 🟡 low.

### 11.1 Consistency

| # | Sev | Issue | Where |
|---|---|---|---|
| C1 | 🟠 | Top bars differ per tab: Steps headlineMedium ExtraBold + date; Calories headlineSmall Bold + date; Workout / Diet / Wellbeing default title, no date | All tabs |
| C2 | 🟠 | Type scale differs. Steps and Calories use 16–18 sp overrides on labels and buttons; other tabs use defaults | Steps, Calories |
| C3 | 🟠 | FAB colors vary arbitrarily (primary / tertiary / secondary / tertiary / none) | All tabs |
| C4 | 🟠 | Duplicate "add" affordances (top-bar + and FAB do the same thing) | Calories, Diet |
| C5 | 🟡 | Corner radii range from 8 to 32 dp with no scale | Global |
| C6 | 🟡 | Two ring implementations, three icon-tile styles, two section-header styles | Components |
| C7 | 🟠 | Semantic colors change meaning by screen (`tertiary` = carbs, active minutes, goal-met, sleep, Yoga; `error` = fat, burned kcal, HIIT, goal line) | Global |
| C8 | 🟡 | Mixed emoji and Material icons for the same concepts (🔥 vs `LocalFireDepartment`) | Global |
| C9 | 🟡 | Logo "F" tile does not match the launcher person-silhouette icon | Brand |
| C10 | 🟡 | Steps list background is `surface`; other tabs use `background` | Steps |

### 11.2 Usability

| # | Sev | Issue | Where |
|---|---|---|---|
| U1 | 🔴 | No Profile / Settings screen. The user can't change step goal, weight, calorie goal, water goal, theme, or reminders, even though onboarding copy says "change this anytime in your profile" | Global |
| U2 | 🔴 | Calorie goal (profile TDEE) and macro targets (hard-coded) on Calories conflict with the active Diet plan | Calories vs Diet |
| U3 | 🔴 | Destructive actions (delete workout, food, plan) happen instantly with no confirm or undo | Workout, Calories, Diet |
| U4 | 🟠 | Free-text date (`YYYY-MM-DD`) and time (HH / MM fields) entry; no pickers | Log Steps, Log Sleep |
| U5 | 🟠 | Tab state is lost on switch: scroll position, Workout sub-tab, running meditation timer | Nav |
| U6 | 🟠 | Mood check-in gives no feedback, and the saved state isn't reflected | Wellbeing |
| U7 | 🟠 | Can't remove a water glass; glass tiles aren't tappable | Wellbeing |
| U8 | 🟠 | Workout exercises capture only a name (no sets / reps / weight); no workout detail; can't choose a date; "Complete" button effectively unreachable | Workout |
| U9 | 🟠 | Categories "Flexibility" and "Custom" can't be selected | Workout |
| U10 | 🟠 | Per-unit foods scaled as per-100 g, so macros come out wrong | Log Food |
| U11 | 🟠 | Default meal is always Breakfast, regardless of time of day | Calories |
| U12 | 🟠 | Stopping meditation early discards the session | Wellbeing |
| U13 | 🟠 | Health Connect permissions are never requested; the sync button silently disappears | Permissions, Steps |
| U14 | 🟡 | History rows have a click ripple but no action | Steps |
| U15 | 🟡 | Permission screen re-evaluates on every launch | Permissions |
| U16 | 🟡 | No onboarding validation; silent defaults (name "Friend", 170 cm, 70 kg…) | Onboarding |
| U17 | 🟡 | Diet plans can't be edited; no macro % or calorie-consistency check | Diet |
| U18 | 🟡 | Back button exits the app from any tab | Nav |

### 11.3 Accessibility

| # | Sev | Issue |
|---|---|---|
| A1 | 🟠 | Touch targets below 48 dp: 32 dp "+ Log" and "+ Glass" buttons, 32 dp workout icon buttons, 22 dp category chip, 36 dp delete buttons |
| A2 | 🟠 | 9 sp chart labels |
| A3 | 🟠 | Emoji-only controls (mood, sleep quality, water) have no content descriptions; most icons pass `contentDescription = null` |
| A4 | 🟠 | Color is the only goal-met signal in chart bars; the 40% alpha red goal line has low contrast |
| A5 | 🟡 | Rows of 4–5 chips in non-scrolling `Row`s will clip at large font scale or on narrow screens (activity level, meal chips at 16 sp, diet templates, workout categories) |
| A6 | 🟡 | No support for large-screen or landscape layouts |
| A7 | 🟡 | Hard-coded strings; no localisation or RTL review (despite `supportsRtl=true`) |

### 11.4 Copy and accuracy

| # | Issue |
|---|---|
| K1 | "Synced … from Google Fit" should say Health Connect |
| K2 | Permission text promises "weekly summaries", which don't exist |
| K3 | "7-Day Avg" sleep actually averages up to 14 entries |
| K4 | Diet "Calories 1240g / 1500g" uses the wrong unit |
| K5 | Diet goal chip shows raw keys ("Fatloss") |
| K6 | "Keep it up! 💪" is shown when over target |
| K7 | 5K Run template repeats "5 km steady run" |
| K8 | README: "Mifflin-St Jeor" (code uses Harris-Benedict); "1,000–25,000" slider (code is 3,000); "AI-generated insights" (simple averages); camera permission requested (it isn't) |

---

## 12. Redesign Opportunities

These suggestions are grounded in data the app already has, so most need **UI work only**.

1. **Add a "Today" / Home dashboard.** One glanceable screen with steps ring, calories left, water, sleep, and today's workout. The ViewModel already exposes all of it. This could replace or join the 5 tabs (e.g. Home · Activity · Nutrition · Wellbeing · Profile).
2. **Profile & Settings screen.** Show name, avatar, BMI, TDEE (already computed), weight, and goals. Let users edit step / calorie / water goals, theme (dynamic color on/off), units, reminders, Health Connect connection, and data reset.
3. **Merge Calories + Diet into "Nutrition".** The active plan should drive the calorie goal and macro targets, so there is one source of truth.
4. **Unified metric color system.** Give each metric a fixed hue (e.g. Steps=blue, Calories=orange, Protein / Carbs / Fat = 3 distinct non-error hues, Sleep=indigo, Water=cyan, Mood=purple). Use them consistently, independent of dynamic color, and use `error` only for errors.
5. **Workout logger 2.0.** Exercise rows with sets × reps × weight steppers, workout detail and edit, date picker, category icons, all 6 categories, and templates that prefill structured sets.
6. **Native pickers.** `DatePicker` for steps, `TimePicker` for sleep, steppers for water (− / +).
7. **History & trends.** 30-day step data is already loaded, and 14 days of sleep. Add week/month toggles, calendar heatmap, weight trend (Health Connect), and heart rate (Health Connect).
8. **Safer actions.** Swipe-to-delete with Undo snackbar, confirm dialogs for plans, edit for food entries.
9. **Food logging UX.** Recent and frequent foods, serving units (piece / scoop / ml / g), no-results and offline states, barcode scanner (camera permission already declared), meal auto-selected by time of day.
10. **Wellbeing.** Show sleep quality and bed/wake times on the chart, tappable glass tiles, save and resume meditation (partial sessions), confirmation and state after mood is saved, and a mood trend line.
11. **Celebrations & motivation.** Goal-reached animation, streak milestones, weekly summary card (the permission copy already promises one).
12. **Permission flow.** Add a Health Connect connect step, "permanently denied → Open settings" state, and persist completion.
13. **Proper design system.** Typography scale (consider a branded display font for numbers), shape scale (e.g. 8 / 12 / 16 / 24 / 28), spacing scale (4-pt grid), and a shared component set (MetricRing, MetricTile, SectionHeader, ListRow, IconTile, EmptyState, ChipGroup with FlowRow).
14. **Brand identity.** Unify the logo with the launcher icon, and define brand colors that also show when dynamic color is off.

---

## 13. Designer Deliverables Checklist

Every surface and state that needs a design, in light and dark mode.

**Foundations**
- [ ] Color tokens (light / dark / brand) + metric palette
- [ ] Type scale (incl. large-number display style)
- [ ] Shape, spacing, elevation scales
- [ ] Icon set (decide emoji vs icons)
- [ ] App icon (adaptive, monochrome) + in-app logo
- [ ] Motion guidelines (ring fill, chart grow, page transitions)

**Pre-app**
- [ ] Onboarding 1 – Welcome
- [ ] Onboarding 2 – Profile (+ validation errors)
- [ ] Onboarding 3 – Goal & activity level (5 levels)
- [ ] Onboarding 4 – Step goal
- [ ] Permissions – default / partially granted / all granted / permanently denied / Health Connect connect

**Shell**
- [ ] Bottom navigation (selected / unselected; badge?)
- [ ] Top app bar pattern (large / small, with date, actions)
- [ ] FAB pattern
- [ ] Snackbar (+ Undo variant)

**Steps**
- [ ] Main: 0 steps / in progress / goal reached / over goal
- [ ] Syncing / Health Connect unavailable / sensor unavailable
- [ ] Weekly chart + history list + streak badge
- [ ] Empty history
- [ ] Log steps dialog (with date picker)

**Workout**
- [ ] Today (empty / with items) · History (empty / with items) · Templates
- [ ] Workout card (per category, completed / planned)
- [ ] New / Edit workout sheet (structured exercises)
- [ ] Workout detail *(new)*

**Calories / Nutrition**
- [ ] Summary (under / at / over goal)
- [ ] Macros (using active plan targets)
- [ ] Meal sections (empty / filled)
- [ ] Log food sheet: idle / typing / local results / loading online / no results / offline / food selected
- [ ] Delete / edit entry

**Diet**
- [ ] Active plan (with / without) + status banner (over / under / on track)
- [ ] Plan list (active / inactive)
- [ ] Create / edit plan sheet (templates, macro %)

**Wellbeing**
- [ ] Sleep card (no data / data) + log sleep sheet (time pickers, quality)
- [ ] Mood check-in (unsaved / saved today)
- [ ] Hydration (0 / partial / goal met / max)
- [ ] Meditation (idle / running / paused / completed)
- [ ] Weekly insights (no data / data)

**New surfaces (recommended)**
- [ ] Home / Today dashboard
- [ ] Profile & Settings
- [ ] Trends / history (week / month)

**System**
- [ ] Foreground step notification
- [ ] Step and water reminder notifications (with actions)
- [ ] Home-screen widget *(optional)*

---

## 14. Appendix — Source File Map

| Area | File | Lines |
|---|---|---|
| Activity, nav, shell | `app/src/main/java/com/fitlife/app/MainActivity.kt` | 123 |
| Theme / colors | `ui/theme/Theme.kt` | 81 |
| Shared components | `ui/components/Components.kt` | 238 |
| Onboarding | `ui/screens/OnboardingScreen.kt` | 271 |
| Permissions | `ui/screens/PermissionScreen.kt` | 160 |
| Steps | `ui/screens/StepsScreen.kt` | 311 |
| Workout | `ui/screens/WorkoutScreen.kt` | 292 |
| Calories | `ui/screens/CaloriesScreen.kt` | 371 |
| Diet | `ui/screens/DietScreen.kt` | 270 |
| Wellbeing | `ui/screens/WellbeingScreen.kt` | 318 |
| ViewModel & UI state | `ui/viewmodel/FitnessViewModel.kt` | 360 |
| Database (entities / DAOs) | `data/local/Database.kt` | 291 |
| Food DB + search API | `data/food/FoodRepository.kt` | 221 |
| Health Connect | `data/healthconnect/HealthConnectManager.kt` | 208 |
| Repository | `data/repository/FitnessRepository.kt` | 176 |
| Step sensor service | `service/StepCounterService.kt` | 163 |
| Background workers | `worker/Workers.kt` | 126 |
| Resources | `res/values/{colors,strings,themes}.xml`, `res/drawable/ic_launcher_foreground.xml` | — |

*All Kotlin paths are relative to `FitLife_Android/app/src/main/java/com/fitlife/app/`.*
