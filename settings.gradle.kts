pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "pixel-browser"
include(":app")
include(":core:engine")
include(":core:tabs")
include(":core:omnibox")
include(":core:data")
include(":core:ui")
include(":feature:browser")
include(":feature:home")
