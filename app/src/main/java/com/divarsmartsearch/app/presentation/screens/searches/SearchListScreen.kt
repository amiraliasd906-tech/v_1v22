package com.divarsmartsearch.app.presentation.screens.searches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.divarsmartsearch.app.domain.model.SavedSearch
import com.divarsmartsearch.app.domain.model.SearchStatus
import com.divarsmartsearch.app.presentation.components.EmptyState
import com.divarsmartsearch.app.presentation.components.ErrorState
import com.divarsmartsearch.app.presentation.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchListScreen(
    onEditSearch: (Int) -> Unit,
    onOpenBrowser: (Int) -> Unit,
    viewModel: SearchListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("جستجوهای من") }) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            state.searches.isEmpty() -> EmptyState(
                message = "هنوز جستجویی نساخته‌اید",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.searches, key = { it.id }) { search ->
                    SearchRow(
                        search = search,
                        onToggle = { viewModel.toggle(search.id) },
                        onEdit = { onEditSearch(search.id) },
                        onOpenBrowser = { onOpenBrowser(search.id) },
                        onDelete = { pendingDeleteId = search.id },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("حذف جستجو") },
            text = { Text("آیا از حذف این جستجو مطمئن هستید؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    pendingDeleteId = null
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun SearchRow(
    search: SavedSearch,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenBrowser: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(search.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (search.status == SearchStatus.ACTIVE) "فعال" else "متوقف",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (search.status == SearchStatus.ACTIVE)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = search.status == SearchStatus.ACTIVE, onCheckedChange = { onToggle() })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenBrowser) {
                    Icon(
                        imageVector = Icons.Outlined.TravelExplore,
                        contentDescription = "باز کردن و رصد در دیوار",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "ویرایش",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
