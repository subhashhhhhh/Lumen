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

// Lumen AI (search, text, faces, subtitles) lives in its own repository, built from source here
// until it's published. A checkout next to this one (../lumen-ai) wins, for working on both at
// once; otherwise the lumen-ai submodule (git clone --recurse-submodules).
val lumenAi = listOf("../lumen-ai", "lumen-ai").firstOrNull { file("$it/settings.gradle.kts").exists() }
    ?: error("Lumen AI is missing: run `git submodule update --init`")
includeBuild(lumenAi) {
    dependencySubstitution {
        substitute(module("com.subhashh.lumen:lumen-ai")).using(project(":ai"))
    }
}

rootProject.name = "Lumen"
include(":app")
include(":baselineprofile")
