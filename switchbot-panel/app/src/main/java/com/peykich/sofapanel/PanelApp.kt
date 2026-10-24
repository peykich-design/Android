package com.peykich.sofapanel

import android.app.Application
import com.peykich.sofapanel.data.PanelRepository
import com.peykich.sofapanel.domain.ChargeManager
import com.peykich.sofapanel.domain.MacroRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PanelApp : Application() {
    /** 画面より長く生きる処理（マクロ実行・充電管理）用 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: PanelRepository
        private set
    lateinit var runner: MacroRunner
        private set
    lateinit var charge: ChargeManager
        private set

    override fun onCreate() {
        super.onCreate()
        repo = PanelRepository(this, appScope)
        runner = MacroRunner(appScope, repo)
        charge = ChargeManager(appScope, repo)
    }
}
