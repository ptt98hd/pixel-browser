package app.pixel.browser.core.history

class InMemoryHistoryStoreContractTest : HistoryStoreContractTest() {
    override fun createStore(): HistoryStore = InMemoryHistoryStore()
}
