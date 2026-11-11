package com.gluelab.jiaoji

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions

private fun parseD(s: String): Double = s.trim().replace("，", ".").replace(",", ".").toDoubleOrNull() ?: 0.0
private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
private val stampFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)

/**
 * 比重计算器：输 6 个称量值，出 比重 / 固体成分 / 发泡率。
 * 每次测完可以存一条记录（输入 + 结果），下面是历史。
 */
@Composable
fun DensityScreen() {
    val ctx = LocalContext.current
    val state by Repo.I.state.collectAsState()

    var m1 by remember { mutableStateOf("") }
    var m2 by remember { mutableStateOf("") }
    var m3 by remember { mutableStateOf("") }
    var m4 by remember { mutableStateOf("") }
    var m5 by remember { mutableStateOf("") }
    var m6 by remember { mutableStateOf("") }
    var clearLog by remember { mutableStateOf(false) }
    // 六个格子依次输入：只输数字，自动排小数点，输满三个自动跳下一格
    val focus = remember { List(6) { FocusRequester() } }
    val kb = LocalSoftwareKeyboardController.current

    fun digitsOf(v: String) = v.filter { it.isDigit() }.take(3)
    fun formatted(d: String) = when {
        d.isEmpty() -> ""
        d.length == 1 -> d
        else -> d[0].toString() + "." + d.substring(1)
    }

    fun setAt(i: Int, raw: String) {
        val d = digitsOf(raw)
        val v = formatted(d)
        when (i) {
            0 -> m1 = v; 1 -> m2 = v; 2 -> m3 = v
            3 -> m4 = v; 4 -> m5 = v; 5 -> m6 = v
        }
        if (d.length >= 3 && i < 5) focus[i + 1].requestFocus()
    }

    fun goNext(i: Int) {
        if (i < 5) focus[i + 1].requestFocus() else kb?.hide()
    }

    val dens = Density(parseD(m1), parseD(m2), parseD(m3), parseD(m4), parseD(m5), parseD(m6))
    val r = DensityCalc.calc(dens.m1, dens.m2, dens.m3, dens.m4, dens.m5, dens.m6)
    val hasSolid = dens.m5 > 0.0 && dens.m6 > 0.0
    val canSave = r.specific != null

    fun fillFrom(rec: DensityRecord) {
        m1 = fmt2(rec.m1); m2 = fmt2(rec.m2); m3 = fmt2(rec.m3)
        m4 = fmt2(rec.m4); m5 = if (rec.m5 > 0) fmt2(rec.m5) else ""; m6 = if (rec.m6 > 0) fmt2(rec.m6) else ""
    }

    LaunchedEffect(Unit) { runCatching { focus[0].requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("比重计算器", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Filled.Share, Ink2, "分享结果") {
                Share.text(
                    ctx, "比重计算结果",
                    "比重：${DensityCalc.text(r.specific, 4)}\n" +
                        "固体成分：${DensityCalc.text(r.solid, 3)} %\n" +
                        "发泡率：${DensityCalc.text(r.foam, 3)} %\n" +
                        "M1~M6：${fmt2(dens.m1)} / ${fmt2(dens.m2)} / ${fmt2(dens.m3)} / " +
                        "${fmt2(dens.m4)} / ${fmt2(dens.m5)} / ${fmt2(dens.m6)}",
                )
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard {
                    Text("称量值", style = MaterialTheme.typography.labelMedium, color = Ink3)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickNum(m1, { setAt(0, it) }, "M1", Modifier.weight(1f), focus[0], 1, { goNext(0) })
                        QuickNum(m2, { setAt(1, it) }, "M2", Modifier.weight(1f), focus[1], 2, { goNext(1) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickNum(m3, { setAt(2, it) }, "M3", Modifier.weight(1f), focus[2], 3, { goNext(2) })
                        QuickNum(m4, { setAt(3, it) }, "M4", Modifier.weight(1f), focus[3], 4, { goNext(3) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickNum(m5, { setAt(4, it) }, "M5 可空", Modifier.weight(1f), focus[4], 5, { goNext(4) })
                        QuickNum(m6, { setAt(5, it) }, "M6 可空", Modifier.weight(1f), focus[5], 6, { goNext(5) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "直接敲数字就行：输 3 2 1 就是 3.21，满三位自动跳到下一格；只有两位按回车也能跳。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "d1=M3−M1  d2=M4−M2  d3=M5−M1  d4=M6−M2\n" +
                            "比重 = d1/(d1−d2)；固体成分 = d3/d1×100；发泡率 = ((d3−d4)/(d1−d2)−1)×100",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                }
            }

            item {
                AppCard(color = BrandSoft, borderColor = BrandSoft) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResultBox("比重", DensityCalc.text(r.specific, 4), Modifier.weight(1f))
                        ResultBox("固体成分", if (hasSolid) DensityCalc.text(r.solid, 3) + " %" else "—", Modifier.weight(1f))
                        ResultBox("发泡率", if (hasSolid) DensityCalc.text(r.foam, 3) + " %" else "—", Modifier.weight(1f))
                    }
                    if (!hasSolid) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "填上 M5、M6（固份）才会算固体成分和发泡率；只算比重时前四个值就够了。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink2,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "d1 ${fmt2(r.d1)}   d2 ${fmt2(r.d2)}   d3 ${fmt2(r.d3)}   d4 ${fmt2(r.d4)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        Repo.I.addDensityRecord(dens.m1, dens.m2, dens.m3, dens.m4, dens.m5, dens.m6)
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = Brand),
                ) {
                    Text(if (canSave) "保存这次结果" else "填够 M1~M4 才能保存", fontSize = 15.sp)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { m1 = ""; m2 = ""; m3 = ""; m4 = ""; m5 = ""; m6 = "" },
                        modifier = Modifier.weight(1f),
                    ) { Text("清空", color = Ink) }
                    Button(
                        onClick = {
                            Share.text(
                                ctx, "比重计算结果",
                                "比重：${DensityCalc.text(r.specific, 4)}\n" +
                                    "固体成分：${DensityCalc.text(r.solid, 3)} %\n" +
                                    "发泡率：${DensityCalc.text(r.foam, 3)} %",
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                    ) { Text("分享结果") }
                }
            }

            // ---------------- 历史
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("历史记录", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.weight(1f))
                    if (state.densityLog.isNotEmpty()) {
                        TextButton(onClick = { clearLog = true }) { Text("清空", color = Ink3, fontSize = 13.sp) }
                    }
                }
            }

            if (state.densityLog.isEmpty()) {
                item {
                    Text(
                        "还没有记录。填好称量值后点「保存这次结果」，这里会按时间排下来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                }
            }

            items(state.densityLog, key = { it.id }) { rec ->
                AppCard(onClick = { fillFrom(rec) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stampFmt.format(Date(rec.at)),
                            fontSize = 12.sp,
                            color = Ink3,
                            modifier = Modifier.weight(1f),
                        )
                        IconAction(Icons.Filled.Delete, Danger, "删掉这条") { Repo.I.removeDensityRecord(rec.id) }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("比重 ${DensityCalc.text(rec.specific, 4)}", fontSize = 15.sp, color = Ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            "固含 " + (rec.solid?.let { DensityCalc.text(it, 3) + "%" } ?: "—"),
                            fontSize = 13.sp,
                            color = Ink2,
                        )
                        Text(
                            "发泡 " + (rec.foam?.let { DensityCalc.text(it, 3) + "%" } ?: "—"),
                            fontSize = 13.sp,
                            color = Ink2,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "M1~M6  ${fmt2(rec.m1)} / ${fmt2(rec.m2)} / ${fmt2(rec.m3)} / " +
                            "${fmt2(rec.m4)} / ${fmt2(rec.m5)} / ${fmt2(rec.m6)}",
                        fontSize = 11.sp,
                        color = Ink3,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (state.densityLog.isNotEmpty()) {
                item {
                    Text(
                        "点一条记录可以把当时的称量值填回上面重新算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                }
            }
        }
    }

    if (clearLog) {
        AlertDialog(
            onDismissRequest = { clearLog = false },
            title = { Text("清空历史记录？", style = MaterialTheme.typography.titleMedium) },
            text = { Text("${state.densityLog.size} 条记录会被删掉，不能恢复。", style = MaterialTheme.typography.bodyMedium, color = Ink2) },
            confirmButton = {
                TextButton(onClick = {
                    clearLog = false
                    Repo.I.clearDensityLog()
                }) { Text("清空", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { clearLog = false }) { Text("取消", color = Ink2) } },
        )
    }
}

@Composable
private fun ResultBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(CardBg)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink3, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = if (value == "—") Ink3 else Ink,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * 只收数字的输入框：敲 3 2 1 显示 3.21，满三位自动跳下一格。
 * 不用自己打小数点，也不用点屏幕切焦点。
 */
@Composable
private fun QuickNum(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester,
    order: Int,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier.focusRequester(requester),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (order < 6) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onDone() },
            onDone = { onDone() },
        ),
    )
}
