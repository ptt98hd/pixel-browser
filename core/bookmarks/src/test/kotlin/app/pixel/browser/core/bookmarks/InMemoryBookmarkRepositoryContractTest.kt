package app.pixel.browser.core.bookmarks

class InMemoryBookmarkRepositoryContractTest : BookmarkRepositoryContractTest() {
    override fun createRepo(
        maxNodes: Int,
        clock: () -> Long,
    ): BookmarkRepository = InMemoryBookmarkRepository(maxNodes, clock)
}
