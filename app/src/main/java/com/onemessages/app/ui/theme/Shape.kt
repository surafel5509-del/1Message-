package com.onemessages.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Generous, consistent rounding: cards and sheets feel like one family rather than a mix of
 * ad-hoc radii. 8 dp stays available for tiny chips and thumbnails.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Bubbles {
    private const val LARGE = 22
    private const val SMALL = 6

    val incoming = RoundedCornerShape(
        topStart = SMALL.dp, topEnd = LARGE.dp, bottomEnd = LARGE.dp, bottomStart = LARGE.dp
    )
    val outgoing = RoundedCornerShape(
        topStart = LARGE.dp, topEnd = SMALL.dp, bottomEnd = LARGE.dp, bottomStart = LARGE.dp
    )

    /** Middle of a run: the side facing the sender stays square so the run reads as one block. */
    fun incoming(groupedAbove: Boolean, groupedBelow: Boolean): RoundedCornerShape = RoundedCornerShape(
        topStart = if (groupedAbove) SMALL.dp else SMALL.dp,
        topEnd = if (groupedAbove) SMALL.dp else LARGE.dp,
        bottomEnd = if (groupedBelow) SMALL.dp else LARGE.dp,
        bottomStart = LARGE.dp,
    )

    fun outgoing(groupedAbove: Boolean, groupedBelow: Boolean): RoundedCornerShape = RoundedCornerShape(
        topStart = if (groupedAbove) SMALL.dp else LARGE.dp,
        topEnd = SMALL.dp,
        bottomEnd = LARGE.dp,
        bottomStart = if (groupedBelow) SMALL.dp else LARGE.dp,
    )
}
