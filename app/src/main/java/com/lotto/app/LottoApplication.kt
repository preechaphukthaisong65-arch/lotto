package com.lotto.app

import android.app.Application
import com.lotto.app.core.di.AppContainer

class LottoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
