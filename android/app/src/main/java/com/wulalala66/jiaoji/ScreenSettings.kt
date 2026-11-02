package com.wulalala66.jiaoji

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

@Composable
fun SettingsScreen(
    state: AppState,
    onOpenType: (String?) -> Unit,
    onOpenDiagnostics: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val s = state.settings
    var showAddCustomer by remember { mutableStateOf(false) }
    var showAddOven by remember { mutableStateOf(false) }
    var renameOvenTarget by remember { mutableStateOf<String?>(null) }
    var confirmDeleteOven by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteType by remember { mutableStateOf<String?>(null) }
    var alarmGranted by remember { mutableStateOf(AlarmScheduler.canExact(ctx)) }
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var overlayOn by remember { mutableStateOf(overlayOk(ctx)) }
    var battOk by remember { mutableStateOf(ignoreBattOk(ctx)) }
    var fsiOk by remember { mutableStateOf(OemHelper.canUseFullScreenIntent(ctx)) }
    // 通知权限是真正的系统运行时权限，可以直接弹系统框问用户要
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifOn = granted || NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    // 用户去系统设置里开了权限再回来，这里要重新读一次，不然一直显示「未开启」
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        alarmGranted = AlarmScheduler.canExact(ctx)
        overlayOn = overlayOk(ctx)
        battOk = ignoreBattOk(ctx)
        fsiOk = OemHelper.canUseFullScreenIntent(ctx)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("设置", style = MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("提醒、产品类型、客户、数据备份", style = MaterialTheme.typography.bodyMedium, color = Ink3)
            }
        }

        item { SectionHeader("提醒") }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("提前提醒", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text("到点前多久先响一声", style = MaterialTheme.typography.bodySmall, color = Ink3)
                    }
                    StepButton("−") {
                        Repo.I.updateSettings { it.copy(preAlertMinutes = (it.preAlertMinutes - 1).coerceAtLeast(0)) }
                    }
                    Text(
                        "${s.preAlertMinutes} 分",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    StepButton("+") {
                        Repo.I.updateSettings { it.copy(preAlertMinutes = (it.preAlertMinutes + 1).coerceAtMost(60)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow("响铃", "到点循环响铃，点「已取出」停止", s.sound) { v ->
                    Repo.I.updateSettings { it.copy(sound = v) }
                }
                ToggleRow("振动", "安静环境下也能感觉到", s.vibrate) { v ->
                    Repo.I.updateSettings { it.copy(vibrate = v) }
                }
                ToggleRow("保持屏幕常亮", "看计时的时候不会黑屏", s.keepAwake) { v ->
                    Repo.I.updateSettings { it.copy(keepAwake = v) }
                }
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (notifOn) "通知权限已开启" else "通知权限未开启",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (notifOn) Ink else Danger,
                        )
                        Text(
                            "没权限时只会在 App 内响铃，锁屏可能收不到",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink3,
                        )
                    }
                    if (!notifOn) {
                        SmallButton("允许通知", true) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (alarmGranted) "精确闹钟已允许" else "精确闹钟未允许",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (alarmGranted) Ink else Danger,
                        )
                        Text(
                            "允许后到点误差在几秒内，否则可能延迟",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink3,
                        )
                    }
                    if (!alarmGranted) {
                        SmallButton("去允许") {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:" + ctx.packageName))
                                runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 黑屏到点能不能直接全屏响铃，实际就看这一项——系统里叫「悬浮窗 / 显示在其他应用上层」，
                // 2.3.1 起就是这么判断的，实测给了它黑屏就能出响铃页。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (overlayOn) "悬浮窗已开启" else "悬浮窗未开启",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (overlayOn) Ink else Danger,
                        )
                        Text(
                            if (overlayOn) "黑屏到点会直接全屏响铃"
                            else "不开启的话，黑屏到点会先让你解锁再响铃",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink3,
                        )
                    }
                    if (!overlayOn) {
                        SmallButton("去开启", true) { openOverlay(ctx) }
                    }
                }
            }
        }

        item { SectionHeader("产品类型", action = "＋ 新建", onAction = { onOpenType(null) }) }

        items(state.types, key = { it.id }) { t ->
            AppCard(onClick = { onOpenType(t.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            buildString {
                                append("${t.tests.size} 个测试项")
                                if (t.bakes.isNotEmpty()) {
                                    append(" · ")
                                    append(t.bakes.joinToString(" / ") { "${it.tempC}℃${it.minutes}分" })
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink3,
                        )
                    }
                    if (t.id != "other") {
                        IconAction(Icons.Filled.Delete, Ink3, "删除类型") { confirmDeleteType = t.id }
                    }
                    Icon(Icons.Filled.ArrowForward, null, tint = Ink3, modifier = Modifier.size(18.dp))
                }
            }
        }

        item { SectionHeader("烘箱", action = "＋ 新增", onAction = { showAddOven = true }) }

        item {
            AppCard {
                Text(
                    "一个烘箱同时只放一个样品。以后车间加新烘箱，在这里添上就行，计时和提醒会自动跟上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(10.dp))
                state.ovens.forEachIndexed { i, ov ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(ovenAccent(ov.colorIdx), 8)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ov.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                            val b = state.bakeInOven(ov.id)
                            Text(
                                if (b == null) "空闲" else "占用中 · ${Fmt.hm(b.endAt)} 出炉",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (b == null) Ink3 else ovenAccent(ov.colorIdx),
                            )
                        }
                        SmallButton("改名") { renameOvenTarget = ov.id }
                        Spacer(Modifier.width(6.dp))
                        IconAction(Icons.Filled.Delete, Ink3, "删除烘箱") { confirmDeleteOven = ov.id }
                    }
                }
            }
        }

        item { SectionHeader("客户", action = "＋ 添加", onAction = { showAddCustomer = true }) }

        item {
            AppCard {
                if (state.customers.isEmpty()) {
                    Text("还没有客户", style = MaterialTheme.typography.bodyMedium, color = Ink3)
                } else {
                    state.customers.forEachIndexed { i, c ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
                            SmallButton("改名") { renameTarget = c }
                            Spacer(Modifier.width(6.dp))
                            IconAction(Icons.Filled.Delete, Ink3, "删除客户") { Repo.I.removeCustomer(c) }
                        }
                    }
                }
            }
        }

        item { SectionHeader("数据") }

        item {
            AppCard {
                Text(
                    "数据保存在手机本地。换手机或重装前先导出备份，导入时把备份内容粘贴进来即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(SoftBg)
                            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) {
                                Share.json(
                                    ctx,
                                    "jiaoji-backup-${Fmt.md(System.currentTimeMillis())}.json",
                                    Repo.I.exportJson(),
                                )
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("导出备份", color = Ink2, style = MaterialTheme.typography.titleSmall) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(SoftBg)
                            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { showImport = true }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("导入备份", color = Ink2, style = MaterialTheme.typography.titleSmall) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "清空全部数据",
                    color = Danger,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { confirmClear = true }
                        .padding(vertical = 10.dp),
                )
            }
        }

        item { SectionHeader("关于") }

        item {
            AppCard(onClick = onOpenDiagnostics) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自检与诊断", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("点开", style = MaterialTheme.typography.bodySmall, color = Brand)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "响铃不响、点了没反应？这里能看到权限状态、已排定的闹钟时间和最近的动作记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("胶记", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("v" + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall, color = Ink3)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "取样 · 烘烤 · 检测。烘烤和取样都挂在系统闹钟上，App 退到后台甚至被划掉也会准时响铃；同时提供「系统倒计时」，可以把计时交给手机自带的时钟 App。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }
        }
    }

    if (showAddOven) {
        InputDialog(
            title = "新增烘箱",
            initial = "",
            label = "烘箱名称",
            onOk = { v ->
                Repo.I.addOven(v)
                showAddOven = false
            },
            onDismiss = { showAddOven = false },
        )
    }

    renameOvenTarget?.let { id ->
        val cur = state.ovenOf(id)?.name ?: ""
        InputDialog(
            title = "烘箱改名",
            initial = cur,
            label = "烘箱名称",
            onOk = { v ->
                Repo.I.renameOven(id, v)
                renameOvenTarget = null
            },
            onDismiss = { renameOvenTarget = null },
        )
    }

    confirmDeleteOven?.let { id ->
        val b = state.bakeInOven(id)
        AlertDialog(
            onDismissRequest = { confirmDeleteOven = null },
            title = { Text("删除这个烘箱？") },
            text = {
                Text(
                    when {
                        b != null -> "${state.ovenName(id)}里还放着样品，等它出炉后再删。"
                        state.ovens.size <= 1 -> "至少要留一个烘箱。"
                        else -> "删除后这个烘箱的计时记录不再显示，已出炉的记录会一起清掉。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Repo.I.removeOven(id)
                        confirmDeleteOven = null
                    },
                    enabled = b == null && state.ovens.size > 1,
                ) { Text("删除", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteOven = null }) { Text("取消") } },
        )
    }

    if (showAddCustomer) {
        InputDialog(
            title = "添加客户",
            initial = "",
            label = "客户名称",
            onOk = { v ->
                Repo.I.addCustomer(v)
                showAddCustomer = false
            },
            onDismiss = { showAddCustomer = false },
        )
    }

    renameTarget?.let { old ->
        InputDialog(
            title = "客户改名",
            initial = old,
            label = "客户名称",
            onOk = { v ->
                Repo.I.renameCustomer(old, v)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onOk = { text ->
                showImport = false
                Repo.I.importJson(text)
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部数据？") },
            text = { Text("样品、烘烤、记录都会被删除，不能恢复。建议先导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.clearAll()
                    confirmClear = false
                }) { Text("清空", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    confirmDeleteType?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteType = null },
            title = { Text("删除这个产品类型？") },
            text = { Text("已经取样记录的数据不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.deleteType(id)
                    confirmDeleteType = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteType = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { onChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Brand,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Line,
                uncheckedBorderColor = Line,
            ),
        )
    }
}

@Composable
fun InputDialog(
    title: String,
    initial: String,
    label: String,
    onOk: (String) -> Unit,
    onDismiss: () -> Unit = {},
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v,
                onValueChange = { v = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onOk(v.trim()) }, enabled = v.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入备份") },
        text = {
            Column {
                Text(
                    "把之前导出的备份内容粘贴到下面：",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = v,
                    onValueChange = { v = it },
                    placeholder = { Text("{\"settings\":…}", color = Ink3, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(v) }, enabled = v.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ToolRow(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        Text("›", color = Ink3, fontSize = 18.sp)
    }
}


@Composable
private fun PermRow(
    title: String,
    sub: String,
    bad: Boolean,
    action: String,
    always: Boolean = false,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (bad) Danger else Ink,
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        if (bad || always) SmallButton(action) { onClick() }
    }
}

private fun overlayOk(ctx: android.content.Context): Boolean = try {
    android.provider.Settings.canDrawOverlays(ctx)
} catch (_: Throwable) {
    true
}

private fun ignoreBattOk(ctx: android.content.Context): Boolean = try {
    val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    pm.isIgnoringBatteryOptimizations(ctx.packageName)
} catch (_: Throwable) {
    true
}
