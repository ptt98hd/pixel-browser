plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", ".claude/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", ".claude/**")
        ktlint(libs.versions.ktlint.get())
    }
}
