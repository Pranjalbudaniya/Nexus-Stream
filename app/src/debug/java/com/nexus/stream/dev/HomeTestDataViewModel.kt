package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.data.local.db.dao.LibraryDao
import com.nexus.stream.data.local.db.dao.ProgressDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface HomeTestDataStatus {
    data class Resource(
        val resId: Int,
        val arg: Any? = null,
    ) : HomeTestDataStatus
}

data class HomeTestDataUiState(
    val status: HomeTestDataStatus? = null,
    val isLoading: Boolean = false,
)

private const val CINEMETA_URL = "https://v3-cinemeta.strem.io/"
private const val MOVIE_DURATION_MS = 6_000_000L // 100 minutes
private const val SERIES_DURATION_MS = 3_000_000L // 50 minutes
private const val SHORT_MOVIE_POSITION_MS = 30_000L // 30 seconds
private const val PROGRESS_25 = 0.25
private const val PROGRESS_55 = 0.55
private const val PROGRESS_85 = 0.85
private const val PROGRESS_40 = 0.40
private const val PROGRESS_60 = 0.60
private const val DEV_PREFIX = "dev:"
private const val ONE_SECOND_MS = 1_000L
private const val CINEMETA_ITEM_COUNT = 3
private const val SERIES_SEASON = 1
private const val SERIES_EPISODE = 3

@HiltViewModel
class HomeTestDataViewModel
    @Inject
    constructor(
        private val remoteDataSource: AddonRemoteDataSource,
        private val libraryDao: LibraryDao,
        private val progressDao: ProgressDao,
        private val progressRepository: ProgressRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeTestDataUiState())
        val uiState: StateFlow<HomeTestDataUiState> = _uiState.asStateFlow()

        fun addCinemetaItems() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                when (val result = remoteDataSource.catalog(CINEMETA_URL, "movie", "top")) {
                    is Outcome.Success -> {
                        val items = result.value.items.take(CINEMETA_ITEM_COUNT)
                        insertCinemetaItems(items)
                        val names = items.joinToString(", ") { it.name }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                status =
                                    HomeTestDataStatus.Resource(
                                        R.string.home_test_data_status_cinemeta_added,
                                        names,
                                    ),
                            )
                        }
                    }

                    is Outcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                status =
                                    HomeTestDataStatus.Resource(
                                        R.string.home_test_data_status_cinemeta_failed,
                                        result.error.toString(),
                                    ),
                            )
                        }
                    }
                }
            }
        }

        private suspend fun insertCinemetaItems(items: List<MetaPreview>) {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                items.forEachIndexed { index, item ->
                    val devId = "$DEV_PREFIX${item.id}"
                    val positionFraction =
                        when (index) {
                            0 -> PROGRESS_25
                            1 -> PROGRESS_55
                            else -> PROGRESS_85
                        }
                    val positionMs = (MOVIE_DURATION_MS * positionFraction).toLong()
                    val timestamp = now - (index * ONE_SECOND_MS)

                    libraryDao.upsert(
                        LibraryItemEntity(
                            metaId = devId,
                            type = item.type,
                            name = item.name,
                            poster = item.poster,
                            background = item.background,
                            inWatchlist = false,
                            addedAt = timestamp,
                        ),
                    )
                    progressDao.upsert(
                        WatchProgressEntity(
                            key = "$devId|$devId",
                            metaId = devId,
                            videoId = devId,
                            type = item.type,
                            season = null,
                            episode = null,
                            positionMs = positionMs,
                            durationMs = MOVIE_DURATION_MS,
                            watched = false,
                            updatedAt = timestamp,
                        ),
                    )
                }
            }
        }

        fun addNoPosterItem() {
            viewModelScope.launch(ioDispatcher) {
                val now = System.currentTimeMillis()
                val devId = "${DEV_PREFIX}no_poster_item"
                val positionMs = (MOVIE_DURATION_MS * PROGRESS_40).toLong()

                libraryDao.upsert(
                    LibraryItemEntity(
                        metaId = devId,
                        type = "movie",
                        name = "No Poster Movie",
                        poster = null,
                        background = null,
                        inWatchlist = false,
                        addedAt = now,
                    ),
                )
                progressDao.upsert(
                    WatchProgressEntity(
                        key = "$devId|$devId",
                        metaId = devId,
                        videoId = devId,
                        type = "movie",
                        season = null,
                        episode = null,
                        positionMs = positionMs,
                        durationMs = MOVIE_DURATION_MS,
                        watched = false,
                        updatedAt = now,
                    ),
                )
                _uiState.update {
                    it.copy(
                        status = HomeTestDataStatus.Resource(R.string.home_test_data_status_no_poster_added),
                    )
                }
            }
        }

        fun addFinishedMovie() {
            viewModelScope.launch(ioDispatcher) {
                val now = System.currentTimeMillis()
                val devId = "${DEV_PREFIX}finished_movie"

                libraryDao.upsert(
                    LibraryItemEntity(
                        metaId = devId,
                        type = "movie",
                        name = "Finished Movie",
                        poster = null,
                        background = null,
                        inWatchlist = false,
                        addedAt = now,
                    ),
                )
                progressDao.upsert(
                    WatchProgressEntity(
                        key = "$devId|$devId",
                        metaId = devId,
                        videoId = devId,
                        type = "movie",
                        season = null,
                        episode = null,
                        positionMs = MOVIE_DURATION_MS,
                        durationMs = MOVIE_DURATION_MS,
                        watched = true,
                        updatedAt = now,
                    ),
                )
                _uiState.update {
                    it.copy(
                        status = HomeTestDataStatus.Resource(R.string.home_test_data_status_finished_added),
                    )
                }
            }
        }

        fun addShortMovie() {
            viewModelScope.launch(ioDispatcher) {
                val now = System.currentTimeMillis()
                val devId = "${DEV_PREFIX}short_movie"

                libraryDao.upsert(
                    LibraryItemEntity(
                        metaId = devId,
                        type = "movie",
                        name = "Short Movie (< 60s)",
                        poster = null,
                        background = null,
                        inWatchlist = false,
                        addedAt = now,
                    ),
                )
                progressDao.upsert(
                    WatchProgressEntity(
                        key = "$devId|$devId",
                        metaId = devId,
                        videoId = devId,
                        type = "movie",
                        season = null,
                        episode = null,
                        positionMs = SHORT_MOVIE_POSITION_MS,
                        durationMs = MOVIE_DURATION_MS,
                        watched = false,
                        updatedAt = now,
                    ),
                )
                _uiState.update {
                    it.copy(
                        status = HomeTestDataStatus.Resource(R.string.home_test_data_status_short_added),
                    )
                }
            }
        }

        fun addSeriesEpisode() {
            viewModelScope.launch(ioDispatcher) {
                val now = System.currentTimeMillis()
                val devId = "${DEV_PREFIX}series_episode"
                val positionMs = (SERIES_DURATION_MS * PROGRESS_60).toLong()

                libraryDao.upsert(
                    LibraryItemEntity(
                        metaId = devId,
                        type = "series",
                        name = "Series Test",
                        poster = null,
                        background = null,
                        inWatchlist = false,
                        addedAt = now,
                    ),
                )
                progressDao.upsert(
                    WatchProgressEntity(
                        key = "$devId|s${SERIES_SEASON}e$SERIES_EPISODE",
                        metaId = devId,
                        videoId = "video_$SERIES_EPISODE",
                        type = "series",
                        season = SERIES_SEASON,
                        episode = SERIES_EPISODE,
                        positionMs = positionMs,
                        durationMs = SERIES_DURATION_MS,
                        watched = false,
                        updatedAt = now,
                    ),
                )
                _uiState.update {
                    it.copy(
                        status = HomeTestDataStatus.Resource(R.string.home_test_data_status_series_added),
                    )
                }
            }
        }

        fun clearTestData() {
            viewModelScope.launch(ioDispatcher) {
                val current = progressRepository.observeContinueWatching().first()
                val devItems = current.filter { it.metaId.startsWith(DEV_PREFIX) }
                devItems.forEach {
                    progressRepository.removeFromContinueWatching(it.metaId)
                }
                // Also clean up any dev items that are hidden by rules (such as finished or <60s movies)
                progressDao.deleteByMeta("${DEV_PREFIX}finished_movie")
                progressDao.deleteByMeta("${DEV_PREFIX}short_movie")

                _uiState.update {
                    it.copy(
                        status =
                            HomeTestDataStatus.Resource(
                                R.string.home_test_data_status_cleared,
                                devItems.size,
                            ),
                    )
                }
            }
        }
    }
