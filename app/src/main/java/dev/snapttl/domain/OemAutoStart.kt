package dev.snapttl.domain

/**
 * Resolves the OEM background auto-start screen so aggressive battery killers do not
 * silently kill the capture monitor service. Reconstructed from Mark's OEM table.
 */
object OemAutoStart {

    data class Component(val packageName: String, val activityClass: String)

    private val TABLE: Map<String, List<Component>> = mapOf(
        "xiaomi" to listOf(
            Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        ),
        "poco" to listOf(
            Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        ),
        "redmi" to listOf(
            Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        ),
        "samsung" to listOf(
            Component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            Component("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
        ),
        "oppo" to listOf(
            Component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Component("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.monitor.activity.StartupAppListActivity")
        ),
        "realme" to listOf(
            Component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Component("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.monitor.activity.StartupAppListActivity")
        ),
        "vivo" to listOf(
            Component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        ),
        "iqoo" to listOf(
            Component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        ),
        "oneplus" to listOf(
            Component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            Component("com.oneplus.opguard", "com.oneplus.opguard.feature.startupmanager.StartupAppListActivity")
        ),
        "huawei" to listOf(
            Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        ),
        "honor" to listOf(
            Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        ),
        "asus" to listOf(
            Component("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            Component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        ),
        "tecno" to listOf(
            Component("com.transsion.phonemanager", "com.transsion.phonemanager.activity.AutoStartManagementActivity"),
            Component("com.infinix.xguard", "com.infinix.xguard.activity.StartupAppsActivity")
        ),
        "infinix" to listOf(
            Component("com.transsion.phonemanager", "com.transsion.phonemanager.activity.AutoStartManagementActivity"),
            Component("com.infinix.xguard", "com.infinix.xguard.activity.StartupAppsActivity")
        ),
        "itel" to listOf(
            Component("com.transsion.phonemanager", "com.transsion.phonemanager.activity.AutoStartManagementActivity"),
            Component("com.infinix.xguard", "com.infinix.xguard.activity.StartupAppsActivity")
        ),
        "nokia" to listOf(
            Component("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")
        ),
        "meizu" to listOf(
            Component("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
            Component("com.meizu.safe", "com.meizu.safe.security.HomeActivity")
        ),
        "zte" to listOf(
            Component("com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManager"),
            Component("com.zte.smartpower", "com.zte.smartpower.ZtePowerManagerActivity")
        ),
        "lenovo" to listOf(
            Component("com.lenovo.safecenter", "com.lenovo.safecenter.AutoStartActivity"),
            Component("com.lenovo.security", "com.lenovo.security.powersettings.PowerSettings")
        ),
        "sony" to listOf(
            Component("com.sonymobile.superstamina", "com.sonymobile.superstamina.SuperStaminaModeActivity"),
            Component("com.sonymobile.cta", "com.sonymobile.cta.SomcCTAMainActivity")
        ),
        "letv" to listOf(
            Component("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        ),
        "leeco" to listOf(
            Component("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        )
    )

    fun resolve(brand: String): Component? {
        val b = brand.trim().lowercase()
        val entry = TABLE.entries.firstOrNull { b.contains(it.key) } ?: return null
        return entry.value.first()
    }
}
