package com.onemessages.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.onemessages.app.R
import com.onemessages.app.ui.theme.Brand

/**
 * Contact avatar: photo when the contact has one and avatars are enabled, otherwise
 * deterministic initials on a tinted gradient so the inbox stays recognisable.
 */
@Composable
fun Avatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showPhoto: Boolean = true,
    ring: Boolean = false,
) {
    val initials = remember(name) { initialsOf(name) }
    val gradient = remember(name) { gradientFor(name) }
    val photo = if (showPhoto) rememberContactPhoto(photoUri) else null
    val description = androidx.compose.ui.platform.LocalContext.current
        .getString(R.string.cd_contact_avatar, name.ifBlank { "#" })
    val ringColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (ring) {
                    Modifier
                        .clip(CircleShape)
                        .background(ringColor)
                        .padding(3.dp)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient))
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * (size.value / 48f)
                ),
            )
        }
    }
}

fun initialsOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "#"
    val letters = trimmed.filter { it.isLetterOrDigit() }
    if (letters.isEmpty()) return "#"
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> (parts[0].firstOrNull()?.toString().orEmpty() +
            parts[1].firstOrNull()?.toString().orEmpty()).uppercase()
        else -> letters.take(2).uppercase()
    }
}

private fun gradientFor(seed: String): List<Color> {
    val palettes = listOf(
        listOf(Brand.AuroraStart, Brand.AuroraMid),
        listOf(Brand.SkyBlue, Color(0xFF6FC9FF)),
        listOf(Color(0xFF2ED3A3), Color(0xFF12A180)),
        listOf(Color(0xFFFF8A65), Color(0xFFE2572B)),
        listOf(Color(0xFFF062A5), Color(0xFFB8306B)),
        listOf(Color(0xFFF2A93B), Color(0xFFC47B00)),
    )
    val index = (seed.hashCode().toLong() and 0x7FFFFFFF).mod(palettes.size)
    return palettes[index]
}
