import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// -----------------------------------------------------------------------------
// Versioning: derived from CI, with safe local-dev fallbacks.
//   versionName  <- VERSION_NAME env (release.yml strips the leading "v" from the tag)
//   versionCode  <- VERSION_CODE env (release.yml passes the monotonic run number)
// Neither is committed anywhere; a plain local build gets 0.0.0-dev / 1.
// -----------------------------------------------------------------------------
val appVersionName: String = (System.getenv("VERSION_NAME") ?: "0.0.0-dev").trim()
val appVersionCode: Int = (System.getenv("VERSION_CODE") ?: "1").trim().toIntOrNull() ?: 1

// -----------------------------------------------------------------------------
// Release signing: fed entirely from environment (release.yml decodes the
// keystore from ANDROID_KEYSTORE_BASE64 into a file and exports its path).
// The config is registered ONLY when the keystore file is actually present, so
// debug builds and the unsigned CI pipeline never need any secret.
// No keystore, password, or alias is ever written to a committed file.
// -----------------------------------------------------------------------------
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val hasReleaseKeystore: Boolean = keystorePath?.let { file(it).exists() } == true

android {
    namespace = "com.a9ito.hermesagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.a9ito.hermesagent"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Ship only the locales we actually translate; keeps the APK lean and makes
    // the supported-locale set explicit. `localeFilters` is the AGP 8.5+/9.x
    // replacement for the deprecated `resourceConfigurations` locale list.
    androidResources {
        localeFilters += listOf("en", "in", "ja")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the release keystore when CI provided it; otherwise leave the
            // build unsigned (a local `assembleRelease` still succeeds, just
            // unsigned). CI always sets the env, so tagged releases are signed.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Fail the CI lint job on any error; warnings stay visible but non-fatal.
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = true
        sarifReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose — versions governed by the BOM.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Persistence
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose tooling / debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
