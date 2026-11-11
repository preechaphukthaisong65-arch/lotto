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
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DrawResultDaoSafetyTest {
    private lateinit var database: LottoDatabase
    private val drawDate = "2026-11-16"
    private val approvedAt = Instant.parse("2026-11-16T09:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LottoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES ('2026-11-01', NULL, 'BOT_VERIFIED', NULL, 1)")
        sql.execSQL("INSERT INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES ('$drawDate', NULL, 'BOT_VERIFIED', NULL, 1)")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refusesManuallyEnteredResultEvenWhenTheValuesAreValid() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                database.drawResultDao().saveBotVerifiedResultAndFindWinners(
                    DrawResultEntity(
                        drawDate = drawDate,
                        threeDigit = "123",
                        twoDigitTop = "23",
                        twoDigitBottom = "45",
                        recordedAt = approvedAt
                    )
                )
            }
        }
        assertEquals(null, kotlinx.coroutines.runBlocking { database.drawResultDao().getResult(drawDate) })
    }

    @Test
    fun acceptsOnlyBotVerifiedResultWithRunEvidence() {
        val winners = kotlinx.coroutines.runBlocking {
            database.drawResultDao().saveBotVerifiedResultAndFindWinners(
                DrawResultEntity(
                    drawDate = drawDate,
                    threeDigit = "012",
                    twoDigitTop = "23",
                    twoDigitBottom = "45",
                    recordedAt = approvedAt,
                    source = "BOT_VERIFIED",
                    verificationDecision = "APPROVED_3_OF_4",
                    verificationRunId = "run-test-001",
                    approvedAt = approvedAt
                )
            )
        }
        assertEquals(0, winners)
        val saved = kotlinx.coroutines.runBlocking { database.drawResultDao().getResult(drawDate) }
        assertEquals("012", saved?.threeDigit)
        assertEquals("BOT_VERIFIED", saved?.source)
        assertEquals("run-test-001", saved?.verificationRunId)
    }
}
