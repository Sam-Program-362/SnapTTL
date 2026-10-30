# SnapTTL — Context

Working context for this task, kept across turns.

## Objective

Clean-room reconstruct the Android screenshot time-to-live app described by the Drive
analysis (`REA / Mark`, target `com.markOne.ss_app` v7.1) as **SnapTTL**
(`dev.snapttl.app`). Deliver a compiling, testable Android project with an APK build
workflow.

## Requirements traceability

| # | Requirement | Where |
|---|---|---|
| 1 | Automatic screenshot detection | `domain/ScreenshotFilter` (two-stage ContentObserver) + `service/CaptureMonitorService` |
| 2 | Floating overlay with countdown + delete | `ui/overlay/CaptureOverlay*` + `CaptureOverlayActivity` |
| 3 | Silent deletion (MediaStore + SAF) | `repository/ScreenshotRepository.deleteNow` + `ExpiryPurgeWorker` |
| 4 | Scheduled deletion queue with silent reschedule | `data/expiry/*`, `ExpiryRepository`, `ExpiryPurgeWorker` |
| 5 | On-device OCR + image labeling index + search | `vision/*`, `data/search/*`, `SearchRepository`, `OcrIndexWorker`, Search screen |
| 6 | Manage-all-screenshots | Home / All Screenshots screens, `ScreenshotRepository.listAll` |
| 7 | Freemium limits + ad refill + premium | `domain/EntitlementState`, `PrefsManager`, Home gating |
| 8 | Crash-resilient silent delete | `ExpiryPurgeWorker`, `CaptureMonitorService`, `BootCompletedReceiver` |
| 9 | OEM auto-start routing | `domain/OemAutoStart` + Settings screen |
| 10 | In-app locale switching | `language/AppLanguage`, `language/LocaleHelper`, Settings |
| 11 | Hidden easter egg mini-game | Home (tap logo 5x) → `MiniGame` + `EasterEggScreen` |
| 12 | Offline, no broad storage, Room KSP schemas | `app/build.gradle.kts`, `.gitignore`, permissions audit |

## Acceptance criteria

1. `./gradlew testDebugUnitTest lint assembleDebug` succeeds.
2. Core logic covered by JUnit (`app/src/test`) and the Python clean-room mirror
   (`tools/clean_room_core_tests.py`).
3. Room schemas exported to `app/schemas/` and reviewed.
4. Packaged permissions reviewed in CI (`.github/workflows/verify.yml`).
5. Release APK build workflow present and runnable (`build-apk.yml`, `release.yml`).
6. Android 14+ (SDK 34/35) and Android 11+ (minSdk 26) functional test matrix documented
   in `docs/SPEC.md`.

## Verification status

- Local: no JDK/Gradle/Android SDK available in the sandbox. Verification limited to
  the Python mirror suite (**8 tests pass**) and static review.
- CI: `.github/workflows/verify.yml` runs the JVM suite, lint, Room schema export,
  permission audit and debug APK build on every PR.
- Device tests (Android 14+, Android 11+) must be run by the user on real hardware or a
  device farm — see `docs/SPEC.md` § Device test matrix.

## Pending / next steps

- [ ] Real `./gradlew testDebugUnitTest lint assembleDebug` in CI.
- [ ] Device functional matrix on Android 14+ and Android 11+.
- [ ] Review generated Room schemas once CI exports them.
- [ ] First release tag to exercise the signed APK workflow.
