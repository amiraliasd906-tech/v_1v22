package com.divarsmartsearch.app.presentation.screens.sellerreport

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.divarsmartsearch.app.domain.model.SellerReport
import com.divarsmartsearch.app.presentation.components.EmptyState
import com.divarsmartsearch.app.presentation.components.ErrorState
import com.divarsmartsearch.app.presentation.components.LoadingState
import com.divarsmartsearch.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerReportScreen(
    onBack: () -> Unit,
    viewModel: SellerReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گزارش فروشنده") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            state.report == null || state.report!!.totalListings == 0 -> EmptyState(
                message = "آگهی دیگری با این شماره پیدا نشد",
                modifier = Modifier.padding(padding),
            )
            else -> SellerReportContent(
                report = state.report!!,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenListing = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            )
        }
    }
}

@Composable
private fun SellerReportContent(
    report: SellerReport,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(report.phoneNumber, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "این شماره در ${report.totalListings} آگهی ذخیره‌شده دیده شده است",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Bug fix, per explicit user request: this used to show
                    // raw percentages ("میانگین احتمال مالک مستقیم بودن:
                    // 50٪ ... 30٪ ... 65٪"), which reads as an unresolved
                    // guess. The rest of the app already treats this as a
                    // hard yes/no (see FilterPipeline and the badge on
                    // ListingCard), so the report gives the same one clear
                    // verdict instead of a number.
                    report.averageAgencyLikelihoodPercent?.let {
                        val likelyAgency = it >= 50
                        Text(
                            if (likelyAgency) {
                                "این شماره در بیشتر آگهی‌هایش شبیه یک مشاور/آژانس رفتار کرده، نه یک مالک مستقیم."
                            } else {
                                "این شماره در بیشتر آگهی‌هایش شبیه یک مالک مستقیم رفتار کرده، نه یک مشاور/آژانس."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (report.totalListings >= 3) {
                        Text(
                            "با توجه به تعداد آگهی‌ها، این شماره احتمالاً متعلق به یک مشاور/آژانس املاک است، نه یک مالک شخصی.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (report.cities.isNotEmpty()) {
            item {
                SectionHeader("شهرها", modifier = Modifier.padding(top = 8.dp))
                Text(report.cities.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (report.neighborhoods.isNotEmpty()) {
            item {
                SectionHeader("محله‌ها", modifier = Modifier.padding(top = 8.dp))
                Text(report.neighborhoods.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
            }
        }

        item { SectionHeader("آگهی‌ها", modifier = Modifier.padding(top = 8.dp)) }

        items(report.listings, key = { it.id }) { listing ->
            Card(
                onClick = { onOpenListing(listing.url) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(listing.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(listing.neighborhood, listing.city).joinToString("، "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
