package app.pixel.browser.core.history

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Entity(tableName = "visits", indices = [Index("url"), Index("visitedAt")])
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    @ColumnInfo(name = "visitedAt") val visitedAt: Long,
)

/** Hàng tổng hợp theo URL (truy vấn `GROUP BY url`). */
data class HitRow(
    val url: String,
    val title: String,
    val visitCount: Int,
    val lastVisitedAt: Long,
)

@Dao
interface VisitDao {
    @Insert
    suspend fun insert(visit: VisitEntity): Long

    @Query("SELECT * FROM visits ORDER BY visitedAt DESC, id DESC LIMIT :limit")
    fun all(limit: Int): Flow<List<VisitEntity>>

    @RawQuery(observedEntities = [VisitEntity::class])
    fun search(query: SupportSQLiteQuery): Flow<List<VisitEntity>>

    @RawQuery
    suspend fun hits(query: SupportSQLiteQuery): List<HitRow>

    @Query("UPDATE visits SET title = :title, visitedAt = :visitedAt WHERE id = :id")
    suspend fun touch(
        id: Long,
        title: String,
        visitedAt: Long,
    ): Int

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM visits WHERE visitedAt BETWEEN :from AND :to")
    suspend fun deleteBetween(
        from: Long,
        to: Long,
    ): Int

    @Query("DELETE FROM visits WHERE visitedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query(
        "DELETE FROM visits WHERE id NOT IN " +
            "(SELECT id FROM visits ORDER BY visitedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun deleteBeyond(keep: Int): Int

    @Query("SELECT COUNT(*) FROM visits")
    suspend fun count(): Int
}

@Database(entities = [VisitEntity::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao
}

fun createHistoryDatabase(
    context: Context,
    name: String = "history.db",
): HistoryDatabase = Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, name).build()

class RoomHistoryStore(
    private val db: HistoryDatabase,
) : HistoryStore {
    private val dao get() = db.visitDao()

    override suspend fun insert(
        url: String,
        title: String,
        visitedAt: Long,
    ): Long = dao.insert(VisitEntity(url = url, title = title, visitedAt = visitedAt))

    override suspend fun touch(
        id: Long,
        title: String,
        visitedAt: Long,
    ): Boolean = dao.touch(id, title, visitedAt) > 0

    override fun entries(
        query: String,
        limit: Int,
    ): Flow<List<HistoryEntry>> {
        val terms = HistoryQuery.terms(query)
        val flow =
            if (terms.isEmpty()) {
                dao.all(limit)
            } else {
                val (where, args) = HistoryQuery.where(terms)
                dao.search(
                    SimpleSQLiteQuery(
                        "SELECT * FROM visits WHERE $where ORDER BY visitedAt DESC, id DESC LIMIT ?",
                        (args + limit).toTypedArray(),
                    ),
                )
            }
        return flow.map { rows -> rows.map { HistoryEntry(it.id, it.url, it.title, it.visitedAt) } }
    }

    override suspend fun suggest(
        terms: List<String>,
        candidateLimit: Int,
    ): List<HistoryHit> {
        val clean = terms.map { it.trim().take(HistoryQuery.MAX_QUERY) }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return emptyList()
        val (where, args) = HistoryQuery.where(clean, "v.")
        val sql =
            "SELECT v.url AS url, " +
                "(SELECT t.title FROM visits t WHERE t.url = v.url ORDER BY t.visitedAt DESC, t.id DESC LIMIT 1) " +
                "AS title, COUNT(*) AS visitCount, MAX(v.visitedAt) AS lastVisitedAt " +
                "FROM visits v WHERE $where " +
                "GROUP BY v.url ORDER BY lastVisitedAt DESC LIMIT ?"
        return dao
            .hits(SimpleSQLiteQuery(sql, (args + candidateLimit).toTypedArray()))
            .map { HistoryHit(it.url, it.title, it.visitCount, it.lastVisitedAt) }
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override suspend fun deleteBetween(
        fromInclusive: Long,
        toInclusive: Long,
    ): Int {
        val removed = dao.deleteBetween(fromInclusive, toInclusive)
        val everything = fromInclusive <= 0L && toInclusive == Long.MAX_VALUE
        reclaim(vacuum = everything && removed > 0)
        return removed
    }

    override suspend fun prune(
        maxEntries: Int,
        olderThan: Long,
    ): Int {
        val removed =
            db.withTransaction {
                dao.deleteOlderThan(olderThan) + dao.deleteBeyond(maxEntries.coerceAtLeast(0))
            }
        if (removed > 0) reclaim(vacuum = false)
        return removed
    }

    /**
     * Dữ liệu đã xóa không được nằm lại trong WAL/trang trống: checkpoint(TRUNCATE) và (khi xóa tất cả) VACUUM.
     * Lỗi ở đây không làm hỏng việc xóa nên chỉ bỏ qua.
     */
    private suspend fun reclaim(vacuum: Boolean) =
        withContext(Dispatchers.IO) {
            try {
                val sql = db.openHelper.writableDatabase
                if (vacuum) sql.execSQL("VACUUM")
                sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (_: android.database.SQLException) {
                // Best-effort.
            }
        }

    override suspend fun count(): Int = dao.count()
}

/** Dựng mệnh đề `LIKE` an toàn: `%`, `_`, `\` là ký tự thường (ESCAPE `\`), mọi giá trị đi qua tham số. */
internal object HistoryQuery {
    const val MAX_QUERY = 256
    private val WHITESPACE = Regex("\\s+")

    fun terms(query: String): List<String> =
        query
            .trim()
            .take(MAX_QUERY)
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }

    fun where(
        terms: List<String>,
        prefix: String = "",
    ): Pair<String, List<Any>> {
        val args = ArrayList<Any>()
        val clause =
            terms.joinToString(" AND ") { t ->
                val pattern = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
                args += pattern
                args += pattern
                "(${prefix}url LIKE ? ESCAPE '\\' OR ${prefix}title LIKE ? ESCAPE '\\')"
            }
        return clause to args
    }
}
