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

// Lumen AI (search, text, faces, subtitles) comes from Maven Central (app.lumen-photos:lumen-ai).
// A checkout next to this repository (../lumen-ai) replaces it, for working on both at once.
if (file("../lumen-ai/settings.gradle.kts").exists()) {
    includeBuild("../lumen-ai") {
        dependencySubstitution {
            substitute(module("app.lumen-photos:lumen-ai")).using(project(":ai"))
        }
    }
}

rootProject.name = "Lumen"
include(":app")
include(":baselineprofile")
