# SnapTTL — Specification

SnapTTL gives every screenshot a time-to-live. Screenshots are detected, surfaced in a
floating overlay, and silently deleted once their scheduled time expires. OCR and image
labeling build a full-text search index. This is a clean-room reconstruction of the
analyzed app (target `com.markOne.ss_app`, v7.1) under a new identity.

## Identity

| | |
|---|---|
| Package | `dev.snapttl` (applicationId `dev.snapttl.app`) |
| App name | SnapTTL |
| Tagline | Time-to-live for every screenshot |
| minSdk / targetSdk | 26 / 35 |
| Easter egg | "Sweep Blitz" |

## Screens

1. **Home** — 15/45-minute quick delete chips (free tier), thumbnail grid of all
   screenshots, persistent capture-service banner, hidden easter-egg trigger.
2. **Scheduled** — the expiry queue (`expiry_queue_item`), upcoming times, cancel.
3. **All Screenshots** — full thumbnail grid, manual delete.
4. **Search** — OCR text + image-label search against `ocr_label_index`.
5. **Settings** — overlay permission, service toggle, SAF tree, storage auto-delete,
   advanced (30 s / 5 min / 30 min / 3 days), OCR index toggle, language, auto-start.
6. **Easter Egg** — hidden mini-game.

## Domain rules

- **Screenshot filter** (`domain/ScreenshotFilter`): URI must be
  `content://media/external/images/media/<id>`; filename or path must indicate a
  screenshot; reject `.tmp` / `.pending` / `.trashed` / `.deleted` / dot-prefixed files.
- **Entitlement** (`domain/EntitlementState`): free tier allows 15–45 minutes only.
  Rewarded-ad refill grants +20 days, capped at 40 days. `buy_premium` / `remove_ads`
  are one-time purchases that unlock all durations.
- **OEM auto-start** (`domain/OemAutoStart`): maps brand → auto-start manager
  component for 20 OEM families.
- **Mini-game** (`domain/MiniGame`): `NOVICE` / `STEADY` / `SHARP` / `LEGEND`.

## Room schemas

`expiry_queue_item(id, filePath, deleteTimeEpochMs, timeUnit, workManagerId UNIQUE,
createdAtEpochMillis, screenshotId)`

`ocr_label_index(screenshotId PRIMARY KEY, extractedText, labels,
indexedAtEpochMillis)`

Exported to `app/schemas/` (KSP `room.schemaLocation`).

## Android permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Foreground capture service on API 33+ |
| `READ_MEDIA_IMAGES` | MediaStore images, API 33+ |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Partial-access photos, API 33+ |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | MediaStore images, API 29–32 |
| `SYSTEM_ALERT_WINDOW` | Floating overlay |

No broad storage permission, no network/account permissions.

## Device test matrix

Manual (or device-farm) checks per target API:

| Check | Android 14+ (34/35) | Android 11+ (26–33) |
|---|---|---|
| Screenshot detected, overlay appears | ✅ | ✅ |
| 15/45 min schedule accepted | ✅ | ✅ |
| SAF tree granted + auto-delete | ✅ | ✅ |
| ML Kit OCR + image labeling | ✅ | ✅ |
| Partial photo access flow | ✅ | n/a |
| Auto-start routing (Settings) | ✅ | ✅ |
| Kill app → device boot → pending deletions run | ✅ | ✅ |
| In-app language switch persists | ✅ | ✅ |
| Hidden easter egg | ✅ | ✅ |

## Build

```
./gradlew testDebugUnitTest lint assembleDebug
```

CI workflows: `.github/workflows/verify.yml` (tests/lint/APK/permissions),
`build-apk.yml` (unsigned release), `release.yml` (signed release via secrets).
