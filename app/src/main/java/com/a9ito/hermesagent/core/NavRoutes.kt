package com.a9ito.hermesagent.core

/**
 * Pure navigation routing table — the route strings and the primary/secondary
 * split, with NO Android or Compose types, so the nav-shell logic (which tab is
 * active, whether a route lives behind the "More" menu) is unit-testable on the
 * JVM. The [Destination] enum in the ui package carries the icons + string
 * resources and delegates its classification here.
 */
object NavRoutes {
    const val CHAT = "chat"
    const val SESSIONS = "sessions"
    const val RUNS = "runs"
    const val TOOLS = "tools"
    const val JOBS = "jobs"
    const val STATUS = "status"
    const val SETTINGS = "settings"

    /** Shown directly in the bottom navigation bar (kept within M3's 3-5 range). */
    val PRIMARY: List<String> = listOf(CHAT, SESSIONS, RUNS)

    /** Reached through the "More" menu sheet. */
    val SECONDARY: List<String> = listOf(TOOLS, JOBS, STATUS, SETTINGS)

    /** Every top-level route, primary first. */
    val ALL: List<String> = PRIMARY + SECONDARY

    /** The app's start destination. */
    const val START: String = CHAT

    fun isSecondary(route: String?): Boolean = route in SECONDARY

    fun isPrimary(route: String?): Boolean = route in PRIMARY

    /** True when [route] is a known top-level destination (not a detail route like session_chat/{id}). */
    fun isTopLevel(route: String?): Boolean = route in ALL
}
