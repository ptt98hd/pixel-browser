plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pixel.browser.core.data"
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
    api(libs.androidx.datastore.preferences)

    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
