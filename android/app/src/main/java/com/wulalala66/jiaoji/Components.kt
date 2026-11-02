package com.wulalala66.jiaoji

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 每秒刷新的「现在」，用于所有倒计时 */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return now
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = CardBg,
    borderColor: Color = Line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                ) else Modifier
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

@Composable
fun OvenPill(state: AppState, oven: String) {
    val idx = state.ovenColorIdx(oven)
    Pill(state.ovenName(oven), ovenAccent(idx), ovenSoft(idx))
}

/**
 * 页面底部的主按钮（保存 / 开始计时 / 完成检测），高度、圆角、禁用态统一在这里。
 */
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
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            imeAction = if (imeNext) androidx.compose.ui.text.input.ImeAction.Next
            else androidx.compose.ui.text.input.ImeAction.Done,
        ),
    )
}

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
            .clip(CircleShape)
            .background(if (selected) BrandSoft else CardBg)
            .border(1.dp, if (selected) Brand else Line, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
fun StepRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    hint: String? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        StepButton("−", onMinus)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        StepButton("+", onPlus)
    }
}

@Composable
fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SoftBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Ink2)
    }
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

@Composable
fun CountdownText(msLeft: Long, color: Color = Ink, size: Int = 22) {
    Text(
        text = Fmt.hms(msLeft),
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
fun ProgressBar(fraction: Float, color: Color = Brand, height: Int = 6) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(SoftBg)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun VerdictPill(t: SampleTest) {
    when (verdictOf(t)) {
        Verdict.PASS -> Pill("合格", Ok, OkSoft)
        Verdict.FAIL -> Pill("不合格", Danger, DangerSoft)
        Verdict.SKIP -> Pill("未测", Ink3, SoftBg)
        Verdict.EMPTY -> Pill("待录入", Ink3, SoftBg)
    }
}

@Composable
fun CardTitleRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun SpacerH(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun SpacerW(w: Int) = Spacer(Modifier.width(w.dp))

fun greeting(now: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val h = c.get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "夜深了"
        h < 9 -> "早上好"
        h < 12 -> "上午好"
        h < 14 -> "中午好"
        h < 18 -> "下午好"
        else -> "晚上好"
    }
}

@Composable
fun RowGap(size: Int = 8) {
    Spacer(Modifier.height(size.dp))
}

@Composable
fun HorizontalGap(size: Int = 8) {
    Spacer(Modifier.width(size.dp))
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun RowScope.RowSpacer() = Spacer(Modifier.weight(1f))

@Composable
fun MiniBar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(SoftBg)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(CircleShape)
                .background(color)
        )
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
