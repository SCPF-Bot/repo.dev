pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // gradle/libs.versions.toml is loaded automatically by Gradle 8.2+ as the
    // default "libs" version catalog. No explicit from() call needed.
}

rootProject.name = "MLBBAssistant"
include(":app")
