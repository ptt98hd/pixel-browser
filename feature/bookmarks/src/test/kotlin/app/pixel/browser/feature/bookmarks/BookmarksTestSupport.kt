package app.pixel.browser.feature.bookmarks

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.core.app.ActivityOptionsCompat
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.runBlocking

fun SemanticsNodeInteraction.textValue(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.Text) { emptyList() }
        .joinToString(" ") { it.text }

fun SemanticsNodeInteraction.editableText(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.EditableText) {
            androidx.compose.ui.text
                .AnnotatedString("")
        }.text

fun SemanticsNodeInteraction.customActionLabels(): List<String> =
    fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

fun SemanticsNodeInteraction.invokeCustomAction(label: String): Boolean =
    fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .first { it.label == label }
        .action()

fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }

/** Gateway giả: ghi lại lời gọi, trả kết quả theo URI. */
class FakeBookmarkFileGateway(
    var reads: Map<String, FileRead> = emptyMap(),
    var writeResult: Boolean = true,
) : BookmarkFileGateway {
    val readCalls = mutableListOf<Pair<String, Int>>()
    val writes = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun read(
        uri: String,
        maxBytes: Int,
    ): FileRead {
        readCalls += uri to maxBytes
        return reads[uri] ?: FileRead.Unreadable
    }

    override suspend fun write(
        uri: String,
        bytes: ByteArray,
    ): Boolean {
        writes += uri to bytes
        return writeResult
    }
}

/** Bọc [InMemoryBookmarkRepository], đếm các lời gọi quan trọng. */
class SpyBookmarkRepository(
    val delegate: InMemoryBookmarkRepository = InMemoryBookmarkRepository(),
) : BookmarkRepository by delegate {
    val importCalls = mutableListOf<Pair<app.pixel.browser.core.bookmarks.ImportedFolder, String>>()
    val searchCalls = mutableListOf<String>()
    val updateCalls = mutableListOf<List<Any>>()
    val deleteCalls = mutableListOf<Long>()
    val restoreCalls = mutableListOf<app.pixel.browser.core.bookmarks.DeletedTree>()
    var moveResult: BookmarkResult<Unit>? = null
    var importResult: BookmarkResult<Int>? = null
    var importFailure: Throwable? = null

    override suspend fun importTree(
        root: app.pixel.browser.core.bookmarks.ImportedFolder,
        folderTitle: String,
    ): BookmarkResult<Int> {
        importCalls += root to folderTitle
        importFailure?.let { throw it }
        return importResult ?: delegate.importTree(root, folderTitle)
    }

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<Bookmark> {
        searchCalls += query
        return delegate.search(query, limit)
    }

    override suspend fun updateBookmark(
        id: Long,
        title: String,
        url: String,
        parentId: Long,
    ): BookmarkResult<Bookmark> {
        updateCalls += listOf(id, title, url, parentId)
        return delegate.updateBookmark(id, title, url, parentId)
    }

    override suspend fun delete(id: Long): BookmarkResult<app.pixel.browser.core.bookmarks.DeletedTree> {
        deleteCalls += id
        return delegate.delete(id)
    }

    override suspend fun restore(tree: app.pixel.browser.core.bookmarks.DeletedTree): Boolean {
        restoreCalls += tree
        return delegate.restore(tree)
    }

    override suspend fun move(
        id: Long,
        newParentId: Long,
    ): BookmarkResult<Unit> = moveResult ?: delegate.move(id, newParentId)
}

fun <T> BookmarkResult<T>.value(): T = (this as BookmarkResult.Ok).value

fun BookmarkRepository.addBm(
    url: String,
    title: String,
    parent: Long = ROOT_ID,
): Bookmark = runBlocking { addBookmark(url, title, parent).value() }

fun BookmarkRepository.addFolder(
    title: String,
    parent: Long = ROOT_ID,
): BookmarkFolder = runBlocking { createFolder(parent, title).value() }

/** Registry ghi lại các lần launch thay cho trình chọn file thật (mẫu của spec 010 AC23). */
class TestActivityResultRegistry : ActivityResultRegistry() {
    data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

    val launches = mutableListOf<Launch>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += Launch(requestCode, contract, input)
    }

    fun <O> respond(result: O?) {
        dispatchResult(launches.last().requestCode, result)
    }
}
