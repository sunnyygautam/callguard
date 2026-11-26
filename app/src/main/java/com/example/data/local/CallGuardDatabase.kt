package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local Room Database storing user-managed spam number series/prefixes and
 * historical logs of automatically blocked incoming calls.
 *
 * Uses a thread-safe double-checked singleton so both the Compose UI (via ViewModel)
 * and Android's [com.example.telecom.PrefixCallScreeningService] share the same instance.
 */
@Database(
    entities = [
        BlockedPrefixEntity::class,
        BlockedCallLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun blockedPrefixDao(): BlockedPrefixDao

    abstract fun blockedCallLogDao(): BlockedCallLogDao

    companion object {
        private const val DATABASE_NAME = "call_guard_prefixes.db"

        /**
         * Preserves existing saved prefix rules when upgrading from v1 to v2 by creating
         * the new `blocked_call_logs` table and its `blocked_at` index.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_call_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `phone_number` TEXT NOT NULL,
                        `normalized_number` TEXT NOT NULL,
                        `matched_prefix` TEXT NOT NULL,
                        `matched_label` TEXT NOT NULL,
                        `blocked_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_blocked_call_logs_blocked_at` ON `blocked_call_logs` (`blocked_at`)"
                )
            }
        }

        /**
         * Preserves existing prefix rules and blocked call history when upgrading from v2 to v3
         * by adding on-device ML spam probability columns to `blocked_call_logs`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `blocked_call_logs` ADD COLUMN `spam_probability` INTEGER NOT NULL DEFAULT -1"
                )
                db.execSQL(
                    "ALTER TABLE `blocked_call_logs` ADD COLUMN `ml_reason` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Preserves existing blocked prefix rules when upgrading from v3 to v4 by adding the
         * `match_type` column ('PREFIX' or 'EXACT_NUMBER') to `blocked_prefixes`.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `blocked_prefixes` ADD COLUMN `match_type` TEXT NOT NULL DEFAULT 'PREFIX'"
                )
            }
        }

        @Volatile
        private var instance: CallGuardDatabase? = null

        fun getInstance(context: Context): CallGuardDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallGuardDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { db ->
                        instance = db
                    }
            }
        }
    }
}
