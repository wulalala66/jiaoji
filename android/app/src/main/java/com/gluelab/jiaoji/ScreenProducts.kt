package com.gluelab.jiaoji

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 产品（客户）→ 烘烤条件表。
 *
 * 交互：
 *  · 点产品 = 按这条条件开始倒计时；一个产品有多条条件时，先弹一层让你选
 *  · 长按产品 = 改（增删条件、电泳板）
 */
@Composable
fun ProductsScreen(state: AppState) {
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<Product?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    // 拖动期间只改这几个内存状态，一次都不碰数据源 —— 松手才提交。
    // 参考 sh.calvin.reorderable 的做法：数据不动，靠 translationY 让位。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTo by remember { mutableIntStateOf(-1) }
    var dragHeight by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val key = q.trim()
    val list = state.products.filter { p ->
        key.isBlank() || p.name.contains(key, true) ||
            p.bakes.any { "${it.tempC} ${it.minutes}".contains(key) }
    }

    /**
     * 点条件 = 直接给系统时钟写一个倒计时。
     *
     * 用 android.intent.action.SET_TIMER，不需要任何权限，OPPO/华为/小米/三星的
     * 自带时钟都认；到点由系统响铃，App 被杀也不影响。
     * 极少数机型没有能接这个动作的时钟 App，才退回应用内计时。
     */
    fun start(p: Product, b: BakePreset) {
        TimerAlert.cancel(ctx)
        val label = "${p.name} ${b.tempC}℃"
        val via = SystemTimer.start(ctx, b.minutes, label)
        if (via != null) {
            // 系统时钟接管了，App 这边不再重复计时
            Repo.I.stopTimer()
            Toast0(ctx, "已交给系统时钟：${b.minutes} 分钟（$via）")
            return
        }
        Repo.I.startTimer(p.name, b.tempC, b.minutes)
        Toast0(ctx, "这台手机没有可调用的时钟，改用应用内计时")
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
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

        state.timer?.let { t ->
            TimerBanner(
                timer = t,
                onPause = { Repo.I.pauseTimer() },
                onResume = { Repo.I.resumeTimer() },
                onStop = { confirmStop = true },
            )
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(list, key = { _, p -> p.id }) { index, p ->
                // 别的卡片要往哪个方向让位：被拖的在往下走，中间的往上让一格，反之亦然
                val targetShift = when {
                    draggingId == null || dragTo < 0 || dragFrom < 0 || draggingId == p.id -> 0f
                    index in (dragFrom + 1)..dragTo -> -dragHeight.toFloat()
                    index in dragTo until dragFrom -> dragHeight.toFloat()
                    else -> 0f
                }
                val shift by animateFloatAsState(
                    targetValue = targetShift,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
                    label = "shift",
                )
                AppCard(
                    modifier = Modifier
                        .zIndex(if (draggingId == p.id) 1f else 0f)
                        .graphicsLayer {
                            val dragging = draggingId == p.id
                            translationY = if (dragging) dragDelta else shift
                            if (dragging) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 12.dp.toPx()
                            }
                        },
                    onClick = {
                        when {
                            p.bakes.isEmpty() -> Toast0(ctx, "「${p.name}」还没有烘烤条件，长按可以编辑")
                            p.bakes.size == 1 -> start(p, p.bakes.first())
                            else -> choosing = p
                        }
                    },
                    onLongClick = { editing = p },
                ) {
                  Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    BakePresetPills(p.bakes)
                    if (p.hasPlates) {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            "🧫 " + p.plateText(),
                            if (p.plateNew > 0) Warn else Ink2,
                            if (p.plateNew > 0) WarnSoft else SoftBg,
                        )
                        val noted = p.plateLines().filter { it.contains("·") }
                        if (noted.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(noted.joinToString(" ｜ "), style = MaterialTheme.typography.bodySmall, color = Ink3)
                        }
                    }
                    }
                    // 拖动排序：只在没搜索时显示，避免「拖了但顺序看着乱」
                    if (key.isBlank()) {
                        DragHandle(
                            active = draggingId == p.id,
                            modifier = Modifier.pointerInput(p.id) {
                                // PointerInputScope 本身是 Density，阈值在这里换px
                                val edgeZonePx = 72.dp.toPx()
                                val maxStepPx = 14.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // 一按下就吃掉，父级的长按编辑收不到，两个手势不再打架
                                    down.consume()
                                    val vis = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == p.id } ?: return@awaitEachGesture
                                    draggingId = p.id
                                    dragFrom = vis.index
                                    dragTo = vis.index
                                    dragHeight = vis.size
                                    dragDelta = 0f
                                    var lastY = down.position.y
                                    try {
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed) break
                                            ch.consume()
                                            dragDelta += ch.position.y - lastY
                                            lastY = ch.position.y

                                            val info = listState.layoutInfo
                                            // 拖到上下边缘时自动滚动（列表比屏幕长的时候必需）
                                            val pointerY = (info.visibleItemsInfo
                                                .firstOrNull { it.key == p.id }?.offset ?: vis.offset) +
                                                down.position.y + dragDelta
                                            val edge = edgeZonePx
                                            val step = when {
                                                pointerY > info.viewportEndOffset - edge ->
                                                    ((pointerY - (info.viewportEndOffset - edge)) / edge)
                                                        .coerceIn(0f, 1f) * maxStepPx
                                                pointerY < info.viewportStartOffset + edge ->
                                                    -(((info.viewportStartOffset + edge) - pointerY) / edge)
                                                        .coerceIn(0f, 1f) * maxStepPx
                                                else -> 0f
                                            }
                                            if (step != 0f) listState.dispatchRawDelta(step)

                                            // 每一帧都重新取布局：自动滚动之后 vis 里的 offset 已经过期
                                            val fresh = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == p.id } ?: vis
                                            val center = fresh.offset + dragHeight / 2f + dragDelta
                                            val over = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { center >= it.offset && center < it.offset + it.size }
                                            if (over != null && over.key != p.id) dragTo = over.index
                                        }
                                    } finally {
                                        val to = dragTo
                                        draggingId = null
                                        dragDelta = 0f
                                        dragFrom = -1
                                        dragTo = -1
                                        // 松手才提交一次：整个拖动过程只写一次库
                                        if (to >= 0 && to != vis.index) Repo.I.moveProduct(p.id, to)
                                    }
                                }
                            },
                        )
                    }
                  }
                }
            }
            if (list.isEmpty()) {
                item { EmptyHint("🔍", "没找到", "换个词试试，或者点右上角 ＋ 新增一条") }
            }
            item {
                Text(
                    "点一下条件 → 交给系统时钟计时；长按产品可以改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    // 多条件：先选一条
    choosing?.let { p ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("选一个烘烤条件", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("「${p.name}」有 ${p.bakes.size} 条条件，点哪条就按哪条计时。", style = MaterialTheme.typography.bodySmall, color = Ink3)
                    Text("会用系统时钟计时，到点由手机自己响。", style = MaterialTheme.typography.bodySmall, color = Ink3)
                    p.bakes.forEach { b ->
                        Surface(
                            onClick = {
                                choosing = null
                                start(p, b)
                            },
                            color = BrandSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(b.text(), color = BrandDark, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Text("开始计时 →", color = Brand, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("取消", color = Ink2) } },
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("结束这次计时？", style = MaterialTheme.typography.titleMedium) },
            text = { Text("还剩 ${fmtLeft(state.timer?.remaining(System.currentTimeMillis()) ?: 0L)}，结束了就不提醒了。", style = MaterialTheme.typography.bodyMedium, color = Ink2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    TimerAlert.cancel(ctx)
                    Repo.I.stopTimer()
                }) { Text("结束", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("继续烤", color = Ink2) } },
        )
    }

    if (creating) ProductEditDialog(existing = null, onDismiss = { creating = false })
    editing?.let { p -> ProductEditDialog(existing = p, onDismiss = { editing = null }) }
}

private fun Toast0(ctx: android.content.Context, msg: String) {
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------- 计时条

@Composable
private fun TimerBanner(timer: BakeTimer, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = timer.remaining(now)
    val done = left <= 0 && !timer.paused

    // 到点只提醒一次（按 endAt 记住，换一个计时会重新算）
    var firedFor by remember { mutableStateOf(-1L) }
    LaunchedEffect(done, timer.endAt) {
        if (done && firedFor != timer.endAt) {
            firedFor = timer.endAt
            TimerAlert.fire(ctx, "烘烤到点了", timer.label().ifBlank { "该出炉了" })
        }
    }

    val bg = when {
        done -> DangerSoft
        timer.paused -> WarnSoft
        else -> BrandSoft
    }
    Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timer.label().ifBlank { "烘烤计时" },
                    fontSize = 13.sp,
                    color = if (done) Danger else Ink2,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text("应用内计时 · 共 ${timer.minutes} 分", fontSize = 12.sp, color = Ink3)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (done) "到点了，出炉！" else fmtLeft(left),
                fontSize = if (done) 22.sp else 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (done) Danger else Ink,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            if (timer.paused) {
                Text("已暂停", fontSize = 12.sp, color = Warn)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!done) {
                    Surface(
                        onClick = { if (timer.paused) onResume() else onPause() },
                        color = CardBg,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            if (timer.paused) "继续" else "暂停",
                            fontSize = 13.sp,
                            color = Ink,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                Surface(onClick = onStop, color = CardBg, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        if (done) "知道了" else "结束",
                        fontSize = 13.sp,
                        color = if (done) Danger else Ink2,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

private fun fmtLeft(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

// ---------------------------------------------------------------- 卡片里的条件

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BakePresetPills(bakes: List<BakePreset>) {
    if (bakes.isEmpty()) {
        Text("还没填烘烤条件（长按编辑）", style = MaterialTheme.typography.bodySmall, color = Ink3)
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

// ---------------------------------------------------------------- 编辑弹层

private data class PresetRow(val id: String, val temp: String, val min: String)

@Composable
private fun ProductEditDialog(existing: Product?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    val rows = remember {
        mutableStateListOf<PresetRow>().apply {
            existing?.bakes?.forEach { add(PresetRow(Repo.uid(), "${it.tempC}", "${it.minutes}")) }
            if (isEmpty()) add(PresetRow(Repo.uid(), "140", "30"))
        }
    }
    val plates = remember {
        mutableStateListOf<Plate>().apply { existing?.plates?.forEach { add(it.copy()) } }
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

                Spacer(Modifier.height(10.dp))
                ThinDivider()
                Spacer(Modifier.height(10.dp))
                Text("电泳板（每块单独记用途，不需要就不加）", style = MaterialTheme.typography.labelSmall, color = Ink3)
                Spacer(Modifier.height(6.dp))
                plates.forEachIndexed { i, plate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        ChoiceChip(
                            text = if (plate.fresh) "新板" else "旧板",
                            selected = plate.fresh,
                            onClick = { plates[i] = plate.copy(fresh = !plate.fresh) },
                        )
                        OutlinedTextField(
                            value = plate.note,
                            onValueChange = { v -> plates[i] = plate.copy(note = v) },
                            placeholder = { Text("用途 / 要求，可空", style = MaterialTheme.typography.bodySmall, color = Ink3) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = fieldColors(),
                        )
                        IconAction(Icons.Filled.Delete, Danger, "删掉这块板") { plates.removeAt(i) }
                    }
                }
                TextButton(onClick = { plates.add(Plate(Repo.uid(), fresh = false, note = "")) }) {
                    Text("＋ 加一块板", color = Brand)
                }
                Text(
                    "例：安徽万安 4 块 —— 1 块新板写「2mm 流挂」，3 块旧板空着。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )

                if (confirmDelete) {
                    Spacer(Modifier.height(6.dp))
                    Text("删掉「${existing?.name ?: ""}」？", style = MaterialTheme.typography.bodySmall, color = Danger)
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
                        if (t in 30..300 && m in 1..600) BakePreset(t, m) else null
                    }
                    val plateList = plates.toList()
                    if (existing == null) Repo.I.addProduct(name.trim(), bakes, plateList)
                    else Repo.I.updateProduct(existing.id, name.trim(), bakes, plateList)
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

/** 右侧的三条杠：按住上下拖，换位置 */
@Composable
private fun DragHandle(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 6.dp, top = 2.dp)
            .size(width = 34.dp, height = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) BrandSoft else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = "按住拖动排序",
            tint = if (active) Brand else Ink3,
            modifier = Modifier.size(20.dp),
        )
    }
}
