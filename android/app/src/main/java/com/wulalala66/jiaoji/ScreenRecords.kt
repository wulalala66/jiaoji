package com.wulalala66.jiaoji

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RecordsScreen(state: AppState, now: Long, onOpenSample: (String) -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var badOnly by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val from = when (mode) {
        0 -> todayStart(now)
        1 -> todayStart(now) - 6 * 86_400_000L
        else -> 0L
    }
    val list = state.samples
        .filter { it.sampledAt >= from }
        .filter { s ->
            query.isBlank() ||
                s.customer.contains(query, true) ||
                state.typeName(s.typeId).contains(query, true) ||
                s.batchNo.contains(query, true) ||
                s.note.contains(query, true)
        }
        .filter { s -> !badOnly || s.tests.any { verdictOf(it) == Verdict.FAIL } }
        .sortedByDescending { it.sampledAt }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("记录", style = MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("查历史数据、导 Excel、生成日报", style = MaterialTheme.typography.bodyMedium, color = Ink3)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegItem("今天", mode == 0) { mode = 0 }
                SegItem("近 7 天", mode == 1) { mode = 1 }
                SegItem("全部", mode == 2) { mode = 2 }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜客户、类型、批次、备注", color = Ink3, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip("只看不合格", badOnly) { badOnly = !badOnly }
                Spacer(Modifier.weight(1f))
                Text("${list.size} 个样品", style = MaterialTheme.typography.labelMedium, color = Ink3)
            }
        }

        if (list.isEmpty()) {
            item { AppCard { EmptyHint("🗂", "这里还没有记录", "取样并录入数据后，样品会自动归档到这里") } }
        }

        items(list, key = { it.id }) { s ->
            val st = statsOf(s)
            AppCard(onClick = { onOpenSample(s.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.customer.ifBlank { "未填客户" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                state.typeName(s.typeId),
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink3,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${Fmt.md(s.sampledAt)} ${Fmt.hm(s.sampledAt)} · ${s.batchNo} · ${st.done}/${st.total} 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink3,
                        )
                    }
                    when {
                        st.bad > 0 -> Pill("${st.bad} 不合格", Color.White, Danger)
                        s.status == "done" -> Pill("已完成", Ok, OkSoft)
                        else -> Pill("进行中", Ink2, SoftBg)
                    }
                }
                if (s.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(s.note, style = MaterialTheme.typography.bodySmall, color = Ink2, maxLines = 2)
                }
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WideButton("导出 CSV") {
                    if (list.isNotEmpty()) {
                        Share.csv(ctx, "jiaoji-${Fmt.md(System.currentTimeMillis())}.csv", Share.csvOf(state, list))
                    }
                }
                WideButton("复制日报") {
                    if (list.isNotEmpty()) {
                        val txt = Share.reportOf(state, list)
                        Share.text(ctx, "检测日报", txt)
                    }
                }
            }
        }
    }
}

@Composable
private fun SegItem(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Brand else CardBg)
            .sizeIn(minHeight = 40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (active) Color.White else Ink2,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RowScope.WideButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(SoftBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink2, style = MaterialTheme.typography.titleSmall)
    }
}
