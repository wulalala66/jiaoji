package com.gluelab.jiaoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设置：数据备份 + 关于。功能都下线了，这一页只留必须的几件事。 */
@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("设置", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            SectionHeader("数据")
            AppCard {
                Text(
                    "配方和条件表都存在这台手机上，没有账号、没有云同步。换手机用下面两个按钮搬。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("导出备份（JSON）") {
                    Share.json(ctx, "胶记备份.json", Repo.I.exportJson())
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("清空所有数据", enabled = true) { confirmClear = true }
                Spacer(Modifier.height(6.dp))
                Text(
                    "导入备份：把 .json 文件发到微信「文件传输助手」，点开后选「用其他应用打开 → 胶记」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }

            SectionHeader("系统时钟")
            AppCard {
                Text(
                    "点烘烤条件时会交给系统时钟计时。如果那台机器上一直没反应，" +
                        "点下面的按钮，把结果复制发给我，我就能知道该用哪个入口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("检测系统时钟") {
                    Share.text(ctx, "胶记 · 系统时钟自检", SystemTimer.probeReport(ctx))
                }
            }

            SectionHeader("关于")
            AppCard {
                KeyValue("版本", BuildConfig.VERSION_NAME)
                KeyValue("包名", BuildConfig.APPLICATION_ID)
                KeyValue("数据", "${state.products.size} 条产品条件 · ${state.densityLog.size} 条比重记录")
                Spacer(Modifier.height(6.dp))
                Text(
                    "胶记 · 胶粘剂实验室的随身工具：比重计算、产品条件表与烘烤计时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有数据？", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "产品条件表和比重记录都会删掉，恢复默认的 16 条条件。这个操作不能撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    Repo.I.importJson(Codec.encode(Defaults.state()))
                }) { Text("清空", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消", color = Ink2) } },
        )
    }
}
