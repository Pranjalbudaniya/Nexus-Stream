package com.nexus.stream.domain.usecase

import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.domain.model.OnlineSubtitle
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.supports
import com.nexus.stream.domain.repository.AddonRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SubtitleHints(
    val filename: String?,
    val videoHash: String?,
    val videoSize: Long?,
)

private const val SUBTITLE_TIMEOUT_MS = 8_000L
private const val MAX_ONLINE_SUBTITLES = 60

class LoadOnlineSubtitles
    @Inject
    constructor(
        private val addons: AddonRepository,
        private val remote: AddonRemoteDataSource,
    ) {
        suspend fun hasMatchingAddons(
            type: String,
            videoId: String,
        ): Boolean {
            val enabledAddons = addons.observeEnabled().first()
            return enabledAddons.any { addon ->
                addon.manifest.supports("subtitles", type, videoId)
            }
        }

        suspend operator fun invoke(
            type: String,
            videoId: String,
            hints: SubtitleHints?,
        ): List<OnlineSubtitle> {
            val enabledAddons = addons.observeEnabled().first()
            val matchingAddons =
                enabledAddons.filter { addon ->
                    addon.manifest.supports("subtitles", type, videoId)
                }
            if (matchingAddons.isEmpty()) {
                return emptyList()
            }

            val extra =
                buildMap {
                    hints?.videoHash?.let { put("videoHash", it) }
                    hints?.videoSize?.let { put("videoSize", it.toString()) }
                    hints?.filename?.let { put("filename", it) }
                }

            val resultsPerAddon =
                coroutineScope {
                    matchingAddons
                        .map { addon ->
                            async {
                                try {
                                    withTimeoutOrNull(SUBTITLE_TIMEOUT_MS) {
                                        val outcome =
                                            remote.subtitles(
                                                addon.transportUrl,
                                                type,
                                                videoId,
                                                extra,
                                            )
                                        when (outcome) {
                                            is Outcome.Success -> {
                                                outcome.value.map { sub ->
                                                    OnlineSubtitle(subtitle = sub, addonName = addon.manifest.name)
                                                }
                                            }

                                            is Outcome.Failure -> {
                                                emptyList()
                                            }
                                        }
                                    }.orEmpty()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }.map { it.await() }
                }

            return resultsPerAddon
                .flatten()
                .distinctBy { it.subtitle.url }
                .take(MAX_ONLINE_SUBTITLES)
        }
    }
