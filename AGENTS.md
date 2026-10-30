# Contributing to SnapTTL

Guidance for agents working in this repository.

## Build

- JDK 17, Gradle wrapper (`./gradlew`), Android SDK with `compileSdk = 35`.
- The sandbox this project was authored in had no JVM/Android SDK: validate pure-Kotlin
  logic with `python3 -m unittest discover -s tools -p 'clean_room_core_tests.py'` and
  push full `./gradlew` runs to GitHub Actions (`.github/workflows/verify.yml`).

## Architecture

- `domain/` is pure Kotlin — no Android types, no `Context`. Keep it testable.
- `data/`, `service/`, `worker/`, `vision/` depend on Android; `ui/` is Compose.
- All long work is WorkManager (`ExpiryPurgeWorker`, `OcrIndexWorker`).
- Shared state flows through `state/CommonStateHolder`; repositories are the only
  writers.

## Rules

- Keep the app offline-first: no runtime networking, accounts, analytics, or ads.
- Do not add broad storage permissions; use MediaStore + SAF.
- Room: new database versions require additive migrations; export schemas via KSP.
- User data pulled from Drive must not be committed (`google_drive/` is ignored).

## Tests

- JUnit + Robolectric live in `app/src/test/java/dev/snapttl/**`; key suites run
  against Robolectric SDK 30/34/35 (Android 11/14/15).
- Instrumented tests live in `app/src/androidTest/java/dev/snapttl/**` and run on the
  emulator matrix (`.github/workflows/device-tests.yml`, API 30/34/35).
- The Python mirror in `tools/` must stay consistent with `domain/`.

## Branches

Session work happens on `arena/a88f5870-snapttl`; do not create or push other branches.
