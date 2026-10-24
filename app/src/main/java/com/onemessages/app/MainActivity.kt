package com.onemessages.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.onemessages.app.core.Logx
import com.onemessages.app.ui.AppRoot
import com.onemessages.app.ui.AppViewModel
import com.onemessages.app.ui.navigation.NavTarget
import com.onemessages.app.ui.navigation.ShareIntentParser
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host. The splash screen API shows the brand mark while the app decides
 * between onboarding, the app lock and the inbox, then Compose owns everything.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val deepLink = mutableStateOf<NavTarget?>(null)

    /** Set when the app leaves the foreground; used to decide whether the lock should return. */
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.initialising.value }
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AppRoot(
                viewModel = viewModel,
                deepLink = deepLink.value,
                onDeepLinkConsumed = { deepLink.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Permissions, the SMS role and SIMs can all change while we are backgrounded.
        viewModel.refreshPermissions()
        // Re-lock only after a real absence. Document pickers, permission dialogs and the
        // role chooser also stop the activity, and locking during those would throw away the
        // user's work mid-flow.
        val leftAt = backgroundedAt
        if (leftAt > 0L && SystemClock.elapsedRealtime() - leftAt >= LOCK_AFTER_MS) {
            viewModel.lockIfEnabled()
        }
        backgroundedAt = 0L
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    private companion object {
        /** Being away for less than this keeps the app unlocked (backup exports, pickers). */
        const val LOCK_AFTER_MS = 20_000L
    }

    private fun handleIntent(intent: Intent?) {
        val target = ShareIntentParser.fromIntent(intent) ?: return
        // Only the fact that a target exists is logged: never the number or the text.
        Logx.d("Main", "external intent handled")
        deepLink.value = target
    }
}
