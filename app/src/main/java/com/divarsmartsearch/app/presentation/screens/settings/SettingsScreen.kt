package com.divarsmartsearch.app.presentation.screens.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.divarsmartsearch.app.presentation.components.AppTextField
import com.divarsmartsearch.app.presentation.components.SectionHeader
import com.divarsmartsearch.app.util.BatteryOptimization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings
    var apiKeyField by remember(settings.anthropicApiKey) { mutableStateOf(settings.anthropicApiKey.orEmpty()) }

    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(BatteryOptimization.isIgnoringBatteryOptimizations(context))
    }
    val batteryExemptionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system dialog doesn't report the outcome, so just re-check.
        isIgnoringBatteryOptimizations = BatteryOptimization.isIgnoringBatteryOptimizations(context)
    }
    // Also re-check when returning to this screen from the OS Settings app,
    // since that path doesn't go through the launcher callback above.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = BatteryOptimization.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("تنظیمات") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SectionHeader("جستجوی خودکار در پس‌زمینه")
                Text(
                    "وقتی روشن باشد، دیگر لازم نیست خودتان برنامه را باز کنید و صفحه را اسکرول کنید — " +
                        "برنامه هر چند دقیقه یک‌بار خودش جستجوهای فعال شما را در دیوار بررسی می‌کند و " +
                        "فقط برای آگهی‌هایی که صد‌درصد از فیلترها رد شده‌اند اعلان می‌فرستد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                SettingRow(
                    title = "اسکن خودکار در پس‌زمینه فعال باشد",
                    checked = settings.backgroundScanEnabled,
                    onCheckedChange = viewModel::updateBackgroundScanEnabled,
                )
            }
            if (settings.backgroundScanEnabled) {
                item {
                    Text(
                        "فاصله بین هر بررسی: هر ${settings.backgroundScanIntervalMinutes} دقیقه",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = settings.backgroundScanIntervalMinutes.toFloat(),
                        onValueChange = { viewModel.updateBackgroundScanInterval(it.toInt()) },
                        valueRange = 2f..30f,
                        steps = 27,
                    )
                }
                if (!isIgnoringBatteryOptimizations) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "بهینه‌سازی باتری اندروید ممکن است این برنامه را در پس‌زمینه ببندد و " +
                                        "مانع اجرای اسکن خودکار شود. برای اینکه اسکن پس‌زمینه واقعاً کار کند، " +
                                        "اجازه دهید برنامه از بهینه‌سازی باتری مستثنا شود.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(
                                    onClick = {
                                        try {
                                            batteryExemptionLauncher.launch(
                                                BatteryOptimization.requestExemptionIntent(context),
                                            )
                                        } catch (e: ActivityNotFoundException) {
                                            // Some OEM builds (e.g. certain MIUI versions) reject the
                                            // direct-exemption intent outright; fall back to the
                                            // general battery-optimization list.
                                            try {
                                                batteryExemptionLauncher.launch(BatteryOptimization.settingsIntent())
                                            } catch (e2: ActivityNotFoundException) {
                                                // Nothing we can do — no such settings screen exists on this device.
                                            }
                                        }
                                    },
                                ) {
                                    Text("مستثنا کردن از بهینه‌سازی باتری")
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingRow(
                    title = "حالت شب",
                    checked = settings.darkModeEnabled,
                    onCheckedChange = viewModel::updateDarkMode,
                )
            }
            item {
                SettingRow(
                    title = "اعلان‌ها فعال باشند",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::updateNotificationsEnabled,
                )
            }
            item {
                SettingRow(
                    title = "صدای اعلان",
                    checked = settings.notificationSoundEnabled,
                    onCheckedChange = viewModel::updateNotificationSound,
                )
            }

            item {
                SectionHeader("تشخیص مشاور املاک", modifier = Modifier.padding(top = 8.dp))
                Text(
                    "فقط آگهی‌هایی که احتمال مالک‌بودن آن‌ها بین ۵۰٪ تا ۱۰۰٪ تخمین زده شود در " +
                        "نتایج نشان داده می‌شوند — این قانون ثابت است و قابل تغییر نیست.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionHeader("تحلیل هوشمند متن آگهی (اختیاری)", modifier = Modifier.padding(top = 8.dp))
                Text(
                    "برای تحلیل دقیق‌تر متن آگهی با هوش مصنوعی Claude، کلید API خودتان را وارد کنید. " +
                        "بدون این کلید، تشخیص با روش قانون‌محور آفلاین انجام می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AppTextField(
                    value = apiKeyField,
                    onValueChange = { apiKeyField = it },
                    label = "کلید API آنتروپیک (Anthropic)",
                    keyboardType = KeyboardType.Password,
                )
            }
            item {
                androidx.compose.material3.Button(
                    onClick = { viewModel.updateAnthropicApiKey(apiKeyField) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("ذخیره کلید API")
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
