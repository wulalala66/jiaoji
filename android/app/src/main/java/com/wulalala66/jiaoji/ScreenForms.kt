package com.wulalala66.jiaoji

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private fun openDatePicker(ctx: Context, current: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            val n = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
            }
            onPick(n.timeInMillis)
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun openTimePicker(ctx: Context, current: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = current }
    TimePickerDialog(
        ctx,
        { _, h, m ->
            val n = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
            }
            onPick(n.timeInMillis)
        },
        c.get(Calendar.HOUR_OF_DAY),
        c.get(Calendar.MINUTE),
        true,
    ).show()
}

private fun withDay(base: Long, dayOffset: Int): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = base
        add(Calendar.DAY_OF_MONTH, dayOffset)
    }
    return c.timeInMillis
}

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
    }
}

/* ---------------- 新建取样任务 ---------------- */

@Composable
fun AddTaskScreen(state: AppState, onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var raw by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var typeId by remember { mutableStateOf(state.types.firstOrNull()?.id ?: "other") }
    var dueAt by remember { mutableStateOf(System.currentTimeMillis() + 3_600_000L) }
    var note by remember { mutableStateOf("") }

    val parsed = remember(raw) { QuickParser.parse(raw, state.customers, state.types, System.currentTimeMillis()) }

    ScreenScaffold("新建取样任务", onBack) {
        AppCard {
            Text("一句话添加", style = MaterialTheme.typography.labelMedium, color = Ink3)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                placeholder = {
                    Text("8点半 安徽万安 底涂胶", color = Ink3, fontSize = 14.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
            if (parsed.ready) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val parts = buildList {
                        parsed.timeMs?.let { add(Fmt.hm(it)) }
                        if (!parsed.customer.isNullOrBlank()) add(parsed.customer!!)
                        if (parsed.typeId != null) add(state.typeName(parsed.typeId))
                    }
                    parts.forEach { p ->
                        Pill(p, BrandDark, BrandSoft)
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    SmallButton("填入") {
                        parsed.timeMs?.let { dueAt = it }
                        if (!parsed.customer.isNullOrBlank()) customer = parsed.customer!!
                        parsed.typeId?.let { typeId = it }
                        if (parsed.note.isNotBlank()) note = parsed.note
                    }
                }
            } else if (raw.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("没识别到时间，下面手动选一个", style = MaterialTheme.typography.bodySmall, color = Ink3)
            }
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            Text("客户", style = MaterialTheme.typography.labelMedium, color = Ink3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.customers.take(4).forEach { c ->
                    ChoiceChip(c, customer == c) { customer = if (customer == c) "" else c }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                placeholder = { Text("客户 / 车间（可空）", color = Ink3, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            Text("胶的类型", style = MaterialTheme.typography.labelMedium, color = Ink3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.types.forEach { t ->
                    ChoiceChip(t.name, typeId == t.id) { typeId = t.id }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            Text("取样时间", style = MaterialTheme.typography.labelMedium, color = Ink3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("今天", false) { dueAt = withDay(dueAt, 0) }
                ChoiceChip("明天", false) { dueAt = withDay(dueAt, 1) }
                ChoiceChip("后天", false) { dueAt = withDay(dueAt, 2) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(SoftBg)
                        .clickable { openDatePicker(ctx, dueAt) { dueAt = it } }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${Fmt.md(dueAt)} ${weekdayCn(dueAt)}", color = Ink, style = MaterialTheme.typography.titleSmall)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(Brand)
                        .clickable { openTimePicker(ctx, dueAt) { dueAt = it } }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Fmt.hm(dueAt), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(8, 9, 10, 13, 14).forEach { h ->
                    ChoiceChip("${h}:00", false) {
                        dueAt = Calendar.getInstance().apply {
                            timeInMillis = dueAt
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, 0)
                        }.timeInMillis
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("备注（可空）", color = Ink3, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Brand)
                .clickable {
                    Repo.I.addSampling(customer, typeId, dueAt, note)
                    onSaved()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("保存提醒", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- 放进烘箱 ---------------- */

@Composable
fun BakeFormScreen(
    state: AppState,
    bakeId: String?,
    sampleId: String?,
    defaultOven: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val ctx = LocalContext.current
    val editing = bakeId?.let { state.bakes.firstOrNull { b -> b.id == it } }
    val sample = sampleId?.let { state.sampleOf(it) }
    val type = sample?.let { state.typeOf(it.typeId) }

    var oven by remember { mutableStateOf(editing?.oven ?: defaultOven) }
    var temp by remember {
        mutableStateOf((editing?.tempC ?: type?.bakes?.firstOrNull()?.tempC ?: 140).toString())
    }
    var minutes by remember {
        mutableStateOf((editing?.minutes ?: type?.bakes?.firstOrNull()?.minutes ?: 30).toString())
    }
    var startAt by remember { mutableStateOf(editing?.startAt ?: System.currentTimeMillis()) }
    var label by remember {
        mutableStateOf(
            editing?.label
                ?: listOfNotNull(sample?.customer, type?.name).joinToString(" ").ifBlank { "烘烤样品" }
        )
    }

    val tempInt = temp.toIntOrNull() ?: 0
    val minInt = minutes.toIntOrNull() ?: 0
    val ok = tempInt in 20..400 && minInt in 1..1440

    ScreenScaffold(if (editing == null) "放进烘箱" else "修改计时", onBack) {
        if (sample != null) {
            AppCard {
                Text(
                    "${sample.customer} · ${state.typeName(sample.typeId)} · ${sample.batchNo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink2,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        AppCard {
            Text("哪个烘箱", style = MaterialTheme.typography.labelMedium, color = Ink3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("靠门烘箱", oven == "door") { oven = "door" }
                ChoiceChip("靠窗烘箱", oven == "window") { oven = "window" }
            }
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = temp,
                    onValueChange = { temp = it.filter { c -> c.isDigit() } },
                    label = { Text("温度 ℃") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                    label = { Text("时长 分钟") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (!type?.bakes.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("这个产品的常用条件", style = MaterialTheme.typography.labelSmall, color = Ink3)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    type!!.bakes.forEach { p ->
                        ChoiceChip("${p.tempC}℃ ${p.minutes}分", tempInt == p.tempC && minInt == p.minutes) {
                            temp = p.tempC.toString()
                            minutes = p.minutes.toString()
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(140, 150, 160, 180).forEach { t ->
                    ChoiceChip("$t℃", tempInt == t) { temp = t.toString() }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20, 30, 45, 60).forEach { m ->
                    ChoiceChip("$m 分", minInt == m) { minutes = m.toString() }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            Text("开始时间", style = MaterialTheme.typography.labelMedium, color = Ink3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(SoftBg)
                        .clickable {
                            startAt = Calendar.getInstance().apply {
                                timeInMillis = startAt
                                set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                                set(Calendar.MINUTE, Calendar.getInstance().get(Calendar.MINUTE))
                                set(Calendar.SECOND, 0)
                            }.timeInMillis
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("现在开始", color = Ink, style = MaterialTheme.typography.titleSmall) }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(SoftBg)
                        .clickable { openTimePicker(ctx, startAt) { startAt = it } }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(Fmt.hm(startAt), color = Ink, style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (ok) "预计 ${Fmt.hm(startAt + minInt * 60_000L)} 出炉" else "温度或时长填一下",
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
            )
        }

        Spacer(Modifier.height(10.dp))
        AppCard {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("标签") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (ok) Brand else Line)
                .clickable(enabled = ok) {
                    if (editing == null) {
                        Repo.I.addBake(oven, sample?.id, label.trim(), tempInt, minInt, startAt)
                    } else {
                        Repo.I.updateBake(editing.id, oven, label.trim(), tempInt, minInt, startAt, sample?.id)
                    }
                    onSaved()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (editing == null) "开始计时" else "保存修改",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (editing != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                Repo.I.removeBake(editing.id)
                onSaved()
            }) { Text("删除这个计时", color = Danger) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- 产品类型编辑 ---------------- */

@Composable
fun TypeEditorScreen(state: AppState, typeId: String?, onBack: () -> Unit) {
    val existing = typeId?.let { id -> state.types.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var bakes by remember { mutableStateOf(existing?.bakes ?: emptyList()) }
    var tests by remember { mutableStateOf(existing?.tests ?: emptyList()) }
    var newBake by remember { mutableStateOf(false) }
    var newTest by remember { mutableStateOf(false) }

    ScreenScaffold(if (existing == null) "新建产品类型" else "编辑产品类型", onBack) {
        AppCard {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("类型名称") },
                placeholder = { Text("例如 底涂胶", color = Ink3, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader("常用烘烤条件", action = "＋ 添加", onAction = { newBake = true })
        AppCard {
            if (bakes.isEmpty()) {
                Text("还没有预设，添加后放烘箱时一键选", style = MaterialTheme.typography.bodySmall, color = Ink3)
            }
            bakes.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${p.tempC}℃ × ${p.minutes} 分钟",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(Icons.Filled.Close, Ink3, "删除") { bakes = bakes.filterIndexed { j, _ -> j != i } }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader("默认测试项", action = "＋ 添加", onAction = { newTest = true })
        AppCard {
            if (tests.isEmpty()) {
                Text("还没有测试项", style = MaterialTheme.typography.bodySmall, color = Ink3)
            }
            tests.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (t.unit.isBlank()) t.name else "${t.name}（${t.unit}）",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                        )
                        if (t.min.isNotBlank() || t.max.isNotBlank()) {
                            Text(
                                "标准 ${t.min.ifBlank { "—" }}~${t.max.ifBlank { "—" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink3,
                            )
                        }
                    }
                    IconAction(Icons.Filled.Close, Ink3, "删除") { tests = tests.filterIndexed { j, _ -> j != i } }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (name.isBlank()) Line else Brand)
                .clickable(enabled = name.isNotBlank()) {
                    Repo.I.upsertType(
                        ProductType(
                            id = existing?.id ?: ("t_" + Repo.uid()),
                            name = name.trim(),
                            bakes = bakes,
                            tests = tests,
                        )
                    )
                    onBack()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("保存", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(24.dp))
    }

    if (newBake) {
        var t by remember { mutableStateOf("140") }
        var m by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { newBake = false },
            title = { Text("添加烘烤条件") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = t,
                        onValueChange = { t = it.filter { c -> c.isDigit() } },
                        label = { Text("温度 ℃") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = m,
                        onValueChange = { m = it.filter { c -> c.isDigit() } },
                        label = { Text("分钟") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ti = t.toIntOrNull()
                    val mi = m.toIntOrNull()
                    if (ti != null && mi != null) bakes = bakes + BakePreset(ti, mi)
                    newBake = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { newBake = false }) { Text("取消") } },
        )
    }

    if (newTest) {
        var n by remember { mutableStateOf("") }
        var u by remember { mutableStateOf("") }
        var mn by remember { mutableStateOf("") }
        var mx by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newTest = false },
            title = { Text("添加测试项") },
            text = {
                Column {
                    OutlinedTextField(
                        value = n,
                        onValueChange = { n = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = u,
                        onValueChange = { u = it },
                        label = { Text("单位（可空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mn,
                            onValueChange = { mn = it },
                            label = { Text("下限") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = mx,
                            onValueChange = { mx = it },
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
                TextButton(onClick = {
                    if (n.isNotBlank()) {
                        tests = tests + TestDef("t_" + Repo.uid(), n.trim(), u.trim(), mn.trim(), mx.trim())
                    }
                    newTest = false
                }, enabled = n.isNotBlank()) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { newTest = false }) { Text("取消") } },
        )
    }
}

/* ---------------- 已取样确认 ---------------- */

@Composable
fun SampledDialog(
    state: AppState,
    task: SamplingTask,
    onDismiss: () -> Unit,
    onConfirm: (withBake: Boolean, oven: String, tempC: Int, minutes: Int) -> Unit,
) {
    val type = state.typeOf(task.typeId)
    var withBake by remember { mutableStateOf(true) }
    var oven by remember { mutableStateOf("door") }
    var temp by remember { mutableStateOf((type?.bakes?.firstOrNull()?.tempC ?: 0).toString()) }
    var minutes by remember { mutableStateOf((type?.bakes?.firstOrNull()?.minutes ?: 0).toString()) }
    val hasPreset = !type?.bakes.isNullOrEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已取样") },
        text = {
            Column {
                Text(
                    "${task.customer.ifBlank { "未填客户" }} · ${state.typeName(task.typeId)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "批次号 ${state.previewBatchNo(task.typeId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChip("同时放进烘箱", withBake) { withBake = !withBake }
                }
                if (withBake) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("靠门", oven == "door") { oven = "door" }
                        ChoiceChip("靠窗", oven == "window") { oven = "window" }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (hasPreset) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            type!!.bakes.forEach { p ->
                                ChoiceChip(
                                    "${p.tempC}℃ ${p.minutes}分",
                                    temp == p.tempC.toString() && minutes == p.minutes.toString(),
                                ) {
                                    temp = p.tempC.toString()
                                    minutes = p.minutes.toString()
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = temp,
                                onValueChange = { temp = it.filter { c -> c.isDigit() } },
                                label = { Text("温度 ℃") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = fieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                                label = { Text("分钟") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = fieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ti = temp.toIntOrNull() ?: 0
                val mi = minutes.toIntOrNull() ?: 0
                onConfirm(withBake && ti > 0 && mi > 0, oven, ti, mi)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
