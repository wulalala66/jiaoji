package com.wulalala66.jiaoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 每个配方的对话记录（进程内保存，切页不丢） */
object AiChat {
    private val sessions = HashMap<String, SnapshotStateList<ChatMsg>>()

    fun list(formulaId: String): SnapshotStateList<ChatMsg> =
        sessions.getOrPut(formulaId) { mutableStateListOf() }

    fun clear(formulaId: String) {
        sessions.remove(formulaId)
    }

    fun hasAny(formulaId: String): Boolean = sessions[formulaId]?.isNotEmpty() == true
}

private val BubbleShape = RoundedCornerShape(16.dp)

/**
 * 配料 AI 助手：能读配方、改配方、查产品条件表、搜网页、算数。
 * 对话记录按配方分开保存。
 */
@Composable
fun FormulaAiScreen(
    state: AppState,
    formulaId: String,
    onBack: () -> Unit,
) {
    val ai = state.settings.ai
    val f = state.formulaOf(formulaId)
    val msgs = remember(formulaId) { AiChat.list(formulaId) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var streamText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun scrollToEnd() {
        scope.launch {
            if (msgs.isNotEmpty() || streamText.isNotEmpty()) {
                listState.animateScrollToItem((msgs.size + if (streamText.isNotEmpty()) 1 else 0).coerceAtLeast(1))
            }
        }
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isBlank() || busy) return
        if (!ai.ready) {
            error = "先去设置页填 API 地址、Key 和模型"
            return
        }
        error = ""
        input = ""
        msgs.add(ChatMsg(role = "user", content = q))
        busy = true
        streamText = ""
        scope.launch {
            try {
                val history = Ai.maybeCompress(ai, msgs.toList())
                val head = listOf(Ai.systemMsg(ai))
                val added = Ai.runTurn(
                    settings = ai,
                    history = head + history,
                    state = state,
                    onDelta = { piece -> streamText += piece },
                    onStatus = { s -> status = s },
                )
                streamText = ""
                msgs.addAll(added)
            } catch (e: Throwable) {
                streamText = ""
                error = e.message ?: "出错了"
            } finally {
                busy = false
                status = ""
                scrollToEnd()
            }
        }
    }

    LaunchedEffect(msgs.size, streamText) { scrollToEnd() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Ink2)
            }
            Column(Modifier.weight(1f)) {
                Text("配料 AI", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    (f?.name ?: "配方") + " · " + (ai.model.ifBlank { "未选模型" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }
            TextButton(onClick = {
                AiChat.clear(formulaId)
                msgs.clear()
                error = ""
                streamText = ""
            }) { Text("清空", color = Ink3) }
        }

        // 对话
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (msgs.isEmpty() && streamText.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        EmptyHint("🤖", "问点什么吧", "它能看到当前配方的全部数据，也能直接改")
                        Spacer(Modifier.height(14.dp))
                        SuggestionChip("这个配方有什么问题？") { send("帮我看看这个配方有什么问题，粘度、固含、成本上有没有明显不合理的地方") }
                        SuggestionChip("补到 100%") { send("把剩余的部分补到占比最大的原料上，让它正好 100%") }
                        SuggestionChip("降低粘度怎么改") { send("我想把这个配方的粘度降下来，保留现有原料，怎么调比例？给出具体百分比") }
                        SuggestionChip("查一下这个原料") { send("帮我查一下 D1NP 这个原料的常见用法和添加量范围") }
                    }
                }
            }
            items(msgs) { m ->
                when (m.role) {
                    "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(color = Brand, shape = BubbleShape, modifier = Modifier.widthIn(max = 300.dp)) {
                            Text(m.content, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(12.dp, 9.dp))
                        }
                    }
                    "assistant" -> {
                        if (m.content.isNotBlank()) {
                            Surface(color = CardBg, shape = BubbleShape, modifier = Modifier.widthIn(max = 330.dp)) {
                                Column(Modifier.padding(13.dp, 10.dp)) {
                                    Text(m.content, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                        m.toolCalls.forEach { c ->
                            ToolPill("🔧 " + AiTools.label(c.name))
                        }
                    }
                    "tool" -> Unit
                    else -> Unit
                }
            }
            if (streamText.isNotEmpty()) {
                item {
                    Surface(color = CardBg, shape = BubbleShape, modifier = Modifier.widthIn(max = 330.dp)) {
                        Text(streamText, color = Ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(13.dp, 10.dp))
                    }
                }
            }
            if (status.isNotBlank()) {
                item { ToolPill("⏳ " + status) }
            }
            if (error.isNotBlank()) {
                item {
                    Surface(color = DangerSoft, shape = BubbleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(error, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(12.dp, 10.dp))
                    }
                }
            }
        }

        // 输入
        Surface(color = CardBg, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!ai.ready) {
                    Text(
                        "还没配 AI：设置 → AI 助手，填地址、Key、选模型",
                        color = Warn, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问配方、让它改比例、查资料…", color = Ink3, fontSize = 14.sp) },
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(if (busy || input.isBlank()) Line else Brand, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                enabled = !busy && input.isNotBlank(),
                            ) { send(input) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPill(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(color = SoftBg, shape = RoundedCornerShape(10.dp)) {
            Text(text, color = Ink3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        color = SoftBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple()) { onClick() },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💬", fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(text, color = Ink2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ------------------------------------------------------------------ 实时点评

private const val COMMENT_SYSTEM =
    "你是胶粘剂配方工程师。看下面这份配料单，用不超过 60 个字指出一个最值得注意的点" +
        "（比例异常、合计不等于 100%、固含/成本明显不合理、明显缺失的助剂等）。" +
        "没有明显问题就回「看着没问题」。只输出结论，不要客套，不要解释你在做什么。"

private fun formulaSignature(f: Formula): String =
    f.totalG.toString() + "|" + f.purpose + "|" + f.cure + "|" +
        f.items.joinToString(";") { it.name + "=" + it.percent }

private fun commentPrompt(f: Formula): String {
    val items = f.items.joinToString("\n") { "- ${it.name}：${Ai.num(it.percent)}%（${Ai.num(f.gramsOf(it))}g）" }
    return "配方：${f.name}\n一锅总量：${Ai.num(f.totalG)}g\n" +
        (if (f.purpose.isNotBlank()) "实验目的：${f.purpose}\n" else "") +
        (if (f.cure.isNotBlank()) "固化条件：${f.cure}\n" else "") +
        "合计：${Ai.num(f.sumPercent())}%，剩余：${Ai.num(f.restPercent())}%\n原料：\n$items"
}

/**
 * 配方页顶部的 AI 实时点评：改了配方会自己重新看一遍（有防抖和频率限制），
 * 没配 AI 时只显示一个入口。
 */
@Composable
fun AiCommentCard(state: AppState, formulaId: String, onOpenChat: () -> Unit) {
    val ai = state.settings.ai
    val f = state.formulaOf(formulaId) ?: return
    val sig = formulaSignature(f)
    var text by remember(formulaId) { mutableStateOf("") }
    var doneSig by remember(formulaId) { mutableStateOf("") }
    var busy by remember(formulaId) { mutableStateOf(false) }
    var err by remember(formulaId) { mutableStateOf("") }
    var lastAt by remember(formulaId) { mutableStateOf(0L) }

    LaunchedEffect(sig, ai.ready, ai.liveComment) {
        if (!ai.ready || !ai.liveComment || sig == doneSig) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500L)
        val wait = 12_000L - (System.currentTimeMillis() - lastAt)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        busy = true
        try {
            text = Ai.quick(ai, COMMENT_SYSTEM, commentPrompt(f))
            doneSig = sig
            lastAt = System.currentTimeMillis()
            err = ""
        } catch (e: Throwable) {
            err = e.message ?: "点评失败"
        } finally {
            busy = false
        }
    }

    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🤖 AI 点评", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (busy) Text("思考中…", color = Ink3, fontSize = 12.sp)
            TextButton(onClick = onOpenChat) { Text("问它", color = Brand) }
        }
        when {
            !ai.ready -> Text(
                "设置页填好 API 地址和 Key，这里就会实时盯着这个配方。",
                color = Ink3, fontSize = 13.sp,
            )
            err.isNotBlank() -> Text(err, color = Danger, fontSize = 13.sp)
            text.isNotBlank() -> Text(text, color = Ink2, fontSize = 14.sp, lineHeight = 21.sp)
            !ai.liveComment -> Text("实时点评已关（设置页可以打开）", color = Ink3, fontSize = 13.sp)
            else -> Text("改一下配方，我看看有没有问题…", color = Ink3, fontSize = 13.sp)
        }
    }
}
