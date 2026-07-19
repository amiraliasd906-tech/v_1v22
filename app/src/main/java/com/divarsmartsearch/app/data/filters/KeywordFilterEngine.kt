package com.divarsmartsearch.app.data.filters

import com.divarsmartsearch.app.data.local.entity.KeywordFilterEntity

/**
 * Per explicit user request: every keyword the person cares about
 * ("دفتر", "املاک", "مشاور", "کلید", or any custom word they add) is now
 * its OWN independent, toggleable filter — not one hardcoded combined
 * list like before. A listing is checked against EVERY enabled
 * [KeywordFilterEntity] in turn ("هر آگهی از چندین فیلتر رد بشه") and is
 * rejected the moment it matches ANY one of them; no probability, no
 * negation exception, no other filter setting overrides this.
 *
 * Checked against BOTH the title and the description of every listing
 * (see [FilterPipeline] and
 * [com.divarsmartsearch.app.data.webview.ListingIngestionService]), and
 * re-checked on every single ingestion pass — including when an
 * already-stored listing's description is later enriched with the full
 * text from its detail page — so a listing can never end up notified
 * before its full text has been screened against every active filter.
 *
 * Matching for each filter is done word-by-word (not a raw substring
 * search over the whole text), plus a redundant raw-substring safety net,
 * so that:
 *   - Persian/Arabic character variants (ي/ی، ك/ک) are treated the same.
 *   - Zero-width non-joiners inside a word don't hide a match.
 *   - A match counts if a real word in the text starts with the filter's
 *     root, catching every attached-suffix form (مشاوره, مشاورین,
 *     مشاوران, املاکی, دفترها, ...) without listing every inflection by
 *     hand.
 *   - Even if the word-boundary tokenizer ever fails to split some
 *     unusual piece of text correctly, the substring pass still catches it.
 */
object KeywordFilterEngine {

    private const val ARABIC_YEH = 'ي'
    private const val PERSIAN_YEH = 'ی'
    private const val ARABIC_YEH_ALT = '\u064A' // U+064A, another Arabic Yeh form seen in copy-pasted text
    private const val ARABIC_KAF = 'ك'
    private const val PERSIAN_KAF = 'ک'
    private const val ZWNJ = '\u200C'
    private const val ARABIC_TATWEEL = '\u0640'

    /** Splits text into words on anything that isn't a letter or digit. */
    private val WORD_SPLIT_REGEX = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Public so other detectors (e.g. [OwnerDetector]'s keyword lists) can
     * normalize text the same way before matching — otherwise the same
     * Yeh/Kaf/ZWNJ variants that this filter accounts for would silently
     * break their `contains(...)` checks on real Divar text.
     */
    fun normalize(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                ARABIC_YEH, ARABIC_YEH_ALT -> append(PERSIAN_YEH)
                ARABIC_KAF -> append(PERSIAN_KAF)
                ZWNJ, ARABIC_TATWEEL -> Unit // dropped entirely, not even a space, so split words stay joined correctly
                else -> append(ch)
            }
        }
    }

    /**
     * True if [root] appears in [text] — either as the start of some real
     * word (catching inflected forms), or as a raw substring (the
     * redundant safety net). Either pass matching is enough; this is
     * intentionally an OR, never an AND.
     */
    fun matchesRoot(text: String?, root: String): Boolean {
        if (text.isNullOrBlank() || root.isBlank()) return false
        val normalizedText = normalize(text)
        val normalizedRoot = normalize(root)

        val wordMatch = WORD_SPLIT_REGEX.split(normalizedText).any { word ->
            word.isNotEmpty() && word.startsWith(normalizedRoot)
        }
        if (wordMatch) return true

        return normalizedText.contains(normalizedRoot)
    }

    /**
     * Runs [title]/[description] through every enabled filter in
     * [filters], in order, and returns the FIRST one that matches — this
     * is what [FilterPipeline] and the ingestion safety net use to reject
     * a listing and record which specific filter caught it.
     * Returns null only if the listing is clean against every active filter.
     */
    fun findFirstMatch(
        title: String?,
        description: String?,
        filters: List<KeywordFilterEntity>,
    ): KeywordFilterEntity? = filters.firstOrNull { filter ->
        filter.isEnabled && (matchesRoot(title, filter.keyword) || matchesRoot(description, filter.keyword))
    }

    /**
     * Same as [findFirstMatch] but returns every matching filter instead
     * of stopping at the first one — used where the UI wants to show a
     * listing's full rejection breakdown ("چه فیلترهایی این آگهی را رد کردند").
     */
    fun findAllMatches(
        title: String?,
        description: String?,
        filters: List<KeywordFilterEntity>,
    ): List<KeywordFilterEntity> = filters.filter { filter ->
        filter.isEnabled && (matchesRoot(title, filter.keyword) || matchesRoot(description, filter.keyword))
    }
}
