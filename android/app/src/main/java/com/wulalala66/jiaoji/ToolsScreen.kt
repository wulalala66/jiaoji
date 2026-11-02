package com.wulalala66.jiaoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 「工具」页：原来塞在设置最下面的东西，现在是一级入口。
 * 记录与导出也挪到这里了（用得比工具少，但还是要一眼能找到）。
 */
@Composable
fun ToolsScreen(
    state: AppState,
    onOpenRecords: () -> Unit,
    onOpenDensity: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenFormulas: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("工具", style = MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("记录、计算、条件表、配料", style = MaterialTheme.typography.bodyMedium, color = Ink3)
            }
        }

        item {
            AppCard {
                ToolRow("📄", "记录与导出", "今天 / 近 7 天 / 全部 · CSV 和日报", onOpenRecords)
            }
        }

        item {
            AppCard {
                ToolRow("⚖️", "比重计算器", "输 M1~M6，出比重 / 固体成分 / 发泡率", onOpenDensity)
                ThinDivider()
                ToolRow("📋", "产品条件表", "${state.products.size} 条 · 哪个客户烤多少度多久", onOpenProducts)
                ThinDivider()
                ToolRow("⚗️", "配料", "${state.formulas.size} 个配方 · 克数和百分比自动换算", onOpenFormulas)
            }
        }

        item {
            AppCard {
                ToolRow("🩺", "自检与诊断", "权限状态、已排定的闹钟、响铃测试", onOpenDiagnostics)
            }
        }
    }
}
