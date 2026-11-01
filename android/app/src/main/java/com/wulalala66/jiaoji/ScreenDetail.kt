package com.wulalala66.jiaoji

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DetailScreen(
    state: AppState,
    now: Long,
    sampleId: String,
    onBack: () -> Unit,
    onBake: () -> Unit,
) {
    val sample = state.sampleOf(sampleId)
    if (sample == null) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("样品不存在或已删除", color = Ink2)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }
    val stats = statsOf(sample)
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val ctx = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sample.customer.ifBlank { "未填客户" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                        maxLines = 1,
                    )
                    Text(
                        "${state.typeName(sample.typeId)} · ${sample.batchNo} · ${Fmt.hm(sample.sampledAt)} 取样",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                }
                if (sample.status == "done") Pill("已完成", Ok, OkSoft)
                IconAction(Icons.Filled.Share, Ink3, "分享本样品数据") {
                    Share.text(
                        ctx,
                        "${sample.customer} ${state.typeName(sample.typeId)} ${sample.batchNo}",
                        Share.reportOf(state, listOf(sample)),
                    )
                }
                IconAction(Icons.Filled.Delete, Ink3, "删除样品") { confirmDelete = true }
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("录入进度", style = MaterialTheme.typography.labelMedium, color = Ink3)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${stats.done}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Ink,
                            )
                            Text(
                                " / ${stats.total} 项",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink3,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                            if (stats.bad > 0) {
                                Spacer(Modifier.width(10.dp))
                                Pill("${stats.bad} 项不合格", Color.White, Danger)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (stats.bad > 0) DangerSoft else BrandSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${stats.pct}%",
                            color = if (stats.bad > 0) Danger else BrandDark,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(
                    fraction = if (stats.total == 0) 0f else stats.done.toFloat() / stats.total,
                    color = if (stats.bad > 0) Danger else Brand,
                )
            }
        }

        item { SectionHeader("检测数据", action = "＋ 添加", onAction = { showAdd = true }) }

        if (sample.tests.isEmpty()) {
            item { AppCard { EmptyHint("🧾", "没有测试项", "点右上「＋ 添加」加一个，或在设置里给这个产品类型配好默认测试项") } }
        }

        items(sample.tests.size, key = { sample.tests[it].id }) { index ->
            val t = sample.tests[index]
            TestRow(
                test = t,
                prev = state.lastValueFor(sample, t.name),
                onValue = { Repo.I.setTestValue(sample.id, t.id, it) },
                onSkip = { Repo.I.toggleTestSkip(sample.id, t.id) },
                onRemove = { Repo.I.removeTest(sample.id, t.id) },
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )
        }

        item { SampleBakesSection(state, sample.id, now, onBake) }

        item {
            AppCard {
                Text("备注", style = MaterialTheme.typography.labelMedium, color = Ink3)
                Spacer(Modifier.height(6.dp))
                val note = rememberLocalText(sample.note, sample.id, AppConfig.debounceMs) { Repo.I.setSampleNote(sample.id, it) }
                OutlinedTextField(
                    value = note.value,
                    onValueChange = { note.value = it },
                    placeholder = { Text("外观、异常、车间反馈…", color = Ink3, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            if (sample.status == "done") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(SoftBg)
                        .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { Repo.I.reopenSample(sample.id) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("重新打开继续录入", color = Ink2, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(Brand)
                        .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { confirmComplete = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("完成检测", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showAdd) {
        AddTestDialog(
            type = state.typeOf(sample.typeId),
            existing = sample.tests.map { it.name },
            onDismiss = { showAdd = false },
            onAdd = { name, unit, min, max ->
                Repo.I.addTest(sample.id, name, unit, min, max)
                showAdd = false
            },
        )
    }

    if (confirmComplete) {
        val empty = sample.tests.count { verdictOf(it) == Verdict.EMPTY }
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text("完成检测？") },
            text = {
                Text(
                    if (empty > 0) "还有 $empty 项没录入，完成后可以在记录里重新打开。"
                    else "数据已全部录入，完成后面板会归档到记录。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.completeSample(sample.id)
                    confirmComplete = false
                    onBack()
                }) { Text("完成") }
            },
            dismissButton = { TextButton(onClick = { confirmComplete = false }) { Text("再看看") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个样品？") },
            text = { Text("样品和它的烘烤记录都会被删除，不能恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.deleteSample(sample.id)
                    confirmDelete = false
                    onBack()
                }) { Text("删除", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    unfocusedBorderColor = Line,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = Brand,
)

@Composable
private fun TestRow(
    test: SampleTest,
    prev: String? = null,
    onValue: (String) -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
    onNext: () -> Unit,
) {
    val text = rememberLocalText(test.value, test.id, AppConfig.debounceMs) { onValue(it) }
    val v = verdictOf(test)
    AppCard(
        borderColor = when (v) {
            Verdict.FAIL -> Color(0x66BD4632)
            Verdict.PASS -> Color(0x332C7A5B)
            else -> Line
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(test.name, style = MaterialTheme.typography.titleSmall, color = Ink)
            if (test.unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(test.unit, style = MaterialTheme.typography.labelSmall, color = Ink3)
            }
            Spacer(Modifier.weight(1f))
            if (test.min.isNotBlank() || test.max.isNotBlank()) {
                Text(
                    "标准 ${test.min.ifBlank { "—" }}~${test.max.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink3,
                )
                Spacer(Modifier.width(6.dp))
            }
            VerdictPill(test)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (test.skipped) "" else text.value,
                onValueChange = { text.value = it },
                placeholder = {
                    Text(
                        when {
                            test.skipped -> "已跳过"
                            !prev.isNullOrBlank() -> "上批 $prev"
                            else -> "输入数值"
                        },
                        color = Ink3,
                        fontSize = 13.sp,
                    )
                },
                singleLine = true,
                enabled = !test.skipped,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { st ->
                        if (!st.isFocused && text.value != test.value) onValue(text.value)
                    },
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.width(6.dp))
            IconAction(Icons.Filled.Close, Ink3, "跳过") { onSkip() }
            IconAction(Icons.Filled.Delete, Ink3, "删除项") { onRemove() }
        }
    }
}

@Composable
private fun AddTestDialog(
    type: ProductType?,
    existing: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    val common = (type?.tests ?: emptyList()).filter { it.name !in existing }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加测试项") },
        text = {
            Column {
                if (common.isNotEmpty()) {
                    Text("这个类型的常用项", style = MaterialTheme.typography.labelMedium, color = Ink3)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        common.take(3).forEach { d ->
                            ChoiceChip(d.name, false) { onAdd(d.name, d.unit, d.min, d.max) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("单位（可空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { min = it },
                        label = { Text("标准下限") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = max,
                        onValueChange = { max = it },
                        label = { Text("上限") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, unit, min, max) }, enabled = name.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
