plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.pixel.browser.core.bookmarks"
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(project(":core:omnibox"))
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(testFixtures(project(":core:bookmarks")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
