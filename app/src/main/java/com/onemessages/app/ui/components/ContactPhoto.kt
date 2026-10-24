package com.onemessages.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal, dependency-free contact photo loader.
 *
 * Photos come from the contacts provider as content:// thumbnails. We decode a downsampled
 * bitmap off the main thread and cache it in memory so scrolling the inbox stays smooth.
 * No network access is involved or possible (the app has no INTERNET permission).
 */
private val photoCache = object : LruCache<String, Bitmap>(64) {
    override fun sizeOf(key: String, value: Bitmap): Int = 1
}

@Composable
fun rememberContactPhoto(uri: String?, targetSizePx: Int = 192): ImageBitmap? {
    val context = LocalContext.current
    val cached = remember(uri) { uri?.let { photoCache.get(it) } }
    val state by produceState<ImageBitmap?>(
        initialValue = cached?.asImageBitmap(),
        key1 = uri,
        key2 = targetSizePx,
    ) {
        if (uri.isNullOrBlank() || cached != null) return@produceState
        value = loadDownsampled(context, uri, targetSizePx)?.asImageBitmap()
    }
    return state
}

private suspend fun loadDownsampled(context: Context, uri: String, targetSize: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetSize && bounds.outHeight / (sample * 2) >= targetSize) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            bitmap?.also { photoCache.put(uri, it) }
        }.getOrNull()
    }

@Composable
fun rememberImageBitmapState(uri: String?): androidx.compose.runtime.State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState(initialValue = null, uri) {
        if (uri.isNullOrBlank()) return@produceState
        value = loadDownsampled(context, uri, 1024)?.asImageBitmap()
    }
}

/** Clears the in-memory photo cache; called when contacts permission is revoked. */
fun clearPhotoCache() {
    photoCache.evictAll()
}

@Suppress("unused")
@Composable
private fun UnusedCacheState(): androidx.compose.runtime.MutableState<ImageBitmap?> =
    remember { mutableStateOf(null) }
