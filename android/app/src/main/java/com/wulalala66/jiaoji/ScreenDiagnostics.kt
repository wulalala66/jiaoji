package com.wulalala66.jiaoji

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 自检与诊断：响铃不响 / 点了没反应时，用这一页拿到事实。
 */
@Composable
fun DiagnosticsScreen(state: AppState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var clicks by remember { mutableStateOf(0) }
    val dm = ctx.resources.displayMetrics
    val screenW = (dm.widthPixels / dm.density).toInt()
    val screenH = (dm.heightPixels / dm.density).toInt()
    val densityTxt = String.format(java.util.Locale.US, "%.2f", dm.density)
    val fontScaleTxt = String.format(java.util.Locale.US, "%.2f", ctx.resources.configuration.fontScale)
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val logs = remember(tick) { AlarmLog.all(ctx) }
    val pending = remember(tick, state) { AlarmScheduler.pendingMap(ctx) }

    val notifOk = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    val exactOk = AlarmScheduler.canExact(ctx)
    val fsiOk = if (Build.VERSION.SDK_INT >= 34) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.canUseFullScreenIntent() ?: true
        } catch (_: Throwable) {
            true
        }
    } else {
        true
    }
    val battOk = try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Throwable) {
        true
    }
    // 悬浮窗/锁屏显示：很多国产 ROM 用它控制「黑屏时能不能直接盖在锁屏上」
    val lockOk = try {
        Settings.canDrawOverlays(ctx)
    } catch (_: Throwable) {
        true
    }
    val volume = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        "$cur / $max"
    } catch (_: Throwable) {
        "未知"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("自检与诊断", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            SectionHeader("点击自测")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已点：$clicks 次",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Spacer(Modifier.weight(1f))
                    DiagButton("点我 +1", true) { clicks++ }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "数字会变说明触摸正常；数字不变说明触摸没传进来，请把这一页截图发我。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }

            SectionHeader("环境")
            AppCard {
                KeyValue("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
                KeyValue("系统", "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· ${OemHelper.romName()}")
                KeyValue("屏幕", "${screenW}×${screenH} dp · ${densityTxt}x")
                KeyValue("字体缩放", fontScaleTxt)
                KeyValue("深色模式", if (dark) "是" else "否")
                KeyValue("应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }

            SectionHeader("权限与系统设置")
            AppCard {
                CheckRow("通知权限", notifOk, "去开启") { openNotifSettings(ctx) }
                ThinDivider()
                CheckRow("精确闹钟", exactOk, "去允许") { openExactAlarm(ctx) }
                ThinDivider()
                CheckRow("全屏提醒", fsiOk, "去允许") { openFullScreen(ctx) }
                ThinDivider()
                CheckRow("悬浮窗", lockOk, "去开启") { openOverlay(ctx) }
                ThinDivider()
                CheckRow("后台不受限制", battOk, "去设置") { openBattery(ctx) }
                ThinDivider()
                // ColorOS/MIUI 都不提供「后台弹出界面」「自启动」的查询接口（AppOps 10021 会报 Bad operation），
                // 所以这里不能报「未开启」骗人，只给入口。
                UnknownRow("后台弹出界面", "系统不提供检测，以系统设置里为准") { OemHelper.openBackgroundPopup(ctx) }
                ThinDivider()
                UnknownRow("自启动", "系统不提供检测，以系统设置里为准") { OemHelper.openAutoStart(ctx) }
                ThinDivider()
                KeyValue(
                    "上次响铃页",
                    if (AlarmLog.all(ctx).any { it.contains("响铃页到前台") }) "成功过" else "没成功过",
                )
                ThinDivider()
                KeyValue("响铃开关", if (state.settings.sound) "开" else "关")
                KeyValue("振动开关", if (state.settings.vibrate) "开" else "关")
                KeyValue("闹钟音量", volume)
            }

            SectionHeader("已排定的闹钟", trailing = "${pending.size} 个")
            AppCard {
                if (pending.isEmpty()) {
                    Text(
                        "还没有排定的闹钟。加一个烘烤计时或取样提醒后，这里会列出系统闹钟时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                } else {
                    pending.entries.sortedBy { it.value }.forEachIndexed { i, (key, at) ->
                        if (i > 0) ThinDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(keyLabel(state, key), style = MaterialTheme.typography.bodyMedium, color = Ink)
                                Text(key, style = MaterialTheme.typography.bodySmall, color = Ink3)
                            }
                            Text(
                                Fmt.stamp(at),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (at > System.currentTimeMillis()) Brand else Danger,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            SectionHeader("测试", trailing = null)
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagButton("提前提醒音效", false) {
                        try {
                            ContextCompat.startForegroundService(
                                ctx,
                                RingService.intent(ctx, "test:", "提前提醒", "这就是出炉前 5 分钟的提示音", "pre", 6000)
                            )
                        } catch (_: Throwable) {
                        }
                    }
                    DiagButton("响铃 5 秒", true) {
                        try {
                            ContextCompat.startForegroundService(
                                ctx,
                                RingService.intent(ctx, "test:", "测试响铃", "能听到声音就说明铃声正常", "test", 5000)
                            )
                        } catch (_: Throwable) {
                        }
                        tick++
                    }
                    DiagButton("悬浮窗响铃页", false) {
                        RingOverlay.show(
                            ctx, "test:overlay",
                            "悬浮窗响铃页",
                            "这就是黑屏、响铃页起不来时兜底显示的界面",
                            "test",
                        )
                    }
                    DiagButton("全屏响铃页", false) {
                        RingNotif.launchRingActivity(ctx, "test:", "测试全屏提醒", "这就是到点时的样子", "pre")
                        tick++
                    }
                    DiagButton("10 秒后响铃", false) {
                        AlarmScheduler.scheduleTest(ctx, 10)
                        tick++
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagButton("重排闹钟", false) {
                        AlarmScheduler.sync(ctx, Repo.read(ctx))
                        tick++
                    }
                    DiagButton("清空记录", false) {
                        AlarmLog.clear(ctx)
                        tick++
                    }
                }
            }

            SectionHeader("最近动作与闹钟记录", action = "刷新", onAction = { tick++ })
            AppCard {
                if (logs.isEmpty()) {
                    Text("（空）", style = MaterialTheme.typography.bodySmall, color = Ink3)
                } else {
                    logs.take(40).forEach { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.contains("✗")) Danger else Ink2,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "如果这里显示「动作 xxx」说明按钮点到了；没有记录说明触摸没传进来。把这一页截图发给开发者最快。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun keyLabel(state: AppState, key: String): String {
    val i = key.indexOf(':')
    val kind = if (i > 0) key.substring(0, i) else key
    val id = if (i > 0) key.substring(i + 1) else ""
    return when (kind) {
        "b" -> "出炉 · " + (state.bakes.firstOrNull { it.id == id }?.label ?: id)
        "bp" -> "出炉前提醒 · " + (state.bakes.firstOrNull { it.id == id }?.label ?: id)
        "s" -> "取样 · " + (state.sampling.firstOrNull { it.id == id }?.customer ?: id)
        "sp" -> "取样前提醒 · " + (state.sampling.firstOrNull { it.id == id }?.customer ?: id)
        else -> key
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, action: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
        Spacer(Modifier.width(8.dp))
        Text(
            if (ok) "已开启" else "未开启",
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) Ok else Danger,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        if (!ok) {
            DiagButton(action, true) { onFix() }
        }
    }
}

@Composable
private fun UnknownRow(label: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        Spacer(Modifier.width(8.dp))
        DiagButton("去开启", true) { onClick() }
    }
}

@Composable
private fun DiagButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) Brand else SoftBg)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(),
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) Color.White else Ink2,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

/* ---------------- 打开系统设置 ---------------- */

fun openNotifSettings(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    } catch (_: Throwable) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }
}

fun openExactAlarm(ctx: Context) {
    try {
        val i = if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        }
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

fun openFullScreen(ctx: Context) {
    try {
        val i = if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        }
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

fun openAppDetails(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
    }
}

/**
 * 跳到系统里能打开「锁屏显示」的地方。
 * 安卓没有这个权限的申请弹窗，各家的入口也不一样：
 *  - 小米 / 红米（MIUI、HyperOS）：可以直接打开「应用权限管理」页，里面有锁屏显示、自启动、后台弹出界面
 *  - 其它 ROM：退到「应用信息 → 权限」页，锁屏显示通常也在这一页
 */
fun openLockScreenSettings(ctx: Context) {
    // 1) MIUI / HyperOS 的权限编辑页（锁屏显示、自启动、后台弹出界面都在这页）
    try {
        ctx.startActivity(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", ctx.packageName)
                putExtra("extra_package_name", ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return
    } catch (_: Throwable) {
    }
    // 2) 华为 / 荣耀的权限页
    try {
        ctx.startActivity(
            Intent("com.huawei.permissionmanager.ui.MainActivity").apply {
                putExtra("packageName", ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return
    } catch (_: Throwable) {
    }
    // 3) 通用：应用信息页
    openAppDetails(ctx)
}

fun openOverlay(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }
}

fun openBattery(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }
}
