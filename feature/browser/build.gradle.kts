plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.pixel.browser.feature.browser"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }
    buildFeatures { compose = true }
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
    api(project(":core:tabs"))
    api(project(":core:omnibox"))
    api(project(":core:history"))
    api(project(":core:bookmarks"))
    api(project(":core:ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(testFixtures(project(":core:engine")))
    testImplementation(testFixtures(project(":core:history")))
    testImplementation(testFixtures(project(":core:bookmarks")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
