package com.gluelab.jiaoji

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.mutableStateListOf
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow

private val BubbleShape = RoundedCornerShape(14.dp)

/**
 * 「帮我改」发出去的那句话。
 * 模型很常见的毛病是：把改法描述得头头是道，却一个工具都没调 ——
 * 用户以为改好了，回去一看配方没动。这个按钮就是把「说的」变成「做的」。
 */
private const val APPLY_PROMPT =
    "别只描述。按你刚才说的，直接用工具改到配方里，改完用一两句话告诉我改了什么、现在合计多少。"

/** 配料 AI 对话页（右上角「问 AI」进来的那个） */
@Composable
fun FormulaAiScreen(state: AppState, formulaId: String, onBack: () -> Unit) {
    val ai = state.settings.ai
    val f = state.formulaOf(formulaId)
    val s = AiSession.of(formulaId)
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val listState = rememberLazyListState()
    val follow = rememberFollowBottom(listState)
    val scope = rememberCoroutineScope()

    // 还没发出去的附件（只在内存里）
    val pending = remember { mutableStateListOf<Attachment>() }
    var attachSheet by remember { mutableStateOf(false) }
    var loadingFile by remember { mutableStateOf(false) }

    fun addUri(uri: Uri) {
        if (pending.size >= Attach.MAX_FILES) {
            Toast.makeText(ctx, "一次最多带 ${Attach.MAX_FILES} 个附件", Toast.LENGTH_SHORT).show()
            return
        }
        loadingFile = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Attach.load(ctx, uri) }
            }
            loadingFile = false
            result.onSuccess { list ->
                val room = Attach.MAX_FILES - pending.size
                pending.addAll(list.take(room))
                if (list.size > room) Toast.makeText(ctx, "只放了前 $room 个", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(ctx, it.message ?: "读不了这个文件", Toast.LENGTH_LONG).show()
            }
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { addUri(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { addUri(it) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addUri(it) }
    }

    // 只有「本来就贴着底部」的时候才跟着滚：
    // 新消息、流式输出都会滚下去，用户往上翻历史时则完全不打扰。
    LaunchedEffect(s.msgs.size, s.stream.length, s.think.length, s.status) {
        if (follow.value) listState.scrollToBottom()
    }
    // 键盘弹出/收起会改变可视高度，这时也要重新贴底
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportEndOffset }
            .distinctUntilChanged()
            .collect { if (follow.value) listState.scrollToBottom() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("配料 AI", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    (f?.name ?: "配方") + (if (ai.model.isNotBlank()) " · " + ai.model else ""),
                    fontSize = 12.sp,
                    color = Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (s.msgs.isNotEmpty()) {
                MiniAction("清空") { AiSession.clear(formulaId) }
            }
        }

        if (!ai.ready) {
            AppCard {
                Text("还没配置 AI", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text("去「设置 → AI 助手」填接口地址和 Key，再点「拉取」选一个模型。", fontSize = 13.sp, color = Ink3, lineHeight = 19.sp)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (s.msgs.isEmpty() && !s.live) {
                    item {
                        Column {
                            Text("可以直接让它改配方，改完这里就能看到合计变化。", fontSize = 13.sp, color = Ink3)
                            Spacer(Modifier.height(10.dp))
                            val tips = listOf(
                                "这个配方有什么问题",
                                "补到 100%",
                                "怎么把粘度降下来",
                                "查一下 D-80 的供应商",
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tips.forEach { t ->
                                    Surface(
                                        color = SoftBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                        ) {
                                            AiSession.send(formulaId, t, ai)
                                        },
                                    ) {
                                        Text(t, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                itemsIndexed(s.msgs) { index, m ->
                    MessageItem(
                        m = m,
                        isLast = index == s.msgs.lastIndex,
                        busy = s.busy,
                        onCopy = { copyText(ctx, it) },
                        onRegenerate = { AiSession.regenerate(formulaId, ai) },
                        onEdit = { editing = index to it },
                        onApply = {
                            follow.value = true
                            AiSession.send(formulaId, APPLY_PROMPT, ai)
                        },
                    )
                }

                if (s.live) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.think.isNotEmpty() || s.busy) {
                                ReasoningCard(
                                    text = s.think,
                                    live = true,
                                    ms = System.currentTimeMillis() - s.startedAt,
                                )
                            }
                            if (s.stream.isNotBlank()) {
                                Surface(color = CardBg, shape = BubbleShape, modifier = Modifier.widthIn(max = 330.dp)) {
                                    Text(richText(s.stream), color = Ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(13.dp, 10.dp))
                                }
                            }
                            if (s.status.isNotBlank()) ToolPill("⏳ " + s.status)
                        }
                    }
                }

                if (s.error.isNotBlank()) {
                    item { ToolPill("⚠️ " + s.error) }
                }
            }

            // 用户往上翻了：给一个明显的「回到底部」，而不是偷偷把他拽下去
            if (!follow.value) {
                Surface(
                    onClick = {
                        follow.value = true
                        scope.launch { listState.scrollToBottom(animate = true) }
                    },
                    color = CardBg,
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                ) {
                    Text(
                        "↓ 最新",
                        fontSize = 13.sp,
                        color = Brand,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        // 待发附件
        if (pending.isNotEmpty() || loadingFile) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loadingFile) ToolPill("⏳ 正在读取…")
                pending.forEach { a ->
                    Surface(color = BrandSoft, shape = RoundedCornerShape(10.dp)) {
                        Row(
                            Modifier.padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (a.isImage) "🖼 " else "📄 ") + a.name,
                                fontSize = 12.sp,
                                color = BrandDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp),
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                "✕",
                                fontSize = 12.sp,
                                color = Ink3,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                    ) { pending.remove(a) }
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // 输入区
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding().imePadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                color = if (pending.isNotEmpty()) BrandSoft else SoftBg,
                shape = CircleShape,
                modifier = Modifier.size(46.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ) { attachSheet = true },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("＋", fontSize = 22.sp, color = if (pending.isNotEmpty()) Brand else Ink2)
                }
            }
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (ai.ready) "问点什么，或者说「补到 100%」" else "先配置 AI", fontSize = 14.sp, color = Ink3) },
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = fieldColors(),
            )
            Spacer(Modifier.size(8.dp))
            val canSend = (input.isNotBlank() || pending.isNotEmpty()) && !s.busy && ai.ready
            Surface(
                color = if (s.busy) Danger else if (canSend) Brand else SoftBg,
                shape = CircleShape,
                modifier = Modifier.size(46.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ) {
                    when {
                        s.busy -> AiSession.stop(formulaId)
                        canSend -> {
                            follow.value = true
                            // 文本类附件（txt/docx）直接拼进正文；图片走多模态 image_url
                            val extra = pending.filter { !it.isImage }
                                .joinToString("\n\n") { "【附件：${it.name}】\n${it.text}" }
                            val body = listOf(input.trim(), extra).filter { it.isNotBlank() }.joinToString("\n\n")
                            AiSession.send(formulaId, body, ai, pending.filter { it.isImage }.map { it.dataUrl })
                            input = ""
                            pending.clear()
                            scope.launch { listState.scrollToBottom(animate = true) }
                        }
                    }
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (s.busy) {
                        Text("停", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = "发送", tint = if (canSend) Color.White else Ink3, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    editing?.let { (index, text) ->
        EditDialog(
            initial = text,
            onDismiss = { editing = null },
            onOk = { v ->
                editing = null
                AiSession.editAndResend(formulaId, index, v, ai)
            },
        )
    }

    if (attachSheet) {
        AlertDialog(
            onDismissRequest = { attachSheet = false },
            title = { Text("添加附件", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    AttachOption("📷", "拍照", "直接拍配料单、检测报告") {
                        attachSheet = false
                        val uri = newPhotoUri(ctx)
                        if (uri == null) {
                            Toast.makeText(ctx, "建不了临时文件", Toast.LENGTH_SHORT).show()
                        } else {
                            cameraUri = uri
                            takePhoto.launch(uri)
                        }
                    }
                    AttachOption("🖼", "相册", "从相册选图片") {
                        attachSheet = false
                        pickImage.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            )
                        )
                    }
                    AttachOption("📄", "文件", "PDF / Word / txt / csv") {
                        attachSheet = false
                        pickFile.launch(Attach.MIME_TYPES)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { attachSheet = false }) { Text("取消", color = Ink2) }
            },
        )
    }
}

@Composable
private fun AttachOption(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(), onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = Ink3)
        }
    }
}

/** 拍照写到这里：cacheDir/exports 已经在 file_paths.xml 里授权过了 */
private fun newPhotoUri(ctx: Context): Uri? = runCatching {
    val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(dir, "photo_" + System.currentTimeMillis() + ".jpg")
    androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
}.getOrNull()

/** 用户消息里的图片缩略图 */
@Composable
private fun AttachmentStrip(urls: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        urls.take(4).forEach { url ->
            val bmp = remember(url) { decodeDataUrl(url) }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "附件图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    Modifier.size(86.dp).clip(RoundedCornerShape(10.dp)).background(SoftBg),
                    contentAlignment = Alignment.Center,
                ) { Text("🖼", fontSize = 18.sp) }
            }
        }
    }
}

private fun decodeDataUrl(url: String): ImageBitmap? = runCatching {
    val b64 = url.substringAfter("base64,", "")
    if (b64.isEmpty()) return null
    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

/** 一条消息：气泡 + 思考卡 + 工具痕迹 + 操作行 */
@Composable
private fun MessageItem(
    m: ChatMsg,
    isLast: Boolean,
    busy: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit,
    onApply: () -> Unit,
) {
    when (m.role) {
        "user" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (m.images.isNotEmpty()) {
                AttachmentStrip(m.images)
                Spacer(Modifier.height(6.dp))
            }
            if (m.content.isNotBlank()) {
                Surface(color = Brand, shape = BubbleShape, modifier = Modifier.widthIn(max = 300.dp)) {
                    Text(m.content, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(12.dp, 9.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 3.dp, end = 2.dp)) {
                MiniAction("复制") { onCopy(m.content) }
                MiniAction("编辑") { onEdit(m.content) }
            }
        }

        "assistant" -> Column(Modifier.fillMaxWidth()) {
            if (m.thinking.isNotBlank()) {
                ReasoningCard(text = m.thinking, live = false, ms = m.thinkMs)
                Spacer(Modifier.height(8.dp))
            }
            if (m.content.isNotBlank()) {
                Surface(color = CardBg, shape = BubbleShape, modifier = Modifier.widthIn(max = 330.dp)) {
                    Text(richText(m.content), color = Ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(13.dp, 10.dp))
                }
            }
            m.toolCalls.forEach { c -> ToolPill("🔧 " + AiTools.label(c.name)) }
            if (m.content.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 3.dp, start = 2.dp)) {
                    // 只说不做的时候，给一个一键落地
                    if (m.toolCalls.isEmpty() && !busy) MiniAction("帮我改", Brand) { onApply() }
                    MiniAction("复制") { onCopy(m.content) }
                    if (isLast && !busy) MiniAction("重新生成") { onRegenerate() }
                }
            }
        }

        "tool" -> {
            val label = m.name?.let { AiTools.label(it) } ?: "完成"
            val head = m.content.lineSequence().firstOrNull().orEmpty().trim()
            ToolPill("✅ $label：" + if (head.length > 48) head.take(48) + "…" else head)
        }

        else -> Unit
    }
}

/**
 * 思考过程卡片：跑的时候是「思考中」+ 呼吸动画 + 只露最后几行；结束后自动收起成「已思考 N 秒」，可以点开看全文。
 */
@Composable
private fun ReasoningCard(text: String, live: Boolean, ms: Long) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(live) { if (!live) expanded = false }
    val pulse = if (live) rememberPulse() else 1f

    Surface(color = SoftBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💡", fontSize = 12.sp, modifier = Modifier.alpha(pulse))
                Spacer(Modifier.size(6.dp))
                Text(
                    if (live) "思考中…" else "已思考 " + ((ms + 500) / 1000).coerceAtLeast(1L) + " 秒",
                    fontSize = 12.sp,
                    color = Ink3,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.alpha(if (live) pulse else 1f),
                )
                Spacer(Modifier.weight(1f))
                Text(if (expanded) "收起" else "展开", fontSize = 12.sp, color = Brand)
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                val scroll = rememberScrollState()
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = Ink3,
                    lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (expanded) Modifier.heightIn(max = 260.dp).verticalScroll(scroll) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun rememberPulse(): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val v by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseValue",
    )
    return v
}

@Composable
private fun MiniAction(text: String, color: Color = Ink3, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(), onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun ToolPill(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.Center) {
        Surface(color = SoftBg, shape = RoundedCornerShape(10.dp)) {
            Text(text, color = Ink3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun EditDialog(initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改一下这句", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
                colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text) }) { Text("重新发送") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("胶记", text))
    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
}

/**
 * 配料页里的「AI 帮你看一眼」卡片：不自动跑，点一下才问；思考过程和结果就地显示，不跳页。
 */
@Composable
fun AiAskCard(state: AppState, formulaId: String, onOpenChat: () -> Unit) {
    val ai = state.settings.ai
    val id = "ask:$formulaId"
    val s = AiSession.of(id)
    val f = state.formulaOf(formulaId)
    val ctx = LocalContext.current

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI 帮你看一眼", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.weight(1f))
            MiniAction("完整对话 ›") { onOpenChat() }
        }
        Spacer(Modifier.height(8.dp))

        if (!ai.ready) {
            Text("先去「设置 → AI 助手」填接口地址和 Key。", fontSize = 13.sp, color = Warn)
            return@AppCard
        }

        val answer = s.msgs.lastOrNull { it.role == "assistant" }?.content ?: ""
        if (!s.live && s.msgs.isEmpty()) {
            Text("点一下让它挑毛病，也可以直接让它改配方。", fontSize = 13.sp, color = Ink3)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    AiSession.send(
                        id,
                        "看一下「${f?.name ?: "这个配方"}」：合计是不是 100%？有没有明显不合理的配比？" +
                            "只挑最关键的一两点说；该改的地方直接调用工具改掉，然后告诉我改了什么。",
                        ai,
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(12.dp),
            ) { Text("问一下", fontSize = 14.sp) }
            return@AppCard
        }

        if (s.live) {
            ReasoningCard(text = s.think, live = true, ms = System.currentTimeMillis() - s.startedAt)
            if (s.status.isNotBlank()) ToolPill("⏳ " + s.status)
        }
        if (s.stream.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(richText(s.stream), fontSize = 14.sp, color = Ink, lineHeight = 21.sp)
        }
        if (answer.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(richText(answer), fontSize = 14.sp, color = Ink, lineHeight = 21.sp)
            val think = s.msgs.lastOrNull { it.role == "assistant" }?.thinking.orEmpty()
            if (think.isNotBlank() && !s.live) {
                Spacer(Modifier.height(8.dp))
                ReasoningCard(text = think, live = false, ms = s.msgs.lastOrNull { it.role == "assistant" }?.thinkMs ?: s.thinkMs)
            }
        }
        if (s.error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(s.error, fontSize = 13.sp, color = Danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (s.busy) {
                MiniAction("停止") { AiSession.stop(id) }
            } else {
                val changed = s.msgs.lastOrNull { it.role == "assistant" }?.toolCalls?.isNotEmpty() == true
                // 只说不做的时候，把「说的」一键变成「做的」
                if (answer.isNotBlank() && !changed) {
                    MiniAction("帮我改", Brand) { AiSession.send(id, APPLY_PROMPT, ai) }
                }
                MiniAction("再问一次") { AiSession.regenerate(id, ai) }
                if (answer.isNotBlank()) MiniAction("复制") { copyText(ctx, answer) }
                MiniAction("清空") { AiSession.clear(id) }
            }
        }
    }
}
