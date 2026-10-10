package com.a9ito.hermesagent.core

/**
 * Pure, Android-free formatters for the agent status bar. Kept here so the whole
 * number/duration-to-string matrix is unit-tested on the JVM (StatusFormatTest);
 * the composable only reads these.
 */
object StatusFormat {

    /**
     * A running timer, newest-elapsed: "M:SS" under an hour, "H:MM:SS" past it.
     * Used for the live agent-turn / run timer. Negative input clamps to 0.
     */
    fun elapsedTimer(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }

    /**
     * A coarse age, for how long a session has been alive: "45s", "12m",
     * "2h 05m", "3d 4h". Only the two most significant units are shown so the
     * chip stays short. Negative input clamps to 0.
     */
    fun age(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val d = totalSec / 86_400
        val h = (totalSec % 86_400) / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "%dh %02dm".format(h, m)
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    /**
     * A compact token count: raw under 1k, "12.3k" / "1.2M" above, trailing
     * ".0" trimmed ("5.0k" -> "5k"). Negative input clamps to 0.
     */
    fun tokens(n: Long): String {
        val v = n.coerceAtLeast(0)
        return when {
            v < 1_000 -> v.toString()
            v < 1_000_000 -> trimDecimal(v / 1_000.0) + "k"
            else -> trimDecimal(v / 1_000_000.0) + "M"
        }
    }

    private fun trimDecimal(value: Double): String {
        val one = "%.1f".format(value)
        return if (one.endsWith(".0")) one.dropLast(2) else one
    }
}
