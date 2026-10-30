#!/usr/bin/env python3
"""
Clean-room core logic verification for SnapTTL.

Mirrors the Kotlin domain layer in app/src/main/java/dev/snapttl/domain so the core
mechanics (screenshot filtering, freemium/refill/premium entitlement, OCR/label search,
OEM auto-start routing, mini-game ranks) can be verified without a JVM. The Kotlin
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


# ---------------------------------------------------------------- entitlement
class TimeUnit:
    MINUTES = "Minutes"
    HOURS = "Hours"
    DAYS = "Days"
    NONE = "NoTime"


@dataclass
class EntitlementState:
    premium_purchased: bool = False
    ads_removed: bool = False
    refill_expiry_epoch_ms: int = 0

    DAY_MS = 24 * 60 * 60 * 1000
    REFILL_MAX_DAYS = 40
    REFILL_GRANT_DAYS = 20
    FREE_MIN_MINUTES = 15
    FREE_MAX_MINUTES = 45
    FREE_TIME_CHIPS = [15, 45]

    def refill_days_left(self, now_ms: int) -> int:
        if self.refill_expiry_epoch_ms <= now_ms:
            return 0
        return max(0, min(self.REFILL_MAX_DAYS, (self.refill_expiry_epoch_ms - now_ms) // self.DAY_MS))

    def has_unlocked_features(self, now_ms: int) -> bool:
        return self.premium_purchased or self.refill_days_left(now_ms) > 0

    def apply_rewarded_ad_refill(self, now_ms: int) -> int:
        base = max(now_ms, self.refill_expiry_epoch_ms)
        self.refill_expiry_epoch_ms = min(
            now_ms + self.REFILL_MAX_DAYS * self.DAY_MS,
            base + self.REFILL_GRANT_DAYS * self.DAY_MS,
        )
        return self.refill_days_left(now_ms)

    def is_valid_deletion_schedule_seconds(self, seconds: int, now_ms: int) -> bool:
        if seconds <= 0:
            return False
        if self.has_unlocked_features(now_ms):
            return True
        return self.FREE_MIN_MINUTES * 60 <= seconds <= self.FREE_MAX_MINUTES * 60

    def is_valid_deletion_schedule(self, value: int, unit: str, now_ms: int) -> bool:
        if value <= 0 or unit == TimeUnit.NONE:
            return False
        if self.has_unlocked_features(now_ms):
            return True
        return unit == TimeUnit.MINUTES and self.FREE_MIN_MINUTES <= value <= self.FREE_MAX_MINUTES


# ---------------------------------------------------------------- time chips
FREE_CHIPS = [15 * 60, 45 * 60]                      # seconds
ADVANCED_CHIPS = [30, 5 * 60, 30 * 60, 3 * 86_400]   # 30s, 5m, 30m, 3d


def allowed_chips(unlocked: bool) -> List[int]:
    return list(FREE_CHIPS + ADVANCED_CHIPS) if unlocked else list(FREE_CHIPS)


def chip_label(seconds: int) -> str:
    if seconds >= 86_400 and seconds % 86_400 == 0:
        return f"{seconds // 86_400}d"
    if seconds >= 3_600 and seconds % 3_600 == 0:
        return f"{seconds // 3_600}h"
    if seconds >= 60 and seconds % 60 == 0:
        return f"{seconds // 60}m"
    return f"{seconds}s"


def is_valid_deletion_schedule_seconds(seconds: int, entitlement, now: int) -> bool:
    """Mirror of EntitlementState.isValidDeletionScheduleSeconds."""
    if seconds <= 0:
        return False
    if entitlement.has_unlocked_features(now):
        return True
    return 15 * 60 <= seconds <= 45 * 60


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

    def test_freemium_bounds_15_to_45_minutes(self):
        s = EntitlementState()
        now = 1_700_000_000_000
        self.assertTrue(s.is_valid_deletion_schedule(15, TimeUnit.MINUTES, now))
        self.assertTrue(s.is_valid_deletion_schedule(45, TimeUnit.MINUTES, now))
        self.assertFalse(s.is_valid_deletion_schedule(14, TimeUnit.MINUTES, now))
        self.assertFalse(s.is_valid_deletion_schedule(46, TimeUnit.MINUTES, now))

    def test_rewarded_refill_grants_20_days_capped_at_40(self):
        s = EntitlementState()
        now = 1_700_000_000_000
        self.assertEqual(s.apply_rewarded_ad_refill(now), 20)
        self.assertEqual(s.apply_rewarded_ad_refill(now), 40)
        self.assertEqual(s.apply_rewarded_ad_refill(now), 40)

    def test_premium_unlocks_everything(self):
        s = EntitlementState(premium_purchased=True)
        now = 1_700_000_000_000
        self.assertTrue(s.is_valid_deletion_schedule(1, TimeUnit.DAYS, now))

    def test_time_chips(self):
        self.assertEqual(FREE_CHIPS, [900, 2700])
        self.assertEqual(ADVANCED_CHIPS, [30, 300, 1800, 259_200])
        self.assertEqual(allowed_chips(False), [900, 2700])
        self.assertEqual(len(allowed_chips(True)), 6)
        self.assertEqual(chip_label(30), "30s")
        self.assertEqual(chip_label(900), "15m")
        self.assertEqual(chip_label(7_200), "2h")
        self.assertEqual(chip_label(259_200), "3d")

    def test_free_tier_seconds_validation(self):
        s = EntitlementState()
        now = 1_700_000_000_000
        self.assertTrue(s.is_valid_deletion_schedule_seconds(900, now))
        self.assertTrue(s.is_valid_deletion_schedule_seconds(2_700, now))
        self.assertFalse(s.is_valid_deletion_schedule_seconds(899, now))
        self.assertFalse(s.is_valid_deletion_schedule_seconds(30, now))
        self.assertFalse(s.is_valid_deletion_schedule_seconds(259_200, now))

    def test_unlocked_seconds_validation(self):
        s = EntitlementState(refill_expiry_epoch_ms=1_700_000_000_000 + 20 * 86_400_000)
        now = 1_700_000_000_000
        self.assertTrue(s.is_valid_deletion_schedule_seconds(30, now))
        self.assertTrue(s.is_valid_deletion_schedule_seconds(259_200, now))

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
