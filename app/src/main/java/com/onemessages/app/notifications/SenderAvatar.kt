package com.onemessages.app.notifications

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.graphics.drawable.toBitmap
import com.onemessages.app.core.Logx

/**
 * Builds the round sender avatar shown in the notification's large-icon slot.
 *
 * Android only renders a Bitmap there, so the contact photo (when there is one) is decoded and
 * centre-cropped; otherwise we draw the same aurora gradient and initial the in-app avatar uses.
 * Everything happens on the caller's worker thread and never leaves the device.
 */
object SenderAvatar {

    private const val SIZE = 192

    fun load(context: Context, photoUri: String?, displayName: String): Bitmap {
        contactPhoto(context, photoUri)?.let { return circleCrop(it) }
        return monogram(displayName)
    }

    private fun contactPhoto(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(photoUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: contactPhotoByLookup(context, uri)
        }.onFailure { Logx.w("Notif", "contact photo unavailable", it) }.getOrNull()
    }

    /** Some providers hand out a contact URI rather than a display-photo URI. */
    private fun contactPhotoByLookup(context: Context, uri: Uri): Bitmap? = runCatching {
        val contactId = uri.lastPathSegment?.toLongOrNull() ?: return@runCatching null
        val contactUri = ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI,
            contactId,
        )
        ContactsContract.Contacts.openContactPhotoInputStream(
            context.contentResolver,
            contactUri,
            true,
        )?.use { stream -> BitmapFactory.decodeStream(stream) }
    }.getOrNull()

    private fun circleCrop(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val srcRect = Rect(
            (source.width - size) / 2,
            (source.height - size) / 2,
            (source.width + size) / 2,
            (source.height + size) / 2,
        )
        val dstRect = Rect(0, 0, SIZE, SIZE)
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.WHITE
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, paint)
        paint.shader = null
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, srcRect, dstRect, paint)
        return output
    }

    /** Initial on the brand gradient, matching the in-app avatar palette. */
    private fun monogram(displayName: String): Bitmap {
        val output = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, SIZE.toFloat(), SIZE.toFloat(),
                intArrayOf(0xFF8B5CFF.toInt(), 0xFF5F6BFF.toInt(), 0xFF2E9BFF.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, gradient)
        val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = SIZE * 0.44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val baseline = SIZE / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, SIZE / 2f, baseline, textPaint)
        return output
    }

    @Suppress("unused")
    private fun android.graphics.drawable.Drawable.toNotificationBitmap(): Bitmap = toBitmap(SIZE, SIZE)
}
