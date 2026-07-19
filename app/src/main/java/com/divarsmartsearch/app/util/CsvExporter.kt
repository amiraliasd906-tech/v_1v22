package com.divarsmartsearch.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.divarsmartsearch.app.domain.model.Listing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the current results list to a CSV file (opens fine in Excel/Google
 * Sheets/Numbers) and returns a share Intent so the person can save it
 * wherever they like — no server round-trip, everything stays on-device.
 */
object CsvExporter {

    private val HEADER = listOf(
        "عنوان", "قیمت (تومان)", "متراژ", "قیمت هر متر", "شهر", "محله",
        "احتمال مالک بودن (٪)", "امتیاز ستاره‌ای", "آگهی تکراری؟",
        "اختلاف قیمت با میانگین منطقه (٪)", "تعداد تکرار شماره تماس",
        "شماره‌های یافت‌شده", "لینک آگهی",
    )

    fun exportToCsv(context: Context, listings: List<Listing>): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "divar-results-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.csv"
        val file = File(exportsDir, fileName)

        file.bufferedWriter().use { writer ->
            // UTF-8 BOM so Excel opens Persian text correctly instead of mojibake.
            writer.write('\uFEFF'.toString())
            writer.write(HEADER.joinToString(",") { escape(it) })
            writer.newLine()

            for (listing in listings) {
                val row = listOf(
                    listing.title,
                    listing.price?.toLong()?.toString() ?: "",
                    listing.area?.toString() ?: "",
                    listing.pricePerMeter?.toLong()?.toString() ?: "",
                    listing.city ?: "",
                    listing.neighborhood ?: "",
                    listing.ownerProbability?.let { ((it) * 100).toInt().toString() } ?: "",
                    listing.starRating.toString(),
                    if (listing.isDuplicate) "بله" else "خیر",
                    listing.pricePerMeterVsAreaAveragePercent?.let { "%.1f".format(it) } ?: "",
                    listing.phoneRepeatCount.toString(),
                    listing.detectedPhoneNumbers.joinToString(" | "),
                    listing.url,
                )
                writer.write(row.joinToString(",") { escape(it) })
                writer.newLine()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(context: Context, uri: Uri): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "اشتراک‌گذاری فایل CSV")
    }

    private fun escape(value: String): String {
        val needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }
}
