package com.onemessages.app.model

/** Where a message physically lives. We never copy bodies into Room. */
enum class MessageSource { SMS, MMS }

enum class Direction { INCOMING, OUTGOING, DRAFT }

/**
 * User-visible delivery state. [UNKNOWN] means the platform provided no information, which
 * is common for incoming messages and for ROMs that do not store delivery reports.
 */
enum class MessageStatus { NONE, QUEUED, SENDING, SENT, DELIVERED, FAILED, RECEIVED, UNKNOWN }

enum class AttachmentKind { IMAGE, VIDEO, AUDIO, VCARD, DOCUMENT, OTHER }

data class Attachment(
    val id: Long,
    val messageId: Long,
    val contentType: String,
    val uriString: String,
    val fileName: String?,
    val sizeBytes: Long?,
    val kind: AttachmentKind,
    /** True when the part exists in the MMS database but the bytes are not on disk yet. */
    val isPendingDownload: Boolean = false,
)

/**
 * A single SMS or MMS message read from Android's own provider.
 *
 * [threadId] is the system thread id; negative ids are reserved for synthetic entries such
 * as an unsent draft in a brand new conversation.
 */
data class Message(
    val id: Long,
    val source: MessageSource,
    val threadId: Long,
    val address: String,
    val body: String,
    val subject: String? = null,
    val timestamp: Long,
    val direction: Direction,
    val status: MessageStatus,
    val read: Boolean,
    val starred: Boolean,
    val subscriptionId: Int = -1,
    val attachments: List<Attachment> = emptyList(),
    val isMms: Boolean = source == MessageSource.MMS,
    /** MMS only: 128 = downloaded, 129 = download failed, 130 = unseen, 131 = locked. */
    val mmsBox: Int = 0,
    val mmsContentLocation: String? = null,
    val errorCode: Int? = null,
    val hasOtp: Boolean = false,
) {
    val hasAttachments: Boolean get() = attachments.isNotEmpty()

    val previewText: String
        get() = when {
            body.isNotBlank() -> body
            attachments.isNotEmpty() -> attachments.first().let { it.fileName ?: it.contentType }
            subject?.isNotBlank() == true -> subject
            else -> ""
        }
}

/** Result of a send attempt, surfaced to the UI instead of a fake "delivered". */
sealed interface SendOutcome {
    data class Accepted(val outboxId: Long) : SendOutcome
    data class Rejected(val reason: SendFailureReason) : SendOutcome
}

enum class SendFailureReason {
    NO_SIM,
    AIRPLANE_MODE,
    NO_SERVICE,
    PERMISSION_DENIED,
    INVALID_RECIPIENT,
    MESSAGE_TOO_LONG,
    RADIO_ERROR,
    MMS_UNAVAILABLE,
    UNKNOWN,
}
