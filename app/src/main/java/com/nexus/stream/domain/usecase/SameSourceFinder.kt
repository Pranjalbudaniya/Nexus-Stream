package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.model.hashAddonKey
import com.nexus.stream.domain.model.supports
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.presentation.common.PlaybackRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

const val SAME_SOURCE_WAIT_MS = 12_000L

enum class SameSourceReason {
    ADDON_UNAVAILABLE,
    NO_MATCH,
    LOAD_FAILED,
    TIMEOUT,
}

sealed interface SameSourceResult {
    data class Found(
        val stream: ParsedStream,
    ) : SameSourceResult

    data class NotFound(
        val reason: SameSourceReason,
    ) : SameSourceResult
}

fun lastUsedAddonKey(
    fingerprint: SourceFingerprint,
    enabledKeys: Collection<String>,
): String? = enabledKeys.firstOrNull { hashAddonKey(it) in fingerprint.addonKeyHashes }

class SameSourceFinder
    @Inject
    constructor(
        private val streamRepository: StreamRepository,
        private val addonRepository: AddonRepository,
    ) {
        suspend fun find(
            request: PlaybackRequest,
            fingerprint: SourceFingerprint,
        ): SameSourceResult {
            if (!isAddonAvailable(request, fingerprint)) {
                return SameSourceResult.NotFound(SameSourceReason.ADDON_UNAVAILABLE)
            }
            return try {
                withTimeout(SAME_SOURCE_WAIT_MS) {
                    streamRepository
                        .streams(
                            type = request.type,
                            videoId = request.videoId,
                            forceRefresh = false,
                            onlyAddons = null,
                        ).mapNotNull { evaluateEmission(fingerprint, it) }
                        .firstOrNull() ?: SameSourceResult.NotFound(SameSourceReason.NO_MATCH)
                }
            } catch (_: TimeoutCancellationException) {
                SameSourceResult.NotFound(SameSourceReason.TIMEOUT)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SameSourceResult.NotFound(SameSourceReason.LOAD_FAILED)
            }
        }

        private suspend fun isAddonAvailable(
            request: PlaybackRequest,
            fingerprint: SourceFingerprint,
        ): Boolean {
            val activeAddons =
                try {
                    addonRepository.observeActive().first()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            val enabledKeys =
                activeAddons
                    .filter { it.manifest.supports("stream", request.type, request.videoId) }
                    .map { it.transportUrl }
            return lastUsedAddonKey(fingerprint, enabledKeys) != null
        }

        private fun evaluateEmission(
            fingerprint: SourceFingerprint,
            state: StreamsState,
        ): SameSourceResult? =
            when (
                val step =
                    decideResume(
                        autoResume = true,
                        fingerprint = fingerprint,
                        streams = state.items,
                        addonAnswered = state.done,
                        allDone = state.done,
                        waitExpired = false,
                        userPicked = false,
                        handled = false,
                    )
            ) {
                is ResumeStep.Start -> SameSourceResult.Found(step.stream)

                is ResumeStep.NotFound -> SameSourceResult.NotFound(SameSourceReason.NO_MATCH)

                ResumeStep.Waiting,
                ResumeStep.NotApplicable,
                -> null
            }
    }
