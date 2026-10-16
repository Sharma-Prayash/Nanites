# CLAUDE.md — On-Device App Risk Auditor (Nanites)

Hackathon project, PS-01 Mobile Security. Build a working Android APK that scans installed apps,
extracts permissions and special access, groups them into nine categories, and gives each app an
explainable 0–100 risk score. A non-technical user must be able to answer:
1. Which of my apps can do the most harm?  2. Why is each one flagged?  3. What can I do now?

Full spec: `docs/ARCHITECTURE.md` (feature specs F01–F14, E01–E10). Problem statement: `docs/PROBLEM_STATEMENT.md`.
Scoring values: `app/src/main/assets/scoring_config.json` (single source of truth).

## Hard deadline
Working **release** APK installed on the judging device by **hour 5**. Keep the app installable at all
times after hour 2. Never break the build to add a stretch feature.

## Non-negotiable rules
- **On-device only.** No server, no accounts, no analytics, crash reporting, Firebase or ads.
  The only network code is the optional, consent-gated hash lookup in `hashlookup/`.
- **Evidence or nothing.** Every finding cites the observed permission or access state. No sample
  data, no fabricated reasons, no buttons that do nothing. If a feature is not working, hide it.
- **Honest states.** Declared, granted, enabled, UNKNOWN, unsupported, not checked and check failed
  are distinct. Never map unknown/null to false or safe.
- **Risk, not verdict.** UI says "may"; never "is malware"; never "your device is safe".
  Use "No High-risk findings detected by this scan".
- **Verify after remediation.** After Settings or uninstall, rescan that package before marking resolved.
- **Deterministic scoring.** Same snapshot + rule version = same score. Engine reads `scoring_config.json`.
- **No real malware.** Test only with our own benign dummy APKs (see Test kit).

## Tech stack (Kotlin)
- Kotlin 2.x, JVM target 17, Gradle Kotlin DSL + `libs.versions.toml`, KSP, kotlinx.serialization plugin.
- minSdk 26, targetSdk 35, compileSdk 35. `enableEdgeToEdge()` in `MainActivity`.
- Jetpack Compose (BOM) + Material 3, Navigation Compose, Lifecycle ViewModel + `collectAsStateWithLifecycle`.
- kotlinx.coroutines + Flow/StateFlow. Room (KSP). DataStore Preferences. WorkManager (`CoroutineWorker`).
- kotlinx.serialization-json. OkHttp (F11 only). Built-in `PdfDocument` (F14). JUnit 4 + coroutines-test.
- Do NOT use: Java sources, XML layouts/Fragments, LiveData, RxJava, Gson, Hilt/Dagger (manual `AppContainer`).
- Pin each dependency to the latest stable version at project creation.

## Package layout (`com.team.riskauditor`)
```
App.kt, AppContainer.kt, MainActivity.kt
ui/        AuditorApp (NavHost + bottom nav), theme/, home/, apps/, detail/, history/, settings/, rubric/, privacy/
scan/      ScanOrchestrator, PackageScanner, InstallerClassifier, PermissionAnalyzer,
           SpecialAccessAnalyzer, RoleResolver, ManifestInspector, ApkHasher
model/     immutable data classes + enum classes
security/  PURE KOTLIN, no android.* imports: catalog/, rules/, RuleEngine, RiskScorer, Explainer,
           ScoringConfig, PermissionDiffer
data/      db/ (Room), ScanRepository, BaselineStore, AppPrefs (DataStore)
monitor/   PackageChangeReceiver, ChangeCatchUpWorker, RiskNotifier
remediation/ RemediationLauncher, SettingsIntents
report/    PdfReportBuilder, ReportExporter
hashlookup/ HashLookupClient, HashLookupResult   (the ONLY network code)
ml/        MlEstimator, MlModel
```

## Conventions
- MVVM, one-way data flow: screen collects `StateFlow<UiState>`; events are function calls.
- `PackageManager` work on `Dispatchers.IO`; rule evaluation on `Dispatchers.Default`;
  per-app parallelism via `limitedParallelism(4)`. Scan exposes `Flow<ScanProgress>`; cancellable.
- No `!!` outside tests. Wrap every platform call that can throw (`NameNotFoundException`,
  `SecurityException`, `ActivityNotFoundException`) and map failures to explicit states.
- Exclude the auditor's own package from scans.
- Keep R8 rules for `model/` and `security/` serialized classes; test `assembleRelease`, not only debug.

## Rule contract
```kotlin
interface Rule {
    val id: String
    val title: String
    fun evaluate(app: AppSnapshot, trust: TrustContext, config: ScoringConfig): RuleFinding?  // null = not fired
}
data class RuleFinding(
    val ruleId: String, val title: String, val evidence: List<String>, val points: Int,
    val impact: String, val action: RemediationAction, val heuristic: Boolean = false,
)
```

## Scoring (rules-v1) — values live in scoring_config.json
- `score = min(100, round( Σ min(points_i, 24) × installerMultiplier × systemMultiplier ))`
- Per-finding cap 24 ⇒ High (≥50) needs ≥3 findings, Critical (≥75) ≥4. This guarantees the
  "3 reasons for every High/Critical app" requirement. Multipliers are ≤ 1.0 only.
- Bands: Low 0–24, Medium 25–49, High 50–74, Critical 75–100.
- One finding per category row at its highest applicable value (five SMS perms still = one SMS finding).
- Declared-only access scores about half (test-kit apps are installed fresh, ungranted).
- Combinations (R001–R008) are separate bonus findings; never re-score the evidence they build on.
- R009 (hash match) is the only exception: score = max(score, 90).
- Unknown states add 0 points and are listed in the assessment's completeness note.
- Must pass these worked checks (JUnit):
  - Sideloaded fake banking app (accessibility enabled, overlay, SMS, boot) → 96 Critical
  - Sideloaded "Super Torch" (SMS + contacts declared, boot) → 59 High
  - Play messaging app (contacts, camera/mic, location, media, boot) → 38 Medium
  - Play calculator, internet only → 0 Low

## Key platform APIs (details in docs/ARCHITECTURE.md)
- Inventory: `getInstalledPackages(GET_PERMISSIONS)` (needs `QUERY_ALL_PACKAGES`); installer via
  `getInstallSourceInfo` (API 30+) else `getInstallerPackageName`.
- Grants: `requestedPermissionsFlags & REQUESTED_PERMISSION_GRANTED` — trust for dangerous perms only.
  Protection: `getPermissionInfo().protection` / `protectionFlags` (APPOP ⇒ special).
- Accessibility: `Settings.Secure ENABLED_ACCESSIBILITY_SERVICES`. Device admin: `DevicePolicyManager.getActiveAdmins()`.
  Notification listener: `NotificationManagerCompat.getEnabledListenerPackages()`.
- Overlay / install-packages / usage-stats grant for OTHER apps: `AppOpsManager.unsafeCheckOpNoThrow`
  — **SPIKE in hour 1**; on SecurityException or failure use UNKNOWN and score the declaration.
- Roles (E02): default SMS, dialer, launcher, keyboard ⇒ suppress matching category findings.
- Uninstall: `Intent.ACTION_DELETE` + `REQUEST_DELETE_PACKAGES`. Active device admin must be
  deactivated first (send user to `ACTION_SECURITY_SETTINGS`).
- Monitoring (F09): context-registered receiver for PACKAGE_ADDED/REPLACED in `App.onCreate`
  + `getChangedPackages(seq)` catch-up on start and in WorkManager. Best-effort; say so in UI.
- Static checks (F12): exported components via GET_ACTIVITIES|SERVICES|RECEIVERS|PROVIDERS,
  `FLAG_DEBUGGABLE`, `FLAG_USES_CLEARTEXT_TRAFFIC`. No APK parser needed.

## Manifest
`QUERY_ALL_PACKAGES` (tools:ignore), `REQUEST_DELETE_PACKAGES`, `POST_NOTIFICATIONS`, `INTERNET` (F11 only).
`allowBackup="false"`, `usesCleartextTraffic="false"`, only `MainActivity` exported, FileProvider not exported.
The auditor requests no dangerous permissions of its own.

## Build order (stop and make it green at each step)
1. 0:00–0:30 Skeleton, bottom nav, release signing, install a release APK once. Run the AppOps/accessibility/listener/admin spikes.
2. 0:30–1:30 Scanners F01–F04, models, PermissionCatalog.
3. 1:30–2:15 Engine: ScoringConfig, single rules, R001–R007, R005 mismatch, E02 modifiers, scorer, explainer + JUnit worked checks.
4. 2:15–3:15 Screens: Home (E01), Apps (E06), App detail (F06) with targeted links (E09) and rescan on return (E03). = all must-haves.
5. 3:15–3:45 Room scans + baseline, permission diff (F10), History (E04), Settings (E05).
6. 3:45–4:15 Monitoring + notifications (F09), static checks (F12).
7. 4:15–4:45 Release APK on judging device, test-kit run, generate `docs/SCORING_RUBRIC.md` from the config, demo rehearsal.
8. 4:45–5:00 Buffer. Only then F14 PDF, F11 hash lookup, F13 ML (only if a pre-trained model exists).

## Test kit (benign dummy APKs in `testkit/`, declare only — no behaviour)
| App | Declares | Expect |
|---|---|---|
| Super Torch | READ_SMS, READ_CONTACTS, INTERNET, BOOT; sideloaded | R001, R005 → High |
| Bank Helper | accessibility service, SYSTEM_ALERT_WINDOW, RECEIVE_SMS, INTERNET, BOOT | R002, R001 → High; Critical once accessibility enabled |
| Notes Sync | notification listener, INTERNET | R003 |
| Lock Guard | device-admin receiver | R004 when active; uninstall trap |
| Wallpaper v1→v2 | v2 adds READ_SMS | R008 + notification |
| Plain Notes | INTERNET only | Low |

## Definition of done for any feature
Works on a real device in a release build, shows real evidence, handles unknown/failed states
visibly, and has a unit test if it touches `security/`. If not, it stays hidden.
