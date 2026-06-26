package com.hydrogen.screentester

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.LeadingMarginSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    textColor: Int,
    linkColor: Int,
    onLinkClick: ((String) -> Unit)? = null
) {
    val density = LocalDensity.current
    val fontSizeSp = with(density) { fontSize.toPx() }
    val lineHeightSp = with(density) { lineHeight.toPx() }
    val context = LocalContext.current
    val html = markdownToHtml(text)

    AndroidView(
        factory = { ctx ->
            MarkdownTextView(ctx).apply {
                setLineSpacing(0f, lineHeightSp / fontSizeSp)
            }
        },
        update = { tv ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeSp)
            tv.setTextColor(textColor)
            tv.barColor = linkColor

            val styled = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            val spannable = SpannableString(styled)

            // 替换 URLSpan 为可弹窗的 ClickableSpan
            for (span in spannable.getSpans(0, spannable.length, URLSpan::class.java)) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val url = span.url
                spannable.removeSpan(span)
                spannable.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            val fixedUrl = ensureScheme(url)
                            onLinkClick?.invoke(fixedUrl) ?: run {
                                try {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fixedUrl)))
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 给引用行（含零宽空格 '​' 的行）加 LeadingMarginSpan 实现文字避让
            val barMargin = (12 * context.resources.displayMetrics.density).toInt()
            val str = spannable.toString()
            var i = 0
            while (i < str.length) {
                if (str[i] == '​') {
                    // 找到行尾
                    var lineEnd = str.indexOf('\n', i)
                    if (lineEnd == -1) lineEnd = str.length
                    spannable.setSpan(
                        LeadingMarginSpan.Standard(barMargin, 0),
                        i, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    i = lineEnd + 1
                } else {
                    i++
                }
            }

            tv.text = spannable
            tv.setLinkTextColor(linkColor)
            tv.movementMethod = LinkMovementMethod.getInstance()
        },
        modifier = modifier
    )
}

private class MarkdownTextView(context: Context) : AppCompatTextView(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dp = context.resources.displayMetrics.density

    var barColor: Int
        get() = barPaint.color
        set(value) { barPaint.color = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        try {
            val l = layout ?: return
            val t = text
            for (line in 0 until l.lineCount) {
                val charStart = l.getLineStart(line)
                if (charStart < t.length && t[charStart] == '​') {
                    val lineTop = l.getLineTop(line).toFloat()
                    val lineBottom = l.getLineBottom(line).toFloat()
                    canvas.drawRoundRect(
                        0f, lineTop + 2f * dp, 4f * dp, lineBottom - 2f * dp,
                        2f * dp, 2f * dp, barPaint
                    )
                }
            }
        } catch (_: Exception) {}
        super.onDraw(canvas)
    }
}

private fun ensureScheme(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

private fun markdownToHtml(text: String): String {
    val sb = StringBuilder()
    val lines = text.split("\n")
    var inList = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            if (inList) { sb.append("</ul>"); inList = false }
            sb.append("<br>")
            continue
        }
        when {
            trimmed.startsWith("### ") -> { sb.append("<b>${inlineFormat(trimmed.removePrefix("### "))}</b><br>"); continue }
            trimmed.startsWith("## ") -> { sb.append("<b>${inlineFormat(trimmed.removePrefix("## "))}</b><br>"); continue }
            trimmed.startsWith("# ") -> { sb.append("<b>${inlineFormat(trimmed.removePrefix("# "))}</b><br>"); continue }
        }
        if (trimmed.startsWith("> ")) {
            // 零宽空格标记 → onDraw 画竖条 + post-process 加 LeadingMarginSpan
            sb.append("​${inlineFormat(trimmed.removePrefix("> "))}<br>"); continue
        }
        if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
            val checked = trimmed.contains("[x]") || trimmed.contains("[X]")
            val content = trimmed.replaceFirst(Regex("^-\\s\\[.\\]\\s"), "")
            sb.append("${if (checked) "☑ " else "☐ "}${inlineFormat(content)}<br>"); continue
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            if (!inList) { sb.append("<ul>"); inList = true }
            sb.append("<li>${inlineFormat(trimmed.substring(2))}</li>"); continue
        }
        if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
            if (!inList) { sb.append("<ul>"); inList = true }
            sb.append("<li>${inlineFormat(trimmed.replaceFirst(Regex("^\\d+\\.\\s"), ""))}</li>"); continue
        }
        if (inList) { sb.append("</ul>"); inList = false }
        sb.append("${inlineFormat(trimmed)}<br>")
    }
    if (inList) sb.append("</ul>")
    return sb.toString()
}

private fun inlineFormat(text: String): String {
    var result = text
    result = result.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)")) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
    result = result.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }
    result = result.replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }
    result = result.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
    result = result.replace(Regex("_(.+?)_")) { "<em>${it.groupValues[1]}</em>" }
    return result
}
