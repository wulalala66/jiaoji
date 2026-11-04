package com.gluelab.jiaoji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页：AI 配置（地址 / Key / 模型 / 思考档位 / 上下文压缩）+ 数据备份 + 关于。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    state: AppState,
    onOpenDiagnostics: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val ai = state.settings.ai
    val scope = rememberCoroutineScope()

    var showKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickModel by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var promptGen by remember { mutableStateOf(0) }
    val promptDefault = remember { Ai.defaultSystemPrompt() }
    var showImport by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    val baseState = rememberLocalText(ai.baseUrl, "ai-base") { v ->
        // 没写 http:// 的自动补上，否则 Android 会拦明文请求
        Repo.I.updateSettings { it.copy(ai = it.ai.copy(baseUrl = Ai.normalizeBase(v))) }
    }
    val keyState = rememberLocalText(ai.apiKey, "ai-key") { v ->
        Repo.I.updateSettings { it.copy(ai = it.ai.copy(apiKey = v.trim())) }
    }
    val modelState = rememberLocalText(ai.model, "ai-model") { v ->
        Repo.I.updateSettings { it.copy(ai = it.ai.copy(model = v.trim())) }
    }
    // 内置默认值直接填进输入框（不是灰字提示），这样能就地改；改回默认文本或点「恢复默认」等于用内置的
    val promptState = rememberLocalText(
        initial = ai.systemPrompt.ifBlank { promptDefault },
        key = "ai-prompt-$promptGen",
    ) { v ->
        val normalized = if (v.isBlank() || v.trim() == promptDefault.trim()) "" else v
        Repo.I.updateSettings { it.copy(ai = it.ai.copy(systemPrompt = normalized)) }
    }

    fun fetchModels() {
        if (loading) return
        loading = true
        testResult = ""
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Ai.fetchModels(Repo.I.state.value.settings.ai) }
                models = list
                if (list.isEmpty()) testResult = "接口没有返回模型，检查地址对不对"
                else if (Repo.I.state.value.settings.ai.model.isBlank()) {
                    Repo.I.updateSettings { it.copy(ai = it.ai.copy(model = list.first())) }
                    modelState.value = list.first()
                }
            } catch (e: Throwable) {
                testResult = "拉取失败：" + (e.message ?: "未知错误")
            } finally {
                loading = false
            }
        }
    }

    fun test() {
        if (testing) return
        testing = true
        testResult = ""
        scope.launch {
            try {
                val r = Ai.quick(
                    Repo.I.state.value.settings.ai,
                    "你是连通性测试助手，只回一个词。",
                    "回一个词：收到",
                )
                testResult = "通了：" + r.take(60)
            } catch (e: Throwable) {
                testResult = "失败：" + (e.message ?: "未知错误")
            } finally {
                testing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("设置", style = MaterialTheme.typography.titleLarge, color = Ink)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---------------- AI ----------------
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI 助手", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.weight(1f))
                Pill(
                    if (ai.ready) "已配置" else "未配置",
                    if (ai.ready) Ok else Warn,
                    if (ai.ready) OkSoft else WarnSoft,
                )
            }

            AppCard {
                Text(
                    "填一个 OpenAI 兼容的接口就行（/v1 结尾），模型列表会自动拉。",
                    color = Ink3, fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = baseState.value,
                    onValueChange = { baseState.value = it },
                    label = { Text("API 地址", fontSize = 12.sp) },
                    placeholder = { Text("https://xxx/v1", color = Ink3, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = keyState.value,
                    onValueChange = { keyState.value = it },
                    label = { Text("API Key", fontSize = 12.sp) },
                    placeholder = { Text("sk-…", color = Ink3, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "藏" else "看", color = Ink3, fontSize = 12.sp)
                        }
                    },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = modelState.value,
                    onValueChange = { modelState.value = it },
                    label = { Text("模型", fontSize = 12.sp) },
                    placeholder = { Text("点右边拉取，或直接填", color = Ink3, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    trailingIcon = {
                        TextButton(onClick = { fetchModels() }) {
                            Text(if (loading) "拉取中…" else "拉取", color = Brand, fontSize = 12.sp)
                        }
                    },
                    colors = fieldColors(),
                )

                if (models.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("拉到 ${models.size} 个模型，点一个就选中", color = Ink3, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        models.take(12).forEach { m ->
                            ChoiceChip(m, modelState.value == m) {
                                modelState.value = m
                                Repo.I.updateSettings { it.copy(ai = it.ai.copy(model = m)) }
                            }
                        }
                        if (models.size > 12) {
                            ChoiceChip("全部 ${models.size} 个", false) { pickModel = true }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                ThinDivider()
                Spacer(Modifier.height(10.dp))

                Text("思考档位", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("" to "默认", "low" to "低", "medium" to "中", "high" to "高").forEach { (v, label) ->
                        ChoiceChip(label, ai.thinking == v) {
                            Repo.I.updateSettings { it.copy(ai = it.ai.copy(thinking = v)) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "越高越慢越贵，配料这种任务「低」通常就够；改完立刻问它一个问题看效果。",
                    color = Ink3, fontSize = 11.sp,
                )

                Spacer(Modifier.height(10.dp))
                ToggleRow("实时点评", "改配方后自动让 AI 看一眼（有防抖，不刷屏）", ai.liveComment) { v ->
                    Repo.I.updateSettings { it.copy(ai = it.ai.copy(liveComment = v)) }
                }
                ToggleRow("上下文压缩", "聊久了自动把前面的对话压成摘要，省 token", ai.compress) { v ->
                    Repo.I.updateSettings { it.copy(ai = it.ai.copy(compress = v)) }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("系统提示词", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    if (ai.systemPrompt.isBlank()) Pill("内置默认", Ink3, SoftBg)
                    else Pill("已自定义", Brand, BrandSoft)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showPrompt = !showPrompt }) {
                        Text(if (showPrompt) "收起" else "展开", color = Brand, fontSize = 12.sp)
                    }
                }
                if (showPrompt) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = promptState.value,
                        onValueChange = { promptState.value = it },
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Ink),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "这段就是内置默认值，可以直接在上面改。",
                            color = Ink3, fontSize = 11.sp, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            promptState.value = promptDefault
                            promptGen++
                            Repo.I.updateSettings { it.copy(ai = it.ai.copy(systemPrompt = "")) }
                        }) { Text("恢复默认", color = Brand, fontSize = 12.sp) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(
                        text = if (testing) "测试中…" else "测试连接",
                        modifier = Modifier.weight(1f),
                        enabled = !testing && ai.baseUrl.isNotBlank() && ai.apiKey.isNotBlank(),
                    ) { test() }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onOpenDiagnostics,
                        shape = RoundedCornerShape(12.dp),
                        color = SoftBg,
                        modifier = Modifier.weight(0.42f),
                    ) {
                        Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                            Text("诊断", color = Ink2, fontSize = 14.sp)
                        }
                    }
                }
                if (testResult.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        testResult,
                        color = if (testResult.startsWith("通了")) Ok else Danger,
                        fontSize = 12.sp,
                    )
                }
            }

            // ---------------- 数据 ----------------
            SectionHeader("数据")
            AppCard {
                Text(
                    "配方、条件表都存在手机本地，换手机用备份文件搬过去。",
                    color = Ink3, fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallAction("导出备份") {
                        Share.json(ctx, "胶记备份.json", Repo.I.exportJson())
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallAction("导入备份") {
                        showImport = true
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallAction("清空配方") {
                        showClear = true
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.formulas.size} 个配方 · ${state.products.size} 条条件",
                        color = Ink3, fontSize = 12.sp,
                    )
                }
            }

            // ---------------- 关于 ----------------
            SectionHeader("关于")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("胶记 GlueLab", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME} · 比重 / 配料 / 条件表 / AI",
                            color = Ink3, fontSize = 12.sp,
                        )
                    }
                    Pill("本地存储", Ink2, SoftBg)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI 走你自己的接口，Key 只存在这台手机上，不上传任何服务器。",
                    color = Ink3, fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (pickModel) {
        AlertDialog(
            onDismissRequest = { pickModel = false },
            title = { Text("选模型", color = Ink) },
            text = {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    models.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                ) {
                                    modelState.value = m
                                    Repo.I.updateSettings { it.copy(ai = it.ai.copy(model = m)) }
                                    pickModel = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                m,
                                color = if (m == modelState.value) Brand else Ink,
                                fontSize = 14.sp,
                                fontWeight = if (m == modelState.value) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickModel = false }) { Text("关闭", color = Ink2) }
            },
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onOk = { text ->
                showImport = false
                val ok = Repo.I.importJson(text)
                android.widget.Toast.makeText(
                    ctx,
                    if (ok) "导入成功" else "这份备份看不懂",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空配方？", color = Ink) },
            text = { Text("只会删配方和条件表，AI 配置和客户名单保留。", color = Ink2, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    Repo.I.state.value.formulas.toList().forEach { Repo.I.removeFormula(it.id) }
                    showClear = false
                }) { Text("清空", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("算了", color = Ink2) } },
        )
    }
}

/* ---------------- 小控件 ---------------- */

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp)
            Text(sub, color = Ink3, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onChange(it) },
            modifier = Modifier.size(width = 44.dp, height = 26.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Brand,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Line,
                uncheckedBorderColor = Line,
            ),
        )
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = SoftBg,
    ) {
        Text(text, color = Ink2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴备份内容", color = Ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                placeholder = { Text("把导出的 json 粘进来", color = Ink3, fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Ink),
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onOk(text) }) { Text("导入", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Ink2) } },
    )
}
