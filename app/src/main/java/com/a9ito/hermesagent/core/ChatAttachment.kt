package com.a9ito.hermesagent.core

/**
 * One image a user attaches to a chat turn. Pure / Android-free so the payload
 * builder is unit-testable; the Android layer decodes a picked Uri into one of
 * these (mime + base64 bytes) before it reaches the repository.
 *
 * Only images are modelled: the server's session-chat endpoint accepts inline
 * ``image_url`` parts but explicitly rejects file/document parts, so offering
 * anything else would just 400 at send time.
 */
data class ChatAttachment(
    val mimeType: String,
    /** Base64 (no line wrapping) of the raw image bytes. */
    val base64Data: String,
    val displayName: String? = null,
) {
    /** Canonical ``data:image/...;base64,...`` URL the agent pipeline understands. */
    fun toDataUrl(): String = "data:$mimeType;base64,$base64Data"

    val isImage: Boolean get() = mimeType.startsWith("image/")

    companion object {
        /** Server-side default artifact/image cap is generous, but keep requests sane. */
        const val MAX_BYTES: Int = 4 * 1024 * 1024

        /** Max images per turn — keeps the request body bounded and the UI simple. */
        const val MAX_PER_TURN: Int = 4
    }
}
