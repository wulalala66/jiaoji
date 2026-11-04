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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun parseD(s: String): Double = s.trim().replace("，", ".").toDoubleOrNull() ?: 0.0

private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

/**
 * 单独用的比重计算器：输 6 个称量值（M5/M6 可以空着），
 * 直接出 比重 / 固体成分 / 发泡率 三个结果。
 */
@Composable
fun DensityScreen() {
    val ctx = LocalContext.current
    var m1 by remember { mutableStateOf("") }
    var m2 by remember { mutableStateOf("") }
    var m3 by remember { mutableStateOf("") }
    var m4 by remember { mutableStateOf("") }
    var m5 by remember { mutableStateOf("") }
    var m6 by remember { mutableStateOf("") }

    val dens = Density(parseD(m1), parseD(m2), parseD(m3), parseD(m4), parseD(m5), parseD(m6))
    val r = DensityCalc.calc(dens)
    val hasSolid = parseD(m5) > 0.0 && parseD(m6) > 0.0

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
                        "${fmt2(dens.m4)} / ${fmt2(dens.m5)} / ${fmt2(dens.m6)}"
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
                        NumField(m1, { m1 = it }, "M1", Modifier.weight(1f))
                        NumField(m2, { m2 = it }, "M2", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(m3, { m3 = it }, "M3", Modifier.weight(1f))
                        NumField(m4, { m4 = it }, "M4", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(m5, { m5 = it }, "M5", Modifier.weight(1f), placeholder = "可空")
                        NumField(m6, { m6 = it }, "M6", Modifier.weight(1f), placeholder = "可空")
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            m1 = ""; m2 = ""; m3 = ""; m4 = ""; m5 = ""; m6 = ""
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("清空", color = Ink) }
                    Button(
                        onClick = {
                            Share.text(
                                ctx, "比重计算结果",
                                "比重：${DensityCalc.text(r.specific, 4)}\n" +
                                    "固体成分：${DensityCalc.text(r.solid, 3)} %\n" +
                                    "发泡率：${DensityCalc.text(r.foam, 3)} %"
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                    ) { Text("分享结果") }
                }
            }
        }
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
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
