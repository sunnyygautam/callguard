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
    version = 2,
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

        @Volatile
        private var instance: CallGuardDatabase? = null

        fun getInstance(context: Context): CallGuardDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallGuardDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { db ->
                        instance = db
                    }
            }
        }
    }
}
