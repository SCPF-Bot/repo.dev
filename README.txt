repo.dev/
├── .github/workflows         # GitHub Actions configuration
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mlbbassistant/
│   │   │   │   ├── core/
│   │   │   │   │   ├── DraftEngine.kt        # Draft scoring logic
│   │   │   │   │   └── Resource.kt           # Generic Result wrapper
│   │   │   │   ├── data/
│   │   │   │   │   ├── api/                  # Retrofit services & DTOs
│   │   │   │   │   ├── db/                   # Room database, DAOs & entities
│   │   │   │   │   ├── model/                # Hero, DraftState, DraftSuggestion, etc.
│   │   │   │   │   └── repository/           # HeroRepository, UserPreferences, seed data
│   │   │   │   ├── di/                       # Hilt modules
│   │   │   │   │   ├── DatabaseModule.kt
│   │   │   │   │   ├── NetworkModule.kt      # API URL defined here
│   │   │   │   │   └── RepositoryModule.kt
│   │   │   │   ├── overlay/                  # OverlayService & adapter
│   │   │   │   ├── ui/                       # UI components
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── heroes/               # HeroesFragment, HeroesViewModel, HeroAdapter
│   │   │   │   │   ├── draft/                # DraftFragment, DraftViewModel, adapters
│   │   │   │   │   └── settings/             # SettingsFragment, SettingsViewModel
│   │   │   │   └── MLBBApp.kt
│   │   │   └── res/                          # Android resources
│   │   └── test/                             # Unit tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/                                   # Gradle wrapper files
├── scripts/                                  # Utility scripts
├── LICENSE                                   # MIT License
├── README.md
├── build.gradle.kts                          # Project‑level build file
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
