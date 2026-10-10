package com.a9ito.hermesagent.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure, Android-free model for the per-turn reasoning + speed controls, plus the
 * `model_options` JSON the chat endpoints read. No Compose/Android types, so the
 * build rules are unit-tested on the plain JVM.
 *
 * The Hermes chat endpoints (`/v1/chat/completions` and the session chat turn)
 * honor a `model_options` object:
 *  - `reasoning: { enabled, effort }` sets the thinking budget. The server's
 *    effort ladder is none/minimal/low/medium/high/xhigh/max/ultra; unknown
 *    values are ignored server-side, never an error.
 *  - `fast: true` maps to the "priority" service tier (faster, when the provider
 *    offers it).
 *
 * [ReasoningEffort.DEFAULT] means "send nothing", i.e. use the instance's own
 * default; it is NOT the same as [ReasoningEffort.OFF], which explicitly
 * disables reasoning (`enabled:false`).
 */
enum class ReasoningEffort(val key: String, val wire: String?) {
    /** Omit reasoning from model_options entirely (instance default). */
    DEFAULT("default", null),
    /** Explicitly disable reasoning (reasoning.enabled = false). */
    OFF("off", null),
    MINIMAL("minimal", "minimal"),
    LOW("low", "low"),
    MEDIUM("medium", "medium"),
    HIGH("high", "high"),
    XHIGH("xhigh", "xhigh"),
    MAX("max", "max"),
    ULTRA("ultra", "ultra");

    companion object {
        fun fromKey(key: String?): ReasoningEffort = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * The user's per-turn runtime choices. Defaults ([ReasoningEffort.DEFAULT], fast
 * off) reproduce the original behavior: no `model_options` is sent at all.
 */
data class ReasoningPref(
    val effort: ReasoningEffort = ReasoningEffort.DEFAULT,
    val fast: Boolean = false,
) {
    /** True when this pref would send nothing (so callers can skip the field). */
    val isDefault: Boolean
        get() = effort == ReasoningEffort.DEFAULT && !fast

    /**
     * The `model_options` object for this pref, or null when [isDefault] (send
     * nothing). `fast` is emitted only when enabled, so the default turn never
     * pins a service tier.
     */
    fun toModelOptions(): JsonObject? {
        if (isDefault) return null
        return buildJsonObject {
            when (effort) {
                ReasoningEffort.DEFAULT -> {} // nothing
                ReasoningEffort.OFF -> put("reasoning", buildJsonObject { put("enabled", false) })
                else -> put(
                    "reasoning",
                    buildJsonObject {
                        put("enabled", true)
                        put("effort", effort.wire!!)
                    },
                )
            }
            if (fast) put("fast", true)
        }
    }

    companion object {
        val DEFAULT = ReasoningPref()
    }
}
