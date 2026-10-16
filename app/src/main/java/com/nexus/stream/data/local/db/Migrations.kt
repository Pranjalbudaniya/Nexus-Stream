package com.nexus.stream.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `library_items` ADD COLUMN `listStatus` TEXT NOT NULL DEFAULT 'PLAN_TO_WATCH'",
            )
        }
    }
