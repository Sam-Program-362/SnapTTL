#!/usr/bin/env python3
"""
Clean-room core logic verification for SnapTTL.

Mirrors the Kotlin domain layer in app/src/main/java/dev/snapttl/domain so the core
mechanics (screenshot filtering, deletion-duration rules, OCR/label search, OEM
auto-start routing, mini-game ranks) can be verified without a JVM. The Kotlin
equivalents have the same JUnit suites under app/src/test.

Run:  python3 -m unittest tools.clean_room_core_tests -v
"""

import re
import sqlite3
import unittest
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple


# ---------------------------------------------------------------- screenshot filter
MEDIA_ITEM_URI_RE = re.compile(r"^content://media/external/images/media/[0-9]+$")
EXCLUDED_TOKENS = (
    ".tmp", ".pending", ".trashed", ".deleted", ".delete", ".temp",
    ".cache", "cache", ".nomedia", ".Trash", ".thumbnails",
    ".thumbnail", ".partial", ".part",
)


def is_valid_screenshot_event(uri: str, display_name: str, file_path: str) -> bool:
    if not MEDIA_ITEM_URI_RE.match(uri):
        return False
    dn = display_name.lower()
    fp = file_path.lower()
    if "screenshot" not in dn and "screenshots/" not in fp:
        return False
    if display_name.startswith("."):
        return False
    for token in EXCLUDED_TOKENS:
        lowered = token.lower()
        if lowered in fp or lowered in dn:
            return False
    return True


# ---------------------------------------------------------------- deletion duration
SECONDS_PER_MINUTE = 60
SECONDS_PER_HOUR = 3_600
SECONDS_PER_DAY = 86_400

MAX_SECONDS = 7 * SECONDS_PER_DAY            # quick-timer cap: one week
MAX_TARGET_AHEAD_DAYS = 365                  # date/time targets: at most a year out
MAX_CHIPS = 6                                # user's one-tap options: at most six
DEFAULT_CHIPS = [15 * 60, 3600, 6 * 3600, 86400]  # 15m, 1h, 6h, 1d (editable starters)


def is_valid_seconds(seconds: int) -> bool:
    """Mirror of DeletionDuration.isValidSeconds (1 s .. 7 d, for everyone)."""
    return 1 <= seconds <= MAX_SECONDS


def is_valid_target_epoch_ms(target_epoch_ms: int, now_epoch_ms: int) -> bool:
    """Mirror of DeletionDuration.isValidTargetEpochMs (future, under a year)."""
    if target_epoch_ms <= now_epoch_ms:
        return False
    return target_epoch_ms - now_epoch_ms <= MAX_TARGET_AHEAD_DAYS * SECONDS_PER_DAY * 1000


def duration_label(seconds: int) -> str:
    if seconds >= SECONDS_PER_DAY and seconds % SECONDS_PER_DAY == 0:
        return f"{seconds // SECONDS_PER_DAY}d"
    if seconds >= SECONDS_PER_HOUR and seconds % SECONDS_PER_HOUR == 0:
        return f"{seconds // SECONDS_PER_HOUR}h"
    if seconds >= SECONDS_PER_MINUTE and seconds % SECONDS_PER_MINUTE == 0:
        return f"{seconds // SECONDS_PER_MINUTE}m"
    return f"{seconds}s"


def detailed_label(seconds: int) -> str:
    rest = max(0, seconds)
    days, rest = divmod(rest, SECONDS_PER_DAY)
    hours, rest = divmod(rest, SECONDS_PER_HOUR)
    minutes, secs = divmod(rest, SECONDS_PER_MINUTE)
    parts = []
    if days:
        parts.append(f"{days}d")
    if hours:
        parts.append(f"{hours}h")
    if minutes:
        parts.append(f"{minutes}m")
    if secs or not parts:
        parts.append(f"{secs}s")
    return " ".join(parts)


def unit_and_value(seconds: int) -> Tuple[int, str]:
    """Mirror of DeletionDuration.unitAndValue (largest exact unit)."""
    if seconds >= SECONDS_PER_DAY and seconds % SECONDS_PER_DAY == 0:
        return seconds // SECONDS_PER_DAY, "DAYS"
    if seconds >= SECONDS_PER_HOUR and seconds % SECONDS_PER_HOUR == 0:
        return seconds // SECONDS_PER_HOUR, "HOURS"
    if seconds >= SECONDS_PER_MINUTE and seconds % SECONDS_PER_MINUTE == 0:
        return seconds // SECONDS_PER_MINUTE, "MINUTES"
    return seconds, "SECONDS"


def is_valid_chip_list(chips: List[int]) -> bool:
    """Mirror of DeletionDuration.isValidChipList (1..MAX_CHIPS, valid, unique)."""
    return (
        bool(chips)
        and len(chips) <= MAX_CHIPS
        and all(is_valid_seconds(c) for c in chips)
        and len(set(chips)) == len(chips)
    )


def upsert_chip(chips: List[int], old_seconds: Optional[int], new_seconds: int) -> Optional[List[int]]:
    """Mirror of DeletionDuration.upsertChip; None when the edit is rejected."""
    if not is_valid_seconds(new_seconds):
        return None
    if new_seconds != old_seconds and new_seconds in chips:
        return None
    base = [c for c in chips if c != old_seconds] if old_seconds is not None else list(chips)
    if old_seconds is None and len(base) >= MAX_CHIPS:
        return None
    return sorted(base + [new_seconds])


def remove_chip(chips: List[int], seconds: int) -> Optional[List[int]]:
    """Mirror of DeletionDuration.removeChip; None when the last one would go."""
    if len(chips) <= 1 or seconds not in chips:
        return None
    return sorted(c for c in chips if c != seconds)


def encode_chips(chips: List[int]) -> str:
    return ",".join(str(c) for c in chips)


def decode_chips(raw: Optional[str]) -> List[int]:
    if not raw or not raw.strip():
        return []
    out = []
    for part in raw.split(","):
        try:
            value = int(part.strip())
        except ValueError:
            continue
        if is_valid_seconds(value) and value not in out:
            out.append(value)
    return sorted(out)


# ---------------------------------------------------------------- OCR/label search
class MarkPersistenceEngine:
    def __init__(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self._init_schemas()

    def _init_schemas(self):
        cur = self.conn.cursor()
        cur.executescript(
            """
            CREATE TABLE IF NOT EXISTS expiry_queue_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                filePath TEXT NOT NULL,
                deleteTimeEpochMs INTEGER NOT NULL,
                timeUnit TEXT NOT NULL,
                workManagerId TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                screenshotId INTEGER NOT NULL
            );
            CREATE UNIQUE INDEX IF NOT EXISTS idx_expiry_work ON expiry_queue_item(workManagerId);

            CREATE TABLE IF NOT EXISTS ocr_label_index (
                screenshotId INTEGER NOT NULL,
                extractedText TEXT NOT NULL,
                labels TEXT NOT NULL,
                indexedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(screenshotId)
            );
            """
        )
        self.conn.commit()

    def index_screenshot(self, screenshot_id, text, labels, indexed_at_ms):
        self.conn.execute(
            "INSERT OR REPLACE INTO ocr_label_index VALUES (?, ?, ?, ?)",
            (screenshot_id, text, ", ".join(labels), indexed_at_ms),
        )
        self.conn.commit()

    def indexed_among(self, ids):
        rows = self.conn.execute(
            f"SELECT screenshotId FROM ocr_label_index WHERE screenshotId IN ({','.join('?' * len(ids))})",
            ids,
        ).fetchall()
        return {r["screenshotId"] for r in rows}

    def search(self, query):
        rows = self.conn.execute(
            """
            SELECT screenshotId FROM ocr_label_index
            WHERE extractedText LIKE '%' || ? || '%' OR labels LIKE '%' || ? || '%'
            ORDER BY indexedAtEpochMillis DESC
            """,
            (query, query),
        ).fetchall()
        return [r["screenshotId"] for r in rows]


# ---------------------------------------------------------------- OEM auto-start
OEM_AUTOSTART = {
    "xiaomi": ("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    "poco": ("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    "redmi": ("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    "samsung": ("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    "oppo": ("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    "realme": ("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
    "vivo": ("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    "iqoo": ("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
    "huawei": ("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    "honor": ("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    "oneplus": ("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    "asus": ("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
    "tecno": ("com.transsion.phonemanager", "com.transsion.phonemanager.activity.AutoStartManagementActivity"),
    "nokia": ("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
    "meizu": ("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
    "zte": ("com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManager"),
    "lenovo": ("com.lenovo.safecenter", "com.lenovo.safecenter.AutoStartActivity"),
    "sony": ("com.sonymobile.superstamina", "com.sonymobile.superstamina.SuperStaminaModeActivity"),
    "letv": ("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
    "leeco": ("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
}


def resolve_oem_autostart(brand: str) -> Optional[Tuple[str, str]]:
    b = brand.strip().lower()
    for key, comp in OEM_AUTOSTART.items():
        if key in b:
            return comp
    return None


# ---------------------------------------------------------------- mini-game
def mini_game_rank(score: int) -> str:
    if score <= 9:
        return "NOVICE"
    if score <= 24:
        return "STEADY"
    if score <= 39:
        return "SHARP"
    return "LEGEND"


class CleanRoomCoreTests(unittest.TestCase):
    def test_screenshot_filter_accepts_valid(self):
        self.assertTrue(is_valid_screenshot_event(
            "content://media/external/images/media/10492",
            "Screenshot_20261027-013000.png",
            "/storage/emulated/0/Pictures/Screenshots/Screenshot_20261027-013000.png",
        ))

    def test_screenshot_filter_rejects_pending_and_trashed(self):
        self.assertFalse(is_valid_screenshot_event(
            "content://media/external/images/media/10493",
            ".pending-172999-Screenshot_2026.png",
            "/storage/emulated/0/Pictures/Screenshots/.pending-172999-Screenshot_2026.png",
        ))
        self.assertFalse(is_valid_screenshot_event(
            "content://media/external/images/media/10494",
            "Screenshot.png",
            "/storage/emulated/0/Pictures/Screenshots/.trashed_Screenshot.png",
        ))

    def test_quick_timer_accepts_1s_to_7d(self):
        self.assertTrue(is_valid_seconds(1))
        self.assertTrue(is_valid_seconds(30))
        self.assertTrue(is_valid_seconds(900))
        self.assertTrue(is_valid_seconds(MAX_SECONDS))

    def test_quick_timer_rejects_zero_and_beyond_a_week(self):
        self.assertFalse(is_valid_seconds(0))
        self.assertFalse(is_valid_seconds(-60))
        self.assertFalse(is_valid_seconds(MAX_SECONDS + 1))

    def test_datetime_target_must_be_future_within_a_year(self):
        now = 1_800_000_000_000
        self.assertTrue(is_valid_target_epoch_ms(now + 1, now))
        self.assertTrue(is_valid_target_epoch_ms(now + 365 * 86_400_000, now))
        self.assertFalse(is_valid_target_epoch_ms(now, now))
        self.assertFalse(is_valid_target_epoch_ms(now - 1, now))
        self.assertFalse(is_valid_target_epoch_ms(now + 366 * 86_400_000, now))
        self.assertFalse(is_valid_target_epoch_ms(now + 400 * 86_400_000, now))

    def test_duration_labels(self):
        self.assertEqual(duration_label(30), "30s")
        self.assertEqual(duration_label(900), "15m")
        self.assertEqual(duration_label(7_200), "2h")
        self.assertEqual(duration_label(259_200), "3d")
        self.assertEqual(duration_label(MAX_SECONDS), "7d")

    def test_detailed_labels(self):
        self.assertEqual(detailed_label(45), "45s")
        self.assertEqual(detailed_label(5_400), "1h 30m")
        self.assertEqual(detailed_label(2 * 86_400 + 4 * 3_600 + 1_800), "2d 4h 30m")
        self.assertEqual(detailed_label(0), "0s")

    def test_unit_and_value_picks_largest_exact_unit(self):
        self.assertEqual(unit_and_value(900), (15, "MINUTES"))
        self.assertEqual(unit_and_value(30), (30, "SECONDS"))
        self.assertEqual(unit_and_value(7_200), (2, "HOURS"))
        self.assertEqual(unit_and_value(259_200), (3, "DAYS"))
        self.assertEqual(unit_and_value(90), (90, "SECONDS"))

    def test_starter_chips_and_list_rules(self):
        self.assertEqual(DEFAULT_CHIPS, [900, 3600, 21600, 86400])
        self.assertTrue(is_valid_chip_list(DEFAULT_CHIPS))
        self.assertTrue(is_valid_chip_list([30]))
        self.assertTrue(is_valid_chip_list([(i + 1) * 60 for i in range(MAX_CHIPS)]))
        self.assertFalse(is_valid_chip_list([]))
        self.assertFalse(is_valid_chip_list([(i + 1) * 60 for i in range(7)]))
        self.assertFalse(is_valid_chip_list([60, 60]))
        self.assertFalse(is_valid_chip_list([60, 0]))

    def test_upsert_adds_edits_and_rejects_bad_input(self):
        base = [900, 3600]
        self.assertEqual(upsert_chip(base, None, 86400), [900, 3600, 86400])
        self.assertIsNone(upsert_chip(base, None, 900))
        self.assertIsNone(upsert_chip(base, None, 0))
        full = [(i + 1) * 60 for i in range(6)]
        self.assertIsNone(upsert_chip(full, None, 86400))
        self.assertEqual(upsert_chip(full, 360, 3600), [60, 120, 180, 240, 300, 3600])
        self.assertIsNone(upsert_chip(base, 900, 3600))

    def test_remove_never_empties_the_list(self):
        self.assertIsNone(remove_chip([900], 900))
        self.assertEqual(remove_chip([900, 3600], 3600), [900])
        self.assertIsNone(remove_chip([900, 3600], 42))

    def test_encode_decode_round_trips(self):
        chips = [900, 3600, 86400]
        self.assertEqual(decode_chips(encode_chips(chips)), chips)
        self.assertEqual(decode_chips("abc, 900, -5, 0"), [900])
        self.assertEqual(decode_chips(None), [])
        self.assertEqual(decode_chips("  "), [])
        self.assertEqual(decode_chips("60, %d" % (MAX_SECONDS + 1)), [60])

    def test_ocr_and_label_search(self):
        engine = MarkPersistenceEngine()
        engine.index_screenshot(101, "Invoice #4829 Total $129.00", ["Receipt", "Paper"], 1000)
        engine.index_screenshot(102, "Flight Boarding Pass Gate B12", ["QR Code", "Ticket"], 2000)
        self.assertEqual(engine.search("Boarding Pass"), [102])
        self.assertEqual(engine.search("Receipt"), [101])
        self.assertEqual(engine.indexed_among([101, 102, 103]), {101, 102})

    def test_oem_autostart_routing(self):
        self.assertEqual(resolve_oem_autostart("Xiaomi")[0], "com.miui.securitycenter")
        self.assertEqual(resolve_oem_autostart("samsung")[0], "com.samsung.android.lool")
        self.assertEqual(resolve_oem_autostart("TECNO")[0], "com.transsion.phonemanager")
        self.assertIsNone(resolve_oem_autostart("google"))

    def test_mini_game_ranks(self):
        self.assertEqual(mini_game_rank(0), "NOVICE")
        self.assertEqual(mini_game_rank(10), "STEADY")
        self.assertEqual(mini_game_rank(25), "SHARP")
        self.assertEqual(mini_game_rank(40), "LEGEND")


if __name__ == "__main__":
    unittest.main(verbosity=2)
