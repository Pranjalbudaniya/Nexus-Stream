package com.nexus.stream.data.local.db

import androidx.room.withTransaction

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: NexusDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
