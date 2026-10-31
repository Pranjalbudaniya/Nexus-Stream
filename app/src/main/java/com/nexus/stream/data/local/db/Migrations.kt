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

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`url` TEXT, `headersJson` TEXT, `infoHash` TEXT, `trackersJson` TEXT, `fileIdx` INTEGER, " +
                    "`metaId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `metaName` TEXT, " +
                    "`posterUrl` TEXT, `videoId` TEXT NOT NULL, `season` INTEGER, `episode` INTEGER, " +
                    "`fileNameHint` TEXT, `fileName` TEXT NOT NULL, `totalBytes` INTEGER NOT NULL, " +
                    "`downloadedBytes` INTEGER NOT NULL, `state` TEXT NOT NULL, `pauseReason` TEXT, " +
                    "`failure` TEXT, `filePath` TEXT, `supportsResume` INTEGER NOT NULL, `etag` TEXT, " +
                    "`lastModified` TEXT, `allowMetered` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_downloads_metaId_videoId` ON `downloads` (`metaId`, `videoId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_downloads_state` ON `downloads` (`state`)",
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `downloads` ADD COLUMN `galleryUri` TEXT")
            db.execSQL("ALTER TABLE `downloads` ADD COLUMN `galleryState` TEXT NOT NULL DEFAULT 'NONE'")
        }
    }
