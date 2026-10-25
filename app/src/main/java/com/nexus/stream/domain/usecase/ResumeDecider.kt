package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.SourceFingerprint

sealed interface ResumeDecider

sealed interface ResumeStep : ResumeDecider {
    data object NotApplicable : ResumeStep

    data object Waiting : ResumeStep

    data class Start(
        val stream: ParsedStream,
    ) : ResumeStep

    data object NotFound : ResumeStep
}

@Suppress("LongParameterList")
fun decideResume(
    autoResume: Boolean,
    fingerprint: SourceFingerprint?,
    streams: List<ParsedStream>,
    addonAnswered: Boolean,
    allDone: Boolean,
    waitExpired: Boolean,
    userPicked: Boolean,
    handled: Boolean,
): ResumeStep {
    val notApplicable = !autoResume || fingerprint == null || userPicked
    if (notApplicable || handled) return ResumeStep.NotApplicable
    val match = matchSource(fingerprint, streams)
    return when {
        match != null -> ResumeStep.Start(match.stream)
        addonAnswered || allDone || waitExpired -> ResumeStep.NotFound
        else -> ResumeStep.Waiting
    }
}

fun lastUsedKey(
    fingerprint: SourceFingerprint?,
    streams: List<ParsedStream>,
): String? = fingerprint?.let { matchSource(it, streams)?.stream?.key }
