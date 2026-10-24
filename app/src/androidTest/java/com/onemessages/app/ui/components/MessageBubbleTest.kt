package com.onemessages.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onemessages.app.model.Direction
import com.onemessages.app.model.Message
import com.onemessages.app.model.MessageSource
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.ui.theme.OneMessagesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageBubbleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(status: MessageStatus) = Message(
        id = 1L,
        source = MessageSource.SMS,
        threadId = 3L,
        address = "+251911234567",
        body = "See you at 18:00",
        timestamp = 1_700_000_000_000L,
        direction = Direction.OUTGOING,
        status = status,
        read = true,
        starred = false,
    )

    private fun setBubble(status: MessageStatus, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            OneMessagesTheme {
                MessageBubble(
                    message = message(status),
                    senderName = "Sara",
                    isGroupedWithPrevious = false,
                    isGroupedWithNext = false,
                    isSelected = false,
                    selectionMode = false,
                    showSenderName = false,
                    onLongPress = {},
                    onClick = {},
                    onRetry = onRetry,
                    onSaveAttachment = {},
                )
            }
        }
    }

    @Test
    fun bodyIsRendered() {
        setBubble(MessageStatus.SENT)
        composeRule.onNodeWithText("See you at 18:00").assertIsDisplayed()
    }

    @Test
    fun failedMessageCanBeRetried() {
        var retried = false
        setBubble(MessageStatus.FAILED) { retried = true }
        val retryLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(com.onemessages.app.R.string.cd_failed_send)
        composeRule.onNodeWithContentDescription(retryLabel).performClick()
        composeRule.waitForIdle()
        assert(retried)
    }
}
