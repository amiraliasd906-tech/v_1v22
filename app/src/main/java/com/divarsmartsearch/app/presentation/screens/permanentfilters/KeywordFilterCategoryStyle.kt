package com.divarsmartsearch.app.presentation.screens.permanentfilters

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CorporateFare
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every keyword filter has a [category] string (see KeywordFilterEntity)
 * that only ever drives *how it looks* in this screen — an icon and an
 * accent color pulled straight from the app's existing coffee/sage/gold
 * theme, so a new filter category never needs a new color to be invented.
 */
data class KeywordFilterCategoryStyle(
    val icon: ImageVector,
    val label: String,
    val color: Color,
)

@Composable
@ReadOnlyComposable
fun keywordFilterCategoryStyle(category: String): KeywordFilterCategoryStyle = when (category) {
    "real_estate" -> KeywordFilterCategoryStyle(
        icon = Icons.Outlined.Apartment,
        label = "املاک",
        color = MaterialTheme.colorScheme.primary,
    )
    "consultant" -> KeywordFilterCategoryStyle(
        icon = Icons.Outlined.SupportAgent,
        label = "مشاور",
        color = MaterialTheme.colorScheme.tertiary,
    )
    "office" -> KeywordFilterCategoryStyle(
        icon = Icons.Outlined.CorporateFare,
        label = "دفتر",
        color = MaterialTheme.colorScheme.secondary,
    )
    "key" -> KeywordFilterCategoryStyle(
        icon = Icons.Outlined.VpnKey,
        label = "کلید",
        color = MaterialTheme.colorScheme.error,
    )
    "owner" -> KeywordFilterCategoryStyle(
        icon = Icons.Outlined.VerifiedUser,
        label = "تایید مالک",
        color = Color(0xFF2E7D32), // distinct green — the opposite intent of the reject categories above
    )
    else -> KeywordFilterCategoryStyle(
        icon = Icons.Outlined.Category,
        label = "سفارشی",
        color = MaterialTheme.colorScheme.outline,
    )
}
