package com.onemessages.app.domain

import com.google.common.truth.Truth.assertThat
import com.onemessages.app.model.CategoryRule
import com.onemessages.app.model.ConversationCategory
import org.junit.Test

class SmartOrganizerTest {

    private val rules = listOf(
        CategoryRule(keyword = "invoice", category = ConversationCategory.TRANSACTIONS),
        CategoryRule(keyword = "standup", category = ConversationCategory.WORK),
        CategoryRule(
            keyword = "disabled-rule",
            category = ConversationCategory.WORK,
            enabled = false,
        ),
    )

    @Test
    fun classify_matchesKeywordCaseInsensitively() {
        val result = SmartOrganizer.classify(
            body = "Your INVOICE is ready",
            address = "+251911234567",
            rules = rules,
            isKnownContact = true,
        )
        assertThat(result.categories).contains(ConversationCategory.TRANSACTIONS)
        assertThat(result.matchedKeyword).isEqualTo("invoice")
    }

    @Test
    fun classify_ignoresDisabledRules() {
        val result = SmartOrganizer.classify(
            body = "about that disabled-rule",
            address = "+251911234567",
            rules = rules,
            isKnownContact = true,
        )
        assertThat(result.categories).doesNotContain(ConversationCategory.WORK)
    }

    @Test
    fun classify_marksUnknownSendersWithoutJudgingContent() {
        val result = SmartOrganizer.classify(
            body = "hello",
            address = "8040",
            rules = emptyList(),
            isKnownContact = false,
        )
        assertThat(result.categories).contains(ConversationCategory.UNKNOWN)
        assertThat(result.hasFinancialWording).isFalse()
    }

    @Test
    fun classify_flagsFinancialWording() {
        val result = SmartOrganizer.classify(
            body = "You sent 500 birr to 0911223344",
            address = "+251911234567",
            rules = emptyList(),
            isKnownContact = true,
        )
        assertThat(result.hasFinancialWording).isTrue()
    }

    @Test
    fun findOtp_acceptsCodeNextToVerificationKeyword() {
        val match = SmartOrganizer.findOtp(
            body = "Your verification code is 482913. Do not share it.",
            messageId = 7,
            threadId = 3,
            now = 1_000L,
        )
        assertThat(match).isNotNull()
        assertThat(match!!.code).isEqualTo("482913")
        assertThat(match.messageId).isEqualTo(7)
        assertThat(match.detectedAt).isEqualTo(1_000L)
    }

    @Test
    fun findOtp_rejectsPlainConversationAndYears() {
        assertThat(
            SmartOrganizer.findOtp("see you at 19:30", messageId = 1, threadId = 1, now = 0)
        ).isNull()
        assertThat(
            SmartOrganizer.findOtp("your code for 2024 arrived", messageId = 1, threadId = 1, now = 0)
        ).isNull()
    }

    @Test
    fun findOtp_acceptsAForwardedCodeWithNoContext() {
        val match = SmartOrganizer.findOtp("482913", messageId = 2, threadId = 1, now = 5L)
        assertThat(match).isNotNull()
        assertThat(match!!.code).isEqualTo("482913")
    }

    @Test
    fun findOtp_ignoresANumberInAnOrdinaryLongMessage() {
        val body = "Call me on 123456 when you are free, the meeting moved to the afternoon today"
        assertThat(SmartOrganizer.findOtp(body, messageId = 1, threadId = 1, now = 0)).isNull()
    }

    @Test
    fun findOtp_requiresTheCodeToBeNearTheKeyword() {
        val body = "verification needed" + " ".repeat(60) + "482913"
        assertThat(SmartOrganizer.findOtp(body, messageId = 1, threadId = 1, now = 0)).isNull()
    }

    @Test
    fun findOtp_ignoresVeryLongMessages() {
        val long = "verification code 123456 " + "x".repeat(700)
        assertThat(SmartOrganizer.findOtp(long, messageId = 1, threadId = 1, now = 0)).isNull()
    }

    @Test
    fun mask_hidesEveryLetterAndDigit() {
        assertThat(SmartOrganizer.maskCode("482913")).isEqualTo("••••••")
        assertThat(SmartOrganizer.maskCode("AB-12")).isEqualTo("••-••")
    }
}
