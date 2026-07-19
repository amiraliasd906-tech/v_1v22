package com.divarsmartsearch.app.data.filters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Estimates the probability that a listing was posted by an agency
 * rather than the property owner. Ported from the backend's
 * owner_detector.py:
 *   - HeuristicOwnerDetector: offline, rule-based (always available)
 *   - LlmOwnerDetector: sends the description straight from the phone to
 *     Claude for real semantic analysis, when the user has entered their
 *     own Anthropic API key in Settings. Falls back to the heuristic on
 *     any failure or if no key is configured.
 */
object OwnerDetector {

    private val AGENCY_KEYWORDS = mapOf(
        "مشاور املاک" to 0.35, "آژانس املاک" to 0.35, "کد ملک" to 0.30,
        "کد ملکی" to 0.30, "بازدید هماهنگ" to 0.20, "فایل اختصاصی" to 0.20,
        "فایل ویژه" to 0.15, "همکار محترم" to 0.30, "کارشناس فروش" to 0.25,
        "بنگاه" to 0.30, "جهت هماهنگی بازدید" to 0.20, "با هماهنگی مشاور" to 0.25,
    )

    private val OWNER_KEYWORDS = mapOf(
        "بدون واسطه" to 0.35, "مالک مستقیم" to 0.40, "شخصی" to 0.15,
        "فقط مشتری واقعی" to 0.20, "لطفا مشاورین تماس نگیرند" to 0.40,
        "بنگاهی تماس نگیره" to 0.35, "خودم مالکم" to 0.40,
    )

    private val NEGATION_PATTERN = Regex("""مشاور(ین)?\s+(تماس\s+نگیر|زنگ\s+نزن)""")

    /**
     * @param phoneRepeatCount how many *other* stored listings (any search)
     *   share one of this ad's phone numbers. A number that keeps showing
     *   up across many ads is a strong signal of a professional agent
     *   rather than a private owner selling their own place once, so it
     *   nudges the score toward "agency" on top of the text-based signals.
     */
    fun heuristicAgencyProbability(description: String?, phoneRepeatCount: Int = 0): Double {
        // Same Yeh/Kaf/ZWNJ normalization as KeywordFilterEngine — without this,
        // real Divar text using the Arabic forms (ي/ك) or a zero-width
        // non-joiner inside a keyword (very common from copy-pasted ads)
        // silently fails every `contains` check below and the score falls
        // back to the neutral 0.5 baseline no matter what the ad actually says.
        var score = if (description.isNullOrBlank()) 0.5 else {
            val normalized = KeywordFilterEngine.normalize(description)
            var s = 0.5
            for ((phrase, weight) in AGENCY_KEYWORDS) if (normalized.contains(phrase)) s += weight
            for ((phrase, weight) in OWNER_KEYWORDS) if (normalized.contains(phrase)) s -= weight
            if (NEGATION_PATTERN.containsMatchIn(normalized)) s -= 0.4
            s
        }

        // Cautious, capped boost: +0.08 per repeat beyond the first, up to +0.30.
        if (phoneRepeatCount > 0) {
            score += (phoneRepeatCount * 0.08).coerceAtMost(0.30)
        }

        return score.coerceIn(0.0, 1.0)
    }

    @Serializable
    private data class AnthropicMessage(val role: String, val content: String)

    @Serializable
    private data class AnthropicRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<AnthropicMessage>,
        // Deterministic on purpose: this score is compared against a fixed
        // 0.5 threshold to decide visible-vs-rejected, and the pipeline
        // re-evaluates it on every manual refresh. Without pinning this to
        // 0, the same ad text could land on either side of 0.5 on
        // different calls, making a listing randomly flip between the
        // results and the Rejected tab from one refresh to the next.
        val temperature: Double = 0.0,
    )

    @Serializable
    private data class AnthropicContentBlock(val type: String, val text: String? = null)

    @Serializable
    private data class AnthropicResponse(val content: List<AnthropicContentBlock>)

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /**
     * Calls Claude directly from the phone using the user's own API key.
     * Returns null on any failure (missing key, network error, unparseable
     * response) so the caller can fall back to the heuristic.
     */
    private suspend fun callLlm(description: String, apiKey: String, model: String): Double? =
        withContext(Dispatchers.IO) {
            try {
                val prompt = "متن آگهی ملکی زیر را با دقت بخوان و لحن، واژگان و ساختار جمله را " +
                    "تحلیل کن (نه فقط جست‌وجوی کلمات خاص) تا مشخص شود این آگهی احتمالاً " +
                    "توسط یک مشاور/آژانس املاک نوشته شده یا توسط مالک مستقیم ملک.\n\n" +
                    "فقط و فقط یک عدد اعشاری بین 0 و 1 برگردان، بدون هیچ توضیح یا متن " +
                    "اضافه. عدد نزدیک به 1 یعنی احتمال بالای «مشاور املاک»، عدد نزدیک " +
                    "به 0 یعنی احتمال بالای «مالک مستقیم».\n\nمتن آگهی:\n$description"

                val requestBody = json.encodeToString(
                    AnthropicRequest(
                        model = model,
                        max_tokens = 8,
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
                    if (!response.isSuccessful) return@withContext null
                    val bodyText = response.body?.string() ?: return@withContext null
                    val parsed = json.decodeFromString<AnthropicResponse>(bodyText)
                    val rawText = parsed.content.firstOrNull { it.type == "text" }?.text ?: return@withContext null
                    val match = Regex("""\d*\.?\d+""").find(rawText) ?: return@withContext null
                    match.value.toDoubleOrNull()?.coerceIn(0.0, 1.0)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Main entry point: tries the LLM (if configured) and falls back to
     * the heuristic. Returns the agency probability in [0, 1].
     *
     * @param phoneRepeatCount see [heuristicAgencyProbability]. Applied on
     *   top of the LLM score too (the LLM only sees the ad's own text, not
     *   cross-listing phone-number history), with the same capped boost.
     */
    suspend fun agencyProbability(
        description: String?,
        apiKey: String?,
        model: String,
        phoneRepeatCount: Int = 0,
    ): Double {
        if (!description.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            val llmScore = callLlm(description, apiKey, model)
            if (llmScore != null) {
                val boost = if (phoneRepeatCount > 0) (phoneRepeatCount * 0.08).coerceAtMost(0.30) else 0.0
                return (llmScore + boost).coerceIn(0.0, 1.0)
            }
        }
        return heuristicAgencyProbability(description, phoneRepeatCount)
    }
}
