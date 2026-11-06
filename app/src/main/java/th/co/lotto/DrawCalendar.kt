package th.co.lotto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val CALENDAR_CHANNEL_ID = "draw_calendar_reminders"
private const val CALENDAR_WORK_NAME = "lotto_draw_calendar_daily"
private val BANGKOK = ZoneId.of("Asia/Bangkok")

data class DrawCalendarState(
    val status: String,
    val lotteryChecksDates: List<LocalDate>,
    val huayReviewDates: List<LocalDate>,
    val confirmedDates: List<LocalDate>,
    val lastCheckedEpochMillis: Long?,
    val message: String
)

private data class CalendarSourceSnapshot(
    val sourceId: String,
    val dates: List<LocalDate>,
    val fetchedAt: Long,
    val sourceUrl: String,
    val error: String? = null
) {
    val successful: Boolean get() = error == null && dates.isNotEmpty()
}

object DrawCalendarService {
    fun refresh(context: Context): DrawCalendarState {
        val today = LocalDate.now(BANGKOK)
        val lottery = runCatching { LotteryChecksCalendarClient.fetch(today) }
            .getOrElse { CalendarSourceSnapshot("LOTTERYCHECKS", emptyList(), System.currentTimeMillis(),
                "https://lotterychecks.com/draw-calendar", it.message ?: "fetch failed") }
        val huay = runCatching { HuayReviewCalendarClient.fetch(today) }
            .getOrElse { CalendarSourceSnapshot("HUAYREVIEW", emptyList(), System.currentTimeMillis(),
                "https://huayreview.net/calendar-" + (today.year + 543) + "/", it.message ?: "fetch failed") }
        CalendarAuditLog.append(context, lottery)
        CalendarAuditLog.append(context, huay)
        return readState(context)
    }

    fun readState(context: Context): DrawCalendarState {
        val lottery = CalendarAuditLog.latest(context, "LOTTERYCHECKS")
        val huay = CalendarAuditLog.latest(context, "HUAYREVIEW")
        val latestTime = listOfNotNull(lottery?.fetchedAt, huay?.fetchedAt).maxOrNull()
        if (lottery == null || huay == null) {
            return DrawCalendarState("UNAVAILABLE", lottery?.dates.orEmpty(), huay?.dates.orEmpty(),
                emptyList(), latestTime, "ยังไม่มีผลตรวจสอบครบทั้งสองเว็บไซต์")
        }
        val stale = System.currentTimeMillis() - minOf(lottery.fetchedAt, huay.fetchedAt) > 36L * 60L * 60L * 1000L
        if (!lottery.successful || !huay.successful) {
            val reason = listOfNotNull(
                lottery.error?.let { "LotteryChecks: $it" },
                huay.error?.let { "HuayReview: $it" }
            ).joinToString(" • ")
            return DrawCalendarState(if (stale) "STALE" else "UNAVAILABLE",
                lottery.dates, huay.dates, emptyList(), latestTime,
                reason.ifBlank { "แหล่งข้อมูลใดแหล่งหนึ่งไม่มีวันงวดในอนาคต" })
        }
        val confirmed = mutableListOf<LocalDate>()
        val compareCount = minOf(lottery.dates.size, huay.dates.size, 8)
        for (i in 0 until compareCount) {
            if (lottery.dates[i] != huay.dates[i]) break
            confirmed.add(lottery.dates[i])
        }
        val hasMismatch = compareCount > confirmed.size
        if (stale) {
            return DrawCalendarState("STALE", lottery.dates, huay.dates, emptyList(), latestTime,
                "ข้อมูลเกิน 36 ชั่วโมงแล้ว ต้องตรวจสอบใหม่ก่อนใช้ยืนยันวันงวด")
        }
        if (confirmed.isEmpty() || hasMismatch) {
            return DrawCalendarState("DISAGREEMENT", lottery.dates, huay.dates, emptyList(), latestTime,
                "กำหนดการลำดับถัดไปไม่ตรงกัน หรือข้อมูลของงวดถัดไปไม่ครบ จึงยังไม่ส่งการแจ้งเตือนวันงวด")
        }
        return DrawCalendarState("CONFIRMED", lottery.dates, huay.dates, confirmed, latestTime,
            "ยืนยันได้เฉพาะวันที่ตรงกันตามลำดับจากทั้งสองเว็บไซต์")
    }

    internal fun parseLotteryChecks(html: String, today: LocalDate): List<LocalDate> {
        val plain = htmlToText(html)
        val section = plain.substringAfter("งวดถัดไป", plain)
            .substringBefore("หวยลาวพัฒนา")
            .substringBefore("หวยออมสิน")
        return validateFutureDates(fullDateMatches(section), today)
    }

    internal fun parseHuayReview(html: String, pageYear: Int, today: LocalDate): List<LocalDate> {
        val dates = linkedSetOf<LocalDate>()
        dates.addAll(fullDateMatches(htmlToText(html)))
        val rowRegex = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>")
        val dayRegex = Regex("""(\d{1,2})\s*(มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม|ม\.ค\.|ก\.พ\.|มี\.ค\.|เม\.ย\.|พ\.ค\.|มิ\.ย\.|ก\.ค\.|ส\.ค\.|ก\.ย\.|ต\.ค\.|พ\.ย\.|ธ\.ค\.)""")
        for (row in rowRegex.findAll(html)) {
            val rowText = htmlToText(row.groupValues[1])
            val yearText = Regex("""(25\d{2})""").find(rowText)?.groupValues?.get(1)
            val year = yearText?.toIntOrNull() ?: pageYear
            if (year !in 2500..2700) continue
            for (match in dayRegex.findAll(rowText)) {
                val day = match.groupValues[1].toIntOrNull() ?: continue
                val month = thaiMonthNumber(match.groupValues[2]) ?: continue
                runCatching { dates.add(LocalDate.of(year - 543, month, day)) }
            }
        }
        return validateFutureDates(dates.toList(), today)
    }

    private fun fullDateMatches(text: String): List<LocalDate> {
        val monthNames = "มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม|ม\\.ค\\.|ก\\.พ\\.|มี\\.ค\\.|เม\\.ย\\.|พ\\.ค\\.|มิ\\.ย\\.|ก\\.ค\\.|ส\\.ค\\.|ก\\.ย\\.|ต\\.ค\\.|พ\\.ย\\.|ธ\\.ค\\."
        val regex = Regex("""(?:วัน[^\s]+ที่\s*)?(\d{1,2})\s*($monthNames)\s*(25\d{2}|20\d{2})""")
        return regex.findAll(text).mapNotNull { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val month = thaiMonthNumber(match.groupValues[2]) ?: return@mapNotNull null
            val rawYear = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val year = if (rawYear >= 2400) rawYear - 543 else rawYear
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }.toList()
    }

    private fun validateFutureDates(values: List<LocalDate>, today: LocalDate): List<LocalDate> =
        values.distinct().filter { !it.isBefore(today) && ChronoUnit.DAYS.between(today, it) <= 400L }
            .sorted().take(12)

    private fun thaiMonthNumber(raw: String): Int? = when (raw.replace(".", "").trim()) {
        "มกราคม", "มค" -> 1
        "กุมภาพันธ์", "กพ" -> 2
        "มีนาคม", "มีค" -> 3
        "เมษายน", "เมย" -> 4
        "พฤษภาคม", "พค" -> 5
        "มิถุนายน", "มิย" -> 6
        "กรกฎาคม", "กค" -> 7
        "สิงหาคม", "สค" -> 8
        "กันยายน", "กย" -> 9
        "ตุลาคม", "ตค" -> 10
        "พฤศจิกายน", "พย" -> 11
        "ธันวาคม", "ธค" -> 12
        else -> null
    }

    private fun htmlToText(html: String): String =
        Html.fromHtml(html.replace(Regex("(?is)<script.*?</script>|<style.*?</style>"), " "),
            Html.FROM_HTML_MODE_LEGACY).toString().replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
}

private object LotteryChecksCalendarClient {
    private const val URL = "https://lotterychecks.com/draw-calendar"
    fun fetch(today: LocalDate): CalendarSourceSnapshot {
        val dates = DrawCalendarService.parseLotteryChecks(httpGet(URL), today)
        require(dates.isNotEmpty()) { "ไม่พบรายการงวดถัดไปในปฏิทิน" }
        return CalendarSourceSnapshot("LOTTERYCHECKS", dates, System.currentTimeMillis(), URL)
    }
}

private object HuayReviewCalendarClient {
    fun fetch(today: LocalDate): CalendarSourceSnapshot {
        val currentYear = today.year + 543
        val pages = listOf(
            "https://huayreview.net/calendar-$currentYear/" to currentYear,
            "https://huayreview.net/calendar-" + (currentYear + 1) + "/" to (currentYear + 1)
        )
        val dates = linkedSetOf<LocalDate>()
        val errors = mutableListOf<String>()
        pages.forEach { (address, pageYear) ->
            runCatching {
                dates.addAll(DrawCalendarService.parseHuayReview(httpGet(address), pageYear, today))
            }.onFailure { errors.add(it.message ?: "fetch failed") }
        }
        val sorted = dates.sorted().take(12)
        require(sorted.isNotEmpty()) {
            "ไม่พบวันงวดในปฏิทิน HuayReview" +
                if (errors.isNotEmpty()) ": " + errors.joinToString("; ") else ""
        }
        return CalendarSourceSnapshot("HUAYREVIEW", sorted, System.currentTimeMillis(), pages.joinToString(";") { it.first })
    }
}

private fun httpGet(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.setRequestProperty("User-Agent", "Lotto-Android/0.4 (public draw calendar)")
    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
    try {
        val code = connection.responseCode
        require(code in 200..299) { "HTTP $code from $address" }
        val body = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        require(body.isNotBlank()) { "empty response from $address" }
        return body
    } finally {
        connection.disconnect()
    }
}

private object CalendarAuditLog {
    private const val FILE_NAME = "draw_calendar_checks.log"

    @Synchronized
    fun append(context: Context, snapshot: CalendarSourceSnapshot) {
        val dates = snapshot.dates.joinToString(",") { it.toString() }
        val url = Base64.getEncoder().encodeToString(snapshot.sourceUrl.toByteArray(StandardCharsets.UTF_8))
        val reason = Base64.getEncoder().encodeToString(snapshot.error.orEmpty().toByteArray(StandardCharsets.UTF_8))
        val line = listOf(snapshot.fetchedAt.toString(), snapshot.sourceId,
            if (snapshot.successful) "SUCCESS" else "FAILED", dates, url, reason).joinToString("\t") + "\n"
        context.openFileOutput(FILE_NAME, Context.MODE_APPEND).bufferedWriter(StandardCharsets.UTF_8).use { it.write(line) }
    }

    @Synchronized
    fun latest(context: Context, sourceId: String): CalendarSourceSnapshot? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return file.useLines { lines ->
            val records = lines.mapNotNull { parseLine(it) }.filter { it.sourceId == sourceId }.toList()
            val latest = records.lastOrNull() ?: return@useLines null
            if (latest.successful) latest
            else latest.copy(dates = records.lastOrNull { it.successful }?.dates.orEmpty())
        }
    }

    private fun parseLine(line: String): CalendarSourceSnapshot? {
        val parts = line.split("\t")
        if (parts.size != 6) return null
        val fetched = parts[0].toLongOrNull() ?: return null
        val dates = parts[3].split(",").mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        val url = runCatching { String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8) }.getOrDefault("")
        val reason = runCatching { String(Base64.getDecoder().decode(parts[5]), StandardCharsets.UTF_8) }.getOrDefault("")
        return CalendarSourceSnapshot(parts[1], dates, fetched, url, reason.ifBlank { null })
    }
}

object DrawCalendarReminder {
    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        val initial = OneTimeWorkRequestBuilder<DrawCalendarWorker>().setConstraints(constraints).build()
        manager.enqueueUniqueWork("lotto_draw_calendar_initial_refresh", ExistingWorkPolicy.KEEP, initial)
        val request = PeriodicWorkRequestBuilder<DrawCalendarWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        manager.enqueueUniquePeriodicWork(
            CALENDAR_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun deliverDueReminders(context: Context, state: DrawCalendarState) {
        if (state.status != "CONFIRMED") return
        createChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val today = LocalDate.now(BANGKOK)
        val prefs = context.getSharedPreferences("draw_calendar_reminders", Context.MODE_PRIVATE)
        val current = state.confirmedDates.joinToString(",") { it.toString() }
        val previous = prefs.getString("last_confirmed_dates", "").orEmpty()
        if (previous.isNotBlank() && previous != current) {
            val oldDate = previous.substringBefore(",")
            val newDate = state.confirmedDates.firstOrNull()?.toString().orEmpty()
            if (oldDate != newDate && newDate.isNotBlank()) {
                post(context, 800001, "มีการเปลี่ยนวันออกรางวัล",
                    "วันงวดถัดไปเปลี่ยนจาก $oldDate เป็น $newDate ตามข้อมูลที่ทั้งสองเว็บไซต์ยืนยันตรงกัน")
            }
        }
        prefs.edit().putString("last_confirmed_dates", current).apply()
        state.confirmedDates.forEach { date ->
            val days = ChronoUnit.DAYS.between(today, date)
            if (days in 0..7) {
                val key = "reminded_$date"
                if (!prefs.getBoolean(key, false)) {
                    val detail = if (days == 0L) "วันนี้เป็นวันออกรางวัล" else "เหลืออีก $days วัน"
                    post(context, date.hashCode(), "แจ้งเตือนวันออกรางวัล", "งวดวันที่ $date • $detail")
                    prefs.edit().putBoolean(key, true).apply()
                }
            }
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CALENDAR_CHANNEL_ID, "แจ้งเตือนวันออกรางวัล", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
    }

    private fun post(context: Context, id: Int, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CALENDAR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

class DrawCalendarWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val state = DrawCalendarService.refresh(applicationContext)
        DrawCalendarReminder.deliverDueReminders(applicationContext, state)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
