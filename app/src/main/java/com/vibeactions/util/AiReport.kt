package com.vibeactions.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

// Neon project "automatiq": an anonymous token from Neon Auth may only INSERT into ai_reports
// (no read/update/delete; reason/source/length are constrained in the table).
private const val REPORT_AUTH_URL =
    "https://ep-withered-boat-b99idpnw.neonauth.c-7.eu-central-1.aws.neon.tech/neondb/auth"
private const val REPORT_API_URL =
    "https://ep-withered-boat-b99idpnw.apirest.c-7.eu-central-1.aws.neon.tech/neondb/rest/v1"

enum class AiReportReason(val id: String, val label: String) {
    OFFENSIVE("offensive", "Offensive or hateful"),
    HARMFUL("harmful", "Harmful or dangerous"),
    INACCURATE("inaccurate", "Wrong or misleading"),
    SPAM("spam", "Spam"),
    OTHER("other", "Something else")
}

enum class AiReportSource(val id: String) {
    AUTO_REPLY("auto_reply"), VARIATION("variation"), COMPOSE("compose"), PREVIEW("preview")
}

@Serializable
data class AiReportBody(
    val reason: String,
    val source: String,
    @SerialName("ai_text") val aiText: String,
    val note: String? = null,
    val model: String? = null,
    @SerialName("app_version") val appVersion: String? = null
)

@Serializable private data class TokenResponse(val token: String)

private val reportJson = Json { ignoreUnknownKeys = true }

/** Builds the stored report; trims to the table's limits so a long text never fails the insert. */
fun buildAiReport(
    reason: AiReportReason, source: AiReportSource, aiText: String, note: String?,
    model: String?, appVersion: String?
) = AiReportBody(
    reason = reason.id,
    source = source.id,
    aiText = aiText.trim().take(2000).ifEmpty { "(empty)" },
    note = note?.trim()?.take(1000)?.ifEmpty { null },
    model = model?.take(64),
    appVersion = appVersion?.take(32)
)

/** Sends a report of an AI-written text. Only the AI text, reason and optional note leave the
 *  phone — no phone numbers or incoming messages. Throws on failure. */
suspend fun sendAiReport(report: AiReportBody) = withContext(Dispatchers.IO) {
    val token = request("$REPORT_AUTH_URL/token/anonymous", "GET", null, null).let {
        reportJson.decodeFromString(TokenResponse.serializer(), it).token
    }
    request(
        "$REPORT_API_URL/ai_reports", "POST", token,
        reportJson.encodeToString(AiReportBody.serializer(), report)
    )
}

private fun request(url: String, method: String, bearer: String?, body: String?): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        bearer?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("HTTP $code ${err.take(160)}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
