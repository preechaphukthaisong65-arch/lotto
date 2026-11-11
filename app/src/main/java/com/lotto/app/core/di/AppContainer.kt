package com.lotto.app.core.di

import android.content.Context
import androidx.room.Room
import com.lotto.app.data.local.LottoDatabase
import com.lotto.app.data.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.lotto.app.data.backup.EncryptedBackupManager
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.data.repository.CustomerRepository
import com.lotto.app.data.repository.RoomCustomerRepository
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.data.repository.RoomCreditAccountRepository
import com.lotto.app.data.repository.RoomDrawResultRepository
import com.lotto.app.data.repository.RoomSaleRepository
import com.lotto.app.data.repository.RoomSettingsRepository
import com.lotto.app.data.repository.SaleRepository
import com.lotto.app.data.repository.SettingsRepository

class AppContainer(context: Context) {
    init {
        EncryptedBackupManager.recoverInterruptedRestore(context.applicationContext)
    }

    private val databasePassphrase: ByteArray = DatabaseKeyManager.get(context).ensureEncryptedDatabase()

    private val database: LottoDatabase = Room.databaseBuilder(
        context.applicationContext,
        LottoDatabase::class.java,
        LottoDatabase.DATABASE_NAME
    )
        .openHelperFactory(SupportOpenHelperFactory(databasePassphrase.copyOf()))
        .addMigrations(*LottoDatabase.ALL_MIGRATIONS)
        .build()

    val settingsRepository: SettingsRepository by lazy { RoomSettingsRepository(database.appSettingDao()) }
    val customerRepository: CustomerRepository by lazy { RoomCustomerRepository(database.customerDao()) }
    val saleRepository: SaleRepository by lazy { RoomSaleRepository(database.saleDao()) }
    val creditAccountRepository: CreditAccountRepository by lazy { RoomCreditAccountRepository(database.saleDao()) }
    val drawCalendarRepository: DrawCalendarRepository by lazy { DrawCalendarRepository(database.drawCalendarDao()) }
    val drawResultRepository: DrawResultRepository by lazy { RoomDrawResultRepository(database.drawResultDao()) }
    val encryptedBackupManager: EncryptedBackupManager by lazy {
        EncryptedBackupManager(context.applicationContext, database, databasePassphrase.copyOf())
    }
}
