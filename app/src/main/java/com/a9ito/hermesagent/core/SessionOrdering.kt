package com.a9ito.hermesagent.core

/**
 * Display ordering for the sessions list. Pinned sessions float to the top; the
 * server already returns rows newest-first (with pins back-filled past the recency
 * window), so within each group we preserve the server's order via a stable sort.
 *
 * Pure / Android-free so the ordering contract is unit-tested on the JVM.
 */
fun List<SessionSummary>.sortedForDisplay(): List<SessionSummary> =
    sortedByStable { !it.pinned } // false (pinned) sorts before true (unpinned)

/** Stable sort by a Comparable key — preserves input order among equal keys. */
private inline fun <T, K : Comparable<K>> List<T>.sortedByStable(crossinline key: (T) -> K): List<T> =
    withIndex().sortedWith(compareBy({ key(it.value) }, { it.index })).map { it.value }
