package com.a9ito.hermesagent.core

/**
 * Pure, Android-free text neutralization for SECURITY-SENSITIVE control surfaces
 * (a tool name, a command, a command preview shown at the run-approval gate).
 *
 * The agent's output is untrusted (it can carry prompt-injected content). Bidi
 * overrides, isolates, and zero-width / invisible format characters let a string
 * RENDER differently from its real byte order or hide part of itself — the
 * "Trojan Source" class (CWE-451, CVE-2021-42574). On the approval dialog that
 * means a dangerous command could display as something benign while the human
 * taps Allow. [forControlDisplay] replaces every such character (plus C0/C1
 * control codes) with the visible replacement character U+FFFD, so the glyphs a
 * user approves match the actual command.
 *
 * This is applied ONLY to short control identifiers and the approval command,
 * never to prose (assistant/user chat text, reasoning), so legitimate
 * right-to-left scripts and emoji in a conversation are left intact — only the
 * invisible/directional FORMAT characters are neutralized, not RTL letters.
 */
object SafeText {

    private const val REPLACEMENT = '\uFFFD'

    /** True for a character that must not reach a security-sensitive display. */
    private fun isNeutralized(c: Char): Boolean {
        val cp = c.code
        return when {
            cp == '\t'.code || cp == '\n'.code -> false        // keep tab + newline
            cp < 0x20 -> true                                   // C0 controls (incl. \r, VT, FF)
            cp in 0x7F..0x9F -> true                            // DEL + C1 controls
            cp == 0x061C -> true                                // Arabic Letter Mark
            cp in 0x200B..0x200F -> true                        // ZWSP/ZWNJ/ZWJ/LRM/RLM
            cp in 0x202A..0x202E -> true                        // LRE/RLE/PDF/LRO/RLO
            cp in 0x2060..0x2064 -> true                        // WORD JOINER + invisible operators
            cp in 0x2066..0x206F -> true                        // bidi isolates + deprecated format
            cp == 0xFEFF -> true                                // ZWNBSP / BOM
            cp in 0xFFF9..0xFFFB -> true                        // interlinear annotation anchors
            else -> false
        }
    }

    /**
     * Return [raw] with every bidi/zero-width/control character replaced by
     * U+FFFD. Null and empty pass through unchanged; ordinary text (including
     * RTL letters, CJK, and emoji surrogate pairs) is untouched because none of
     * those are in the neutralized set.
     */
    fun forControlDisplay(raw: String?): String? {
        if (raw.isNullOrEmpty()) return raw
        if (raw.none { isNeutralized(it) }) return raw   // fast path: nothing to do
        return buildString(raw.length) {
            for (c in raw) append(if (isNeutralized(c)) REPLACEMENT else c)
        }
    }
}
