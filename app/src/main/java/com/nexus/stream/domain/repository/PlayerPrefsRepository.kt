package com.nexus.stream.domain.repository

import com.nexus.stream.data.player.VideoScale
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow

// Repository interface exposes 16 flows and setters for player and subtitle preferences.
@Suppress("TooManyFunctions")
interface PlayerPrefsRepository {
    val quickPlay: Flow<Boolean>
    val sort: Flow<StreamSort>
    val preferredLanguage: Flow<String?>
    val preferredQuality: Flow<Int?>
    val hideCam: Flow<Boolean>
    val seekStepSeconds: Flow<Int>
    val videoScale: Flow<VideoScale>
    val preferredAudioLanguage: Flow<String?>
    val preferredSubtitleLanguage: Flow<String?>
    val subtitleStyle: Flow<SubtitleStyle>

    suspend fun setQuickPlay(enabled: Boolean)

    suspend fun setSort(sort: StreamSort)

    suspend fun setPreferredLanguage(code: String?)

    suspend fun setPreferredQuality(quality: Int?)

    suspend fun setHideCam(hideCam: Boolean)

    suspend fun setSeekStepSeconds(seconds: Int)

    suspend fun setVideoScale(scale: VideoScale)

    suspend fun setPreferredAudioLanguage(code: String?)

    suspend fun setPreferredSubtitleLanguage(code: String?)

    suspend fun setSubtitleSize(size: SubtitleSize)

    suspend fun setSubtitleColor(id: String)

    suspend fun setSubtitleBackground(background: SubtitleBackground)

    suspend fun setSubtitleEdge(edge: SubtitleEdge)

    suspend fun setSubtitleBold(bold: Boolean)
}
