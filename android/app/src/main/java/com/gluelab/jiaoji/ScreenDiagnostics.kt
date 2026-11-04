package com.gluelab.jiaoji

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自检与诊断：AI 连不上、导出失败、闪退，用这一页拿到事实，复制给我就能定位。
 */
@Composable
fun DiagnosticsScreen(state: AppState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf(AppLog.all(ctx)) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val ai = state.settings.ai
    val dm = ctx.resources.displayMetrics
    val screenW = (dm.widthPixels / dm.density).toInt()
    val screenH = (dm.heightPixels / dm.density).toInt()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconAction(Icons.Filled.ArrowBack, Ink, "返回") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("自检与诊断", style = MaterialTheme.typography.titleLarge, color = Ink)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            SectionHeader("应用")
            AppCard {
                KeyValue("版本", BuildConfig.VERSION_NAME)
                KeyValue("包名", BuildConfig.APPLICATION_ID)
                KeyValue("系统", "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
                KeyValue("机型", "${Build.MANUFACTURER} ${Build.MODEL}")
                KeyValue("屏幕", "${screenW}×${screenH} dp")
                KeyValue("数据", "${state.formulas.size} 个配方 · ${state.products.size} 条条件")
            }

            SectionHeader("AI 连通性")
            AppCard {
                KeyValue(
                    "接口地址",
                    if (ai.baseUrl.isBlank()) "未填写" else Ai.normalizeBase(ai.baseUrl),
                )
                KeyValue("模型", ai.model.ifBlank { "未选择" })
                KeyValue(
                    "Key",
                    if (ai.apiKey.isBlank()) "未填写" else "已填写（${ai.apiKey.length} 位，只存在本机）",
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !testing && ai.baseUrl.isNotBlank() && ai.apiKey.isNotBlank(),
                        onClick = {
                            testing = true
                            testResult = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { testAi(ai) }
                                testResult = r
                                testing = false
                            }
                        },
                    ) {
                        Text(
                            when {
                                testing -> "正在测试…"
                                !ai.baseUrl.isNotBlank() -> "先在设置里填接口地址"
                                !ai.apiKey.isNotBlank() -> "先在设置里填 Key"
                                else -> "测试连接"
                            },
                            color = if (testing) Ink3 else Brand,
                        )
                    }
                }
                testResult?.let { (ok, msg) ->
                    Text(
                        (if (ok) "✓ " else "✗ ") + msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) Ok else Danger,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            SectionHeader("错误日志", trailing = "${logs.size} 条")
            AppCard {
                if (logs.isEmpty()) {
                    Text("还没有记录。出问题时这里会自动写下原因。", style = MaterialTheme.typography.bodySmall, color = Ink3)
                } else {
                    Column(Modifier.height(180.dp).verticalScroll(rememberScrollState())) {
                        logs.reversed().forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink2,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(
                        enabled = logs.isNotEmpty(),
                        onClick = {
                            copy(ctx, "胶记日志", logs.joinToString("\n"))
                            Toast.makeText(ctx, "已复制，直接粘贴发我", Toast.LENGTH_SHORT).show()
                        },
                    ) { Text("复制全部", color = if (logs.isEmpty()) Ink3 else Brand) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        enabled = logs.isNotEmpty(),
                        onClick = {
                            AppLog.clear(ctx)
                            logs = emptyList()
                        },
                    ) { Text("清空", color = if (logs.isEmpty()) Ink3 else Ink2) }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 两步：先拉模型列表确认地址和 Key，再发一句最小对话确认真的能答 */
private suspend fun testAi(ai: AiSettings): Pair<Boolean, String> {
    val models = try {
        Ai.fetchModels(ai)
    } catch (e: Throwable) {
        return false to "拉模型列表失败：${e.message ?: e.javaClass.simpleName}"
    }
    if (models.isEmpty()) return false to "地址通了，但没返回任何模型"
    val model = ai.model.ifBlank { models.first() }
    val started = System.currentTimeMillis()
    val reply = try {
        Ai.quick(ai.copy(model = model), "只回答两个字：收到", "在吗")
    } catch (e: Throwable) {
        return false to "地址和 Key 没问题（${models.size} 个模型），但对话失败：${e.message ?: e.javaClass.simpleName}"
    }
    val ms = System.currentTimeMillis() - started
    val tail = if (reply.isBlank()) "（返回空内容）" else "「${reply.take(20)}」"
    return true to "通了：$model 正常回答$tail，耗时 ${ms}ms，共 ${models.size} 个模型"
}

private fun copy(ctx: Context, label: String, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
