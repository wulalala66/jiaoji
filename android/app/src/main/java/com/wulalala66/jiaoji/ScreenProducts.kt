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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 产品（客户）→ 烘烤条件表。查一下就知道某家的胶要烤多少度多少分钟，
 * 放进烘箱时也能直接挑，省得每次问车间。
 */
@Composable
fun ProductsScreen(state: AppState, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }

    val key = q.trim()
    val list = state.products.filter { p ->
        key.isBlank() || p.name.contains(key, true) ||
            p.bakes.any { "${it.tempC} ${it.minutes}".contains(key) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("产品条件表", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Filled.Add, Brand, "新增产品") { creating = true }
        }

        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("搜产品 / 客户 / 温度", style = MaterialTheme.typography.bodyMedium, color = Ink3) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = fieldColors(),
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { p ->
                AppCard(onClick = { editing = p }) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    BakePresetPills(p.bakes)
                }
            }
            if (list.isEmpty()) {
                item { EmptyHint("🔍", "没找到", "换个词试试，或者点右上角 ＋ 新增一条") }
            }
        }
    }

    if (creating) {
        ProductEditDialog(existing = null, onDismiss = { creating = false })
    }
    editing?.let { p ->
        ProductEditDialog(existing = p, onDismiss = { editing = null })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BakePresetPills(bakes: List<BakePreset>) {
    if (bakes.isEmpty()) {
        Text("还没填烘烤条件", style = MaterialTheme.typography.bodySmall, color = Ink3)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        bakes.forEach { b ->
            Pill("${b.tempC}℃ ${b.minutes}分", Brand, BrandSoft)
        }
    }
}

private data class PresetRow(val id: String, val temp: String, val min: String)

@Composable
private fun ProductEditDialog(existing: Product?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    val rows = remember {
        mutableStateListOf<PresetRow>().apply {
            existing?.bakes?.forEach { add(PresetRow(it.id, "${it.tempC}", "${it.minutes}")) }
            if (isEmpty()) add(PresetRow(Repo.uid(), "140", "30"))
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增产品" else "改产品", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("产品 / 客户名", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("比如 安徽万安", style = MaterialTheme.typography.bodySmall, color = Ink3) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                Text("烘烤条件（温度 / 分钟，可以加多条）", style = MaterialTheme.typography.labelSmall, color = Ink3)
                Spacer(Modifier.height(6.dp))
                rows.forEachIndexed { i, row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        NumField(
                            value = row.temp,
                            onValueChange = { rows[i] = row.copy(temp = it) },
                            label = "℃",
                            modifier = Modifier.weight(1f),
                        )
                        NumField(
                            value = row.min,
                            onValueChange = { rows[i] = row.copy(min = it) },
                            label = "分钟",
                            modifier = Modifier.weight(1f),
                            imeNext = false,
                        )
                        IconAction(Icons.Filled.Delete, Danger, "删掉这条") {
                            if (rows.size > 1) rows.removeAt(i)
                        }
                    }
                }
                TextButton(onClick = { rows.add(PresetRow(Repo.uid(), "150", "25")) }) {
                    Text("＋ 再加一条条件", color = Brand)
                }
                if (confirmDelete) {
                    Text(
                        "删掉「${existing?.name ?: ""}」？",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val bakes = rows.mapNotNull { r ->
                        val t = r.temp.trim().toIntOrNull() ?: return@mapNotNull null
                        val m = r.min.trim().toIntOrNull() ?: return@mapNotNull null
                        if (t in 30..300 && m in 1..600) BakePreset(r.id, t, m, "") else null
                    }
                    if (existing == null) Repo.I.addProduct(name.trim(), bakes)
                    else Repo.I.updateProduct(existing.id, name.trim(), bakes)
                    onDismiss()
                },
            ) { Text("保存", color = Brand) }
        },
        dismissButton = {
            if (existing != null) {
                TextButton(onClick = {
                    if (confirmDelete) {
                        Repo.I.removeProduct(existing.id)
                        onDismiss()
                    } else confirmDelete = true
                }) { Text(if (confirmDelete) "确认删除" else "删除", color = Danger) }
            } else {
                TextButton(onClick = onDismiss) { Text("取消", color = Ink2) }
            }
        },
    )
}
