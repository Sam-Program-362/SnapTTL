# SnapTTL

**Time-to-live for every screenshot.** SnapTTL watches for new screenshots, floats a
countdown over them, and silently deletes them when their scheduled time runs out. It
also builds an on-device OCR + image-labeling search index.

Clean-room reconstruction of the analyzed `com.markOne.ss_app` (Mark) app, under the
SnapTTL identity. No networking, no accounts, no analytics, no ads in core.

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/dev/snapttl/domain` | Pure Kotlin rules: screenshot filter, entitlement, OEM auto-start, mini-game |
| `app/src/main/java/dev/snapttl/data` | Room DBs, DAOs, SharedPreferences, repositories |
| `app/src/main/java/dev/snapttl/vision` | ML Kit OCR + image labeling |
| `app/src/main/java/dev/snapttl/service` | Capture service, boot receiver, notifications |
| `app/src/main/java/dev/snapttl/worker` | WorkManager expiry + OCR indexing |
| `app/src/main/java/dev/snapttl/ui` | Jetpack Compose UI, ViewModels, overlay |
| `app/schemas/` | Exported Room schemas (additive migrations) |
| `tools/clean_room_core_tests.py` | Python mirror of core rules (runs without a JVM) |
| `.github/workflows/` | verify · build APK · signed release |
| `docs/SPEC.md`, `context/context.md` | Spec + working context |

## Build & verify

Requires JDK 17 and the Android SDK (no local toolchain in the sandbox — run these in CI
or a machine with the SDK).

```
./gradlew testDebugUnitTest lint assembleDebug
```

Core-rule cross-check that needs no JVM:

```
python3 -m unittest discover -s tools -p 'clean_room_core_tests.py' -v
```

## Release

- **Unsigned:** `.github/workflows/build-apk.yml` (workflow dispatch / main push).
- **Signed:** `.github/workflows/release.yml` needs the secrets
  `SNAPTTL_KEYSTORE_B64`, `SNAPTTL_KEYSTORE_PASSWORD`, `SNAPTTL_KEY_ALIAS`,
  `SNAPTTL_KEY_PASSWORD`.

## Notes

- Offline-first: no runtime networking/accounts/analytics/ads in core.
- No broad storage permission; MediaStore + SAF only.
- Room schemas versioned; new database versions use additive migrations only.
- Packaged permissions are audited in CI.
