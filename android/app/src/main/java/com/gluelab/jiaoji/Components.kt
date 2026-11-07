package com.gluelab.jiaoji

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ============================================================
//  通用组件：四个工具页共用的小积木，样式只在这里定义一次
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = CardBg,
    borderColor: Color = Line,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick,
                    )
                    else -> Modifier
                }
            )
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun Pill(
    text: String,
    fg: Color = Ink2,
    bg: Color = SoftBg,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** 输入框配色：聚焦描边用品牌色，其余统一细灰边、白底 */
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    unfocusedBorderColor = Line,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = Brand,
)

/**
 * 数字输入框：统一小键盘、Next 跳转、样式，各处复用。
 * 用文本而不是 Double，避免「2.」这种中间状态被打断。
 */
@Composable
fun NumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    imeNext: Boolean = true,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink3) } },
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = fieldColors(),
        trailingIcon = suffix?.let { { Text(it, style = MaterialTheme.typography.labelSmall, color = Ink3) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (imeNext) ImeAction.Next else ImeAction.Done,
        ),
    )
}

/** 页面底部的主按钮（保存 / 导出 / 完成），高度、圆角、禁用态统一在这里 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) Brand else Line)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = enabled,
            ) { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Ink3,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Ink3)
        if (action != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Brand)
            }
        }
    }
}

@Composable
fun EmptyHint(emoji: String, title: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Ink2)
        Spacer(Modifier.height(3.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Ink3, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) BrandSoft else SoftBg)
            .border(1.dp, if (selected) Brand else Line, MaterialTheme.shapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) BrandDark else Ink2,
            maxLines = 1,
        )
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink2)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line)
    )
}

@Composable
fun IconAction(
    icon: ImageVector,
    tint: Color = Ink2,
    contentDescription: String = "",
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 输入落库的防抖时长；测试里会调成 0 让断言稳定 */
object AppConfig {
    var debounceMs: Long = 300
}

/**
 * 输入框本地即时显示 + 停手一小会儿再落库。
 *
 * 直接用 store 里的值当输入框 value 会有两个问题：每敲一个字符就整库序列化一次
 * （数据一多就卡、按钮像点不动），并且输入法组字会被打断。这里先在本地状态里
 * 打字，停顿 [delayMs] 毫秒后提交；离开页面时把没提交的补上。
 */
@Composable
fun rememberLocalText(
    initial: String,
    key: Any?,
    delayMs: Long = AppConfig.debounceMs,
    onCommit: (String) -> Unit,
): MutableState<String> {
    val st = remember(key) { mutableStateOf(initial) }
    val latest = rememberUpdatedState(onCommit)
    LaunchedEffect(key, st.value) {
        if (st.value != initial) {
            delay(delayMs)
            latest.value(st.value)
        }
    }
    DisposableEffect(key) {
        onDispose { if (st.value != initial) latest.value(st.value) }
    }
    return st
}

// ============================================================
//  聊天列表：跟随底部
//  参考主流对话 App（ChatGPT / Claude / LobeChat）的通用做法：
//    · 默认贴底跟随，新消息和流式输出都自动滚下去；
//    · 用户手指一拖就立刻取消跟随，往上翻历史不被打断；
//    · 拖回底部、或点「回到底部」，重新开始跟随。
//  这样就不会出现「想往上翻，被一条新 token 拽回底部」的情况。
// ============================================================

@Composable
fun rememberFollowBottom(listState: LazyListState): MutableState<Boolean> {
    val follow = remember { mutableStateOf(true) }

    val dragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) {
        if (dragging) follow.value = false
    }

    // 手指松开、惯性也停下来之后，看是不是又停在底部了
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@LaunchedEffect
        val atBottom = last.index >= info.totalItemsCount - 1 &&
            last.offset + last.size <= info.viewportEndOffset + 24
        if (atBottom) follow.value = true
    }

    return follow
}

/**
 * 滚到内容最底部。
 * 索引传 totalItemsCount 会被内部 coerce 到末尾，效果就是「贴底」；
 * 传 lastIndex 反而会把最后一条的顶部对齐到屏幕顶部，长消息会看不全。
 */
suspend fun LazyListState.scrollToBottom(animate: Boolean = false) {
    val count = layoutInfo.totalItemsCount
    if (count <= 0) return
    if (animate) animateScrollToItem(count) else scrollToItem(count)
}
