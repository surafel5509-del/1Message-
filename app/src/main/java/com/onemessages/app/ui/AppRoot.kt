package com.onemessages.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.permissions.AppPermissions
import com.onemessages.app.permissions.PermissionGroup
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.lock.AppLockScreen
import com.onemessages.app.ui.navigation.NavTarget
import com.onemessages.app.ui.navigation.OneMessagesNavHost
import com.onemessages.app.ui.onboarding.OnboardingScreen
import com.onemessages.app.ui.theme.OneMessagesTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AppRoot(
    viewModel: AppViewModel,
    deepLink: NavTarget?,
    onDeepLinkConsumed: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val role by viewModel.roleState.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val initialising by viewModel.initialising.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity

    // The language lives in the base context, so a change only takes effect on recreate.
    var appliedLanguage by remember { mutableStateOf(settings.languageTag) }
    LaunchedEffect(settings.languageTag) {
        if (settings.languageTag != appliedLanguage) {
            appliedLanguage = settings.languageTag
            activity?.recreate()
        }
    }
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isExpanded = windowSizeClass != null &&
        windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }
    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onSmsRoleResult(true) }

    fun request(group: PermissionGroup) {
        val permissionsToRequest = AppPermissions.permissionsFor(group)
        if (permissionsToRequest.isEmpty()) return
        when (group) {
            PermissionGroup.SMS -> smsLauncher.launch(permissionsToRequest)
            PermissionGroup.CONTACTS -> contactsLauncher.launch(permissionsToRequest)
            PermissionGroup.NOTIFICATIONS -> notificationsLauncher.launch(permissionsToRequest)
            PermissionGroup.PHONE_STATE -> phoneLauncher.launch(permissionsToRequest)
        }
    }

    fun requestRole() {
        val intent = viewModel.createRoleIntent()
        if (intent != null) {
            roleLauncher.launch(intent)
        } else {
            runCatching { context.startActivity(viewModel.createLegacyRoleIntent()) }
        }
    }

    OneMessagesTheme(settings = settings) {
        when {
            initialising -> SplashContent()
            locked -> AppLockScreen(onUnlocked = viewModel::unlock)
            !settings.onboardingComplete -> OnboardingScreen(
                hasSms = permissions.sms,
                hasContacts = permissions.contacts,
                hasNotifications = permissions.notifications,
                hasPhoneState = permissions.phoneState,
                roleState = role,
                onRequestGroup = ::request,
                onRequestRole = ::requestRole,
                onFinish = { viewModel.completeOnboarding() },
            )
            else -> {
                OneMessagesNavHost(
                    hasSmsPermission = permissions.sms,
                    hasNotificationPermission = permissions.notifications,
                    roleState = role,
                    hasContactsPermission = permissions.contacts,
                    isExpandedLayout = isExpanded,
                    onRequestSmsPermission = { request(PermissionGroup.SMS) },
                    onRequestContactsPermission = { request(PermissionGroup.CONTACTS) },
                    onRequestPhonePermission = { request(PermissionGroup.PHONE_STATE) },
                    onRequestRole = ::requestRole,
                    onOpenSystemNotificationSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                        Unit
                    },
                    deepLink = deepLink,
                    onDeepLinkConsumed = onDeepLinkConsumed,
                )
            }
        }
    }
}

@Composable
private fun SplashContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name = "1", photoUri = null, size = 76.dp, showPhoto = false)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name_brand),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}
