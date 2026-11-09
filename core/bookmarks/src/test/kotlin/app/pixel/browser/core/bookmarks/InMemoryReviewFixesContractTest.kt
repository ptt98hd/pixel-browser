package app.pixel.browser.core.bookmarks

class InMemoryReviewFixesContractTest : ReviewFixesContractTest() {
    override fun createRepo(
        maxNodes: Int,
        clock: () -> Long,
    ): BookmarkRepository = InMemoryBookmarkRepository(maxNodes, clock)
}
