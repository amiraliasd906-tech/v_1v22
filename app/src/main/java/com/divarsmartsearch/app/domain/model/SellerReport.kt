package com.divarsmartsearch.app.domain.model

/**
 * Aggregated view of every stored listing that shares one phone number —
 * lets the person see, at a glance, whether a number belongs to someone
 * posting many ads (likely an agent/dealer) across several neighborhoods,
 * versus a private owner with a single ad.
 */
data class SellerReport(
    val phoneNumber: String,
    val totalListings: Int,
    val cities: List<String>,
    val neighborhoods: List<String>,
    val averageAgencyLikelihoodPercent: Int?,
    val listings: List<Listing>,
)
