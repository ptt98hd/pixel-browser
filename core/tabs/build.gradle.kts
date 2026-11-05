plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pixel.browser.core.tabs"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable +=
            setOf("ObsoleteLintCustomCheck", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(project(":core:engine"))

    testImplementation(testFixtures(project(":core:engine")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
