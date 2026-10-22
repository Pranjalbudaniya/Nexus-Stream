package com.nexus.stream.domain.repository

import com.nexus.stream.data.player.VideoScale
import com.nexus.stream.domain.model.StreamSort
import kotlinx.coroutines.flow.Flow

interface PlayerPrefsRepository {
    val quickPlay: Flow<Boolean>
    val sort: Flow<StreamSort>
    val preferredLanguage: Flow<String?>
    val preferredQuality: Flow<Int?>
    val hideCam: Flow<Boolean>
    val seekStepSeconds: Flow<Int>
    val videoScale: Flow<VideoScale>

    suspend fun setQuickPlay(enabled: Boolean)

    suspend fun setSort(sort: StreamSort)

    suspend fun setPreferredLanguage(code: String?)

    suspend fun setPreferredQuality(quality: Int?)

    suspend fun setHideCam(hideCam: Boolean)

    suspend fun setSeekStepSeconds(seconds: Int)

    suspend fun setVideoScale(scale: VideoScale)
}
