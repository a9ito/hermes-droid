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

/**
 * Case-insensitive filter for the sessions list. Matches [query] against the
 * session title, model id, and preview snippet. A blank query returns the list
 * unchanged. Pure / Android-free so the match rule is unit-tested on the JVM.
 */
fun List<SessionSummary>.filteredBy(query: String): List<SessionSummary> {
    val q = query.trim()
    if (q.isEmpty()) return this
    val needle = q.lowercase()
    return filter { s ->
        s.title.lowercase().contains(needle) ||
            (s.model?.lowercase()?.contains(needle) == true) ||
            (s.preview?.lowercase()?.contains(needle) == true)
    }
}

/**
 * Keep only sessions whose [SessionSummary.source] equals [source]. A null/blank
 * source keeps the whole list (the "All" chip). Client-side, over the already
 * loaded list, like [filteredBy] — distinct from the server's `source` query
 * param, which this mirrors without an extra round-trip.
 */
fun List<SessionSummary>.filteredBySource(source: String?): List<SessionSummary> {
    val s = source?.trim()
    if (s.isNullOrEmpty()) return this
    return filter { it.source == s }
}

/** Distinct non-blank source labels present in the list, sorted for a stable chip row. */
fun List<SessionSummary>.distinctSources(): List<String> =
    mapNotNull { it.source?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()

/** Stable sort by a Comparable key — preserves input order among equal keys. */
private inline fun <T, K : Comparable<K>> List<T>.sortedByStable(crossinline key: (T) -> K): List<T> =
    withIndex().sortedWith(compareBy({ key(it.value) }, { it.index })).map { it.value }
