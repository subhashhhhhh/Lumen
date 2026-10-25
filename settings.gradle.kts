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

// Lumen AI (search, text, faces, subtitles) lives in its own repository, checked out next to
// this one as ../lumen-ai. Built from source here until it's published.
includeBuild("../lumen-ai") {
    dependencySubstitution {
        substitute(module("com.subhashh.lumen:lumen-ai")).using(project(":ai"))
    }
}

rootProject.name = "Lumen"
include(":app")
include(":baselineprofile")
