package com.wulalala66.jiaoji

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TodayScreen(
    state: AppState,
    now: Long,
    onOpenOven: () -> Unit,
    onSampled: (String) -> Unit,
    onOpenSample: (String) -> Unit,
    onAddTask: () -> Unit,
) {
    val tasks = state.pendingToday(now)
    val overdue = state.overdueTasks(now)
    val samples = state.activeSamples()
    val doneToday = state.doneSamples().filter { it.sampledAt >= todayStart(now) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(now), style = MaterialTheme.typography.displaySmall, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${Fmt.md(now)} ${weekdayCn(now)} · ${Fmt.hm(now)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink3,
                        )
                    }
                    val running = state.activeBakes().size
                    if (running > 0) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(BrandSoft)
                                .clickable { onOpenOven() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("烘烤 $running", color = BrandDark, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val sub = when {
                    state.dueBakes().isNotEmpty() -> "有烘烤到点了，先去出炉"
                    overdue.isNotEmpty() -> "有 ${overdue.size} 个取样已到时间"
                    tasks.isNotEmpty() -> "今天 ${tasks.size} 个取样，最近 ${Fmt.hm(tasks.first().dueAt)}"
                    samples.isNotEmpty() -> "${samples.size} 个样品待录入数据"
                    else -> "今天还没有取样任务"
                }
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = Ink2)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OvenTile(state, "door", now, Modifier.weight(1f), onOpenOven)
                OvenTile(state, "window", now, Modifier.weight(1f), onOpenOven)
            }
        }

        item {
            SectionHeader("取样提醒", action = "添加", onAction = onAddTask, trailing = if (tasks.isEmpty()) null else "${tasks.size} 个")
        }

        if (tasks.isEmpty()) {
            item {
                AppCard {
                    EmptyHint("🧪", "今天没有待取样", "车间发时间后，点右下角 + 添加，或用一句话：8点半 安徽万安 底涂胶")
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    state = state,
                    task = task,
                    now = now,
                    onSampled = { onSampled(task.id) },
                    onDelete = { confirmDelete = task.id },
                )
            }
        }

        item {
            SectionHeader("进行中的样品", trailing = if (samples.isEmpty()) null else "${samples.size} 个")
        }

        if (samples.isEmpty()) {
            item {
                AppCard {
                    EmptyHint("📋", "还没有在测的样品", "取样后点「已取样」就会自动建档，按产品类型带出测试项")
                }
            }
        } else {
            items(samples, key = { it.id }) { s ->
                SampleRow(state, s, onOpenSample)
            }
        }

        if (doneToday.isNotEmpty()) {
            item { SectionHeader("今天已完成", trailing = "${doneToday.size} 个") }
            items(doneToday, key = { it.id }) { s ->
                SampleRow(state, s, onOpenSample, dim = true)
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这个取样任务？") },
            text = { Text("删除后不会影响已建的样品档案。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.deleteSampling(id)
                    confirmDelete = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OvenTile(
    state: AppState,
    oven: String,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val window = oven == "window"
    val accent = if (window) OvenWindow else OvenDoor
    val soft = if (window) OvenWindowSoft else OvenDoorSoft
    val list = state.runningBakes(oven)
    val first = list.firstOrNull()
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(soft)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent, 7)
            Spacer(Modifier.width(6.dp))
            Text(ovenName(oven), style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Spacer(Modifier.height(8.dp))
        if (first == null) {
            Text("空闲", style = MaterialTheme.typography.titleMedium, color = Ink2)
            Text("点一下开计时", style = MaterialTheme.typography.bodySmall, color = Ink3)
        } else {
            CountdownText((first.endAt - now).coerceAtLeast(0), accent, 20)
            Spacer(Modifier.height(2.dp))
            Text(
                first.label.ifBlank { "${first.tempC}℃" },
                style = MaterialTheme.typography.bodySmall,
                color = Ink2,
                maxLines = 1,
            )
            if (list.size > 1) {
                Spacer(Modifier.height(2.dp))
                Text("还有 ${list.size - 1} 个", style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
    }
}

@Composable
private fun TaskCard(
    state: AppState,
    task: SamplingTask,
    now: Long,
    onSampled: () -> Unit,
    onDelete: () -> Unit,
) {
    val left = task.dueAt - now
    val late = left <= 0
    AppCard(
        color = if (late) DangerSoft else CardBg,
        borderColor = if (late) Color(0x33BD4632) else Line,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Fmt.hm(task.dueAt),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (late) Danger else Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (late) Pill("已到点", Color.White, Danger)
                    else Pill("${(left / 60000).coerceAtLeast(1)} 分钟后", Ink2, SoftBg)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    task.customer.ifBlank { "未填客户" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(state.typeName(task.typeId), BrandDark, BrandSoft)
                    if (task.note.isNotBlank()) Pill(task.note, Ink2, SoftBg)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Brand)
                        .clickable { onSampled() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("已取样", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                IconAction(Icons.Filled.Delete, Ink3, "删除") { onDelete() }
            }
        }
    }
}

@Composable
fun SampleRow(state: AppState, s: Sample, onOpen: (String) -> Unit, dim: Boolean = false) {
    val stats = statsOf(s)
    AppCard(modifier = Modifier.clickable { onOpen(s.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.customer.ifBlank { "未填客户" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dim) Ink2 else Ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    Pill(state.typeName(s.typeId), BrandDark, BrandSoft)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${s.batchNo} · ${Fmt.full(s.sampledAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(8.dp))
                ProgressBar(
                    fraction = if (stats.total == 0) 0f else stats.done.toFloat() / stats.total,
                    color = if (stats.bad > 0) Danger else Brand,
                    height = 5,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${stats.done}/${stats.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (stats.bad > 0) Danger else Ink2,
                )
                if (stats.bad > 0) {
                    Text("${stats.bad} 项不合格", style = MaterialTheme.typography.labelSmall, color = Danger)
                } else if (s.status == "done") {
                    Text("已完成", style = MaterialTheme.typography.labelSmall, color = Ok)
                } else {
                    Text("待录入", style = MaterialTheme.typography.labelSmall, color = Ink3)
                }
            }
        }
    }
}

@Composable
fun SampleBakesSection(state: AppState, sampleId: String, now: Long, onBake: () -> Unit) {
    val list = state.bakes.filter { it.sampleId == sampleId }
    SectionHeader("烘烤", action = "放烘箱", onAction = onBake)
    if (list.isEmpty()) {
        AppCard { Text("这个样品还没有烘烤记录", style = MaterialTheme.typography.bodyMedium, color = Ink3) }
    } else {
        list.forEach { b ->
            BakeRow(state, b, now)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun BakeRow(state: AppState, b: Bake, now: Long) {
    val left = b.endAt - now
    val running = b.status == "running" || b.status == "due"
    val accent = if (b.oven == "window") OvenWindow else OvenDoor
    AppCard(borderColor = if (b.status == "due") Color(0x55BD4632) else Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OvenPill(b.oven)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${b.tempC}℃ × ${Fmt.dur(b.minutes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ink2,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (running) {
                    CountdownText(left.coerceAtLeast(0), if (b.status == "due") Danger else accent, 22)
                } else {
                    Text("已完成 · ${Fmt.hm(b.doneAt ?: b.endAt)} 取出", style = MaterialTheme.typography.titleMedium, color = Ok)
                }
                if (b.label.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(b.label, style = MaterialTheme.typography.bodySmall, color = Ink3, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (running) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallButton("+5分") { Repo.I.extendBake(b.id, 5) }
                        SmallButton(if (b.status == "due") "已取出" else "取出", filled = true) { Repo.I.markBakeDone(b.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun SmallButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Brand else SoftBg)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = if (filled) Color.White else Ink2,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
