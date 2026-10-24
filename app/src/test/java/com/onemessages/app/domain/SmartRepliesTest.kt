package com.onemessages.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartRepliesTest {

    @Test
    fun questionGetsYesAndNoFirst() {
        val replies = SmartReplies.suggest("Are you coming tonight?")
        assertThat(replies).hasSize(3)
        assertThat(replies.take(2)).containsExactly(SmartReplyKind.YES, SmartReplyKind.NO)
    }

    @Test
    fun greetingIsAnsweredWithAGreeting() {
        val replies = SmartReplies.suggest("Hey")
        assertThat(replies.first()).isEqualTo(SmartReplyKind.GREETING)
    }

    @Test
    fun callRequestSuggestsCallingBack() {
        val replies = SmartReplies.suggest("Can you call me when you are free")
        assertThat(replies.first()).isEqualTo(SmartReplyKind.CALL_ME)
    }

    @Test
    fun thanksIsAcknowledged() {
        val replies = SmartReplies.suggest("Thank you so much")
        assertThat(replies.first()).isEqualTo(SmartReplyKind.OK)
    }

    @Test
    fun unknownTextStillOffersNeutralAcknowledgement() {
        val replies = SmartReplies.suggest("The parcel is at the front desk")
        assertThat(replies).containsAtLeast(SmartReplyKind.OK, SmartReplyKind.THANKS)
    }

    @Test
    fun emptyInputSuggestsNothing() {
        assertThat(SmartReplies.suggest(null)).isEmpty()
        assertThat(SmartReplies.suggest("   ")).isEmpty()
    }

    @Test
    fun suggestionsRespectTheLimitAndNeverRepeat() {
        val replies = SmartReplies.suggest("Hi there", limit = 2)
        assertThat(replies).hasSize(2)
        assertThat(replies).containsNoDuplicates()
    }

    @Test
    fun amharicGreetingIsRecognised() {
        val replies = SmartReplies.suggest("ሰላም")
        assertThat(replies.first()).isEqualTo(SmartReplyKind.GREETING)
    }

    @Test
    fun zeroLimitSuggestsNothing() {
        assertThat(SmartReplies.suggest("Hey", limit = 0)).isEmpty()
    }
}
