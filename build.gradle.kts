buildscript {
    // AGP 9's built-in Kotlin support no longer needs the kotlin-android plugin,
    // but we still pin the exact Kotlin compiler version it uses so it matches
    // the kotlin.compose / kotlin.serialization plugin versions below — otherwise
    // AGP's bundled default KGP version could silently diverge from theirs.
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
