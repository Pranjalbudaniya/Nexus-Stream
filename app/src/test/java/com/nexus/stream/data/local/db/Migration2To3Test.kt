package com.nexus.stream.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class Migration2To3Test {
    @Test
    fun migration2To3_createsTheTableAndIndices() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        MIGRATION_2_3.migrate(db)

        verify(atLeast = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `downloads`") })
        }
        verify(atLeast = 1) {
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_downloads_metaId_videoId`") })
        }
        verify(atLeast = 1) {
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_downloads_state`") })
        }
    }
}
