package com.nexus.stream.data.download

import com.nexus.stream.data.local.db.dao.DownloadDao
import com.nexus.stream.data.local.db.entity.DownloadEntity
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.model.GalleryState
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl
    @Inject
    constructor(
        private val dao: DownloadDao,
    ) : DownloadRepository {
        private val galleryFailures = ConcurrentHashMap<String, GalleryFailure>()

        override fun observeAll(): Flow<List<DownloadItem>> =
            dao.observeAll().map { list -> list.map { DownloadMapper.toDomain(it, galleryFailures[it.id]) } }

        override fun observeForMeta(metaId: String): Flow<List<DownloadItem>> =
            dao.observeForMeta(metaId).map { list -> list.map { DownloadMapper.toDomain(it, galleryFailures[it.id]) } }

        override suspend fun get(id: String): DownloadItem? =
            dao.get(id)?.let { DownloadMapper.toDomain(it, galleryFailures[it.id]) }

        override suspend fun getAll(): List<DownloadItem> =
            dao.getAll().map { DownloadMapper.toDomain(it, galleryFailures[it.id]) }

        override suspend fun insert(item: DownloadItem) {
            if (item.galleryFailure != null) {
                galleryFailures[item.id] = item.galleryFailure
            } else if (item.galleryState != GalleryState.FAILED) {
                galleryFailures.remove(item.id)
            }
            dao.upsert(DownloadMapper.toEntity(item))
        }

        override suspend fun updateProgress(
            id: String,
            downloadedBytes: Long,
            totalBytes: Long,
            state: DownloadState,
            updatedAt: Long,
        ) {
            dao.updateProgress(
                id = id,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                state = state.name,
                updatedAt = updatedAt,
            )
        }

        override suspend fun updateState(
            id: String,
            state: DownloadState,
            updatedAt: Long,
            pauseReason: PauseReason?,
            failure: DownloadFailure?,
        ) {
            dao.updateState(
                id = id,
                state = state.name,
                updatedAt = updatedAt,
                pauseReason = pauseReason?.name,
                failure = failure?.name,
            )
        }

        override suspend fun updateCompleted(
            id: String,
            filePath: String,
            totalBytes: Long,
            completedAt: Long,
            updatedAt: Long,
        ) {
            dao.updateCompleted(
                id = id,
                filePath = filePath,
                totalBytes = totalBytes,
                completedAt = completedAt,
                updatedAt = updatedAt,
            )
        }

        override suspend fun updateSource(
            id: String,
            source: DownloadSource,
            state: DownloadState,
            updatedAt: Long,
        ) {
            val existing = dao.get(id) ?: return
            val updated =
                when (source) {
                    is DownloadSource.Http -> {
                        existing.copy(
                            kind = DownloadKind.HTTP.name,
                            url = source.url,
                            headersJson = DownloadMapper.serializeMap(source.headers),
                            infoHash = null,
                            trackersJson = null,
                            fileIdx = null,
                            state = state.name,
                            updatedAt = updatedAt,
                        )
                    }

                    is DownloadSource.Torrent -> {
                        existing.copy(
                            kind = DownloadKind.TORRENT.name,
                            url = null,
                            headersJson = null,
                            infoHash = source.infoHash,
                            trackersJson = DownloadMapper.serializeList(source.trackers),
                            fileIdx = source.fileIdx,
                            state = state.name,
                            updatedAt = updatedAt,
                        )
                    }
                }
            dao.upsert(updated)
        }

        override suspend fun setAllowMetered(
            id: String,
            allowMetered: Boolean,
            updatedAt: Long,
        ) {
            dao.setAllowMetered(
                id = id,
                allowMetered = allowMetered,
                updatedAt = updatedAt,
            )
        }

        override suspend fun delete(id: String) {
            galleryFailures.remove(id)
            dao.delete(id)
        }
    }

private object DownloadMapper {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun serializeMap(map: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map)

    fun parseMap(jsonStr: String): Map<String, String> =
        runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), jsonStr)
        }.getOrNull().orEmpty()

    fun serializeList(list: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), list)

    fun parseList(jsonStr: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), jsonStr)
        }.getOrNull().orEmpty()

    fun toDomain(
        entity: DownloadEntity,
        failure: GalleryFailure? = null,
    ): DownloadItem {
        val parsedKind = runCatching { DownloadKind.valueOf(entity.kind) }.getOrDefault(DownloadKind.HTTP)
        val parsedState = runCatching { DownloadState.valueOf(entity.state) }.getOrDefault(DownloadState.FAILED)
        val parsedFailure =
            entity.failure?.let { runCatching { DownloadFailure.valueOf(it) }.getOrDefault(DownloadFailure.UNKNOWN) }
                ?: if (parsedState == DownloadState.FAILED) DownloadFailure.UNKNOWN else null
        val parsedPauseReason = entity.pauseReason?.let { runCatching { PauseReason.valueOf(it) }.getOrNull() }
        val parsedGalleryState =
            runCatching { GalleryState.valueOf(entity.galleryState) }.getOrDefault(GalleryState.NONE)

        val source: DownloadSource =
            if (parsedKind == DownloadKind.TORRENT) {
                val trackers = entity.trackersJson?.let { parseList(it) }.orEmpty()
                DownloadSource.Torrent(
                    infoHash = entity.infoHash.orEmpty(),
                    trackers = trackers,
                    fileIdx = entity.fileIdx,
                )
            } else {
                val headers = entity.headersJson?.let { parseMap(it) }.orEmpty()
                DownloadSource.Http(
                    url = entity.url.orEmpty(),
                    headers = headers,
                )
            }

        return DownloadItem(
            id = entity.id,
            kind = parsedKind,
            source = source,
            metaId = entity.metaId,
            type = entity.type,
            title = entity.title,
            metaName = entity.metaName,
            posterUrl = entity.posterUrl,
            videoId = entity.videoId,
            season = entity.season,
            episode = entity.episode,
            fileNameHint = entity.fileNameHint,
            fileName = entity.fileName,
            totalBytes = entity.totalBytes,
            downloadedBytes = entity.downloadedBytes,
            state = parsedState,
            pauseReason = parsedPauseReason,
            failure = parsedFailure,
            filePath = entity.filePath,
            supportsResume = entity.supportsResume,
            etag = entity.etag,
            lastModified = entity.lastModified,
            allowMetered = entity.allowMetered,
            galleryState = parsedGalleryState,
            galleryUri = entity.galleryUri,
            galleryFailure = failure,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            completedAt = entity.completedAt,
        )
    }

    fun toEntity(item: DownloadItem): DownloadEntity {
        val isTorrent = item.source is DownloadSource.Torrent
        val torrentSource = item.source as? DownloadSource.Torrent
        val httpSource = item.source as? DownloadSource.Http

        return DownloadEntity(
            id = item.id,
            kind = if (isTorrent) DownloadKind.TORRENT.name else DownloadKind.HTTP.name,
            url = httpSource?.url,
            headersJson = httpSource?.headers?.let { serializeMap(it) },
            infoHash = torrentSource?.infoHash,
            trackersJson = torrentSource?.trackers?.let { serializeList(it) },
            fileIdx = torrentSource?.fileIdx,
            metaId = item.metaId,
            type = item.type,
            title = item.title,
            metaName = item.metaName,
            posterUrl = item.posterUrl,
            videoId = item.videoId,
            season = item.season,
            episode = item.episode,
            fileNameHint = item.fileNameHint,
            fileName = item.fileName,
            totalBytes = item.totalBytes,
            downloadedBytes = item.downloadedBytes,
            state = item.state.name,
            pauseReason = item.pauseReason?.name,
            failure = item.failure?.name,
            filePath = item.filePath,
            supportsResume = item.supportsResume,
            etag = item.etag,
            lastModified = item.lastModified,
            allowMetered = item.allowMetered,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            completedAt = item.completedAt,
            galleryUri = item.galleryUri,
            galleryState = item.galleryState.name,
        )
    }
}
