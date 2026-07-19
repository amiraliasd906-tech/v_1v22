package com.divarsmartsearch.app.data.ai

import com.divarsmartsearch.app.domain.model.Listing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets the person ask free-form questions about one listing ("این ملک سند
 * دارد؟", "آیا این قیمت برای این متراژ منطقی است؟") using the same
 * Anthropic API key they already entered in Settings for owner detection.
 * Runs directly from the phone — no separate backend.
 */
@Singleton
class ListingAiAssistant @Inject constructor() {

    @Serializable
    private data class AnthropicMessage(val role: String, val content: String)

    @Serializable
    private data class AnthropicRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<AnthropicMessage>,
    )

    @Serializable
    private data class AnthropicContentBlock(val type: String, val text: String? = null)

    @Serializable
    private data class AnthropicResponse(val content: List<AnthropicContentBlock>)

    @Serializable
    private data class AnthropicErrorBody(val error: AnthropicErrorDetail? = null)

    @Serializable
    private data class AnthropicErrorDetail(val message: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class AskResult {
        data class Success(val answer: String) : AskResult()
        data class Failure(val message: String) : AskResult()
    }

    suspend fun askQuestion(
        listing: Listing,
        question: String,
        apiKey: String?,
        model: String,
    ): AskResult = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext AskResult.Failure(
                "برای استفاده از دستیار هوش مصنوعی، ابتدا کلید API آنتروپیک خودتان را در تنظیمات وارد کنید."
            )
        }
        if (question.isBlank()) {
            return@withContext AskResult.Failure("سؤال نمی‌تواند خالی باشد.")
        }

        try {
            val context = buildString {
                appendLine("عنوان آگهی: ${listing.title}")
                listing.price?.let { appendLine("قیمت: ${it.toLong()} تومان") }
                listing.area?.let { appendLine("متراژ: ${it.toInt()} متر") }
                listing.pricePerMeter?.let { appendLine("قیمت هر متر: ${it.toLong()} تومان") }
                listing.city?.let { appendLine("شهر: $it") }
                listing.neighborhood?.let { appendLine("محله: $it") }
                appendLine("احتمال مالک مستقیم بودن آگهی‌دهنده: ${listing.ownerProbability?.let { (it * 100).toInt() } ?: "نامشخص"}٪")
                if (listing.isDuplicate) appendLine("این آگهی احتمالاً تکراری/بازنشرشده تشخیص داده شده است.")
                listing.pricePerMeterVsAreaAveragePercent?.let {
                    val direction = if (it >= 0) "بالاتر" else "پایین‌تر"
                    appendLine("قیمت هر متر این آگهی حدود ${"%.0f".format(kotlin.math.abs(it))}٪ ${direction} از میانگین منطقه است.")
                }
                if (!listing.description.isNullOrBlank()) {
                    appendLine("متن کامل آگهی:")
                    appendLine(listing.description)
                }
            }

            val prompt = "تو یک دستیار خرید ملک هستی. با توجه به اطلاعات زیر دربارهٔ یک آگهی ملکی از دیوار، " +
                "به سؤال کاربر با دقت و به زبان فارسی و به‌طور مختصر پاسخ بده. اگر پاسخ از روی متن آگهی " +
                "قابل استنتاج نیست، صادقانه بگو که این اطلاعات در آگهی ذکر نشده و حدس نزن.\n\n" +
                "اطلاعات آگهی:\n$context\n\nسؤال کاربر: $question"

            val requestBody = json.encodeToString(
                AnthropicRequest(
                    model = model,
                    max_tokens = 700,
                    messages = listOf(AnthropicMessage(role = "user", content = prompt)),
                )
            )

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string()
                if (!response.isSuccessful) {
                    val errorMessage = bodyText
                        ?.let { runCatching { json.decodeFromString<AnthropicErrorBody>(it) }.getOrNull() }
                        ?.error?.message
                    return@withContext AskResult.Failure(
                        errorMessage ?: "خطا در ارتباط با Claude (کد ${response.code})"
                    )
                }
                if (bodyText == null) return@withContext AskResult.Failure("پاسخی از سرور دریافت نشد.")

                val parsed = json.decodeFromString<AnthropicResponse>(bodyText)
                val answer = parsed.content.firstOrNull { it.type == "text" }?.text
                if (answer.isNullOrBlank()) {
                    AskResult.Failure("پاسخ قابل فهمی دریافت نشد.")
                } else {
                    AskResult.Success(answer.trim())
                }
            }
        } catch (e: Exception) {
            AskResult.Failure(e.message ?: "خطایی هنگام ارتباط با هوش مصنوعی رخ داد.")
        }
    }
}
