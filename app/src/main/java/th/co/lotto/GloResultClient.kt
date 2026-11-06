package th.co.lotto

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GloTopResult(
    val drawDateIso: String,
    val firstPrizeNumber: String,
    val threeTopNumber: String,
    val sourceUrl: String,
    val fetchedAtEpochMillis: Long
)

/**
 * Retrieves only public draw-result data from the official GLO endpoint.
 * No sale, customer, credit, payment, or receipt data is sent.
 */
class GloResultClient(
    private val baseUrl: String = OFFICIAL_BASE_URL,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 15_000
) {
    fun fetchTopResult(drawDateEpochMillis: Long): GloTopResult {
        val requestedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bangkok")
        }.format(Date(drawDateEpochMillis))
        val day = requestedDate.substring(8, 10)
        val month = requestedDate.substring(5, 7)
        val year = requestedDate.substring(0, 4)
        val requestBody = JSONObject()
            .put("date", day)
            .put("month", month)
            .put("year", year)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val endpoint = "$baseUrl/api/checking/getLotteryResult"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
        }

        try {
            connection.outputStream.use { it.write(requestBody) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RESPONSE_BYTES) { "GLO response is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: ByteArray(0)
            require(status == HttpURLConnection.HTTP_OK) { "GLO returned HTTP $status" }
            return parseResponse(String(bytes, Charsets.UTF_8), requestedDate, endpoint, System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val OFFICIAL_BASE_URL = "https://www.glo.or.th"
        const val OFFICIAL_ENDPOINT = "$OFFICIAL_BASE_URL/api/checking/getLotteryResult"
        private const val MAX_RESPONSE_BYTES = 1_048_576

        internal fun parseResponse(
            json: String,
            requestedDateIso: String,
            sourceUrl: String = OFFICIAL_ENDPOINT,
            fetchedAtEpochMillis: Long = 0L
        ): GloTopResult {
            val envelope = JSONObject(json)
            require(envelope.optInt("statusCode") == 200 && envelope.optBoolean("status")) {
                "GLO did not confirm a successful result response"
            }
            val response = envelope.optJSONObject("response")
                ?: error("GLO response object is missing")
            val result = response.optJSONObject("result")
                ?: error("GLO has not published a result for this draw date")
            require(result.optInt("status") == 1) { "GLO draw result is not in published status" }
            val drawDate = result.optString("date")
            require(drawDate == requestedDateIso) {
                "GLO draw date mismatch: requested $requestedDateIso, received $drawDate"
            }
            val firstPrize = result.getJSONObject("data")
                .getJSONObject("first")
                .getJSONArray("number")
            require(firstPrize.length() > 0) { "GLO first-prize number is missing" }
            var firstEntry: JSONObject? = null
            var firstRound = Int.MAX_VALUE
            for (index in 0 until firstPrize.length()) {
                val candidate = firstPrize.getJSONObject(index)
                val round = candidate.optInt("round", index + 1)
                if (round < firstRound) {
                    firstRound = round
                    firstEntry = candidate
                }
            }
            val firstNumber = requireNotNull(firstEntry).getString("value").trim()
            require(firstNumber.length == 6 && firstNumber.all { it in '0'..'9' }) {
                "GLO first-prize number must contain exactly six digits"
            }
            return GloTopResult(
                drawDateIso = drawDate,
                firstPrizeNumber = firstNumber,
                threeTopNumber = firstNumber.takeLast(3),
                sourceUrl = sourceUrl,
                fetchedAtEpochMillis = fetchedAtEpochMillis
            )
        }
    }
}
