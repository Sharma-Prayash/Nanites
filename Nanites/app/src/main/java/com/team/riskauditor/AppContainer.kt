package com.team.riskauditor

import android.content.Context
import com.team.riskauditor.data.AppPrefs
import com.team.riskauditor.data.ScanRepository
import com.team.riskauditor.scan.ScanOrchestrator
import com.team.riskauditor.security.RuleEngine
import com.team.riskauditor.security.ScoringConfig
import com.team.riskauditor.ui.components.AppIconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (no DI framework). One instance per process, created in [App]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val scoringConfig: ScoringConfig =
        appContext.assets.open("scoring_config.json").bufferedReader().use { ScoringConfig.parse(it.readText()) }

    val ruleEngine = RuleEngine(scoringConfig)
    val prefs = AppPrefs(appContext)
    val orchestrator = ScanOrchestrator(appContext, ruleEngine)
    val scanRepository = ScanRepository(orchestrator, prefs, applicationScope)
    val iconLoader = AppIconLoader(appContext.packageManager)
}
