plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.uekala.schichtwerk"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.uekala.schichtwerk"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        // Version steht in gradle.properties (appVersion); ein Release-Tag mobile-vX.Y.Z muss dazu passen.
        versionName = providers.gradleProperty("appVersion").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Bleibt unsigniert: Die CI signiert mit Schlüsselrotation (tools/sign-release.sh).
        }
        debug {
            // Testversion: eigene App neben der echten, mit dem Debug-Schlüssel – überschreibt sie nie.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
