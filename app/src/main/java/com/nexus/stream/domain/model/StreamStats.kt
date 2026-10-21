package com.nexus.stream.domain.model

data class StreamStats(
    val speedBps: Long,
    val peers: Int,
    val aheadBytes: Long,
    val downloadedBytes: Long,
    val fileSizeBytes: Long,
    val waitMs: Long,
)
