# SnapTTL — Context

Working context for this task, kept across turns. Current session branch:
`arena/85a2efc2-snapttl` (supersedes the earlier `arena/a88f5870-snapttl` session that
produced PR #1).

## Objective
Clean-room reconstruct the Android screenshot time-to-live app described by the Drive
analysis (`REA / Mark`, target `com.markOne.ss_app` v7.1) as **SnapTTL**
(`dev.snapttl.app`). Reconstruct it **in GitHub**, re-review the code, test it on
Android 14+ and 11+, and once sound, run an APK-building workflow.

## Session log (2026-10-30, arena/85a2efc2-snapttl)

1. Re-reviewed everything from PR #1 (full findings: `context/review-2026-10-30.md`).
   Main problems: CI red on main (ML Kit imports, `Intent.component` shadowing) and the
   TTL pipeline was non-functional — the purge worker had **no initial delay**, cancel
   didn't cancel WorkManager work, rescheduling stacked rows, "Delete now" didn't delete
   now, plus 15 more findings.
2. Fixed all findings (commit `a216034`):
   - compile: ML Kit bundled-model imports (`vision.text.latin`,
     `vision.label.defaults` — verified against googlesamples/mlkit), component fix.
   - `ExpiryRepository`: `schedule`/`scheduleIn` with `setInitialDelay`,
     unique work `snapttl_purge_<id>` + `REPLACE`, cancel removes row + work,
     `rescheduleAll()` reconcile at boot/startup, worker lookup by `workManagerId`.
   - `ExpiryPurgeWorker`: already-deleted ⇒ success; failed ⇒ `retry()` (≤3 attempts,
     row kept).
   - Overlay: real delete-now, honest share&delete label, seconds chips.
   - `TimeChips` domain object (30s/5m/30m/3d advanced; 15/45m free) + entitlement
     validation in seconds; prefs key `time_chips_seconds`.
   - `CaptureMonitorService`: ACTION_STOP satisfies startForeground contract;
     duplicate-observer debounce.
   - Search: flatMapLatest, indexing via `OcrIndexWorker` (respects OCR toggle),
     failed analyses retried, stale rows dropped.
   - Settings: SAF screenshots-folder picker, overlay-permission row, duration chips,
     OCR toggle; per-app locale (33+ `LocaleManager`, below: config wrap + recreate).
   - Permissions: media group full-or-partial; SDK-aware request list.
   - Manifest: dropped INTERNET/ACCESS_NETWORK_STATE (fully offline).
   - UI wired to string resources; thumbnails in Home/All-Screenshots/Search.
3. Tests: new `TimeChipsTest`, `ExpiryRepositoryTest` (WorkManager test harness,
   asserts `initialDelayMillis`), seconds-entitlement tests, Robolectric suites at
   SDK 30/34/35, Python mirror extended (11 tests, green), instrumented suite
   (`AppLaunchTest`, `ScreenshotRepositoryDeviceTest`, `ExpiryPipelineDeviceTest`) for
   the emulator matrix.
4. Workflows: `verify.yml` (strict permission allowlist + Room schema artifact),
   NEW `device-tests.yml` (API 30/34/35 emulators, connected tests), `build-apk.yml`
   (unsigned release + debug + aapt badging check), `release.yml` (signed, attaches
   APK to tag releases). Actions bumped to current majors (checkout@v7, setup-java@v6,
   upload-artifact@v7, setup-gradle@v6).
5. Pushed `a216034` → CI runs in progress (Verify + Device tests).

## Requirements traceability
| # | Requirement | Where |
|---|---|---|
| 1 | Automatic screenshot detection | `domain/ScreenshotFilter` + `service/CaptureMonitorService` |
| 2 | Floating overlay with countdown + delete | `ui/overlay/CaptureOverlay*` + `CaptureOverlayActivity` |
| 3 | Silent deletion (SAF tree first, MediaStore fallback) | `repository/ScreenshotRepository.deleteScreenshot` + `ExpiryPurgeWorker` |
| 4 | Scheduled deletion queue with silent reschedule | `data/expiry/*`, `ExpiryRepository` (delayed, REPLACE, rescheduleAll), `ExpiryPurgeWorker` |
| 5 | On-device OCR + image labeling index + search | `vision/*`, `data/search/*`, `SearchRepository`, `OcrIndexWorker`, Search screen |
| 6 | Manage-all-screenshots | Home recent grid / All Screenshots screens, `ScreenshotRepository.listAll` |
| 7 | Freemium limits + ad refill + premium | `domain/EntitlementState`, `domain/TimeChips`, `PrefsManager`, Home/Settings gating |
| 8 | Crash-resilient silent delete | `ExpiryPurgeWorker` retry, `rescheduleAll`, `BootCompletedReceiver`, service auto-start in `MainActivity` |
| 9 | OEM auto-start routing | `domain/OemAutoStart` + Settings screen |
| 10 | In-app locale switching | `language/AppLanguage`, `language/LocaleHelper` (33+ per-app locale), Settings |
| 11 | Hidden easter egg mini-game | Home (tap title 5x) → `MiniGame` + `EasterEggScreen` |
| 12 | Offline, no broad storage, Room KSP schemas | manifest (no network perms), CI permission allowlist, `app/schemas/` |

## Verification status
- Local: no JDK/Gradle/Android SDK in the sandbox. Python mirror: **11 tests pass**.
- **CI green** on branch `arena/85a2efc2-snapttl` @ `6e3fca7`:
  - **Verify** run `41065467886` ✓ — clean-room Python mirror, Robolectric unit
    tests (SDK 30/34/35, hermetic apps), lint (0 errors), Room schema export,
    debug APK build, and the packaged-permission audit (exact 11-permission
    allowlist, `INTERNET`/`ACCESS_NETWORK_STATE` stripped via
    `tools:node="remove"` from the ML Kit manifests).
  - **Device tests** run `41065468083` ✓ — connected tests on Android 11 / 14 / 15
    emulators (AppLaunch, ScreenshotRepository, ExpiryPipeline incl. the
    cancelled-timer regression).
- Room schemas exported and **committed** to `app/schemas/` by the
  `export-schemas.yml` workflow (runs on data-layer pushes to the working
  branch + main, commits the KSP-generated JSONs back).

## Late-stage fixes found by CI
- `backup_rules.xml`: a redundant `<exclude>` next to an `<include>` is a
  FullBackupContent **error** — the include alone already excludes everything else.
- ML Kit's common library injects `INTERNET` + `ACCESS_NETWORK_STATE` into the
  merged manifest even for bundled models; removed with
  `tools:node="remove"` to honor the offline contract.
- `ExpiryRepository.schedule()` returned the **pre-insert** entity (id = 0), so
  `cancel(returnedItem.id)` was a silent no-op and a cancelled purge still fired
  (caught by the API-34 device test; regression test added). It now returns
  `item.copy(id = dao.insertOrReplace(item))`.

## Pending / next steps
- [x] CI green on Verify + Device tests (API 30/34/35).
- [x] Room schema JSONs committed to `app/schemas/`.
- [x] Build APK workflow green — artifacts `snapttl-release-apk` (unsigned) +
      `snapttl-debug-apk` produced on main (run `41069286895`).
- [x] PR #2 merged to main; **main is fully green**: Verify `41069286967` ✓,
      Device tests `41069286937` ✓, Build APK `41069286895` ✓,
      Export Room schemas `41069286974` ✓.
- [ ] Tag `v0.1.0` only if a signed release is wanted (needs
      `SNAPTTL_KEYSTORE_*` secrets).
