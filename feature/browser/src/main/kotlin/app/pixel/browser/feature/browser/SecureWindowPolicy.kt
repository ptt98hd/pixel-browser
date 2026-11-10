package app.pixel.browser.feature.browser

/** Khi nào cửa sổ phải đặt `FLAG_SECURE` (spec 016 AC16). */
object SecureWindowPolicy {
    fun shouldSecure(
        blockScreenshots: Boolean,
        selectedIsPrivate: Boolean,
        switcherOpen: Boolean,
        switcherShowsPrivate: Boolean,
    ): Boolean = blockScreenshots && (selectedIsPrivate || (switcherOpen && switcherShowsPrivate))
}
