@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemessages.app.ui.theme.LocalMotion
import com.onemessages.app.ui.theme.auroraBrush

/**
 * The app's only persistent navigation: three destinations, nothing else.
 *
 * Chats, Starred and Settings are the three things a messaging app is actually used for; the
 * previous four-tab bar spent a quarter of its width on Contacts, which is reachable from the
 * chats header. The bar floats above the wallpaper as a single rounded surface, the selected
 * destination expands into an aurora pill with its label, and unread chats show a count badge
 * so the number is visible without opening anything.
 */
enum class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    CHATS(Routes.CHATS, com.onemessages.app.R.string.nav_chats, Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble),
    STARRED(Routes.STARRED, com.onemessages.app.R.string.nav_starred, Icons.Outlined.StarOutline, Icons.Rounded.Star),
    SETTINGS(Routes.SETTINGS, com.onemessages.app.R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun AppNavBar(
    currentRoute: String?,
    unreadCount: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 14.dp,
        tonalElevation = 3.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavDestination.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    badge = if (destination == NavDestination.CHATS) unreadCount else 0,
                    motionEnabled = motion.enabled,
                    onClick = { onNavigate(destination.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    badge: Int,
    motionEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val duration = if (motionEnabled) 220 else 0
    val horizontal by animateDpAsState(
        targetValue = if (selected) 18.dp else 12.dp,
        animationSpec = tween(duration),
        label = "navPadding",
    )
    val vertical by animateDpAsState(
        targetValue = if (selected) 10.dp else 12.dp,
        animationSpec = tween(duration),
        label = "navVertical",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(duration),
        label = "navContent",
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(auroraBrush(dark), shape)
                } else {
                    Modifier
                }
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = horizontal, vertical = vertical),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                if (badge > 0) {
                    Badge(
                        count = badge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 10.dp),
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(destination.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Unread count bubble; caps at 99+ so it can never stretch the pill. */
@Composable
private fun Badge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.height(16.dp).widthIn(min = 16.dp),
    ) {
        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Vertical version of the same navigation for tablets and foldables. */
@Composable
fun AppNavRail(
    currentRoute: String?,
    unreadCount: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NavDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                val dark = isSystemInDarkTheme()
                val shape = RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .then(if (selected) Modifier.background(auroraBrush(dark), shape) else Modifier)
                        .clickable(role = Role.Tab) { onNavigate(destination.route) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    if (selected && destination == NavDestination.CHATS && unreadCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
