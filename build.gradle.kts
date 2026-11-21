plugins {
    id("com.android.application") version "9.5.2" apply false
    // Nur für die Kotlin-Version: Kotlin selbst bringt AGP seit 9.0 mit, das Plugin wird nicht angewendet.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
