plugins {
    id("com.android.application")
}

android {
    namespace = "de.meisterstueck.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.meisterstueck.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
