package com.scrbl.app.data.local.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migration registry.
 * Never use destructive migrations; register every future schema version transition here.
 */
object ScrbLMigrations {

    /**
     * Baseline forward-compatible no-op migration template for 1 -> 2 when needed in future versions.
     * Kept as an explicit verified migration object for test suite verification.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserves all existing recall rows without modification.
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_recalls_favourites` ON `recalls` (`is_favourite`, `created_at_epoch_millis`)"
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = emptyArray()
}
