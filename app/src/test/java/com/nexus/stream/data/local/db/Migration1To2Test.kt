package com.nexus.stream.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class Migration1To2Test {
    @Test
    fun migration1To2_executesExpectedAlterTableSql() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        MIGRATION_1_2.migrate(db)

        verify(exactly = 1) {
            db.execSQL(
                "ALTER TABLE `library_items` ADD COLUMN `listStatus` TEXT NOT NULL DEFAULT 'PLAN_TO_WATCH'",
            )
        }
    }
}
