package com.divarsmartsearch.app.presentation.screens.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.divarsmartsearch.app.domain.model.HistoryTab
import com.divarsmartsearch.app.presentation.components.EmptyState
import com.divarsmartsearch.app.presentation.components.ErrorState
import com.divarsmartsearch.app.presentation.components.ListingCard
import com.divarsmartsearch.app.presentation.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val tabs = listOf(HistoryTab.SEEN to "مشاهده‌شده", HistoryTab.SAVED to "ذخیره‌شده", HistoryTab.REJECTED to "ردشده")
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("تاریخچه") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == state.selectedTab }) {
                tabs.forEach { (tab, label) ->
                    // Each tab shows its own total, independent of which tab is
                    // currently selected — e.g. "ردشده (۴۲)" — instead of only
                    // ever revealing a count for whichever single tab happens
                    // to be open.
                    val count = state.tabCounts[tab]
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(if (count != null) "$label ($count)" else label) },
                    )
                }
            }

            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(message = state.error!!, onRetry = viewModel::load)
                state.listings.isEmpty() -> EmptyState("موردی برای نمایش وجود ندارد")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.listings, key = { it.id }) { listing ->
                        ListingCard(
                            listing = listing,
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing.url)))
                            },
                            onSave = { viewModel.onSave(listing.id) },
                            // On the Rejected tab, re-rejecting an already-rejected
                            // listing changed nothing visible — the card just sat
                            // there with no feedback, which looked exactly like a
                            // broken button. There, the same button slot now
                            // restores the listing instead (see isRestoreAction).
                            onReject = {
                                if (state.selectedTab == HistoryTab.REJECTED) {
                                    viewModel.onRestore(listing.id)
                                } else {
                                    viewModel.onReject(listing.id)
                                }
                            },
                            isRestoreAction = state.selectedTab == HistoryTab.REJECTED,
                            // On the Saved tab there's nothing for a "Save" button
                            // to usefully do — the listing is already saved, so
                            // pressing it again was a silent no-op that read as
                            // broken. Hidden there; only the delete action remains,
                            // which moves the listing into the Rejected tab.
                            showSaveButton = state.selectedTab != HistoryTab.SAVED,
                            rejectContentDescription = if (state.selectedTab == HistoryTab.SAVED) {
                                "حذف از ذخیره‌شده‌ها"
                            } else {
                                "رد کردن"
                            },
                            onBlockPhoneNumber = { viewModel.onBlockPhoneNumber(it) },
                            onCall = { phoneNumber ->
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
