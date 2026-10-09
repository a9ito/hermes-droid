package com.a9ito.hermesagent.data.remote

import okio.BufferedSource

/** Thrown when a single SSE line exceeds [SseLineReader.MAX_LINE_BYTES]. */
class SseLineTooLongException(limit: Long) :
    Exception("SSE line exceeded $limit bytes without a line break")

/**
 * Bounded line reader for the Server-Sent Events streams.
 *
 * The three SSE streamers (chat, session chat, run events) consume an untrusted
 * response body one line at a time. A malicious or compromised server can send a
 * line that never contains a line break; the unbounded [BufferedSource.readUtf8Line]
 * then grows okio's buffer without limit until the process OOMs (a denial of
 * service that crash-loops the app). This cap aborts such a stream instead.
 *
 * Kept as a tiny okio-only helper (no Android/OkHttp types) so the limit
 * behavior is unit-tested on the JVM with a plain [okio.Buffer].
 */
object SseLineReader {

    /**
     * Largest single SSE line we will buffer. SSE frames are small (a chat delta,
     * one JSON event); 16 MiB is far above any legitimate line yet bounds a
     * hostile unterminated stream. A `data:` line echoing an inline image would
     * be the largest realistic case and still sits well under this.
     */
    const val MAX_LINE_BYTES: Long = 16L * 1024 * 1024

    /**
     * Read one line (without its terminator), or null at end of stream, matching
     * [BufferedSource.readUtf8Line]'s EOF contract. Throws [SseLineTooLongException]
     * when more than [limit] bytes arrive with no line break, so the caller can
     * close the connection rather than let the buffer grow unbounded.
     */
    fun readLine(source: BufferedSource, limit: Long = MAX_LINE_BYTES): String? {
        // Scan only the first (limit + 1) bytes for a newline; okio loads up to
        // that window and no further, so a hostile unterminated line cannot pull
        // the whole stream into memory here.
        val newline = source.indexOf('\n'.code.toByte(), 0, limit + 1)
        if (newline != -1L) {
            // A line break is within the cap: readUtf8LineStrict consumes through
            // it and also strips a preceding '\r' (handles CRLF and LF).
            return source.readUtf8LineStrict()
        }
        // No newline in the window. If the buffer now holds more than `limit`
        // bytes, this is an over-long (abusive) line: abort.
        if (source.buffer.size > limit) {
            throw SseLineTooLongException(limit)
        }
        // Otherwise the stream is exhausted with a short, newline-less remainder
        // (a truncated final line). Return it once, then null, like readUtf8Line.
        return if (source.buffer.size == 0L) null else source.readUtf8()
    }
}
