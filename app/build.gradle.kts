plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = System.getenv("JARVIS_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("JARVIS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("JARVIS_KEY_ALIAS")
val releaseKeyPassword = System.getenv("JARVIS_KEY_PASSWORD")
val canSignRelease = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.neuralhud.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "7.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug7"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
