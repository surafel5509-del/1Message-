package com.onemessages.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationMetaEntity::class,
        DraftEntity::class,
        CategoryRuleEntity::class,
        DiagnosticEntity::class,
        OutboxEntity::class,
        StarredMessageEntity::class,
    ],
    version = OneMessagesDatabase.VERSION,
    exportSchema = true,
)
abstract class OneMessagesDatabase : RoomDatabase() {
    abstract fun conversationMetaDao(): ConversationMetaDao
    abstract fun draftDao(): DraftDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun diagnosticsDao(): DiagnosticsDao
    abstract fun outboxDao(): OutboxDao
    abstract fun starredMessageDao(): StarredMessageDao

    companion object {
        const val NAME = "onemessages.db"
        const val VERSION = 2

        /**
         * v2 added per-conversation blocking, priority notifications and category overrides.
         * Written as an additive ALTER so existing pins/drafts survive the upgrade.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversation_meta ADD COLUMN priority INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE conversation_meta ADD COLUMN isBlocked INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE conversation_meta ADD COLUMN categoryOverride TEXT"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `starred_messages` (
                        `messageId` INTEGER NOT NULL,
                        `threadId` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_starred_messages_threadId` " +
                        "ON `starred_messages` (`threadId`)"
                )
            }
        }

        /** Applied only on fresh installs / destructive test harnesses. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
