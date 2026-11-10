plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pixel.browser.core.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }
    testFixtures { enable = true }
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable +=
            setOf("ObsoleteLintCustomCheck", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.webkit)

    testImplementation(testFixtures(project(":core:engine")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testFixturesImplementation(libs.junit)
}
