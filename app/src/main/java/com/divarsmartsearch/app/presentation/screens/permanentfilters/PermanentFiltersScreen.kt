package com.divarsmartsearch.app.presentation.screens.permanentfilters

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.divarsmartsearch.app.domain.model.KeywordFilter
import com.divarsmartsearch.app.presentation.components.AppTextField
import com.divarsmartsearch.app.presentation.components.EmptyState
import com.divarsmartsearch.app.presentation.components.LoadingState
import com.divarsmartsearch.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermanentFiltersScreen(
    viewModel: PermanentFiltersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Shows the outcome of the automatic re-check that fires right after a
    // keyword filter is added/re-enabled ("N آگهی به حذف‌شده‌ها منتقل شد"),
    // same one-shot pattern as ResultsScreen's snackbarMessage.
    LaunchedEffect(state.reapplyMessage) {
        state.reapplyMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearReapplyMessage()
        }
    }

    // Opens the SYSTEM contact picker filtered to contacts that have a
    // phone number. This does NOT require the app to hold READ_CONTACTS
    // itself — the picker is a separate trusted app, and it grants a
    // one-off, temporary read permission on just the single row the user
    // taps, which is exactly why ACTION_PICK on the Phone content URI is
    // used here instead of a generic contact URI (that would return a
    // contact ID needing a broader, separately-permissioned query to
    // resolve into an actual number).
    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                if (!number.isNullOrBlank()) {
                    viewModel.addNumberFromContact(number, name)
                }
            }
        }
    }
    fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("فیلترهای دائمی") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Keyword filters: each word is its own independent filter ----
            item {
                SectionTitleRow(
                    icon = Icons.Outlined.FilterAlt,
                    title = "فیلترهای کلمه‌ای",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "هر کلمه یک فیلتر جداست. اگر آگهی حتی یکی از فیلترهای فعال را داشته باشد (در عنوان یا توضیحات)، نمایش داده نمی‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.newFilterType == "exclude",
                        onClick = { viewModel.updateNewFilterType("exclude") },
                        label = { Text("رد کن (اگر بود، پنهان شود)") },
                        leadingIcon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    FilterChip(
                        selected = state.newFilterType == "owner_signal",
                        onClick = { viewModel.updateNewFilterType("owner_signal") },
                        label = { Text("تایید مالک (اگر بود، نشان بده)") },
                        leadingIcon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppTextField(
                        value = state.newFilterLabel,
                        onValueChange = viewModel::updateNewFilterLabel,
                        label = if (state.newFilterType == "owner_signal") "عبارتی که یعنی مالک است" else "کلمهٔ فیلتر جدید",
                        isError = state.addFilterError != null,
                        supportingText = state.addFilterError,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(onClick = viewModel::addKeywordFilter, enabled = !state.isReapplyingFilters) {
                        if (state.isReapplyingFilters) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = "افزودن فیلتر")
                        }
                    }
                }
                if (state.newFilterType == "owner_signal") {
                    Text(
                        "این نوع، فقط تشخیص هوشمند/احتمالی مشاور بودن را نادیده می‌گیرد — اگر آگهی همزمان یکی از " +
                            "کلمات «رد کن» (مثل دفتر/مشاور/املاک) را هم داشته باشد، همچنان پنهان می‌ماند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (state.isLoadingKeywordFilters) {
                item { LoadingState(modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            } else {
                items(state.keywordFilters, key = { "kw_${it.id}" }) { filter ->
                    KeywordFilterRow(
                        filter = filter,
                        onToggle = { enabled -> viewModel.toggleKeywordFilter(filter, enabled) },
                        onDelete = { viewModel.removeKeywordFilter(filter.id) },
                    )
                }
            }

            // ---- Permanent phone-number blocklist ----
            item {
                SectionTitleRow(
                    icon = Icons.Outlined.Phone,
                    title = "شماره‌های مسدود",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    "شماره‌هایی که هرگز نمی‌خواهید آگهی‌شان نمایش داده شود",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SectionHeader("افزودن شماره جدید", modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppTextField(
                        value = state.newPhoneNumber,
                        onValueChange = viewModel::updateNewPhoneNumber,
                        label = "شماره تلفن",
                        keyboardType = KeyboardType.Phone,
                        isError = state.addError != null,
                        supportingText = state.addError,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedIconButton(onClick = { launchContactPicker() }) {
                        Icon(Icons.Outlined.Contacts, contentDescription = "انتخاب از مخاطبین")
                    }
                }
                Text(
                    "به‌جای تایپ، می‌توانید شماره را مستقیماً از مخاطبین گوشی انتخاب کنید — همان لحظه به لیست مسدود اضافه می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                AppTextField(
                    value = state.newPhoneNote,
                    onValueChange = viewModel::updateNewPhoneNote,
                    label = "یادداشت (اختیاری)",
                )
            }
            item {
                OutlinedButton(onClick = viewModel::addNumber, modifier = Modifier.fillMaxWidth()) {
                    Text("افزودن به لیست")
                }
            }

            if (state.isLoading) {
                item { LoadingState() }
            } else if (state.numbers.isEmpty()) {
                item { EmptyState("هیچ شماره‌ای مسدود نشده است") }
            } else {
                item { SectionHeader("لیست فعلی", modifier = Modifier.padding(top = 16.dp)) }
                items(state.numbers, key = { "phone_${it.id}" }) { blocked ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(blocked.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                                blocked.note?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.removeNumber(blocked.id) }) {
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
        }
    }
}

@Composable
private fun SectionTitleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun KeywordFilterRow(
    filter: KeywordFilter,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val style = keywordFilterCategoryStyle(filter.category)

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(200)),
        colors = CardDefaults.cardColors(
            containerColor = if (filter.isEnabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.icon, contentDescription = null, tint = style.color)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    filter.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (filter.isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "سفارشی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Switch(
                checked = filter.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = style.color),
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "حذف فیلتر",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
