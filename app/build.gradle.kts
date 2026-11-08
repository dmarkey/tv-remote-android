plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dmarkey.tvremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dmarkey.tvremote"
        // Built for one phone only: Poco F9 Ultra (HyperOS 3 / Android 16).
        minSdk = 34
        targetSdk = 35
        // CI sets these from the release tag and run number; local builds stay 1 / 1.0.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "1.0"
    }

    // Release key from ~/.gradle/gradle.properties (local) or environment variables (CI).
    // Never commit the keystore or its passwords.
    fun secret(name: String): String? = providers.gradleProperty(name).orNull ?: System.getenv(name)

    signingConfigs {
        create("release") {
            secret("TVREMOTE_STORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = secret("TVREMOTE_STORE_PASSWORD")
                keyAlias = secret("TVREMOTE_KEY_ALIAS")
                keyPassword = secret("TVREMOTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8-optimised: Compose is far snappier when minified.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // The release key when configured, else the local debug key (sideloading only).
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
