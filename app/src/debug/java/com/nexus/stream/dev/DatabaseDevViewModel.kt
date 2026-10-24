package com.nexus.stream.dev

import android.database.Cursor
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexus.stream.data.local.db.NexusDatabase
import com.nexus.stream.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryItemDebugRow(
    val metaId: String,
    val type: String,
    val name: String,
    val inWatchlist: Boolean,
    val listStatus: String,
    val addedAt: Long,
)

data class WatchProgressDebugRow(
    val metaId: String,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
    val libraryStatus: String? = null,
)

data class MetaCacheDebugRow(
    val metaId: String,
    val type: String,
    val fetchedAt: Long,
    val jsonSnippet: String,
)

data class AddonDebugRow(
    val name: String,
    val enabled: Boolean,
    val position: Int,
    val host: String,
)

data class DatabaseDebugState(
    val version: Int = 0,
    val tableCounts: Map<String, Int> = emptyMap(),
    val libraryItems: List<LibraryItemDebugRow> = emptyList(),
    val watchProgress: List<WatchProgressDebugRow> = emptyList(),
    val metaCache: List<MetaCacheDebugRow> = emptyList(),
    val addons: List<AddonDebugRow> = emptyList(),
    val isLoading: Boolean = false,
)

private const val JSON_SNIPPET_LENGTH = 80
private const val ROW_LIMIT = 20

@HiltViewModel
class DatabaseDevViewModel
    @Inject
    constructor(
        private val database: NexusDatabase,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DatabaseDebugState())
        val state: StateFlow<DatabaseDebugState> = _state.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val newState = withContext(dispatcher) { queryDatabase() }
                _state.value = newState
            }
        }

        private fun queryDatabase(): DatabaseDebugState {
            val db = database.openHelper.readableDatabase
            val version = db.version

            val tables =
                listOf(
                    "addons",
                    "library_items",
                    "meta_cache",
                    "search_history",
                    "skip_segment_cache",
                    "watch_progress",
                )
            val counts =
                tables.associateWith { table ->
                    runCatching {
                        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table")).use { cursor ->
                            if (cursor.moveToFirst()) cursor.getInt(0) else 0
                        }
                    }.getOrDefault(0)
                }

            val libraryItems = readLibraryItems(db)
            val watchProgress = readWatchProgress(db)
            val metaCache = readMetaCache(db)
            val addons = readAddons(db)

            return DatabaseDebugState(
                version = version,
                tableCounts = counts,
                libraryItems = libraryItems,
                watchProgress = watchProgress,
                metaCache = metaCache,
                addons = addons,
                isLoading = false,
            )
        }

        private fun readLibraryItems(db: SupportSQLiteDatabase): List<LibraryItemDebugRow> {
            val rows = mutableListOf<LibraryItemDebugRow>()
            runCatching {
                db
                    .query(SimpleSQLiteQuery("SELECT * FROM library_items ORDER BY addedAt DESC LIMIT $ROW_LIMIT"))
                    .use { cursor ->
                        val idIdx = cursor.getColumnIndex("metaId")
                        val typeIdx = cursor.getColumnIndex("type")
                        val nameIdx = cursor.getColumnIndex("name")
                        val inWatchIdx = cursor.getColumnIndex("inWatchlist")
                        val statusIdx = cursor.getColumnIndex("listStatus")
                        val addedIdx = cursor.getColumnIndex("addedAt")

                        while (cursor.moveToNext()) {
                            rows.add(
                                LibraryItemDebugRow(
                                    metaId = cursor.getStringOrEmpty(idIdx),
                                    type = cursor.getStringOrEmpty(typeIdx),
                                    name = cursor.getStringOrEmpty(nameIdx),
                                    inWatchlist = cursor.getIntOrDefault(inWatchIdx, 0) == 1,
                                    listStatus = if (statusIdx >= 0) cursor.getStringOrEmpty(statusIdx) else "N/A",
                                    addedAt = cursor.getLongOrDefault(addedIdx, 0L),
                                ),
                            )
                        }
                    }
            }
            return rows
        }

        private fun readWatchProgress(db: SupportSQLiteDatabase): List<WatchProgressDebugRow> {
            val rows = mutableListOf<WatchProgressDebugRow>()
            runCatching {
                val sql =
                    """
                    SELECT p.metaId, p.videoId, p.positionMs, p.durationMs, p.watched, p.updatedAt, l.listStatus
                    FROM watch_progress p
                    LEFT JOIN library_items l ON p.metaId = l.metaId
                    ORDER BY p.updatedAt DESC
                    LIMIT $ROW_LIMIT
                    """.trimIndent()
                db.query(SimpleSQLiteQuery(sql)).use { cursor ->
                    val metaIdx = cursor.getColumnIndex("metaId")
                    val videoIdx = cursor.getColumnIndex("videoId")
                    val posIdx = cursor.getColumnIndex("positionMs")
                    val durIdx = cursor.getColumnIndex("durationMs")
                    val watchedIdx = cursor.getColumnIndex("watched")
                    val updatedIdx = cursor.getColumnIndex("updatedAt")
                    val statusIdx = cursor.getColumnIndex("listStatus")

                    while (cursor.moveToNext()) {
                        rows.add(
                            WatchProgressDebugRow(
                                metaId = cursor.getStringOrEmpty(metaIdx),
                                videoId = cursor.getStringOrEmpty(videoIdx),
                                positionMs = cursor.getLongOrDefault(posIdx, 0L),
                                durationMs = cursor.getLongOrDefault(durIdx, 0L),
                                watched = cursor.getIntOrDefault(watchedIdx, 0) == 1,
                                updatedAt = cursor.getLongOrDefault(updatedIdx, 0L),
                                libraryStatus =
                                    if (statusIdx >= 0 && !cursor.isNull(statusIdx)) {
                                        cursor.getString(statusIdx)
                                    } else {
                                        null
                                    },
                            ),
                        )
                    }
                }
            }
            return rows
        }

        private fun readMetaCache(db: SupportSQLiteDatabase): List<MetaCacheDebugRow> {
            val rows = mutableListOf<MetaCacheDebugRow>()
            runCatching {
                db
                    .query(SimpleSQLiteQuery("SELECT * FROM meta_cache ORDER BY fetchedAt DESC LIMIT $ROW_LIMIT"))
                    .use { cursor ->
                        val metaIdx = cursor.getColumnIndex("metaId")
                        val typeIdx = cursor.getColumnIndex("type")
                        val fetchIdx = cursor.getColumnIndex("fetchedAt")
                        val dataIdx = cursor.getColumnIndex("data")

                        while (cursor.moveToNext()) {
                            val data = cursor.getStringOrEmpty(dataIdx)
                            rows.add(
                                MetaCacheDebugRow(
                                    metaId = cursor.getStringOrEmpty(metaIdx),
                                    type = cursor.getStringOrEmpty(typeIdx),
                                    fetchedAt = cursor.getLongOrDefault(fetchIdx, 0L),
                                    jsonSnippet = data.take(JSON_SNIPPET_LENGTH),
                                ),
                            )
                        }
                    }
            }
            return rows
        }

        private fun readAddons(db: SupportSQLiteDatabase): List<AddonDebugRow> {
            val rows = mutableListOf<AddonDebugRow>()
            runCatching {
                db.query(SimpleSQLiteQuery("SELECT * FROM addons ORDER BY position ASC")).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val enabledIdx = cursor.getColumnIndex("enabled")
                    val posIdx = cursor.getColumnIndex("position")
                    val urlIdx = cursor.getColumnIndex("transportUrl")

                    while (cursor.moveToNext()) {
                        val transportUrl = cursor.getStringOrEmpty(urlIdx)
                        val host =
                            runCatching {
                                Uri
                                    .parse(
                                        transportUrl,
                                    ).host ?: transportUrl
                            }.getOrDefault(transportUrl)
                        rows.add(
                            AddonDebugRow(
                                name = cursor.getStringOrEmpty(nameIdx),
                                enabled = cursor.getIntOrDefault(enabledIdx, 0) == 1,
                                position = cursor.getIntOrDefault(posIdx, 0),
                                host = host,
                            ),
                        )
                    }
                }
            }
            return rows
        }

        private fun Cursor.getStringOrEmpty(index: Int): String =
            if (index >= 0 && !isNull(index)) getString(index) else ""

        private fun Cursor.getIntOrDefault(
            index: Int,
            default: Int,
        ): Int = if (index >= 0 && !isNull(index)) getInt(index) else default

        private fun Cursor.getLongOrDefault(
            index: Int,
            default: Long,
        ): Long = if (index >= 0 && !isNull(index)) getLong(index) else default
    }
