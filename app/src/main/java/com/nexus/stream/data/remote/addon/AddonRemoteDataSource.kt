package com.nexus.stream.data.remote.addon

import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.Subtitle
import com.nexus.stream.domain.model.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import javax.inject.Inject

data class FetchedManifest(
    val manifest: AddonManifest,
    val rawJson: String,
)

data class FetchedMeta(
    val meta: MetaDetail,
    val rawJson: String,
)

data class CatalogPage(
    val items: List<MetaPreview>,
    val skipped: Int,
)

class AddonRemoteDataSource
    @Inject
    constructor(
        private val api: AddonApi,
        private val json: Json,
        @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        suspend fun manifestWithRaw(transportUrl: String): Outcome<FetchedManifest> {
            val url = AddonUrlBuilder.manifestUrl(transportUrl)
            return safeCall(url) { targetUrl ->
                val jsonObject = api.get(targetUrl)
                withContext(dispatcher) {
                    val dto = json.decodeFromJsonElement<ManifestDto>(jsonObject)
                    val manifest = AddonMappers.mapManifest(dto, json)
                    FetchedManifest(
                        manifest = manifest,
                        rawJson = jsonObject.toString(),
                    )
                }
            }
        }

        suspend fun manifest(transportUrl: String): Outcome<AddonManifest> =
            manifestWithRaw(transportUrl).map { it.manifest }

        suspend fun catalog(
            transportUrl: String,
            type: String,
            catalogId: String,
            extra: Map<String, String> = emptyMap(),
        ): Outcome<CatalogPage> {
            val url = AddonUrlBuilder.resourceUrl(transportUrl, "catalog", type, catalogId, extra)
            return safeCall(url) { targetUrl ->
                val jsonObject = api.get(targetUrl)
                withContext(dispatcher) {
                    val rawMetas = (jsonObject["metas"] as? JsonArray)?.toList().orEmpty()
                    val seen = mutableSetOf<String>()
                    val items = mutableListOf<MetaPreview>()
                    var skipped = 0

                    for (element in rawMetas) {
                        try {
                            val dto = json.decodeFromJsonElement<MetaPreviewDto>(element)
                            val mapped = AddonMappers.mapMetaPreview(dto)
                            if (mapped != null) {
                                if (seen.add(mapped.id)) {
                                    items.add(mapped)
                                }
                            } else {
                                skipped++
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: SerializationException) {
                            skipped++
                        } catch (_: IllegalArgumentException) {
                            skipped++
                        }
                    }
                    CatalogPage(items = items, skipped = skipped)
                }
            }
        }

        suspend fun metaWithRaw(
            transportUrl: String,
            type: String,
            id: String,
        ): Outcome<FetchedMeta> {
            val url = AddonUrlBuilder.resourceUrl(transportUrl, "meta", type, id)
            return safeCall(url) { targetUrl ->
                val jsonObject = api.get(targetUrl)
                withContext(dispatcher) {
                    val targetObj =
                        when (val rawMeta = jsonObject["meta"]) {
                            is JsonObject -> rawMeta.takeIf { it.isNotEmpty() }
                            is JsonArray -> rawMeta.firstOrNull() as? JsonObject
                            else -> null
                        } ?: throw NotFoundException()

                    val dto = json.decodeFromJsonElement<MetaDetailDto>(targetObj)
                    val mappedMeta = AddonMappers.mapMetaDetail(dto) ?: throw NotFoundException()
                    FetchedMeta(
                        meta = mappedMeta,
                        rawJson = jsonObject.toString(),
                    )
                }
            }
        }

        suspend fun meta(
            transportUrl: String,
            type: String,
            id: String,
        ): Outcome<MetaDetail> = metaWithRaw(transportUrl, type, id).map { it.meta }

        suspend fun streams(
            transportUrl: String,
            type: String,
            videoId: String,
        ): Outcome<List<Stream>> {
            val url = AddonUrlBuilder.resourceUrl(transportUrl, "stream", type, videoId)
            return safeCall(url) { targetUrl ->
                val jsonObject = api.get(targetUrl)
                withContext(dispatcher) {
                    val rawStreams = (jsonObject["streams"] as? JsonArray)?.toList().orEmpty()
                    val streams = mutableListOf<Stream>()

                    for (element in rawStreams) {
                        try {
                            val dto = json.decodeFromJsonElement<StreamDto>(element)
                            streams.add(AddonMappers.mapStream(dto))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: SerializationException) {
                            // Drop corrupt item
                        } catch (_: IllegalArgumentException) {
                            // Drop corrupt item
                        }
                    }
                    streams
                }
            }
        }

        suspend fun subtitles(
            transportUrl: String,
            type: String,
            videoId: String,
            extra: Map<String, String> = emptyMap(),
        ): Outcome<List<Subtitle>> {
            val url = AddonUrlBuilder.resourceUrl(transportUrl, "subtitles", type, videoId, extra)
            return safeCall(url) { targetUrl ->
                val jsonObject = api.get(targetUrl)
                withContext(dispatcher) {
                    val rawSubtitles = (jsonObject["subtitles"] as? JsonArray)?.toList().orEmpty()
                    val subtitles = mutableListOf<Subtitle>()

                    for (element in rawSubtitles) {
                        try {
                            val dto = json.decodeFromJsonElement<SubtitleDto>(element)
                            val mapped = AddonMappers.mapSubtitle(dto)
                            if (mapped != null) {
                                subtitles.add(mapped)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: SerializationException) {
                            // Drop corrupt item
                        } catch (_: IllegalArgumentException) {
                            // Drop corrupt item
                        }
                    }
                    subtitles
                }
            }
        }

        private class NotFoundException : Exception()

        private suspend fun <T> safeCall(
            url: String?,
            block: suspend (String) -> T,
        ): Outcome<T> {
            if (url == null) return Outcome.Failure(AppError.InvalidUrl)
            return try {
                Outcome.Success(block(url))
            } catch (e: CancellationException) {
                throw e
            } catch (_: NotFoundException) {
                Outcome.Failure(AppError.NotFound)
            } catch (_: SocketTimeoutException) {
                Outcome.Failure(AppError.Timeout)
            } catch (_: InterruptedIOException) {
                Outcome.Failure(AppError.Timeout)
            } catch (e: HttpException) {
                if (e.code() == HTTP_NOT_FOUND) {
                    Outcome.Failure(AppError.NotFound)
                } else {
                    Outcome.Failure(AppError.Http(e.code()))
                }
            } catch (_: IOException) {
                Outcome.Failure(AppError.Network)
            } catch (_: SerializationException) {
                Outcome.Failure(AppError.Parse)
            } catch (_: IllegalArgumentException) {
                Outcome.Failure(AppError.Parse)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Outcome.Failure(AppError.Unknown(e.message))
            }
        }

        companion object {
            private const val HTTP_NOT_FOUND = 404
        }
    }
