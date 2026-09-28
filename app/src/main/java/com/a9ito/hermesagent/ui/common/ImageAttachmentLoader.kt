package com.a9ito.hermesagent.ui.common

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.a9ito.hermesagent.core.ChatAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a user-picked image [Uri] into a pure [ChatAttachment] (mime +
 * base64), off the main thread. Returns null when the content can't be read,
 * isn't an image, or exceeds [ChatAttachment.MAX_BYTES] — the caller surfaces a
 * message rather than sending an oversized body the server would reject.
 *
 * Kept thin and Android-only: all payload shaping lives in the pure
 * SessionChatPayload so it stays unit-testable.
 */
object ImageAttachmentLoader {

    suspend fun load(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: return@withContext Result.Error.UnreadableType
        if (!mime.startsWith("image/")) return@withContext Result.Error.NotAnImage
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        } ?: return@withContext Result.Error.Unreadable
        if (bytes.size > ChatAttachment.MAX_BYTES) return@withContext Result.Error.TooLarge
        if (bytes.isEmpty()) return@withContext Result.Error.Unreadable
        val displayName = queryDisplayName(context, uri)
        Result.Ok(
            ChatAttachment(
                mimeType = mime,
                base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                displayName = displayName,
            ),
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (t: Throwable) {
        null
    }

    sealed interface Result {
        data class Ok(val attachment: ChatAttachment) : Result
        sealed interface Error : Result {
            data object NotAnImage : Error
            data object TooLarge : Error
            data object Unreadable : Error
            data object UnreadableType : Error
        }
    }
}
