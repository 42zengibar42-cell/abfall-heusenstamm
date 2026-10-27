plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
android {
    namespace = "de.abfallheusenstamm.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "de.abfallheusenstamm.app"
        minSdk = 23
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
    }
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "abfall42hs"
            keyAlias = "abfall"
            keyPassword = "abfall42hs"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
}
