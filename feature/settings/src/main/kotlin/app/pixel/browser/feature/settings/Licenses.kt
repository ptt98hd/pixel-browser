package app.pixel.browser.feature.settings

data class LicenseEntry(
    val name: String,
    /** Mã SPDX. */
    val license: String,
    val url: String,
)

/** Thư viện mã nguồn mở được đóng gói trong app (spec 014 AC16); tĩnh, không gọi mạng. */
object Licenses {
    val all: List<LicenseEntry> =
        listOf(
            LicenseEntry("AndroidX Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
            LicenseEntry(
                "AndroidX Core, Activity and Lifecycle",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx",
            ),
            LicenseEntry(
                "AndroidX DataStore",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/datastore",
            ),
            LicenseEntry(
                "AndroidX Graphics Shapes",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/graphics",
            ),
            LicenseEntry("AndroidX Room", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
            LicenseEntry("Kotlin", "Apache-2.0", "https://kotlinlang.org/"),
            LicenseEntry("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
            LicenseEntry("Material Icons and Symbols", "Apache-2.0", "https://fonts.google.com/icons"),
        )
}
