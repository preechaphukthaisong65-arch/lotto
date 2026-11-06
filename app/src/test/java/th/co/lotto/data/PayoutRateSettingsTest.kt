package th.co.lotto.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PayoutRateSettingsTest {
    private lateinit var database: LottoDatabase
    private lateinit var dao: LottoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LottoDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.lottoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ratesArePersistedAsVersionedConfigurationAndAudited() {
        val now = 1_000L
        val settings = PayoutRateSettingsEntity(
            twoDigitRateHundredths = 7_000L,
            threeTopRateHundredths = 50_000L,
            threeBottomRateHundredths = 50_000L,
            threeToadRateHundredths = 10_000L,
            configVersion = 0L,
            updatedAtEpochMillis = now
        )
        val first = dao.savePayoutRateSettings(
            settings,
            AuditEventEntity("event-1", "PAYOUT_SETTINGS", "1", "UPDATED", "initial", now)
        )
        assertEquals(1L, first.configVersion)
        assertEquals(7_000L, dao.getPayoutRateSettings()?.twoDigitRateHundredths)

        val second = dao.savePayoutRateSettings(
            settings.copy(twoDigitRateHundredths = 7_500L, updatedAtEpochMillis = now + 1),
            AuditEventEntity("event-2", "PAYOUT_SETTINGS", "1", "UPDATED", "changed", now + 1)
        )
        assertEquals(2L, second.configVersion)
        assertEquals(7_500L, dao.getPayoutRateSettings()?.twoDigitRateHundredths)
        assertEquals(
            listOf("Payout configuration version 1", "Payout configuration version 2"),
            dao.getAuditEvents("1").map { it.details }
        )
    }

    @Test
    fun zeroOrNegativePayoutRateIsRejected() {
        val settings = PayoutRateSettingsEntity(
            twoDigitRateHundredths = 0L,
            threeTopRateHundredths = 50_000L,
            threeBottomRateHundredths = 50_000L,
            threeToadRateHundredths = 10_000L,
            configVersion = 0L,
            updatedAtEpochMillis = 1L
        )
        assertThrows(IllegalArgumentException::class.java) {
            dao.savePayoutRateSettings(
                settings,
                AuditEventEntity("event-invalid", "PAYOUT_SETTINGS", "1", "UPDATED", "invalid", 1L)
            )
        }
        assertEquals(null, dao.getPayoutRateSettings())
    }
}
