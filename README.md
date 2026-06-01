# MLBB Assistant

A **Mobile Legends: Bang Bang** draft-assistant Android app.

Provides real-time hero pick/ban suggestions, hero browsing with search and role filtering, and an optional floating overlay usable during the in-game draft phase.

---

## Features

| Feature | Description |
|---|---|
| **Hero browser** | Full hero roster with win/pick/ban rates, role, and lane. Swipe-to-refresh syncs from the API. |
| **Draft assistant** | Add ally picks, enemy picks, and bans; receive ranked hero suggestions scored by counter-pick advantage, team synergy, and meta win-rate. |
| **Floating overlay** | A draggable, collapsible overlay window displayed on top of other apps (requires `SYSTEM_ALERT_WINDOW` permission). |
| **Settings** | Toggle overlay, adjust opacity, set suggestion count, and tune scoring weights. |
| **Offline seed data** | 24 heroes are pre-loaded on first launch so the app works without a network connection. |

---

## Architecture

```
app/
├── core/
│   ├── DraftEngine.kt        # Pure scoring logic (counter + synergy + meta)
│   └── Resource.kt           # Generic Result wrapper
├── data/
│   ├── api/                  # Retrofit service + DTOs + mappers
│   ├── db/                   # Room database, DAOs, entities, TypeConverters
│   ├── model/                # Domain models (Hero, DraftState, DraftSuggestion…)
│   └── repository/           # HeroRepository, UserPreferences (DataStore), Seed data
├── di/                       # Hilt modules (Network, Database, Repository)
├── overlay/                  # OverlayService (foreground) + adapter
└── ui/
    ├── heroes/               # HeroesFragment + HeroesViewModel + HeroAdapter
    ├── draft/                # DraftFragment + DraftViewModel + adapters
    └── settings/             # SettingsFragment + SettingsViewModel
```

**Tech stack:** Kotlin · Hilt · Room · Retrofit + Gson · OkHttp · Kotlin Coroutines + Flow · DataStore Preferences · Navigation Component · Material 3 · ViewBinding

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device or emulator running API 26+

### Build

```bash
git clone https://github.com/your-org/mlbb-assistant.git
cd mlbb-assistant
./gradlew assembleDebug
```

### API endpoint

Open `app/src/main/java/com/mlbbassistant/di/NetworkModule.kt` and replace:

```kotlin
private const val BASE_URL = "https://api.mlbbassistant.example.com/v1/"
```

with your actual API base URL. The endpoint must implement:

```
GET /meta/snapshot?patch=<optional>
→ MetaSnapshotDto { patch, updated_at, heroes: [ HeroDto, … ] }
```

If no API is available, the app operates fully on the seed data bundled in `SeedDataProvider.kt`.

---

## Scoring Algorithm

```
score = w_meta × metaScore + w_counter × counterScore + w_synergy × synergyScore
```

| Component | Source |
|---|---|
| `metaScore` | Hero win-rate, modestly adjusted by ban-rate |
| `counterScore` | Fraction of enemy picks the hero hard-counters |
| `synergyScore` | Fraction of ally picks the hero has synergy with |

Default weights: **meta 35% · counter 40% · synergy 25%** — tunable in Settings.

---

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | Fetch hero data from the API |
| `SYSTEM_ALERT_WINDOW` | Draw the draft overlay over other apps |
| `FOREGROUND_SERVICE` | Keep the overlay service alive |
| `POST_NOTIFICATIONS` | Show the persistent overlay notification (Android 13+) |

---

## License

MIT — see `LICENSE` for details.
