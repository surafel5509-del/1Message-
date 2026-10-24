package com.onemessages.app

import android.content.Intent
import android.os.Bundle
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
    }

    /** Called when the app comes back after the user has been away for a while. */
    override fun onStop() {
        super.onStop()
        viewModel.lockIfEnabled()
    }

    private fun handleIntent(intent: Intent?) {
        val target = ShareIntentParser.fromIntent(intent) ?: return
        // Only the fact that a target exists is logged: never the number or the text.
        Logx.d("Main", "external intent handled")
        deepLink.value = target
    }
}
