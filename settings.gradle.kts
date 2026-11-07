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
}

rootProject.name = "rpg-agent-desk"

// :brief is plain Kotlin (no Android) and holds all Airtable + brief logic, so it can be
// unit-tested on any JVM. :app is the Android UI, notification and scheduling layer.
include(":brief", ":app")
