package com.onemessages.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.onemessages.app.core.Logx
import com.onemessages.app.model.AttachmentKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attachment access through the Storage Access Framework.
 *
 * We never ask for broad storage permissions: the photo picker and ACTION_OPEN_DOCUMENT hand
 * us one URI at a time. Saves go to a user-chosen document URI.
 */
@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    data class AttachmentMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
        val contentType: String?,
    )

    fun queryMetadata(uri: Uri): AttachmentMetadata = runCatching {
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                AttachmentMetadata(
                    displayName = if (nameIdx >= 0) c.getString(nameIdx) else null,
                    sizeBytes = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null,
                    contentType = resolver.getType(uri),
                )
            } else {
                AttachmentMetadata(null, null, resolver.getType(uri))
            }
        } ?: AttachmentMetadata(null, null, resolver.getType(uri))
    }.getOrElse {
        Logx.w("Attachment", "metadata query failed", it)
        AttachmentMetadata(null, null, resolver.getType(uri))
    }

    fun kindOf(contentType: String?): AttachmentKind = when {
        contentType == null -> AttachmentKind.OTHER
        contentType.startsWith("image/") -> AttachmentKind.IMAGE
        contentType.startsWith("video/") -> AttachmentKind.VIDEO
        contentType.startsWith("audio/") -> AttachmentKind.AUDIO
        contentType.contains("vcard") -> AttachmentKind.VCARD
        contentType.startsWith("text/") || contentType == "application/pdf" -> AttachmentKind.DOCUMENT
        else -> AttachmentKind.OTHER
    }

    fun isTransportableOverMms(contentType: String?, sizeBytes: Long?): Boolean {
        if (contentType == null) return false
        val supported = contentType.startsWith("image/") ||
            contentType.startsWith("video/") ||
            contentType.startsWith("audio/") ||
            contentType.contains("vcard")
        return supported && (sizeBytes == null || sizeBytes <= MAX_MMS_BYTES)
    }

    /**
     * Copies an attachment into the app cache for preview. Bounded so a huge file cannot
     * exhaust storage; returns null when the copy fails.
     */
    suspend fun cacheForPreview(uri: Uri, maxBytes: Long = MAX_PREVIEW_BYTES): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "previews").apply { mkdirs() }
                val extension = resolver.getType(uri)?.substringAfterLast('/')?.take(8).orEmpty()
                val target = File(dir, "preview_${uri.hashCode().toUInt()}" + if (extension.isNotEmpty()) ".$extension" else "")
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) error("attachment too large for preview")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@withContext null
                target
            }.getOrElse {
                Logx.w("Attachment", "preview cache failed", it)
                null
            }
        }

    /** Copies a stored MMS part to a location the user picked with ACTION_CREATE_DOCUMENT. */
    suspend fun saveToDocument(source: Uri, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(destination, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: error("destination not writable")
            } ?: error("source not readable")
            true
        }.getOrElse {
            Logx.w("Attachment", "save failed", it)
            false
        }
    }

    fun viewIntent(uri: Uri, mimeType: String?): Intent? {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun shareIntent(uri: Uri, mimeType: String?): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * FileProvider URI for a cached file, used when handing attachments to other apps.
     * The provider only exposes cache/mms, never the app's databases.
     */
    fun providerUriFor(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
    }.getOrNull()

    fun suggestedFileName(uri: Uri, contentType: String?): String {
        val metadata = queryMetadata(uri)
        val base = metadata.displayName?.takeIf { it.isNotBlank() }
            ?: "attachment_${System.currentTimeMillis()}"
        return sanitizeFileName(base, contentType)
    }

    fun sanitizeFileName(name: String, contentType: String?): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").take(120)
        if (cleaned.contains('.')) return cleaned
        val extension = when {
            contentType?.startsWith("image/jpeg") == true -> "jpg"
            contentType?.startsWith("image/png") == true -> "png"
            contentType?.startsWith("video/") == true -> "mp4"
            contentType?.startsWith("audio/") == true -> "m4a"
            contentType == "application/pdf" -> "pdf"
            contentType?.contains("vcard") == true -> "vcf"
            else -> "bin"
        }
        return "$cleaned.$extension"
    }

    fun openUri(uriString: String): Uri = Uri.parse(uriString)

    companion object {
        /** Common carrier limit for a single MMS; carriers may be lower and the platform enforces it. */
        const val MAX_MMS_BYTES = 600L * 1024L

        /** Previews above this size are not copied into the app cache. */
        const val MAX_PREVIEW_BYTES = 25L * 1024L * 1024L
    }
}
