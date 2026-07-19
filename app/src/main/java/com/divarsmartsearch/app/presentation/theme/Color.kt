package com.divarsmartsearch.app.presentation.theme

import androidx.compose.ui.graphics.Color

// Modern dark-minimal palette, chosen by the user: cool navy/charcoal
// neutrals throughout, carrying exactly ONE brand accent color (a warm
// amber-gold) instead of the previous three-hue coffee/sage/gold scheme.
// Secondary/tertiary are still defined (Material3 needs them for a few
// roles like the bottom-nav indicator), but deliberately desaturated,
// near-neutral blue-grays — so the app reads as "navy + one accent," not
// "several competing colors." Success/Error stay as small, functional
// status colors (star rating, agency-warning, owner-confirmed) — not
// part of the decorative brand palette, so they don't fight the accent.

// ---- Dark (the primary, hero theme) ----
val AccentGoldDark = Color(0xFFE3A94F) // the one accent — warm amber-gold against cool navy
val OnAccentGoldDark = Color(0xFF2B1B04)
val AccentGoldContainerDark = Color(0xFF4A3410)
val OnAccentGoldContainerDark = Color(0xFFFFDFA8)

val NeutralAccentDark = Color(0xFF8B99A6) // desaturated blue-gray, NOT a second hue
val OnNeutralAccentDark = Color(0xFF1A2027)
val NeutralAccentContainerDark = Color(0xFF2A333C)
val OnNeutralAccentContainerDark = Color(0xFFD7DEE5)

val BackgroundDark = Color(0xFF10151A) // near-black navy
val SurfaceDark = Color(0xFF161C22)
val SurfaceVariantDark = Color(0xFF1D242B) // cards
val OnSurfaceDark = Color(0xFFECEEF0)
val OnSurfaceVariantDark = Color(0xFF98A3AC)
val OutlineDark = Color(0xFF3A434B)

val SuccessDark = Color(0xFF7FD99A) // functional only: "confident owner" signal
val ErrorDark = Color(0xFFFFB4A9) // functional only: reject/agency-warning

// ---- Light (companion, same accent, cool-neutral surfaces) ----
val AccentGoldLight = Color(0xFFA36F1D)
val OnAccentGoldLight = Color(0xFFFFFFFF)
val AccentGoldContainerLight = Color(0xFFFFDFA8)
val OnAccentGoldContainerLight = Color(0xFF2B1B04)

val NeutralAccentLight = Color(0xFF54626D)
val OnNeutralAccentLight = Color(0xFFFFFFFF)
val NeutralAccentContainerLight = Color(0xFFD7DEE5)
val OnNeutralAccentContainerLight = Color(0xFF141C22)

val BackgroundLight = Color(0xFFF7F8FA)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceVariantLight = Color(0xFFE9ECEF) // cards
val OnSurfaceLight = Color(0xFF171C20)
val OnSurfaceVariantLight = Color(0xFF4B565E)
val OutlineLight = Color(0xFF7C8890)

val SuccessLight = Color(0xFF2E7D46)
val ErrorLight = Color(0xFFBA1B1B)

// Shared / neutral accents used for chips, badges, etc.
val AgencyWarningColor = ErrorDark
val OwnerBadgeColorLight = SuccessLight
val OwnerBadgeColorDark = SuccessDark
