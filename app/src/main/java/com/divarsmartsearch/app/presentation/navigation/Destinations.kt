package com.divarsmartsearch.app.presentation.navigation

sealed class Destination(val route: String) {
    // New-search creation flow (nested graph): step 1 = filters, step 2 = permanent filters
    data object NewSearchGraph : Destination("new_search_graph")
    data object SearchForm : Destination("search_form")
    data object SelectPermanentFilters : Destination("select_permanent_filters")

    // Edit-search flow: same two steps, pre-filled from an existing search
    data object EditSearchGraph : Destination("edit_search_graph")
    data object EditSearchForm : Destination("edit_search_form/{searchId}") {
        fun createRoute(searchId: Int) = "edit_search_form/$searchId"
    }
    data object EditSelectPermanentFilters : Destination("edit_select_permanent_filters")

    // In-app WebView: browse Divar and passively extract listings for a given search
    data object DivarWebView : Destination("divar_webview/{searchId}") {
        fun createRoute(searchId: Int) = "divar_webview/$searchId"
    }

    // In-app WebView: browse Divar's real site to pick filters, then hand the
    // resulting URL back to whichever search-form screen opened it.
    // Two separate routes (rather than one shared route reused in both
    // nested graphs) because Navigation Compose route strings must be
    // unique within the graph they're looked up from.
    data object FilterPicker : Destination("filter_picker")
    data object EditFilterPicker : Destination("edit_filter_picker")

    // Main bottom-navigation destinations
    data object SearchList : Destination("search_list")
    data object Results : Destination("results")
    data object History : Destination("history")
    data object PermanentFiltersManagement : Destination("permanent_filters_management")
    data object Settings : Destination("settings")

    // Per-phone-number seller report (feature: تشخیص هوشمندتر مالک / گزارش فروشنده)
    data object SellerReport : Destination("seller_report/{phoneNumber}") {
        fun createRoute(phoneNumber: String) = "seller_report/$phoneNumber"
    }

    // Per-listing AI question/answer assistant
    data object ListingAiChat : Destination("listing_ai_chat/{listingId}") {
        fun createRoute(listingId: Int) = "listing_ai_chat/$listingId"
    }
}

data class BottomNavItem(
    val destination: Destination,
    val labelResId: Int,
)
