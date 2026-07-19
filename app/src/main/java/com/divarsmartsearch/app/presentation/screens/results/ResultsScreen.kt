package com.divarsmartsearch.app.presentation.screens.results

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.util.ListingGroup
import com.divarsmartsearch.app.util.groupWithDuplicates
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.divarsmartsearch.app.presentation.components.EmptyState
import com.divarsmartsearch.app.presentation.components.ErrorState
import com.divarsmartsearch.app.presentation.components.ListingCard
import com.divarsmartsearch.app.presentation.components.LoadingState
import com.divarsmartsearch.app.util.CsvExporter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ResultsScreen(
    onOpenSellerReport: (String) -> Unit = {},
    onOpenAiChat: (Int) -> Unit = {},
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Shows the *total* number of matching listings, not just
                    // however many happen to be scrolled into view — mirrors the
                    // per-tab totals now shown on the History screen (Seen/Saved/Rejected).
                    // Always includes the number (even 0) once loaded, same as
                    // the History tab labels, instead of hiding it when empty.
                    Text(if (!state.isLoading) "نتایج (${state.listings.size})" else "نتایج")
                },
                actions = {
                    // Re-runs the filter pipeline against whatever is
                    // already in the list — needed for e.g. a keyword
                    // filter added just now on the "فیلترهای دائمی" screen,
                    // which otherwise would only apply to listings scanned
                    // *after* the change.
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "اعمال دوباره فیلترها")
                        }
                    }
                    if (state.listings.isNotEmpty()) {
                        IconButton(onClick = {
                            val uri = CsvExporter.exportToCsv(context, state.listings)
                            context.startActivity(CsvExporter.shareIntent(context, uri))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "خروجی CSV")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            state.listings.isEmpty() -> EmptyState(
                message = "هنوز آگهی‌ای مطابق فیلترها پیدا نشده",
                modifier = Modifier.padding(padding),
            )
            else -> {
                // Ads that ListingEnricher flagged as the same listing
                // (shared phone number, or near-identical title/price/area)
                // are pulled next to each other here instead of sitting
                // wherever their own date/price happened to sort them —
                // otherwise a republished ad could show up dozens of
                // listings away from the original with no way to tell.
                val groups = remember(state.listings) { state.listings.groupWithDuplicates() }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(groups, key = { it.primary.id }) { group ->
                        val cardFor: @Composable (Listing, Modifier) -> Unit = { listing, modifier ->
                            ListingCard(
                            listing = listing,
                            onClick = {
                                viewModel.onOpened(listing.id)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(listing.url))
                                )
                            },
                            onSave = { viewModel.onSave(listing.id) },
                            onReject = { viewModel.onReject(listing.id) },
                            onBlockPhoneNumber = { viewModel.onBlockPhoneNumber(it) },
                            onViewSellerReport = onOpenSellerReport,
                            onAskAi = { onOpenAiChat(listing.id) },
                            onCall = { phoneNumber ->
                                // ACTION_DIAL only opens the phone app with the
                                // number pre-filled — it never places the call
                                // by itself, the same as tapping a phone
                                // number link in Divar's own app/site. No
                                // CALL_PHONE permission needed or requested.
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                                )
                            },
                            modifier = modifier,
                            )
                        }

                        if (!group.hasDuplicates) {
                            cardFor(
                                group.primary,
                                // Smooth slide/fade instead of an abrupt jump when a
                                // card leaves the list (saved, rejected, or a new
                                // one arrives from a background scan).
                                Modifier.animateItemPlacement(tween(280)),
                            )
                        } else {
                            DuplicateCluster(
                                group = group,
                                modifier = Modifier.animateItemPlacement(tween(280)),
                                cardContent = cardFor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps a primary listing plus everything ListingEnricher matched to it in
 * one visually distinct block: a tinted, outlined container with a small
 * "N آگهی مشابه" header, and a colored bracket running down the left edge
 * connecting every card in the group — so instead of a lone badge on each
 * card, the whole cluster reads as one family of ads at a glance.
 */
@Composable
private fun DuplicateCluster(
    group: ListingGroup,
    cardContent: @Composable (Listing, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = accent.copy(alpha = 0.06f),
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(top = 10.dp, bottom = 12.dp, start = 8.dp, end = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = " ${group.all.size} آگهی مشابه هم اینجا کنار هم چیده شدند (احتمال آگهی تکراری)",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // The bracket: a single rounded vertical bar spanning every
            // card in the group, so the eye reads them as one unit even
            // though each is still its own full ListingCard underneath.
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(color = accent.copy(alpha = 0.5f), shape = RoundedCornerShape(999.dp)),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.all.forEach { listing ->
                    cardContent(listing, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
