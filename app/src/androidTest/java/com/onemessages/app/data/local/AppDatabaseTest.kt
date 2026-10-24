package com.onemessages.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the app-owned database on a real Android runtime: drafts that survive process
 * death, the outbox states the sender relies on, stars and rule uniqueness.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: OneMessagesDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMessagesDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun draftIsPersistedAndOverwritten() = runTest {
        val dao = db.draftDao()
        dao.upsert(DraftEntity(threadId = 42L, text = "first", updatedAt = 1L))
        assertThat(dao.get(42L)?.text).isEqualTo("first")

        dao.upsert(DraftEntity(threadId = 42L, text = "second", updatedAt = 2L))
        assertThat(dao.observe(42L).first()?.text).isEqualTo("second")
        assertThat(dao.observeAll().first()).hasSize(1)

        dao.delete(42L)
        assertThat(dao.get(42L)).isNull()
    }

    @Test
    fun outboxRowsMoveThroughStates() = runTest {
        val dao = db.outboxDao()
        val id = dao.insert(
            OutboxEntity(
                threadId = 7L,
                address = "+251911234567",
                body = "hello",
                subscriptionId = 1,
                state = "QUEUED",
            )
        )
        assertThat(dao.pendingForThread(7L)).hasSize(1)

        dao.updateState(id, "SENT", errorCode = null, attemptDelta = 1)
        assertThat(dao.get(id)?.state).isEqualTo("SENT")
        assertThat(dao.get(id)?.attempts).isEqualTo(1)
        assertThat(dao.pendingForThread(7L)).isEmpty()
    }

    @Test
    fun conversationMetaSurvivesAndArchives() = runTest {
        val dao = db.conversationMetaDao()
        dao.upsert(
            ConversationMetaEntity(
                threadId = 11L,
                isPinned = true,
                isArchived = false,
                isMuted = false,
            )
        )
        assertThat(dao.get(11L)?.isPinned).isTrue()
        assertThat(dao.archivedThreadIds()).isEmpty()

        dao.upsert(
            ConversationMetaEntity(
                threadId = 11L,
                isPinned = false,
                isArchived = true,
                isMuted = false,
            )
        )
        assertThat(dao.archivedThreadIds()).containsExactly(11L)
    }

    @Test
    fun keywordRulesAreUnique() = runTest {
        val dao = db.categoryRuleDao()
        dao.upsert(CategoryRuleEntity(keyword = "invoice", category = "TRANSACTIONS"))
        dao.upsert(CategoryRuleEntity(keyword = "invoice", category = "WORK"))
        assertThat(dao.count()).isEqualTo(1)
    }

    @Test
    fun starsRoundTrip() = runTest {
        val dao = db.starredMessageDao()
        dao.upsert(StarredMessageEntity(messageId = 5L, threadId = 2L, source = "SMS", addedAt = 1L))
        assertThat(dao.isStarred(5L)).isTrue()
        assertThat(dao.observeAll().first()).hasSize(1)
        dao.delete(5L)
        assertThat(dao.isStarred(5L)).isFalse()
    }
}
