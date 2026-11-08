package com.scrbl.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scrbl.app.data.local.room.migrations.ScrbLMigrations

/**
 * Room database for ScrbL (`scrbl_notebook.db`).
 *
 * Strict data-integrity contract (10_ANDROID_PLATFORM_PRIVACY.md):
 * - `exportSchema = true` exports schema JSON to `app/schemas/`.
 * - Uses only explicit non-destructive migrations (`ScrbLMigrations.ALL_MIGRATIONS`).
 * - Uses explicit SQLite triggers to enforce immutability of core columns at the database level
 *   even if raw SQL were ever executed.
 */
@Database(
    entities = [RecallEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ScrbLDatabase : RoomDatabase() {

    abstract fun recallDao(): RecallDao

    companion object {
        const val DATABASE_NAME = "scrbl_notebook.db"

        @Volatile
        private var INSTANCE: ScrbLDatabase? = null

        fun getInstance(context: Context): ScrbLDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): ScrbLDatabase {
            return Room.databaseBuilder(
                appContext,
                ScrbLDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*ScrbLMigrations.ALL_MIGRATIONS)
                .addCallback(ImmutabilityTriggerCallback())
                .build()
        }
    }

    /**
     * Installs SQLite triggers that guard against accidental mutation of immutable recall fields
     * (`id`, `type`, `text_body`, `voice_relative_path`, `voice_title`, `created_at_epoch_millis`,
     * `captured_local_date`, `captured_zone_id`, `audio_duration_millis`) or row deletion.
     */
    class ImmutabilityTriggerCallback : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_recalls_prevent_immutable_update
                BEFORE UPDATE ON recalls
                WHEN OLD.id != NEW.id
                  OR OLD.type != NEW.type
                  OR IFNULL(OLD.text_body, '') != IFNULL(NEW.text_body, '')
                  OR IFNULL(OLD.voice_relative_path, '') != IFNULL(NEW.voice_relative_path, '')
                  OR IFNULL(OLD.voice_title, '') != IFNULL(NEW.voice_title, '')
                  OR OLD.created_at_epoch_millis != NEW.created_at_epoch_millis
                  OR OLD.captured_local_date != NEW.captured_local_date
                  OR OLD.captured_zone_id != NEW.captured_zone_id
                  OR IFNULL(OLD.audio_duration_millis, -1) != IFNULL(NEW.audio_duration_millis, -1)
                BEGIN
                    SELECT RAISE(ABORT, 'ScrbL immutable recall fields cannot be modified after save.');
                END;
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_recalls_prevent_delete
                BEFORE DELETE ON recalls
                BEGIN
                    SELECT RAISE(ABORT, 'ScrbL committed recalls cannot be deleted.');
                END;
                """.trimIndent()
            )
        }
    }
}
