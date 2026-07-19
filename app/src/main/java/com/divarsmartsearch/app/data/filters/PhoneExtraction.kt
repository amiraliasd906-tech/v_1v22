package com.divarsmartsearch.app.data.filters

/**
 * Extracts phone-number-like patterns from free text (an ad's title and
 * description), since agencies often type their number directly into
 * the text instead of (or in addition to) a dedicated contact field.
 * Ported from the original backend's app/services/filters/phone_extraction.py
 * — same regex and normalization logic, now running entirely on-device.
 */
object PhoneExtraction {

    private val MOBILE_PATTERN = Regex("""(?:\+98|0098|98|0)?\s*9\d{2}[\s.-]?\d{3}[\s.-]?\d{4}""")
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    private fun toAsciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = PERSIAN_DIGITS.indexOf(ch)
            sb.append(if (idx >= 0) idx.toString() else ch.toString())
        }
        return sb.toString()
    }

    fun normalizeMobileNumber(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("0098")) digits = digits.substring(4)
        else if (digits.startsWith("98")) digits = digits.substring(2)
        if (!digits.startsWith("0")) digits = "0$digits"
        return digits
    }

    /** Scans one or more text fields and returns de-duplicated normalized numbers, in order first seen. */
    fun extractPhoneNumbers(vararg texts: String?): List<String> {
        val found = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val normalizedText = toAsciiDigits(text)
            for (match in MOBILE_PATTERN.findAll(normalizedText)) {
                val number = normalizeMobileNumber(match.value)
                if (number.length != 11) continue
                if (seen.add(number)) found.add(number)
            }
        }
        return found
    }
}
