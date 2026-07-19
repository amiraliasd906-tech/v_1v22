package com.divarsmartsearch.app.data.webview

import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON shape sent by the injected JS in DivarWebViewScreen —
 * the Kotlin equivalent of the old browser extension's content scripts.
 */
@Serializable
data class ExtractedListing(
    val divarToken: String,
    val url: String,
    val title: String,
    val description: String? = null,
    val price: Double? = null,
    val area: Double? = null,
    val pricePerMeter: Double? = null,
    val neighborhood: String? = null,
    val contactPhone: String? = null,
)

@Serializable
data class ExtractedListingBatch(
    val listings: List<ExtractedListing>,
)
