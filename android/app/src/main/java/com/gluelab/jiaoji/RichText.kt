package com.gluelab.jiaoji

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * 模型回复里最常出现的是 **粗体**、`行内代码` 和「- 」列表。
 * 直接丢给 Text 会原样显示星号（截图里那种 `**填料超载**`），所以做一层最小解析。
 *
 * 只认这三种，别的都当普通文字：不做 HTML、不解析链接、不执行任何东西 ——
 * 模型说什么就显示什么，最多换个字重。
 */
fun richText(raw: String): AnnotatedString = buildAnnotatedString {
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = BrandSoft,
        color = BrandDark,
    )
    raw.lineSequence().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val trimmed = line.trimStart()
        when {
            // 代码块围栏本身不显示，里面照常输出
            trimmed.startsWith("```") -> Unit
            // 列表符号统一成小圆点，缩进保留
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("· ") -> {
                append(line.take(line.length - trimmed.length))
                append("· ")
                appendInline(trimmed.drop(2), codeStyle)
            }
            else -> appendInline(line, codeStyle)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String, codeStyle: SpanStyle) {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                append(text.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                pushStyle(codeStyle)
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}

/** 一行纯文本预览：去掉标记，用于工具提示、摘要这类地方 */
fun plainText(raw: String, max: Int = 60): String {
    val cleaned = raw
        .replace(Regex("```[a-zA-Z]*"), "")
        .replace("**", "")
        .replace("`", "")
        .replace(Regex("^\\s*[-*·]\\s+", RegexOption.MULTILINE), "· ")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()
    return if (cleaned.length > max) cleaned.take(max) + "…" else cleaned
}
