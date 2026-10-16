package com.team.riskauditor.security

import com.team.riskauditor.model.ActiveState
import com.team.riskauditor.model.AppRole
import com.team.riskauditor.model.AppSnapshot
import com.team.riskauditor.model.GrantState
import com.team.riskauditor.model.InstallerInfo
import com.team.riskauditor.model.InstallerSource
import com.team.riskauditor.model.ManifestFacts
import com.team.riskauditor.model.PermissionObservation
import com.team.riskauditor.model.ProtectionLevel
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.model.SpecialAccessObservation
import com.team.riskauditor.model.SpecialAccessType
import com.team.riskauditor.security.catalog.PermissionCatalog
import com.team.riskauditor.security.rules.TrustContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkedChecksTest {

    private val config = ScoringConfig.parse(File("src/main/assets/scoring_config.json").readText())
    private val engine = RuleEngine(config)

    private fun perm(short: String, grant: GrantState, protection: ProtectionLevel = ProtectionLevel.DANGEROUS) =
        "android.permission.$short".let { PermissionObservation(it, protection, grant, PermissionCatalog.categoryOf(it)) }

    private fun normal(short: String) = perm(short, GrantState.GRANTED, ProtectionLevel.NORMAL)
    private fun declared(short: String) = perm(short, GrantState.NOT_GRANTED)
    private fun granted(short: String) = perm(short, GrantState.GRANTED)

    private fun access(type: SpecialAccessType, declared: Boolean, state: ActiveState) =
        SpecialAccessObservation(type, declared, state)

    private fun app(
        label: String,
        source: InstallerSource,
        permissions: List<PermissionObservation>,
        special: List<SpecialAccessObservation> = emptyList(),
        system: Boolean = false,
    ) = AppSnapshot(
        packageName = "com.test." + label.lowercase().replace(" ", ""),
        label = label, versionName = "1.0", versionCode = 1, firstInstallTime = 0, lastUpdateTime = 0,
        installer = InstallerInfo(source, null), targetSdk = 35, isSystemApp = system,
        permissions = permissions, specialAccess = special,
        manifest = ManifestFacts(debuggable = false, cleartextTraffic = false, exportedUnprotected = emptyList()),
    )

    private val fakeBank = app(
        "Bank Helper", InstallerSource.SIDELOADED,
        listOf(
            declared("RECEIVE_SMS"), normal("INTERNET"), normal("RECEIVE_BOOT_COMPLETED"),
            perm("SYSTEM_ALERT_WINDOW", GrantState.UNKNOWN, ProtectionLevel.SPECIAL),
        ),
        listOf(
            access(SpecialAccessType.ACCESSIBILITY, true, ActiveState.ENABLED),
            access(SpecialAccessType.OVERLAY, true, ActiveState.NOT_ENABLED),
        ),
    )

    private val superTorch = app(
        "Super Torch", InstallerSource.SIDELOADED,
        listOf(declared("READ_SMS"), declared("READ_CONTACTS"), normal("INTERNET"), normal("RECEIVE_BOOT_COMPLETED")),
    )

    private val messenger = app(
        "Chat Messenger", InstallerSource.PLAY_STORE,
        listOf(
            granted("READ_CONTACTS"), granted("CAMERA"), granted("RECORD_AUDIO"), granted("ACCESS_FINE_LOCATION"),
            granted("READ_MEDIA_IMAGES"), normal("INTERNET"), normal("RECEIVE_BOOT_COMPLETED"),
        ),
    )

    private val calculator = app("Calculator", InstallerSource.PLAY_STORE, listOf(normal("INTERNET")))

    @Test fun sideloadedFakeBankingAppIsCritical96() {
        val a = engine.assess(fakeBank)
        assertEquals(96, a.score)
        assertEquals(RiskBand.CRITICAL, a.band)
        assertTrue(a.findings.map { it.ruleId }.containsAll(listOf("ACCESSIBILITY", "OVERLAY", "SMS", "R001", "R002")))
    }

    @Test fun superTorchIsHigh59() {
        val a = engine.assess(superTorch)
        assertEquals(59, a.score)
        assertEquals(RiskBand.HIGH, a.band)
        assertTrue(a.findings.any { it.ruleId == "R005" && it.heuristic })
    }

    @Test fun playMessagingAppIsMedium38() {
        val a = engine.assess(messenger)
        assertEquals(38, a.score)
        assertEquals(RiskBand.MEDIUM, a.band)
    }

    @Test fun playCalculatorInternetOnlyIsLow0() {
        val a = engine.assess(calculator)
        assertEquals(0, a.score)
        assertEquals(RiskBand.LOW, a.band)
        assertTrue(a.findings.isEmpty())
    }

    @Test fun bankHelperBeforeAccessibilityIsEnabledIsHigh() {
        val fresh = fakeBank.copy(specialAccess = listOf(
            access(SpecialAccessType.ACCESSIBILITY, true, ActiveState.NOT_ENABLED),
            access(SpecialAccessType.OVERLAY, true, ActiveState.NOT_ENABLED),
        ))
        assertEquals(RiskBand.HIGH, engine.assess(fresh).band)
    }

    @Test fun everyHighOrCriticalHasAtLeastThreeReasons() {
        listOf(fakeBank, superTorch).map { engine.assess(it) }.forEach {
            assertTrue(it.band >= RiskBand.HIGH)
            assertTrue("${it.packageName} has ${it.findings.size} reasons", it.findings.size >= 3)
            assertTrue(Explainer.meetsReasonRequirement(it))
        }
    }

    @Test fun scoringIsDeterministic() {
        val first = engine.assess(fakeBank)
        repeat(5) { assertEquals(first, engine.assess(fakeBank)) }
    }

    @Test fun findingsNeverExceedCap() {
        engine.assess(fakeBank).findings.forEach { assertTrue(it.points <= config.findingCap) }
    }

    @Test fun defaultSmsRoleSuppressesSmsAndR001() {
        val a = engine.assess(superTorch, TrustContext(roles = setOf(AppRole.DEFAULT_SMS)))
        val ids = a.findings.map { it.ruleId }
        assertFalse("SMS" in ids)
        assertFalse("R001" in ids)
        assertEquals(setOf("SMS", "R001"), a.suppressed.map { it.finding.ruleId }.toSet())
    }

    @Test fun unknownAccessStateScoresDeclarationAndIsListed() {
        val app = calculator.copy(specialAccess = listOf(access(SpecialAccessType.OVERLAY, true, ActiveState.CHECK_FAILED)))
        val a = engine.assess(app)
        assertEquals(config.single("OVERLAY").declaredPoints, a.findings.single { it.ruleId == "OVERLAY" }.points)
        assertTrue(a.completeness.any { it.contains("Display over other apps") })
    }

    @Test fun systemAppMultiplierHalvesScore() {
        val sys = superTorch.copy(isSystemApp = true, installer = InstallerInfo(InstallerSource.PRELOADED, null))
        val a = engine.assess(sys)
        // No outside-store finding, store value of R001; then × systemApp
        val raw = config.single("SMS").declaredPoints + config.single("CONTACTS_CALL_LOGS").declaredPoints +
            config.single("BOOT_PERSISTENCE").grantedPoints + (config.combo("R001").points ?: 0) + (config.combo("R005").points ?: 0)
        assertEquals(Math.round(raw * config.modifiers.systemApp).toInt(), a.score)
    }

    /** Permission set observed for WhatsApp (Play Store) on a real Android 17 phone during calibration. */
    private val heavyPlayMessenger = app(
        "WhatsApp", InstallerSource.PLAY_STORE,
        listOf(
            granted("RECEIVE_SMS"), granted("SEND_SMS"), granted("READ_CONTACTS"), granted("WRITE_CONTACTS"),
            granted("CAMERA"), granted("RECORD_AUDIO"), granted("READ_MEDIA_IMAGES"), granted("READ_MEDIA_VIDEO"),
            granted("ANSWER_PHONE_CALLS"), granted("READ_PHONE_STATE"), declared("CALL_PHONE"),
            declared("ACCESS_FINE_LOCATION"), declared("ACCESS_COARSE_LOCATION"),
            normal("INTERNET"), normal("RECEIVE_BOOT_COMPLETED"), normal("FOREGROUND_SERVICE"),
            perm("REQUEST_INSTALL_PACKAGES", GrantState.NOT_GRANTED, ProtectionLevel.SPECIAL),
        ),
        listOf(access(SpecialAccessType.INSTALL_PACKAGES, true, ActiveState.NOT_ENABLED)),
    ).let { it.copy(manifest = it.manifest.copy(exportedUnprotected = List(25) { i -> "com.whatsapp.Exported$i" })) }

    @Test fun heavyPlayStoreMessengerIsMediumNotHigh() {
        val a = engine.assess(heavyPlayMessenger)
        assertEquals(RiskBand.MEDIUM, a.band)
        assertFalse("R001 should not fire for a store install", a.findings.any { it.ruleId == "R001" })
    }

    @Test fun sameAccessFromOutsideAStoreIsStillCritical() {
        val sideloaded = heavyPlayMessenger.copy(installer = InstallerInfo(InstallerSource.SIDELOADED, "com.android.packageinstaller"))
        val a = engine.assess(sideloaded)
        assertEquals(RiskBand.CRITICAL, a.band)
        assertTrue(a.findings.any { it.ruleId == "R001" })
    }

    @Test fun newSensitivePermissionAfterUpdateFiresR008() {
        val v2 = calculator.copy(permissions = calculator.permissions + declared("READ_SMS"))
        val a = engine.assess(v2, TrustContext(baselinePermissions = setOf("android.permission.INTERNET"), baselineVersionName = "1.0"))
        assertTrue(a.findings.any { it.ruleId == "R008" })
        assertFalse(engine.assess(v2).findings.any { it.ruleId == "R008" })
    }

    @Test fun bandsCoverWholeRange() {
        assertEquals(RiskBand.LOW, config.bandFor(24))
        assertEquals(RiskBand.MEDIUM, config.bandFor(25))
        assertEquals(RiskBand.HIGH, config.bandFor(50))
        assertEquals(RiskBand.CRITICAL, config.bandFor(75))
        assertEquals(RiskBand.CRITICAL, config.bandFor(100))
    }
}
