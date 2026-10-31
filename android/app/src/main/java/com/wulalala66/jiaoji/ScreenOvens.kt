package com.wulalala66.jiaoji

import android.provider.AlarmClock
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OvensScreen(
    state: AppState,
    now: Long,
    onNewBake: (String) -> Unit,
    onOpenSample: (String) -> Unit,
) {
    var oven by remember { mutableStateOf("door") }
    val window = oven == "window"
    val accent = if (window) OvenWindow else OvenDoor
    val list = state.runningBakes(oven)
    val recent = state.recentBakes(now - 12 * 3_600_000L).filter { it.oven == oven }
    val ctx = LocalContext.current
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("烘箱", style = MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("两个烘箱分开计时，到点会响铃提醒", style = MaterialTheme.typography.bodyMedium, color = Ink3)
            }
        }

        item {
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OvenSwitch("靠门烘箱", oven == "door", OvenDoor) { oven = "door" }
                OvenSwitch("靠窗烘箱", oven == "window", OvenWindow) { oven = "window" }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent)
                    .clickable { onNewBake(oven) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "＋ 在${ovenName(oven)}放一个样品",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (list.isEmpty()) {
            item {
                AppCard {
                    EmptyHint(
                        "⏱",
                        "${ovenName(oven)}现在空着",
                        "放进去后点上面的按钮开始计时，到点手机响铃提醒",
                    )
                }
            }
        } else {
            items(list, key = { it.id }) { b ->
                TimerCard(
                    state = state,
                    b = b,
                    now = now,
                    onOpenSample = onOpenSample,
                    onSysTimer = {
                        val secs = ((b.endAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(10)
                        startSystemTimer(ctx, secs, "${b.label} ${b.tempC}℃")
                    },
                    onRemove = { confirmRemove = b.id },
                )
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader("刚出炉", trailing = "${recent.size} 个") }
            items(recent, key = { "done-" + it.id }) { b ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.label.ifBlank { "烘烤样品" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink2,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${b.tempC}℃ × ${Fmt.dur(b.minutes)} · ${Fmt.hm(b.doneAt ?: b.endAt)} 取出 · ${Fmt.ago(b.doneAt ?: b.endAt, now)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink3,
                            )
                        }
                        val sid = b.sampleId
                        if (sid != null) {
                            SmallButton("看样品") { onOpenSample(sid) }
                        }
                    }
                }
            }
        }
    }

    confirmRemove?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("删除这个计时？") },
            text = { Text("删除后到点不会再提醒。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.removeBake(id)
                    confirmRemove = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun OvenSwitch(text: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) accent else CardBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (active) Color.White else Ink2,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TimerCard(
    state: AppState,
    b: Bake,
    now: Long,
    onOpenSample: (String) -> Unit,
    onSysTimer: () -> Unit,
    onRemove: () -> Unit,
) {
    val window = b.oven == "window"
    val accent = if (window) OvenWindow else OvenDoor
    val due = b.status == "due"
    val total = (b.endAt - b.startAt).coerceAtLeast(1)
    val passed = (now - b.startAt).coerceAtLeast(0)
    val fraction = (passed.toFloat() / total).coerceIn(0f, 1f)
    val left = b.endAt - now

    AppCard(borderColor = if (due) Color(0x55BD4632) else Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OvenPill(b.oven)
            Spacer(Modifier.width(6.dp))
            Text("${b.tempC}℃ × ${Fmt.dur(b.minutes)}", style = MaterialTheme.typography.labelMedium, color = Ink2)
            Spacer(Modifier.weight(1f))
            Text(
                if (due) "到点了" else "计时中",
                style = MaterialTheme.typography.labelMedium,
                color = if (due) Danger else accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            CountdownText(left.coerceAtLeast(0), if (due) Danger else Ink, 34)
            Spacer(Modifier.weight(1f))
            Text("预计 ${Fmt.hm(b.endAt)} 出炉", style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(fraction = fraction, color = if (due) Danger else accent, height = 6)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                b.label.ifBlank { "烘烤样品" },
                style = MaterialTheme.typography.titleSmall,
                color = Ink2,
                modifier = Modifier.weight(1f),
            )
            val sid = b.sampleId
            if (sid != null) {
                SmallButton("看样品") { onOpenSample(sid) }
                Spacer(Modifier.width(6.dp))
            }
            SmallButton("删除") { onRemove() }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip("已取出", true) { Repo.I.markBakeDone(b.id) }
            ActionChip("+5 分钟") { Repo.I.extendBake(b.id, 5) }
            ActionChip("延后 2 分") { Repo.I.snoozeBake(b.id, 2) }
            ActionChip("系统倒计时", onClick = onSysTimer)
        }
    }
}

@Composable
private fun ActionChip(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Brand else SoftBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (filled) Color.White else Ink2,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

fun startSystemTimer(ctx: Context, seconds: Int, label: String) {
    try {
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    } catch (e: Exception) {
        // 部分机型没有时钟 App，忽略
    }
}
