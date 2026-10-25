package com.onemessages.app.di

import android.content.Context
import androidx.room.Room
import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.CategoryRuleDao
import com.onemessages.app.data.local.ConversationMetaDao
import com.onemessages.app.data.local.DiagnosticsDao
import com.onemessages.app.data.local.DraftDao
import com.onemessages.app.data.local.OneMessagesDatabase
import com.onemessages.app.data.local.OutboxDao
import com.onemessages.app.data.local.LocalMessageDao
import com.onemessages.app.data.local.ScheduledMessageDao
import com.onemessages.app.data.local.StarredMessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OneMessagesDatabase {
        val database = Room.databaseBuilder(
            context,
            OneMessagesDatabase::class.java,
            OneMessagesDatabase.NAME,
        )
            .addMigrations(*OneMessagesDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        Logx.attach(database.diagnosticsDao())
        return database
    }

    @Provides
    fun provideConversationMetaDao(db: OneMessagesDatabase): ConversationMetaDao = db.conversationMetaDao()

    @Provides
    fun provideDraftDao(db: OneMessagesDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideCategoryRuleDao(db: OneMessagesDatabase): CategoryRuleDao = db.categoryRuleDao()

    @Provides
    fun provideDiagnosticsDao(db: OneMessagesDatabase): DiagnosticsDao = db.diagnosticsDao()

    @Provides
    fun provideOutboxDao(db: OneMessagesDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideStarredMessageDao(db: OneMessagesDatabase): StarredMessageDao = db.starredMessageDao()

    @Provides
    fun provideScheduledMessageDao(db: OneMessagesDatabase): ScheduledMessageDao = db.scheduledMessageDao()

    @Provides
    fun provideLocalMessageDao(db: OneMessagesDatabase): LocalMessageDao = db.localMessageDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
