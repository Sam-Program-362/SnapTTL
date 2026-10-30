# SnapTTL — Context

Working context for this task, kept across turns. Current session branch:
`arena/3e8349f3-snapttl` (supersedes `arena/85a2efc2-snapttl`, merged via PRs
#2–#4, and the original `arena/a88f5870-snapttl` that produced PR #1).

## Objective
Clean-room reconstruct the Android screenshot time-to-live app described by the Drive
analysis (`REA / Mark`, target `com.markOne.ss_app` v7.1) as **SnapTTL**
(`dev.snapttl.app`). Reconstruct it **in GitHub**, re-review the code, test it on
Android 14+ and 11+, and once sound, run an APK-building workflow.

## User feedback round 2 (2026-10-30, debug-APK test on an Android 16 device)
Three directives, all implemented this session:

1. **Freemium removed** — SnapTTL is a free app. `EntitlementState`, premium /
   ads-removed / refill prefs, gating UI in Home/Settings/All-Screenshots and the
   subscriptions card are gone. Silent auto-delete is available to everyone.
2. **Durations are user-set quick options, not pre-installed** — the user
   maintains their **own one-tap options** (up to six, each 1 s … 7 days;
   starters 15 m / 1 h / 6 h / 1 d, fully editable via tap-to-edit, × to
   remove, "Add duration" to add — Home and Settings both host the manager).
   The floating overlay shows those options as one-tap buttons, plus a
   **"Custom…"** button opening a free-form h/m/s timer, plus the subtle
   **"Pick a date & time instead"** affordance (days/hours/minutes with an
   absolute-date preview, up to a year ahead) — the only way past a week.
   Stored in `custom_durations_seconds` (comma-separated seconds). Silent
   auto-delete and bulk delete use the **first** option. (Feedback round 3
   clarified the design: options-first with custom/date-time behind buttons,
   not a bare stepper on the popup.)
3. **Popup didn't appear on Android 16** — root cause: the overlay was an *Activity*
   launched from the background; blocked background-activity launches fail
   **silently** (Android 10+ BAL rules, narrowed further on 15/16) even with
   SYSTEM_ALERT_WINDOW granted. The toast path worked because toasts need no BAL.
   Fix: the card is now a **WindowManager overlay window** (`TYPE_APPLICATION_OVERLAY`
   view owned by `CaptureMonitorService`, Compose hosted via a custom
   LifecycleOwner/SavedStateRegistryOwner) — adding a window needs no activity
   launch at all. When SAW is missing or the window is rejected, a **heads-up
   notification with actions** (Delete now / Delete in default / Keep) fires via
   `CaptureNotificationFallback` + `CaptureActionReceiver`.

### Deferred monetization option (recorded for the future)
The user may revisit a **rewarded-ad refill**: watching an ad grants **+20 days** of
"extra durations unlocked", stacking up to a **40-day cap** (expiry persisted as an
epoch-ms). The previous clean-room implementation (removed in this session) had:
`EntitlementState` with `REFILL_GRANT_DAYS=20`, `REFILL_MAX_DAYS=40`,
`hasUnlockedFeatures`, `applyRewardedAdRefill`, pref keys
`have_premium_purchased` / `have_ads_removed` / `refill_expiry_date`; freemium rule
"free tier may only arm 15–45 minute durations". History lives in git
(commit `d557a3f` and earlier). **No ads, no premium, no network calls exist today.**

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
| 2 | Floating overlay with countdown + delete | `ui/overlay/CaptureOverlay` + `CaptureOverlayWindows` (WindowManager overlay window) + notification fallback |
| 3 | Silent deletion (SAF tree first, MediaStore fallback) | `repository/ScreenshotRepository.deleteScreenshot` + `ExpiryPurgeWorker` |
| 4 | Scheduled deletion queue with silent reschedule | `data/expiry/*`, `ExpiryRepository` (delayed, REPLACE, rescheduleAll), `ExpiryPurgeWorker` |
| 5 | On-device OCR + image labeling index + search | `vision/*`, `data/search/*`, `SearchRepository`, `OcrIndexWorker`, Search screen |
| 6 | Manage-all-screenshots | Home recent grid / All Screenshots screens, `ScreenshotRepository.listAll` |
| 7 | Free app: user-set durations (stepper ≤ 7 d; overlay date/time path ≤ 1 y) | `domain/DeletionDuration`, `ui/common/DurationPicker`, overlay stepper + date/time mode, `PrefsManager.autoDeleteDurationSeconds` |
| 8 | Crash-resilient silent delete | `ExpiryPurgeWorker` retry, `rescheduleAll`, `BootCompletedReceiver`, service auto-start in `MainActivity` |
| 9 | OEM auto-start routing | `domain/OemAutoStart` + Settings screen |
| 10 | In-app locale switching | `language/AppLanguage`, `language/LocaleHelper` (33+ per-app locale), Settings |
| 11 | Hidden easter egg mini-game | Home (tap title 5x) → `MiniGame` + `EasterEggScreen` |
| 12 | Offline, no broad storage, Room KSP schemas | manifest (no network perms), CI permission allowlist, `app/schemas/` |

## Verification status
- Local: no JDK/Gradle/Android SDK in the sandbox. Python mirror: **15 tests pass**
  (duration model + chip-list rules).
- **Round 3 (user-defined quick options on the popup) green on `main`** @ `1b8be8e`
  (PR #8 + #9): Verify + Device tests (Android 11/14/15) all ✓.
- **Signed release v0.1.1 published** —
  https://github.com/Sam-Program-362/SnapTTL/releases/tag/v0.1.1
  (`app-release.apk`, versionCode 2). v0.1.0 (round-2 popup, superseded) also
  remains published.
- **Round 2 (free app + durations + popup fix) fully green on `main`** @ `b69d7f2`
  (PR #5 + #6): Verify `41109855494` ✓, Device tests `41109855811` ✓ (Android
  11/14/15), Build APK `41109855435` ✓, Export Room schemas ✓.
- **Signed release v0.1.0 published** (run `41109873781` ✓) — release "SnapTTL
  v0.1.0" carries `app-release.apk` signed with the user's keystore
  (`SNAPTTL_KEYSTORE_*` secrets). Note: `gh release upload` requires the release
  to exist; the workflow now creates it with `--generate-notes` when missing.

## Pending / next steps
- [x] CI green on Verify + Device tests (API 30/34/35).
- [x] Room schema JSONs committed to `app/schemas/`.
- [x] Build APK workflow green — unsigned artifacts on every main push.
- [x] PR #2/#3/#4 (round 1), PR #5/#6 (round 2) merged; main fully green.
- [x] Signed release v0.1.0 tagged and published with the signed APK attached.
- [ ] User re-tests the **signed** APK on the Android 16 device: popup after
      screenshot (needs "Display over other apps"), custom durations via the
      stepper, date/time path, and the notification fallback when the overlay
      permission is off.
- [ ] If the ad-refill idea is revived, see "Deferred monetization option" above.

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
- androidTest suspend calls must be wrapped in `runBlocking` (five were bare).

## Device-test emulator saga (resolved 2026-10-30)
Connected tests started failing on 2026-10-30 ~14:20Z with "Timeout waiting for
emulator to boot" on every API level, then API 34/35 only. Root-caused through
workflow-emitted annotations to **runner disk pressure**, not code:
- The API 34/35 system images default to a **~7.2 GB userdata partition**
  ("FATAL | Not enough space to create userdata partition … need 7372.80 MB")
  and the runner image's preinstalled NDK + extra system images left only
  ~6.5–7.2 GB free. Fix: delete `/usr/local/lib/android/sdk/ndk/*`,
  `system-images/*` (before installing the one this leg needs), `sources/*`,
  `skins/*` — then all three API levels boot and pass.
- `device-tests.yml` no longer uses `reactivecircus/android-emulator-runner`; it
  boots the emulator manually with **up to 3 retries** and surfaces the emulator
  log tail as annotations on failure (GitHub caps annotations at 10/step — keep
  the tail short so the actual FATAL line survives).
- Runners pinned to `ubuntu-22.04`; emulator flags `-no-snapshot
  -feature -Vulkan -feature GLDirectMem -partition-size 2048`; `libpulse0`
  installed (emulator 37's qemu links it); AVD config sets
  `disk.dataPartition.size=2g`, `hw.ramSize=2048`.
- Emulator `-version` prints to **stderr**; capture with `2>&1`.
- `gh run rerun` / `workflow_dispatch` are not permitted for this token —
  retrigger by pushing.

## Round-1 close-out (superseded by the status above)
- [x] CI green on Verify + Device tests (API 30/34/35).
- [x] Room schema JSONs committed to `app/schemas/`.
- [x] Build APK workflow green — artifacts `snapttl-release-apk` (unsigned) +
      `snapttl-debug-apk` produced on main (run `41069286895`).
- [x] PR #2 merged to main; **main is fully green**: Verify `41069286967` ✓,
      Device tests `41069286937` ✓, Build APK `41069286895` ✓,
      Export Room schemas `41069286974` ✓.
- [x] Tag `v0.1.0` — signed release published (see Verification status).
