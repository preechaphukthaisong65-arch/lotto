package com.lotto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lotto.app.data.local.entity.AppSettingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AppSettingDaoAtomicTest {
    private lateinit var database: LottoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LottoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO app_settings (key, value, updatedAt) VALUES (?, ?, ?)",
            arrayOf("clock_high_water_epoch_ms", "1000", 1000L)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun multiSettingSaveRollsBackAllValuesAndAuditWhenOneValueFails() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                database.appSettingDao().saveManyWithAudit(
                    listOf(
                        AppSettingEntity("risk_limit_a", "100", 2_000L),
                        AppSettingEntity("risk_limit_b", "200", 500L)
                    )
                )
            }
        }

        assertNull(runBlocking { database.appSettingDao().getByKey("risk_limit_a") })
        assertNull(runBlocking { database.appSettingDao().getByKey("risk_limit_b") })

        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM setting_audit_log"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }
}
