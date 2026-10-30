package dev.snapttl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemAutoStartTest {

    @Test
    fun resolves_xiaomi_family() {
        assertEquals(
            "com.miui.securitycenter",
            OemAutoStart.resolve("Xiaomi")?.packageName
        )
        assertEquals(
            "com.miui.securitycenter",
            OemAutoStart.resolve("Redmi")?.packageName
        )
    }

    @Test
    fun resolves_samsung() {
        assertEquals(
            "com.samsung.android.lool",
            OemAutoStart.resolve("samsung")?.packageName
        )
    }

    @Test
    fun resolves_oppo_and_realme() {
        assertEquals(
            "com.coloros.safecenter",
            OemAutoStart.resolve("OPPO")?.packageName
        )
        assertEquals(
            "com.coloros.safecenter",
            OemAutoStart.resolve("realme")?.packageName
        )
    }

    @Test
    fun resolves_vivo_and_transsion() {
        assertEquals(
            "com.vivo.permissionmanager",
            OemAutoStart.resolve("vivo")?.packageName
        )
        assertEquals(
            "com.transsion.phonemanager",
            OemAutoStart.resolve("TECNO")?.packageName
        )
    }

    @Test
    fun unknown_brand_returns_null() {
        assertNull(OemAutoStart.resolve("google"))
        assertNull(OemAutoStart.resolve(""))
    }
}
