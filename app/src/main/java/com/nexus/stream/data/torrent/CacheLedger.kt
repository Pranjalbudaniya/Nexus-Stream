package com.nexus.stream.data.torrent

interface CacheLedger {
    suspend fun get(key: String): LedgerEntry?

    suspend fun put(entry: LedgerEntry)

    suspend fun remove(key: String)

    suspend fun all(): List<LedgerEntry>
}
