package com.divarsmartsearch.app.util

import com.divarsmartsearch.app.domain.model.Listing

/**
 * A cluster of listings that [ListingEnricher] believes are the same ad
 * (republished, or matched by phone/title/price+area). [primary] is
 * whichever one of them is the "root" the others were matched against —
 * or, if that root isn't part of the currently visible list (e.g. it was
 * rejected), simply the first one encountered. [duplicates] holds the
 * rest, in their original list order.
 */
data class ListingGroup(
    val primary: Listing,
    val duplicates: List<Listing> = emptyList(),
) {
    val all: List<Listing> get() = if (duplicates.isEmpty()) listOf(primary) else listOf(primary) + duplicates
    val hasDuplicates: Boolean get() = duplicates.isNotEmpty()
}

/**
 * Reorders a flat, already-sorted list of listings so that every ad and
 * whatever [Listing.duplicateOfListingId] chain it points to end up right
 * next to each other, instead of scattered across the results at whatever
 * position their own scan/price/date put them at. Group position in the
 * output is the position of whichever member of the group appeared first
 * in the input list, so the overall sort order (newest first, etc.) is
 * otherwise preserved.
 *
 * Safe against duplicate chains that point to a listing outside the
 * current list (filtered out, rejected, etc.) and against any accidental
 * cycles in duplicateOfListingId.
 */
fun List<Listing>.groupWithDuplicates(): List<ListingGroup> {
    val byId = associateBy { it.id }

    fun resolveRootId(start: Listing): Int {
        var current = start
        val seen = mutableSetOf<Int>()
        while (current.isDuplicate && current.duplicateOfListingId != null) {
            if (!seen.add(current.id)) break // cycle guard
            val next = byId[current.duplicateOfListingId] ?: break
            current = next
        }
        return current.id
    }

    val groupOrder = mutableListOf<Int>()
    val membersByRootId = mutableMapOf<Int, MutableList<Listing>>()

    for (listing in this) {
        val rootId = resolveRootId(listing)
        val bucket = membersByRootId.getOrPut(rootId) {
            groupOrder += rootId
            mutableListOf()
        }
        bucket += listing
    }

    return groupOrder.map { rootId ->
        val members = membersByRootId.getValue(rootId)
        val primary = members.firstOrNull { it.id == rootId } ?: members.first()
        val duplicates = members.filterNot { it.id == primary.id }
        ListingGroup(primary = primary, duplicates = duplicates)
    }
}
