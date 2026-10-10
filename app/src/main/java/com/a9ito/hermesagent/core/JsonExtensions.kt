package com.a9ito.hermesagent.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Pure, Android-free primitive extractors for a kotlinx [JsonObject].
 *
 * The two SSE parsers (RunSseParser, SessionSseParser) and other decoders each
 * hand-rolled the identical `(obj[key] as? JsonPrimitive)?.contentOrNull ?: ""`
 * idiom as local functions. These shared extensions give one tested definition.
 * kotlinx.serialization.json is Android-free, so this stays in the pure `core/`
 * layer and is unit-tested on the JVM.
 */

/** The string content of [key], or "" when absent or not a primitive. */
fun JsonObject.stringOrEmpty(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

/** The non-empty string content of [key], or null when absent/empty/not a primitive. */
fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

/** The boolean content of [key], or false when absent or not a boolean primitive. */
fun JsonObject.booleanOrFalse(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

/** The long content of [key], or 0 when absent or not a numeric primitive. */
fun JsonObject.longOrZero(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
