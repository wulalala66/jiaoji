package com.wulalala66.jiaoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun f1(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun f2(v: Double): String = String.format(Locale.US, "%.2f", v)

private fun Formula.restLabel(): String =
    "还差 ${f1(restPercent())}%（${f1(restGrams())} g）"

/* ---------------- 配料列表 ---------------- */

@Composable
fun FormulasScreen(state: AppState, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("配料", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Filled.Add, Brand, "新建配方") {
                onOpen(Repo.I.addFormula("新配方"))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.formulas, key = { it.id }) { f ->
                AppCard(onClick = { onOpen(f.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            f.name.ifBlank { "未命名" },
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Pill("${f1(f.totalG)} g", Ink2, SoftBg)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${f.items.size} 种原料 · 合计 ${f2(f.sumPercent())}%" +
                            if (f.sumPercent() < 99.95) " · " + f.restLabel() else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (f.sumPercent() < 99.95) Warn else Ink3,
                    )
                }
            }
            if (state.formulas.isEmpty()) {
                item { EmptyHint("⚗️", "还没有配方", "点右上角 ＋ 建一个，比如「底涂胶 A 料」") }
            }
            item {
                Text(
                    "一锅按 100% 算：输克数或百分比都行，另一边自动换算；剩余量可以一键补到某一行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/* ---------------- 配料编辑器 ---------------- */

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun FormulaEditorScreen(state: AppState, formulaId: String, onBack: () -> Unit) {
    val f = state.formulaOf(formulaId)
    if (f == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("这条配方已经不在了", color = Ink2)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("返回", color = Brand) }
        }
        return
    }

    var confirmDelete by remember { mutableStateOf(false) }
    val nameState = rememberLocalText(f.name, f.id) { n -> Repo.I.updateFormula(f.id) { it.name = n } }
    var totalText by remember(f.id) { mutableStateOf(f1(f.totalG)) }
    LaunchedEffect(f.totalG) {
        if (f.totalG > 0 && totalText.trim().toDoubleOrNull() != f.totalG) totalText = f1(f.totalG)
    }
    val total = if (f.totalG > 0) f.totalG else 100.0
    val rest = f.restPercent()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("配料", style = MaterialTheme.typography.titleLarge, color = Ink)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = nameState.value,
                onValueChange = { nameState.value = it },
                placeholder = { Text("配方名", style = MaterialTheme.typography.bodyMedium, color = Ink3) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                colors = fieldColors(),
            )

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("一锅总量", style = MaterialTheme.typography.labelSmall, color = Ink3)
                        Text(
                            "${f1(f.totalG)} g",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("合计", style = MaterialTheme.typography.labelSmall, color = Ink3)
                        Text(
                            "${f2(f.sumPercent())}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (f.sumPercent() > 100.05) Danger
                            else if (f.sumPercent() < 99.95) Warn else Ok,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumField(
                        value = totalText,
                        onValueChange = { s ->
                            totalText = s
                            s.trim().toDoubleOrNull()?.let { v -> if (v > 0) Repo.I.setFormulaTotal(f.id, v) }
                        },
                        label = "改总量（g）",
                        modifier = Modifier.weight(1f),
                        imeNext = false,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (rest > 0.05) f.restLabel()
                        else if (rest < -0.05) "超了 ${f1(-rest)}%" else "刚好 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rest > 0.05) Warn else if (rest < -0.05) Danger else Ok,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader("原料")
            Spacer(Modifier.height(4.dp))

            AppCard {
                f.items.forEach { item ->
                    key(item.id) { FormulaItemRow(f, item, total) }
                }
                if (f.items.isEmpty()) {
                    Text("还没有原料", style = MaterialTheme.typography.bodySmall, color = Ink3)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { Repo.I.addFormulaItem(f.id, "原料${f.items.size + 1}", 0.0) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) { Text("＋ 添加原料", color = Brand) }
            }

            if (rest > 0.05 && f.items.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                AppCard {
                    Text(
                        "把剩下的 ${f1(rest)}%（${f1(f.restGrams())} g）补到：",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink2,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        f.items.forEach { item ->
                            ChoiceChip(item.name.ifBlank { "未命名" }, false) {
                                Repo.I.fillFormulaRest(f.id, item.id)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        Repo.I.duplicateFormula(f.id)
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("复制一份", color = Ink) }
                if (confirmDelete) {
                    Button(
                        onClick = {
                            Repo.I.removeFormula(f.id)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    ) { Text("确认删除") }
                } else {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("删除", color = Danger) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "百分比是准的，克数按总量换算；改总量所有克数会跟着变。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormulaItemRow(f: Formula, item: FormulaItem, total: Double) {
    var gText by remember { mutableStateOf(f1(f.gramsOf(item))) }
    var pText by remember { mutableStateOf(f2(item.percent)) }
    var pWritten by remember { mutableStateOf(item.percent) }
    val nameState = rememberLocalText(item.name, item.id) { n ->
        Repo.I.updateFormulaItem(f.id, item.id, name = n)
    }

    // 总量变了 → 克数跟着变
    LaunchedEffect(total) {
        gText = f1(f.gramsOf(item))
    }
    // 别处改了百分比（比如「补齐」）→ 两个框都刷新
    LaunchedEffect(item.percent) {
        if (item.percent != pWritten) {
            pWritten = item.percent
            pText = f2(item.percent)
            gText = f1(f.gramsOf(item))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        OutlinedTextField(
            value = nameState.value,
            onValueChange = { nameState.value = it },
            placeholder = { Text("原料名", style = MaterialTheme.typography.bodySmall, color = Ink3) },
            singleLine = true,
            modifier = Modifier.weight(1.5f),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = fieldColors(),
        )
        NumField(
            value = gText,
            onValueChange = { s ->
                gText = s
                val v = s.trim().toDoubleOrNull()
                if (v != null && v >= 0) {
                    val pct = if (total > 0) v / total * 100.0 else 0.0
                    pWritten = pct
                    pText = f2(pct)
                    Repo.I.updateFormulaItem(f.id, item.id, percent = pct)
                }
            },
            label = "g",
            modifier = Modifier.weight(0.9f),
            imeNext = false,
        )
        NumField(
            value = pText,
            onValueChange = { s ->
                pText = s
                val v = s.trim().toDoubleOrNull()
                if (v != null && v >= 0) {
                    pWritten = v
                    gText = f1(v * total / 100.0)
                    Repo.I.updateFormulaItem(f.id, item.id, percent = v)
                }
            },
            label = "%",
            modifier = Modifier.weight(0.9f),
            imeNext = false,
        )
        IconAction(Icons.Filled.Delete, Danger, "删掉这行") {
            Repo.I.removeFormulaItem(f.id, item.id)
        }
    }
}
