package com.onemessages.app.ui.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.onemessages.app.R

/**
 * Optional app lock. It guards entry to the app only; it is not a substitute for the device
 * lock and cannot encrypt records that Android's SMS database owns.
 */
object AppLockAuthenticator {

    fun canAuthenticate(context: android.content.Context): Boolean {
        val allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        return BiometricManager.from(context).canAuthenticate(allowed) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            // Pre-Android 11 the prompt can still fall back to the device PIN/pattern/password,
            // which keeps the lock usable on phones without enrolled fingerprints.
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        runCatching { prompt.authenticate(builder.build()) }.onFailure { onFailure() }
    }
}

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canAuthenticate = remember(context) { AppLockAuthenticator.canAuthenticate(context) }
    val title = stringResource(R.string.privacy_lock_title)
    val subtitle = stringResource(R.string.privacy_lock_subtitle)
    val cancel = stringResource(R.string.cancel)

    fun authenticate() {
        if (!canAuthenticate) return
        // The app lock is a convenience gate, so a missing FragmentActivity host must not
        // strand the user on a screen that can never authenticate.
        val host = activity ?: run { onUnlocked(); return }
        AppLockAuthenticator.prompt(
            activity = host,
            title = title,
            subtitle = subtitle,
            cancelLabel = cancel,
            onSuccess = onUnlocked,
            onFailure = { },
        )
    }

    LaunchedEffect(canAuthenticate) {
        if (canAuthenticate) authenticate()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                if (canAuthenticate) {
                    Button(onClick = { authenticate() }) {
                        Text(stringResource(R.string.privacy_lock_unlock))
                    }
                } else {
                    Text(
                        stringResource(R.string.privacy_app_lock_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    // No secure screen lock exists on this device, so there is nothing to
                    // authenticate against. The alternative would lock the user out of
                    // their own messages permanently.
                    TextButton(onClick = onUnlocked) {
                        Text(stringResource(R.string.privacy_lock_continue))
                    }
                }
            }
        }
    }
}
