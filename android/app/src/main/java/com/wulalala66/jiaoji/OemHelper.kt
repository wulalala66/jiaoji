package com.wulalala66.jiaoji

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 的「后台弹界面 / 自启动 / 锁屏显示」这些权限没有统一 API，
 * 各家都是一个自己的设置页。这里按厂商给一串候选入口，逐个试，
 * 全都打不开就退回应用详情页（参考 getActivity/XXPermissions 的做法）。
 *
 * ColorOS（OPPO / 一加 / realme）上真正决定「黑屏能不能弹出响铃页」的是
 * 「允许后台弹出界面」，其次才是锁屏显示和自启动。
 */
object OemHelper {

    private fun prop(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        c.getMethod("get", String::class.java).invoke(null, key) as? String ?: ""
    } catch (_: Throwable) {
        ""
    }

    val isColorOs: Boolean by lazy {
        prop("ro.build.version.opporom").isNotEmpty() ||
            prop("ro.build.version.oplusrom").isNotEmpty() ||
            prop("ro.oplus.version").isNotEmpty() ||
            listOf(Build.MANUFACTURER, Build.BRAND).any {
                val s = it.orEmpty().lowercase()
                s.contains("oppo") || s.contains("oneplus") || s.contains("realme") || s.contains("oplus")
            }
    }

    val isMiui: Boolean by lazy {
        prop("ro.miui.ui.version.name").isNotEmpty() ||
            prop("ro.mi.os.version.name").isNotEmpty() ||
            listOf(Build.MANUFACTURER, Build.BRAND).any {
                val s = it.orEmpty().lowercase()
                s.contains("xiaomi") || s.contains("redmi") || s.contains("poco")
            }
    }

    val isEmui: Boolean by lazy {
        prop("ro.build.version.emui").isNotEmpty() ||
            prop("hw_sc.build.platform.version").isNotEmpty() ||
            listOf(Build.MANUFACTURER, Build.BRAND).any {
                val s = it.orEmpty().lowercase()
                s.contains("huawei") || s.contains("honor")
            }
    }

    val isVivo: Boolean by lazy {
        prop("ro.vivo.os.version").isNotEmpty() ||
            listOf(Build.MANUFACTURER, Build.BRAND).any {
                val s = it.orEmpty().lowercase()
                s.contains("vivo") || s.contains("iqoo")
            }
    }

    fun romName(): String = when {
        isColorOs -> "ColorOS / OPPO 系"
        isMiui -> "MIUI / HyperOS"
        isEmui -> "EMUI / HarmonyOS"
        isVivo -> "OriginOS / vivo"
        else -> "Android 原生"
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (_: Throwable) {
        false
    }

    private fun firstOk(ctx: Context, list: List<Intent>): Boolean {
        for (i in list) if (start(ctx, i)) return true
        return false
    }

    /** 安全中心 App 首页（XXPermissions 的做法：只要能把安全中心打开，用户自己也能找到） */
    private fun openSafeCenter(ctx: Context, packages: List<String>): Boolean {
        for (p in packages) {
            val i = ctx.packageManager.getLaunchIntentForPackage(p) ?: continue
            if (start(ctx, i)) return true
        }
        return false
    }

    /** ColorOS：允许后台弹出界面（黑屏弹响铃页的关键） */
    fun openBackgroundPopup(ctx: Context): Boolean {
        val pkg = ctx.packageName
        if (isColorOs) {
            val list = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity",
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.floatwindow.FloatWindowListActivity",
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.PermissionManagerActivity",
                    )
                    putExtra("package_name", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.PermissionManagerActivity",
                    )
                    putExtra("package_name", pkg)
                },
                Intent().apply {
                    component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.PermissionTopActivity")
                },
            )
            if (firstOk(ctx, list)) return true
            if (openSafeCenter(ctx, listOf("com.coloros.safecenter", "com.oplus.safecenter", "com.color.safecenter", "com.oppo.safe"))) return true
        }
        if (isMiui) {
            val list = listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    )
                    putExtra("extra_pkgname", pkg)
                },
                Intent("miui.intent.action.APP_PERM_EDITOR").apply { putExtra("extra_pkgname", pkg) },
            )
            if (firstOk(ctx, list)) return true
        }
        if (isVivo) {
            val list = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
                    )
                    putExtra("packagename", pkg)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.FloatWindowManager",
                    )
                },
            )
            if (firstOk(ctx, list)) return true
        }
        return openAppDetails(ctx)
    }

    /** 自启动 / 关联启动 */
    fun openAutoStart(ctx: Context): Boolean {
        val list = ArrayList<Intent>()
        if (isColorOs) {
            list += Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                )
            }
            list += Intent().apply {
                component = ComponentName(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.startupapp.StartupAppListActivity",
                )
            }
            list += Intent().apply {
                component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            }
        }
        if (isMiui) {
            list += Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            }
        }
        if (isVivo) {
            list += Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                )
            }
        }
        if (isEmui) {
            list += Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                )
            }
        }
        if (firstOk(ctx, list)) return true
        if (isColorOs && openSafeCenter(ctx, listOf("com.coloros.safecenter", "com.oplus.safecenter", "com.oppo.safe"))) return true
        return openAppDetails(ctx)
    }

    /** 电池：允许后台高耗电 / 不限制 */
    fun openBatterySettings(ctx: Context): Boolean {
        val list = ArrayList<Intent>()
        if (isColorOs) {
            list += Intent().apply {
                component = ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
                )
            }
            list += Intent().apply {
                component = ComponentName(
                    "com.oplus.battery",
                    "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
                )
            }
            list += Intent().apply {
                component = ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity",
                )
            }
        }
        list += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        list += Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + ctx.packageName),
        )
        if (firstOk(ctx, list)) return true
        return openAppDetails(ctx)
    }

    fun openAppDetails(ctx: Context): Boolean {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        // ColorOS 官方给的一个跳转优化：带上受阻权限列表，会直接落到对应权限页
        if (isColorOs) {
            i.putExtra("isGetPermission", true)
            val b = android.os.Bundle()
            b.putStringArrayList(
                "permissionList",
                arrayListOf(android.Manifest.permission.POST_NOTIFICATIONS),
            )
            i.putExtras(b)
        }
        return start(ctx, i)
    }

    /** Android 14 起全屏通知要单独授权 */
    fun canUseFullScreenIntent(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } else {
            true
        }
    } catch (_: Throwable) {
        true
    }

    fun openFullScreenIntentSettings(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            val i = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:" + ctx.packageName))
            if (start(ctx, i)) return true
        }
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        if (start(ctx, i)) return true
        return openAppDetails(ctx)
    }
}
