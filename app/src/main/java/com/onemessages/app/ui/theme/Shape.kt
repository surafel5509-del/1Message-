package com.onemessages.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object Bubbles {
    val incoming = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val outgoing = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val incomingGrouped = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    val outgoingGrouped = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
}
