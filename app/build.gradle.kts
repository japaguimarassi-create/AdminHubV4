plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseStorePath = System.getenv("JARVIS_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("JARVIS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("JARVIS_KEY_ALIAS")
val releaseKeyPassword = System.getenv("JARVIS_KEY_PASSWORD")
val debugStorePath = System.getenv("JARVIS_DEBUG_KEYSTORE_PATH")
val debugStorePassword = System.getenv("JARVIS_DEBUG_KEYSTORE_PASSWORD")
val debugKeyAlias = System.getenv("JARVIS_DEBUG_KEY_ALIAS")
val debugKeyPassword = System.getenv("JARVIS_DEBUG_KEY_PASSWORD")
val googleWebClientId = System.getenv("JARVIS_GOOGLE_WEB_CLIENT_ID").takeUnless { it.isNullOrBlank() } ?: "188208581214-dtshluh0fkclfq8mnupaf2nn35l1flrc.apps.googleusercontent.com"
val jarvisAuthEndpoint = System.getenv("JARVIS_AUTH_ENDPOINT").orEmpty()
val supabasePublishableKey = System.getenv("JARVIS_SUPABASE_PUBLISHABLE_KEY").orEmpty()
val canSignDebug = listOf(
    debugStorePath,
    debugStorePassword,
    debugKeyAlias,
    debugKeyPassword
).all { !it.isNullOrBlank() }
val canSignRelease = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "15.0.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quotedBuildConfig(googleWebClientId))
        buildConfigField("String", "JARVIS_AUTH_ENDPOINT", quotedBuildConfig(jarvisAuthEndpoint))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quotedBuildConfig(supabasePublishableKey))
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (canSignDebug) {
            create("jarvisDebug") {
                storeFile = file(debugStorePath!!)
                storePassword = debugStorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
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
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            if (canSignDebug) signingConfig = signingConfigs.getByName("jarvisDebug")
            versionNameSuffix = "-debug"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
