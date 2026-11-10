package com.lotto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lotto.app.data.local.entity.DrawResultEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawResultDaoSafetyTest {
    private lateinit var database: LottoDatabase
    private val drawDate = "2026-11-16"
    private val beforeFourPm = Instant.parse("2026-11-16T08:59:59Z").toEpochMilli()
    private val exactlyFourPm = Instant.parse("2026-11-16T09:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LottoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES ('2026-11-01', NULL, 'TEST', NULL, 1)")
        sql.execSQL("INSERT INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES ('$drawDate', NULL, 'TEST', NULL, 1)")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refusesResultBeforeFourPmThailandTimeAndAcceptsExactBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                database.drawResultDao().saveResultAndFindWinners(
                    DrawResultEntity(
                        drawDate = drawDate,
                        threeDigit = "123",
                        twoDigitTop = "23",
                        twoDigitBottom = "45",
                        recordedAt = beforeFourPm
                    )
                )
            }
        }
        assertEquals(null, kotlinx.coroutines.runBlocking { database.drawResultDao().getResult(drawDate) })

        val winners = kotlinx.coroutines.runBlocking {
            database.drawResultDao().saveResultAndFindWinners(
                DrawResultEntity(
                    drawDate = drawDate,
                    threeDigit = "123",
                    twoDigitTop = "23",
                    twoDigitBottom = "45",
                    recordedAt = exactlyFourPm
                )
            )
        }
        assertEquals(0, winners)
        assertEquals("123", kotlinx.coroutines.runBlocking { database.drawResultDao().getResult(drawDate) }?.threeDigit)
    }

    @Test
    fun refusesCorrectionBeforeFourPmAndKeepsOriginalResult() {
        kotlinx.coroutines.runBlocking {
            database.drawResultDao().saveResultAndFindWinners(
                DrawResultEntity(
                    drawDate = drawDate,
                    threeDigit = "123",
                    twoDigitTop = "23",
                    twoDigitBottom = "45",
                    recordedAt = exactlyFourPm
                )
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                database.drawResultDao().correctResultAndRebuildWinners(
                    drawDate = drawDate,
                    threeDigit = "999",
                    twoDigitTop = "99",
                    twoDigitBottom = "99",
                    reason = "ทดสอบ",
                    correctedAt = beforeFourPm
                )
            }
        }
        assertEquals("123", kotlinx.coroutines.runBlocking { database.drawResultDao().getResult(drawDate) }?.threeDigit)
    }
}
