plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.subhashh.lumen"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.subhashh.lumen"
        // Android 11: All files access (hidden folders) and MediaStore trash.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.svg)

    // Zoomable, subsampled photos — saket/telephoto, Apache 2.0.
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)

    // Liquid glass — Kyant0/backdrop, Apache 2.0.
    implementation(libs.backdrop)
}
