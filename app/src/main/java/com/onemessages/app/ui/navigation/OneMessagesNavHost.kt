package com.onemessages.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onemessages.app.R
import com.onemessages.app.model.SmsRoleState
import com.onemessages.app.ui.contacts.ContactsRoute
import com.onemessages.app.ui.conversation.ConversationRoute
import com.onemessages.app.ui.conversation.NewMessageRoute
import com.onemessages.app.ui.inbox.InboxRoute
import com.onemessages.app.ui.search.SearchRoute
import com.onemessages.app.ui.settings.AboutRoute
import com.onemessages.app.ui.settings.AppearanceSettingsRoute
import com.onemessages.app.ui.settings.BackupSettingsRoute
import com.onemessages.app.ui.settings.MessagingSettingsRoute
import com.onemessages.app.ui.settings.NotificationSettingsRoute
import com.onemessages.app.ui.settings.OrganizationSettingsRoute
import com.onemessages.app.ui.settings.PrivacySettingsRoute
import com.onemessages.app.ui.settings.SettingsHomeRoute
import com.onemessages.app.ui.settings.StorageSettingsRoute

@Composable
fun OneMessagesNavHost(
    navController: NavHostController = rememberNavController(),
    hasSmsPermission: Boolean,
    roleState: SmsRoleState,
    hasContactsPermission: Boolean,
    isExpandedLayout: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onRequestRole: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    deepLink: NavTarget? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val badgeViewModel: NavBadgeViewModel = hiltViewModel()
    val unreadTotal by badgeViewModel.unread.collectAsStateWithLifecycle()

    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        when {
            target.threadId != null && target.threadId > 0 -> navController.navigate(
                Routes.conversation(target.threadId, target.address.orEmpty())
            ) { launchSingleTop = true }
            !target.address.isNullOrBlank() || !target.body.isNullOrBlank() -> navController.navigate(
                Routes.newMessage(target.address, target.body)
            ) { launchSingleTop = true }
        }
        onDeepLinkConsumed()
    }

    val openAppSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
            )
        }
        Unit
    }
    val currentRoute = backStackEntry?.destination?.route
    val showBars = currentRoute in Routes.topLevel

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBars && !isExpandedLayout) {
                AppNavBar(
                    currentRoute = currentRoute,
                    unreadCount = unreadTotal,
                    onNavigate = ::navigateTopLevel,
                )
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showBars && isExpandedLayout) {
                AppNavRail(
                    currentRoute = currentRoute,
                    unreadCount = unreadTotal,
                    onNavigate = ::navigateTopLevel,
                )
            }
            Box(Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = Routes.CHATS) {
                    composable(Routes.CHATS) {
                        InboxRoute(
                            filterArg = null,
                            onOpenConversation = { threadId, address ->
                                navController.navigate(Routes.conversation(threadId, address))
                            },
                            onCompose = { navController.navigate(Routes.newMessage()) },
                            onSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenArchived = { navController.navigate(Routes.ARCHIVED) },
                            onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                            onOpenSettings = { navigateTopLevel(Routes.SETTINGS) },
                            hasSmsPermission = hasSmsPermission,
                            roleState = roleState,
                            onRequestSmsPermission = onRequestSmsPermission,
                            onRequestRole = onRequestRole,
                        )
                    }
                    composable(Routes.STARRED) {
                        InboxRoute(
                            filterArg = "starred",
                            onOpenConversation = { threadId, address ->
                                navController.navigate(Routes.conversation(threadId, address))
                            },
                            onCompose = { navController.navigate(Routes.newMessage()) },
                            onSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenArchived = { navController.navigate(Routes.ARCHIVED) },
                            onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                            onOpenSettings = { navigateTopLevel(Routes.SETTINGS) },
                            hasSmsPermission = hasSmsPermission,
                            roleState = roleState,
                            onRequestSmsPermission = onRequestSmsPermission,
                            onRequestRole = onRequestRole,
                        )
                    }
                    composable(Routes.ARCHIVED) {
                        InboxRoute(
                            filterArg = "archived",
                            onOpenConversation = { threadId, address ->
                                navController.navigate(Routes.conversation(threadId, address))
                            },
                            onCompose = { navController.navigate(Routes.newMessage()) },
                            onSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenArchived = { },
                            onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                            onOpenSettings = { navigateTopLevel(Routes.SETTINGS) },
                            hasSmsPermission = hasSmsPermission,
                            roleState = roleState,
                            onRequestSmsPermission = onRequestSmsPermission,
                            onRequestRole = onRequestRole,
                        )
                    }
                    composable(Routes.CONTACTS) {
                        ContactsRoute(
                            onBack = { navController.popBackStack() },
                            onMessage = { address ->
                                navController.navigate(Routes.newMessage(address))
                            },
                            onRequestContactsPermission = onRequestContactsPermission,
                            onOpenSettings = openAppSettings,
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchRoute(
                            onBack = { navController.popBackStack() },
                            onOpenConversation = { threadId, address ->
                                navController.navigate(Routes.conversation(threadId, address))
                            },
                            onMessageContact = { address ->
                                navController.navigate(Routes.newMessage(address))
                            },
                        )
                    }
                    composable(
                        route = Routes.NEW_MESSAGE,
                        arguments = listOf(
                            navArgument("address") { type = NavType.StringType; defaultValue = "" },
                            navArgument("body") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) {
                        NewMessageRoute(
                            onBack = { navController.popBackStack() },
                            onOpenConversation = { threadId, address ->
                                navController.navigate(Routes.conversation(threadId, address)) {
                                    popUpTo(Routes.NEW_MESSAGE) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(
                        route = Routes.CONVERSATION,
                        arguments = listOf(
                            navArgument("threadId") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("address") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) {
                        ConversationRoute(
                            hasSmsPermission = hasSmsPermission,
                            onBack = { navController.popBackStack() },
                            onRequestPermission = onRequestSmsPermission,
                            onForward = { body ->
                                navController.navigate(Routes.newMessage(body = body))
                            },
                            onOpenContact = { identity ->
                                val intent = if (identity.contactId != null) {
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            "content://com.android.contacts/contacts/${identity.contactId}"
                                        ),
                                    )
                                } else {
                                    android.content.Intent(
                                        android.content.Intent.ACTION_INSERT,
                                        android.provider.ContactsContract.Contacts.CONTENT_URI,
                                    )
                                }
                                runCatching { context.startActivity(intent) }
                            },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsHomeRoute(
                            onBack = null,
                            onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                            onOpenNotifications = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                            onOpenPrivacy = { navController.navigate(Routes.SETTINGS_PRIVACY) },
                            onOpenSim = { navController.navigate(Routes.SETTINGS_MESSAGING) },
                            onOpenOrganization = { navController.navigate(Routes.SETTINGS_ORGANIZATION) },
                            onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                            onOpenStorage = { navController.navigate(Routes.SETTINGS_STORAGE) },
                            onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
                            onRequestRole = onRequestRole,
                        )
                    }
                    composable(Routes.SETTINGS_APPEARANCE) {
                        AppearanceSettingsRoute(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS_NOTIFICATIONS) {
                        NotificationSettingsRoute(
                            onBack = { navController.popBackStack() },
                            onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
                        )
                    }
                    composable(Routes.SETTINGS_PRIVACY) {
                        PrivacySettingsRoute(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS_MESSAGING) {
                        MessagingSettingsRoute(
                            onBack = { navController.popBackStack() },
                            onRequestPhonePermission = onRequestPhonePermission,
                        )
                    }
                    composable(Routes.SETTINGS_ORGANIZATION) {
                        OrganizationSettingsRoute(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS_BACKUP) {
                        BackupSettingsRoute(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS_STORAGE) {
                        StorageSettingsRoute(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS_ABOUT) {
                        AboutRoute(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
