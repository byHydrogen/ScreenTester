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
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
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
    highlightColor: Int? = null,
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
                // 长按可选中并复制
                setTextIsSelectable(true)
            }
        },
        update = { tv ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeSp)
            tv.setTextColor(textColor)
            tv.barColor = linkColor
            if (highlightColor != null) tv.highlightColor = highlightColor

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

            // 标题行（\u2060 标记）在正文字号上放大一档，配合 <b> 呈现小标题观感
            val headingScale = 1.2f
            var h = 0
            while (h < str.length) {
                if (str[h] == '\u2060') {
                    var lineEnd = str.indexOf('\n', h)
                    if (lineEnd == -1) lineEnd = str.length
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(headingScale),
                        h, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    h = lineEnd + 1
                } else {
                    h++
                }
            }

            // EditText 的 text 是 Editable：以 SPANNABLE 方式注入，样式保留
            tv.setText(spannable, TextView.BufferType.SPANNABLE)
            tv.setLinkTextColor(linkColor)
            // 用只看点击的版本：不做"选中链接范围"，避免按下时出现主题色方块；
            // 长按选中文本仍由 TextView 自身处理（保留选区底色）
            tv.movementMethod = LinkOnlyMovementMethod()
        },
        modifier = modifier
    )
}

private class MarkdownTextView(context: Context) : AppCompatEditText(context) {
    init {
        // 只读 EditText：Editor 以可编辑文本的方式接管选择手柄的边缘自动滚动
        //（普通 selectable TextView 拖手柄到边缘不会滚）。
        // 键盘输入禁掉、输入法不弹、光标不画、去掉输入框自带下划线背景——
        // 对外仍然是"可选中复制的只读文本"
        keyListener = null
        showSoftInputOnFocus = false
        isCursorVisible = false
        background = null
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dp = context.resources.displayMetrics.density

    var barColor: Int
        get() = barPaint.color
        set(value) { barPaint.color = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        try {
            val l = layout ?: return
            val t = text ?: return
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
            // \u2060 标记标题行：渲染阶段整行放大一档（fromHtml 不支持 <font size>，字号不走 HTML）
            trimmed.startsWith("### ") -> { sb.append("\u2060<b>${inlineFormat(trimmed.removePrefix("### "))}</b><br>"); continue }
            trimmed.startsWith("## ") -> { sb.append("\u2060<b>${inlineFormat(trimmed.removePrefix("## "))}</b><br>"); continue }
            trimmed.startsWith("# ") -> { sb.append("\u2060<b>${inlineFormat(trimmed.removePrefix("# "))}</b><br>"); continue }
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

// 负责"点开链接" + 自身的拖拽滚动（带惯性）：
// 1) 不调用 LinkMovementMethod.onTouchEvent，不会把链接范围设为选区（那会画出主题色高亮方块）；
// 2) 外层不包 Compose verticalScroll（否则拖动选择手柄到边缘时外部容器不会跟着滚），
//    滚动所有权归 TextView 自己：手指拖拽 1:1 跟手，松手按速度惯性滑行，
//    选择手柄拖出边缘由 Editor 自动滚
private class LinkOnlyMovementMethod : LinkMovementMethod() {
    private var lastY = 0f
    private var isDragging = false
    private var touchSlop = -1
    private var velocityTracker: VelocityTracker? = null
    private var scroller: OverScroller? = null
    private var flingView: TextView? = null

    // 惯性滑行：每帧消费 scroller 进度并滚动，直到停下
    private val flingRunnable = object : Runnable {
        override fun run() {
            val view = flingView ?: return
            val s = scroller ?: return
            if (s.computeScrollOffset()) {
                view.scrollTo(0, s.currY.coerceIn(0, maxScroll(view)))
                view.postOnAnimation(this)
            } else {
                flingView = null
            }
        }
    }

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        if (touchSlop < 0) touchSlop = ViewConfiguration.get(widget.context).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFling(widget)
                obtainTracker().clear()
                obtainTracker().addMovement(event)
                lastY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = lastY - event.y
                if (isDragging || kotlin.math.abs(dy) > touchSlop) {
                    isDragging = true
                    lastY = event.y
                    scrollToClamped(widget, (widget.scrollY + dy).toInt())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (!isDragging) {
                    val handled = clickLinkIfAny(widget, buffer, event)
                    recycleTracker()
                    return handled
                }
                // 先取速度再回收 tracker（recycle 后不可再用）
                val tracker = velocityTracker
                var vy = 0f
                if (tracker != null) {
                    tracker.computeCurrentVelocity(1000)
                    vy = tracker.yVelocity
                }
                recycleTracker()
                if (kotlin.math.abs(vy) > ViewConfiguration.get(widget.context).scaledMinimumFlingVelocity) {
                    startFling(widget, vy)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                recycleTracker()
                return true
            }
        }
        return false
    }

    private fun obtainTracker(): VelocityTracker {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        return velocityTracker!!
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun maxScroll(widget: TextView): Int =
        ((widget.layout?.height ?: 0) - widget.height + widget.paddingTop + widget.paddingBottom).coerceAtLeast(0)

    private fun scrollToClamped(widget: TextView, y: Int) {
        widget.scrollTo(0, y.coerceIn(0, maxScroll(widget)))
    }

    private fun startFling(widget: TextView, vy: Float) {
        if (maxScroll(widget) == 0) return
        val s = scroller ?: OverScroller(widget.context).also { scroller = it }
        s.forceFinished(true)
        s.fling(0, widget.scrollY, 0, -vy.toInt(), 0, 0, 0, maxScroll(widget))
        flingView = widget
        widget.removeCallbacks(flingRunnable)
        widget.postOnAnimation(flingRunnable)
    }

    private fun stopFling(widget: TextView) {
        scroller?.forceFinished(true)
        widget.removeCallbacks(flingRunnable)
        flingView = null
    }

    private fun clickLinkIfAny(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val l = widget.layout ?: return false
        // 内部滚动后，布局坐标 = 视口坐标 + scrollY
        val offset = l.getOffsetForHorizontal(l.getLineForVertical((event.y + widget.scrollY).toInt()), event.x)
        val spans = buffer.getSpans(offset, offset, ClickableSpan::class.java)
        if (spans.isNotEmpty()) {
            widget.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            spans[0].onClick(widget)
            return true
        }
        return false
    }
}
