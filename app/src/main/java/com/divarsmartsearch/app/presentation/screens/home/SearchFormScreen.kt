package com.divarsmartsearch.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.divarsmartsearch.app.presentation.components.AppTextField
import com.divarsmartsearch.app.presentation.components.SectionHeader

/**
 * Step 1 of "new search". Per explicit user request, the old
 * price-range / area-range / location / property-type / listing-age
 * range filters have been removed from this screen entirely — only the
 * search name, the Divar search-page link, and the "فقط آگهی‌های
 * شخصی" (personal-ads-only) option remain. Telling agency posts apart
 * from real owners now happens entirely through the keyword filters +
 * AI owner-detection configured in the next step ("فیلترهای دائمی"),
 * not through these structured ranges.
 *
 * The underlying [com.divarsmartsearch.app.domain.model.SavedSearchDraft]
 * still technically has the old fields (so existing saved searches from
 * before this change keep working), they're just never set from this UI
 * anymore, which means [com.divarsmartsearch.app.data.filters.FilterPipeline]
 * never rejects anything based on them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFormScreen(
    viewModel: NewSearchViewModel,
    onContinue: () -> Unit,
    onOpenFilterPicker: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val draft = state.draft

    if (state.isLoadingForEdit) {
        Scaffold(topBar = { TopAppBar(title = { Text("در حال بارگذاری…") }) }) { padding ->
            com.divarsmartsearch.app.presentation.components.LoadingState(
                modifier = Modifier.padding(padding)
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (state.isEditMode) "ویرایش جستجو" else "جستجوی جدید") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionHeader("اطلاعات پایه")
                AppTextField(
                    value = draft.name,
                    onValueChange = viewModel::updateName,
                    label = "نام جستجو",
                )
            }
            item {
                AppTextField(
                    value = draft.searchUrl,
                    onValueChange = viewModel::updateSearchUrl,
                    label = "لینک جستجوی دیوار",
                    keyboardType = KeyboardType.Uri,
                    supportingText = "لینک صفحه نتایج جستجو را از اپ یا سایت دیوار کپی کنید",
                )
            }
            item {
                Button(
                    onClick = onOpenFilterPicker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("افزودن فیلتر جدید")
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تشخیص آگهی شخصی",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "این جستجو فقط آگهی‌هایی را نشان می‌دهد که احتمال مالک‌بودن آن‌ها " +
                                "بین ۵۰٪ تا ۱۰۰٪ تخمین زده شود — نه بیشتر، نه کمتر؛ این قانون ثابت " +
                                "است و قابل تغییر نیست. فیلترهای کلمه‌ای که خودتان از صفحه «فیلترهای " +
                                "دائمی» اضافه می‌کنید، مستقل از این قانون و همیشه فعال، هر آگهی " +
                                "منطبق را رد می‌کنند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            state.formError?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        if (viewModel.validateStepOne()) onContinue()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("ادامه: فیلترهای دائمی")
                }
            }
        }
    }
}
