package com.lotto.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CalendarBotStatus(
    val message: String,
    val checkedAt: String?
) {
    fun displayText(): String = buildString {
        append(message)
        if (!checkedAt.isNullOrBlank()) append("\nตรวจล่าสุด: ").append(checkedAt)
    }
}

class CalendarBotStatusRepository {
    suspend fun fetchLatest(): CalendarBotStatus = withContext(Dispatchers.IO) {
        val url = URL("https://raw.githubusercontent.com/preechaphukthaisong65-arch/lotto/main/data/bot/glo-calendar-status.json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "LottoAndroidApp/1.0")
            useCaches = false
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext CalendarBotStatus("บอทกำลังรอตรวจสอบรอบแรกจากเว็บไซต์กองสลาก", null)
            }
            if (statusCode !in 200..299) {
                return@withContext CalendarBotStatus("ไม่สามารถอ่านรายงานบอทได้ (HTTP $statusCode)", null)
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val message = json.optString("message_th", "ได้รับรายงานจากบอท แต่ไม่มีข้อความสถานะ")
            val checkedAt = json.optString("checked_at_thailand").takeIf { it.isNotBlank() && it != "null" }
            CalendarBotStatus(message, checkedAt)
        } catch (error: Exception) {
            CalendarBotStatus("ไม่สามารถเชื่อมต่อรายงานบอทได้: ${error.javaClass.simpleName}", null)
        } finally {
            connection.disconnect()
        }
    }
}
