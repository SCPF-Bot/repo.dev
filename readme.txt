repo.dev/
├── .github/
│   └── workflows/
│       └── build.yml               (optional CI)
├── .devcontainer/
│   └── devcontainer.json          (optional Codespace config)
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/mlbbdraftassistant/
│           │   ├── MainActivity.kt
│           │   └── OverlayService.kt
│           └── res/
│               ├── layout/
│               │   └── floating_view.xml
│               ├── values/
│               │   ├── strings.xml
│               │   └── styles.xml
│               └── drawable/       (empty)
├── build.gradle.kts               (project‑level)
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
