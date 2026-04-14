# Tripoo — Group trip planner

Android app for planning group trips: shared trip hub, expenses, tasks, and participants, backed by Firebase (Auth, Firestore, Storage). Local trip alerts use Firestore fan-out documents plus WorkManager (no FCM server or Cloud Functions required for notifications).

## Features

- **Authentication**: Email and password sign-in and sign-up (Firebase Auth). Google Sign-In UI is present in layouts but currently hidden in `AuthFragment`.
- **Trip dashboard**: List of trips tied to the signed-in user; create a trip, join with a code, open a trip, or manage profile.
- **Trip home**: Countdown / in-trip / post-trip messaging, trip summary, pull-to-refresh, and an in-trip **banner ad** (AdMob).
- **Join codes**: Codes are generated as `TRP-` plus three random characters from `A–Z` and `0–9` (see `TripRepository`).
- **Expenses**: Add and list expenses with categories, split among members, timestamps, and a **settled** flag; trip organiser or co-organisers (`TripMember.isAdmin`) can mark expenses settled in the UI.
- **Tasks**: Categories (e.g. general, booking, packing), assignee, due date, priority, notes; deadline scheduling hooks into `TripDeadlineWorker` for local notifications.
- **Groups / participants**: Member list for the active trip (navigation graph uses `GroupsFragment` with label “Participants”).
- **Profile**: Display name, photo (camera / storage), language and currency preferences, trip list, spending summary, sign-out.
- **Real-time data**: Firestore snapshot listeners in repositories; Kotlin **Flow** and coroutines in newer code paths alongside **LiveData** in older ViewModels.
- **Local notifications**: `fanoutNotifications` subcollection per trip, `FanoutTripNotificationListener`, and notification tap handling in `MainActivity` / splash.
- **Ads**: AdMob initialized in `TripooApplication`; banner placements and optional exit interstitial (`TripExitInterstitialHelper`).

## Tech stack

| Item | Value |
|------|--------|
| **Languages** | Kotlin and Java (mixed module) |
| **Package / applicationId** | `com.manikandan.tripoo` |
| **Min SDK** | 24 |
| **Target / compile SDK** | 36 |
| **Java / Kotlin JVM** | 17 |
| **Version** | `versionName` **1.3.2** (`versionCode` **8**) — see `app/build.gradle.kts` |
| **Android Gradle Plugin** | 8.9.1 (`gradle/libs.versions.toml`) |
| **Kotlin** | 1.9.0 |
| **Gradle wrapper** | 8.11.1 (`gradle/wrapper/gradle-wrapper.properties`) |
| **UI** | Material Design 3, View binding, Navigation Component (Kotlin Safe Args) |
| **Async** | Kotlin coroutines, AndroidX Lifecycle (ViewModel / LiveData / runtime) |
| **Backend** | Firebase Auth, Firestore, Storage; Firebase BOM 33.7.0 |
| **Other** | Glide 4.14.2, WorkManager 2.9.1, Google Play services Ads 23.6.0 |

## Project structure

Source root: `app/src/main/java/com/manikandan/tripoo/`.

```
com/manikandan/tripoo/
├── MainActivity.kt              # Nav host, edge-to-edge, notification permission, deep link from notifications
├── TripooApplication.kt         # AdMob init, notification channel, auth listener → listeners / workers
├── ads/                         # e.g. TripExitInterstitialHelper
├── data/
│   ├── model/                   # Trip, TripMember, User, Expense, Task, TripWithMeta, …
│   └── repository/              # Firestore access (Trip, User, Expense, Task, …)
├── notifications/               # Fan-out publisher/listener, workers, prefs, constants
├── ui/
│   ├── auth/                    # Auth, login, sign-up
│   ├── splash/
│   ├── dashboard/               # Trip list / dashboard
│   ├── home/                    # Trip home shell, create/join (Java fragments used by nav graph)
│   ├── expenses/
│   ├── tasks/
│   ├── groups/                  # Participants for active trip
│   └── profile/
├── view/                        # Custom views (e.g. TripCountdownView)
├── viewmodel/                   # Java/Kotlin ViewModels
└── utils/                       # Formatting, constants, trip code utilities, etc.
```

Navigation is defined in `app/src/main/res/navigation/nav_graph.xml` (start: `splashFragment`). **Release builds** expect a `key.properties` file at the repo root and a keystore path referenced there — see `app/build.gradle.kts` `signingConfigs`.

## Setup

### Prerequisites

- Android Studio (recent stable; Ladybug / Narwhal-era or newer recommended)
- JDK 17
- A Firebase project

### Firebase

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an **Android** app with package name **`com.manikandan.tripoo`** (must match `applicationId`).
3. Download **`google-services.json`** and place it in **`app/`**.  
   - The real file is **gitignored** (see `.gitignore`).  
   - You can start from `app/google-services.json.example` and replace placeholders; ensure `package_name` is **`com.manikandan.tripoo`** (the example file may still show a placeholder package).
4. Enable **Authentication** → Email/Password (and Google later if you re-enable the buttons in `AuthFragment`).
5. Create **Firestore** (start in locked mode, then deploy rules).
6. Enable **Storage** if you use profile / trip images.
7. For any Google Sign-In you enable later, add your app’s **SHA-1** / **SHA-256** in Firebase and use a valid Web client ID in `strings.xml` where needed.

Deploy the rules in **`firestore.rules`** to the Firestore **Rules** tab in the console.

### AdMob

The manifest references `@string/admob_app_id`. Replace ad unit strings in `app/src/main/res/values/strings.xml` with your own AdMob app and unit IDs for production.

### Build and run

```bash
git clone <repository-url>
cd Tripoo
```

On Windows:

```bat
gradlew.bat assembleDebug
```

On macOS / Linux:

```bash
./gradlew assembleDebug
```

Open the project in Android Studio, sync Gradle, run on an emulator or device (API 24+).

## Architecture notes

- **Single activity**: `MainActivity` hosts `NavHostFragment`; fragments swap via the navigation graph.
- **MVVM**: Fragments / binding + ViewModels; repositories encapsulate Firestore (and related side effects such as fan-out notification writes).
- **Trip context**: After opening a trip, the custom bottom bar on **Home** switches between **Home**, **Expenses**, **Tasks**, and **Groups** (labels as in `fragment_home.xml`).

## Firestore shape (high level)

Collections and fields follow the Kotlin/Java models (e.g. `Trip`, `User`, `Expense`, `Task`, `TripMember`). Typical layout:

- **`users/{uid}`** — profile fields including `tripIds`, optional `lastActiveTripId`, preferences, avatar hints.
- **`trips/{tripId}`** — trip document: e.g. `name`, `destination`, `description`, `startDate`, `endDate`, `budget`, `adminId`, `joinCode`, `memberIds`, `status` (`upcoming` / `active` / `past`).
- **`trips/{tripId}/members/{userId}`** — `TripMember` (including `isAdmin` for co-organisers).
- **`trips/{tripId}/expenses/{expenseId}`** — `Expense` (`splitWith`, `paidBy`, `category`, `settled`, …).
- **`trips/{tripId}/tasks/{taskId}`** — `Task` (`category`, `assignedTo`, `completed`, `dueDate`, `priority`, `notes`, `deadlineNotified`, …).
- **`trips/{tripId}/fanoutNotifications/{id}`** — short-lived notification payloads for clients; rules allow members to read/create/delete (no updates).

Authoritative access control is in **`firestore.rules`** (e.g. authenticated reads on `users` for member display, join-by-code updates to `memberIds`, member-gated subcollections). **Do not rely on the app alone for security** — rules must match your data model.

## Troubleshooting

- **Missing `google-services.json`**: Gradle will fail until the file exists under `app/` with the correct `package_name`.
- **Auth / Dynamic Links**: Ensure Firebase Android app registration matches **`com.manikandan.tripoo`** and SHA keys are registered if using Google providers.
- **Firestore permission errors**: Publish the rules from `firestore.rules` and confirm the user is signed in and appears in `memberIds` for that trip where required.
- **Release signing**: Without `key.properties` and a valid keystore, release signing config may be incomplete; debug builds do not use that block the same way.

## Contributing

Fork, branch, change, and open a pull request with a short description of behaviour and any Firebase or config steps reviewers need.

## License

No `LICENSE` file is included in this repository. Add one if you intend to distribute under a specific license.
