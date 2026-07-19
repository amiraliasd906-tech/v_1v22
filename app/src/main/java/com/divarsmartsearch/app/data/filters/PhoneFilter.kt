package com.divarsmartsearch.app.data.filters

import com.divarsmartsearch.app.data.local.entity.ListingEntity

/**
 * Applies the permanent phone-number blocklist. Checks both the
 * official contact field and any numbers typed directly into the
 * title/description (common with agencies) — ported from the backend's
 * phone_filter.py.
 */
object PhoneFilter {

    fun normalizePhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("98")) digits = "0" + digits.substring(2)
        else if (digits.startsWith("9") && digits.length == 10) digits = "0$digits"
        return digits
    }

    /** All phone numbers associated with a listing: contact field + anything in the text. */
    fun listingPhoneNumbers(listing: ListingEntity): List<String> {
        val numbers = PhoneExtraction.extractPhoneNumbers(listing.title, listing.description).toMutableList()
        listing.contactPhone?.let {
            val normalized = normalizePhone(it)
            if (normalized.isNotBlank() && normalized !in numbers) numbers.add(0, normalized)
        }
        return numbers
    }

    fun isBlocked(listing: ListingEntity, blockedNumbers: Set<String>): Boolean {
        if (blockedNumbers.isEmpty()) return false
        return listingPhoneNumbers(listing).any { it in blockedNumbers }
    }
}
