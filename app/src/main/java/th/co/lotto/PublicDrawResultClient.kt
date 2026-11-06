package th.co.lotto

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PublicDrawResult(
    val sourceId: String,
    val drawDateIso: String,
    val firstPrizeNumber: String,
    val threeTopNumber: String,
    val twoBottomNumber: String,
    val sourceUrl: String,
    val fetchedAtEpochMillis: Long
)

private object PublicJsonHttp {
    fun get(url: String, maxBytes: Int = 2_000_000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Lotto-Android-Research/1.0")
        }
        try {
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) { "HTTP $status จากแหล่งข้อมูล" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total = Math.addExact(total, count)
                    require(total <= maxBytes) { "ข้อมูลจากเว็บไซต์มีขนาดเกินกำหนด" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}

class LotteryChecksClient(
    private val endpoint: String = "https://lotterychecks.com/api/public/results?lottery=thai-government&limit=1"
) {
    fun fetch(drawDateEpochMillis: Long): PublicDrawResult {
        val date = isoDate(drawDateEpochMillis)
        val root = JSONObject(PublicJsonHttp.get(endpoint))
        require(root.optBoolean("success")) { "LotteryChecks ยังไม่ส่งผลสำเร็จ" }
        val rows = root.optJSONArray("data") ?: error("LotteryChecks ไม่มีข้อมูลผลรางวัล")
        val row = (0 until rows.length()).asSequence().mapNotNull { rows.optJSONObject(it) }
            .firstOrNull { it.optString("drawDate") == date }
            ?: error("LotteryChecks ยังไม่มีผลของงวด $date")
        val prizes = row.optJSONArray("prizes") ?: error("LotteryChecks ไม่มีรายการรางวัล")
        fun prize(slug: String, digits: Int): String {
            val item = (0 until prizes.length()).asSequence().mapNotNull { prizes.optJSONObject(it) }
                .firstOrNull { it.optString("slug") == slug }
                ?: error("LotteryChecks ไม่มีรางวัล $slug")
            val numbers = item.optJSONArray("numbers") ?: error("LotteryChecks ไม่มีเลข $slug")
            val value = numbers.optString(0)
            require(value.length == digits && value.all { it in '0'..'9' }) { "เลข $slug จาก LotteryChecks ไม่ถูกต้อง" }
            return value
        }
        val first = prize("first-prize", 6)
        val lastTwo = prize("last-two-digit", 2)
        return PublicDrawResult("LOTTERYCHECKS", date, first, first.takeLast(3), lastTwo,
            endpoint, System.currentTimeMillis())
    }
}

class HuayReviewClient(
    private val endpoint: String = "https://raw.githubusercontent.com/huayreview/thai-lottery-results/main/thai-lottery-results.json"
) {
    fun fetch(drawDateEpochMillis: Long): PublicDrawResult {
        val date = isoDate(drawDateEpochMillis)
        val root = JSONObject(PublicJsonHttp.get(endpoint))
        val draws = root.optJSONArray("draws") ?: error("HuayReview ไม่มีรายการ draws")
        val row = (0 until draws.length()).asSequence().mapNotNull { draws.optJSONObject(it) }
            .firstOrNull { it.optString("draw_date") == date }
            ?: error("HuayReview ยังไม่มีผลของงวด $date")
        val first = firstText(row.opt("first_prize"))
        val lastTwo = firstText(row.opt("last_two_digits"))
        require(first.length == 6 && first.all { it in '0'..'9' }) { "เลขรางวัลที่ 1 จาก HuayReview ไม่ถูกต้อง" }
        require(lastTwo.length == 2 && lastTwo.all { it in '0'..'9' }) { "เลขท้าย 2 ตัวจาก HuayReview ไม่ถูกต้อง" }
        return PublicDrawResult("HUAYREVIEW", date, first, first.takeLast(3), lastTwo,
            endpoint, System.currentTimeMillis())
    }

    private fun firstText(value: Any?): String = when (value) {
        is JSONArray -> value.optString(0)
        is String -> value
        else -> ""
    }
}

private fun isoDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Bangkok")
    }.format(Date(epochMillis))
