package com.onemessages.app.ui.navigation

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutesTest {

    @Test
    fun conversationRouteEncodesTheAddress() {
        val route = Routes.conversation(12L, "+251 91 123\r4567")
        assertThat(route).startsWith("conversation/12?address=")
        // A raw '+' or newline would break Navigation's pattern matching.
        assertThat(route.substringAfter("address=")).doesNotContain(" ")
        assertThat(Uri.decode(route.substringAfter("address="))).isEqualTo("+251 91 123\r4567")
    }

    @Test
    fun newMessageRouteHandlesMissingValues() {
        assertThat(Routes.newMessage()).isEqualTo("newMessage?address=&body=")
    }

    @Test
    fun topLevelDestinationsAreStable() {
        assertThat(Routes.topLevel).containsExactly(
            Routes.CHATS, Routes.CONTACTS, Routes.STARRED, Routes.SETTINGS
        )
    }
}
