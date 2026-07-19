package com.divarsmartsearch.app.presentation.screens.home

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.divarsmartsearch.app.presentation.components.AppTextField
import com.divarsmartsearch.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPermanentFiltersScreen(
    viewModel: NewSearchViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                    viewModel.addBlockedNumberFromContact(number, name)
                }
            }
        }
    }
    fun launchContactPicker() {
        pickContactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }

    LaunchedEffect(Unit) { viewModel.loadBlockedNumbers() }
    LaunchedEffect(state.didSaveSuccessfully) {
        if (state.didSaveSuccessfully) {
            viewModel.resetAfterSave()
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("فیلترهای دائمی") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "شماره‌هایی که هرگز نمی‌خواهید آگهی‌شان نمایش داده شود را اینجا اضافه یا مدیریت کنید. این فهرست روی همه جستجوها اعمال می‌شود.",
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
                        isError = state.addPhoneError != null,
                        supportingText = state.addPhoneError,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedIconButton(onClick = { launchContactPicker() }) {
                        Icon(Icons.Outlined.Contacts, contentDescription = "انتخاب از مخاطبین")
                    }
                }
            }
            item {
                AppTextField(
                    value = state.newPhoneNote,
                    onValueChange = viewModel::updateNewPhoneNote,
                    label = "یادداشت (اختیاری)",
                )
            }
            item {
                OutlinedButton(
                    onClick = viewModel::addBlockedNumber,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("افزودن به لیست")
                }
            }

            item {
                SectionHeader("شماره‌های مسدود‌شده فعلی", modifier = Modifier.padding(top = 16.dp))
            }

            if (state.isLoadingBlockedNumbers) {
                item { CircularProgressIndicator() }
            } else if (state.blockedNumbers.isEmpty()) {
                item {
                    Text(
                        "هیچ شماره‌ای مسدود نشده است",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.blockedNumbers, key = { it.id }) { blocked ->
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
                            IconButton(onClick = { viewModel.removeBlockedNumber(blocked.id) }) {
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

            state.saveError?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("بازگشت")
                    }
                    Button(
                        onClick = { viewModel.saveSearch() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(if (state.isEditMode) "ذخیره تغییرات" else "ذخیره جستجو")
                    }
                }
            }
        }
    }
}
