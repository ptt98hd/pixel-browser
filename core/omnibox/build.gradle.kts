plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// CI chạy `testDebugUnitTest` cho các module Android; alias để test JVM của module này cũng chạy.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Alias của `test` để CI (testDebugUnitTest) chạy cả module JVM."
    dependsOn(tasks.named("test"))
}
