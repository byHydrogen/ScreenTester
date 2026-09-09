package com.hydrogen.screentester

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import android.view.View
import android.view.animation.PathInterpolator

class TestFrameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private var borderPath = Path()

    // 用于追踪倒计时淡入的时间戳
    private var countdownStartTime: Long = 0L

    // 提示文案转场状态（旧文案淡出缩小、新文案淡入放大）
    private var promptType: String? = null
    private var promptFadeText: String? = null
    private var promptFadeStart = 0L
    private var promptLastText = ""
    private val promptFadeMs = 150f

    // 精度模式"退出倒计时"的淡出（松手后 150ms 内缩小淡出）
    private var lastCountdownText: String? = null
    private var countdownHideAt = 0L

    // 圆角参数文案转场（拨"用自定义圆角半径"或纯净模式开关时内容会变）
    private var paramTextKey: String? = null
    private var paramLastLines: List<ParamLine> = emptyList()
    private var paramFadeLines: List<ParamLine> = emptyList()
    private var paramFadeStart = 0L

    // 签名显隐转场（纯净模式开关时淡出/淡入）
    private var sigCompactLast = false
    private var sigFadeStart = 0L

    // 用于检测外部“切换圆角模式”按钮引发的状态变更
    private var lastUseCustomRadius: Boolean? = null

    // === Shader 缓存相关变量 ===
    private var currentGradientShader: Shader? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var lastMultiColorMode: Boolean = false
    private var lastSelectedColors: List<Int> = emptyList()
    private var lastSegmentLength: Float = -1f

    // 记录倒计时秒数
    var remainingSeconds: Int = 0
        set(value) {
            // 记录初始启动时间戳
            if (field == 0 && value > 0) {
                countdownStartTime = System.currentTimeMillis()
            } else if (value == 0) {
                countdownStartTime = 0L
            }
            field = value
            invalidate()
        }

    // 测试模式（切换时由 modeProgress 驱动过渡动画）
    var isAdvancedMode: Boolean = false
        set(value) {
            if (field != value) {
                isHapticFeedbackEnabled = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                animateModeProgress(if (value) 1f else 0f)
            }
            field = value
            invalidate()
        }

    // 模式切换进度：0 = 圆角，1 = 精度（onDraw 按它插值绘制）
    var modeProgress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private var modeAnimator: ValueAnimator? = null

    private fun animateModeProgress(target: Float) {
        modeAnimator?.cancel()
        modeAnimator = ValueAnimator.ofFloat(modeProgress, target).apply {
            // 双向统一 400ms
            duration = 400L
            // PathInterpolator(0.4, 0, 0.2, 1) = FastOutSlowIn 曲线
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { modeProgress = it.animatedValue as Float }
            start()
        }
    }

    // 检查并更新渐变 Shader，只有在需要时才重新分配内存
    private fun updateGradientShaderIfNeeded() {
        val currentMultiColorMode = ThemeSettings.isMultiColorMode
        val currentSelectedColors = ThemeSettings.multiColorSelectedColors
        val currentSegmentLength = ThemeSettings.multiColorSegmentLength

        // 检查是否需要更新（尺寸变化、模式变化、颜色配置变化）
        val needUpdate = lastWidth != width || lastHeight != height ||
                lastMultiColorMode != currentMultiColorMode ||
                lastSegmentLength != currentSegmentLength ||
                lastSelectedColors != currentSelectedColors

        if (!needUpdate) return

        // 记录当前状态
        lastWidth = width
        lastHeight = height
        lastMultiColorMode = currentMultiColorMode
        lastSegmentLength = currentSegmentLength
        lastSelectedColors = currentSelectedColors.toList() // 复制一份，防止外部修改引用

        if (currentMultiColorMode && currentSelectedColors.size >= 2 && width > 0 && height > 0) {
            val repeatCount = if (currentSegmentLength == 0f) 1 else {
                val totalLength = width.coerceAtLeast(height)
                (totalLength / (currentSegmentLength * 200)).toInt().coerceAtLeast(1)
            }
            val colors = mutableListOf<Int>()
            val positions = mutableListOf<Float>()

            for (i in 0 until repeatCount) {
                for ((index, color) in currentSelectedColors.withIndex()) {
                    colors.add(color)
                    positions.add((i * currentSelectedColors.size + index).toFloat() / (repeatCount * currentSelectedColors.size))
                }
            }
            colors.add(currentSelectedColors.last())
            positions.add(1.0f)

            currentGradientShader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                colors.toIntArray(), positions.toFloatArray(), Shader.TileMode.REPEAT
            )
        } else {
            currentGradientShader = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        // 背景：圆角 = 自定义色；精度 = 开关?自定义色:黑27%。切换时两色插值
        val advancedBg = if (ThemeSettings.precisionUsesCustomBg) ThemeSettings.testLineBgColor else 0x44000000
        canvas.drawColor(lerpColor(ThemeSettings.testLineBgColor, advancedBg, modeProgress))

        // 通过每帧差分比对，捕捉外部“切换系统默认/手动校准”按钮的点击事件
        val currentCustomRadius = ThemeSettings.useCustomRadius
        if (lastUseCustomRadius != null && lastUseCustomRadius != currentCustomRadius) {
            isHapticFeedbackEnabled = true
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        lastUseCustomRadius = currentCustomRadius

        // 动态检查并更新 Shader (避免每帧创建对象)
        updateGradientShaderIfNeeded()

        // 应用颜色或 Shader
        if (ThemeSettings.isMultiColorMode && ThemeSettings.multiColorSelectedColors.size >= 2) {
            linePaint.shader = currentGradientShader
            linePaint.color = Color.WHITE
        } else {
            linePaint.shader = null
            linePaint.color = ThemeSettings.testLineColor
        }

        // 交叉过渡：线条纯淡入淡出，文字类淡入淡出 + 轻微缩放
        val textScaleNormal = 1f - 0.1f * modeProgress
        val textScaleAdvanced = 0.9f + 0.1f * modeProgress
        if (modeProgress < 1f) {
            val fade = ((1f - modeProgress) * 255).toInt()
            var count = canvas.saveLayerAlpha(0f, 0f, w, h, fade)
            drawNormalBorder(canvas)
            canvas.restoreToCount(count)
            count = canvas.saveLayerAlpha(0f, 0f, w, h, fade)
            scaleAround(canvas, textScaleNormal)
            drawNormalTexts(canvas)
            canvas.restoreToCount(count)
        }
        if (modeProgress > 0f) {
            val fade = (modeProgress * 255).toInt()
            var count = canvas.saveLayerAlpha(0f, 0f, w, h, fade)
            drawAdvancedLines(canvas)
            canvas.restoreToCount(count)
            count = canvas.saveLayerAlpha(0f, 0f, w, h, fade)
            scaleAround(canvas, textScaleAdvanced)
            drawAdvancedTexts(canvas)
            canvas.restoreToCount(count)
        }

        // 机型 + 主标题：两模式之间做字号/位置插值（morph），不参与淡入淡出
        drawCenterTitles(canvas, modeProgress)
        // 签名：平移过渡（纯净模式下淡出，由提示区那份接管）
        drawSignature(canvas, modeProgress)
    }

    /** 普通文字颜色：跟随线条色（开关开启）或自定义字体色 */
    private fun bodyTextColor(): Int =
        if (ThemeSettings.textFollowsLine) ThemeSettings.testLineColor
        else ThemeSettings.testLineTextColor

    // 圆角（线条）模式专属线条：屏幕边框（模式切换时纯淡入淡出）
    private fun drawNormalBorder(canvas: Canvas) {
        val currentThickness = ThemeSettings.testLineThickness
        linePaint.strokeWidth = currentThickness

        val w = width.toFloat()
        val h = height.toFloat()
        val inset = currentThickness / 2f

        // 几何统一走公共函数
        borderPath = buildScreenBorderPath(w, h, rootWindowInsets, currentThickness, context)
        canvas.drawPath(borderPath, linePaint)
    }

    // 圆角模式文字：圆角参数、退出提示
    private fun drawNormalTexts(canvas: Canvas) {
        val currentThickness = ThemeSettings.testLineThickness

        val w = width.toFloat()
        val h = height.toFloat()
        val totalDuration = 250f
        val elapsed = System.currentTimeMillis() - countdownStartTime
        if (elapsed < totalDuration && countdownStartTime > 0L) {
            postInvalidateOnAnimation() // 持续驱动高刷重绘
        }

        val centerX = w / 2f
        val centerY = h / 2f

        // 参数文字：内容变化（拨"用自定义圆角半径"或纯净模式开关）时做淡出缩小 / 淡入放大
        val paramLines = normalParamLines(h, currentThickness)
        val paramKey = paramLines.joinToString("|") { it.text }
        if (paramKey != paramTextKey) {
            paramFadeLines = paramLastLines
            paramFadeStart = System.currentTimeMillis()
            paramTextKey = paramKey
        }
        paramLastLines = paramLines
        val paramT = if (paramFadeStart == 0L) 1f
        else ((System.currentTimeMillis() - paramFadeStart) / promptFadeMs).coerceIn(0f, 1f)
        if (paramT < 1f) {
            postInvalidateOnAnimation()
            drawParamLines(canvas, paramFadeLines, centerX, 1f - paramT, 1f - 0.1f * paramT)
        }
        drawParamLines(canvas, paramLines, centerX, paramT, 0.9f + 0.1f * paramT)

        // 中央机型与主标题在 drawCenterTitles 里做模式间 morph，这里只画其余内容
        textPaint.textSize = 42f
        textPaint.isFakeBoldText = false

        // 提示文案（退出提示 ↔ "请继续按住"）：切换时淡入淡出 + 轻微缩放
        val promptY = centerY + 140f
        fun drawPrompt(text: String, alphaF: Float, scale: Float) {
            // 显式用 body 色：纯净模式参数区为空不设色，不显式设置会继承主标题的残留颜色
            textPaint.color = bodyTextColor()
            textPaint.alpha = (140 * alphaF).toInt()
            val count = canvas.save()
            scaleAroundPoint(canvas, centerX, promptY, scale)
            canvas.drawText(text, centerX, promptY, textPaint)
            canvas.restoreToCount(count)
        }

        // 提示文案：纯净模式 @byHydrogen ↔ 请继续按住，普通模式 退出提示 ↔ 请继续按住。
        // 切换时统一转场：旧文案淡出缩小、新文案淡入放大
        val compact = ThemeSettings.isCompactModeEnabled
        val exitHint = if (ThemeSettings.longPressExitEnabled) {
            "按返回键 或 长按${ThemeSettings.longPressExitSeconds}秒 退出"
        } else {
            "按返回键退出"
        }
        val holding = remainingSeconds > 0
        val promptText = when {
            holding -> "请继续按住 $remainingSeconds 秒..."
            compact -> "@byHydrogen"
            else -> exitHint
        }
        val type = if (holding) "hold" else if (compact) "sig" else "exit"
        if (type != promptType) {
            promptFadeText = promptLastText
            promptFadeStart = System.currentTimeMillis()
            promptType = type
        }
        promptLastText = promptText
        val tp = if (promptFadeStart == 0L) 1f
        else ((System.currentTimeMillis() - promptFadeStart) / promptFadeMs).coerceIn(0f, 1f)
        if (tp < 1f) {
            postInvalidateOnAnimation()
            val fading = promptFadeText
            if (!fading.isNullOrEmpty()) drawPrompt(fading, 1f - tp, 1f - 0.1f * tp)
        }
        drawPrompt(promptText, tp, 0.9f + 0.1f * tp)
    }

    /** 圆角模式底部参数文字的一行 */
    private data class ParamLine(val text: String, val y: Float, val alpha: Int)

    // 圆角模式的参数文字行（纯净模式下为空）
    private fun normalParamLines(h: Float, thickness: Float): List<ParamLine> {
        if (ThemeSettings.isCompactModeEnabled) return emptyList()
        if (ThemeSettings.useCustomRadius) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isG2Enabled = prefs.getBoolean("is_g2_enabled", false)
            val tl = ThemeSettings.radiusTL.coerceAtLeast(0f)
            val tr = ThemeSettings.radiusTR.coerceAtLeast(0f)
            val bl = ThemeSettings.radiusBL.coerceAtLeast(0f)
            val br = ThemeSettings.radiusBR.coerceAtLeast(0f)
            return if (tl == tr && tr == bl && bl == br) {
                val g2Prefix = if (isG2Enabled) "G2平滑 | " else ""
                listOf(
                    ParamLine(
                        "圆角半径(手动校准): $g2Prefix${tl.toInt()} px | 线条粗细: ${String.format("%.1f", thickness)} px",
                        h - 120f, 100
                    )
                )
            } else {
                val startY = h - 170f
                listOf(
                    ParamLine("圆角半径(手动校准): ${if (isG2Enabled) "G2平滑" else "普通圆角"}", startY, 130),
                    ParamLine("左上:${tl.toInt()} 右上:${tr.toInt()} 左下:${bl.toInt()} 右下:${br.toInt()} px", startY + 42f, 100),
                    ParamLine("线条粗细: ${String.format("%.1f", thickness)} px", startY + 84f, 75)
                )
            }
        }
        val systemR = rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
        return listOf(
            ParamLine(
                "圆角半径(系统默认): ${systemR.toInt()} px | 线条粗细: ${String.format("%.1f", thickness)} px",
                h - 120f, 100
            )
        )
    }

    // 参数文字整块按 alpha/scale 绘制（转场用）
    private fun drawParamLines(canvas: Canvas, lines: List<ParamLine>, centerX: Float, alphaF: Float, scale: Float) {
        if (lines.isEmpty() || alphaF <= 0f) return
        textPaint.color = bodyTextColor()
        textPaint.textSize = 32f
        textPaint.isFakeBoldText = false
        val count = canvas.save()
        scaleAroundPoint(canvas, centerX, lines.first().y, scale)
        lines.forEach {
            textPaint.alpha = (it.alpha * alphaF).toInt()
            canvas.drawText(it.text, centerX, it.y, textPaint)
        }
        canvas.restoreToCount(count)
        textPaint.alpha = 255
    }

    // 精度模式线条：四周 1px 边线、分区分隔线、分区左右粗线
    private fun drawAdvancedLines(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val zoneHeight = h / 5f

        linePaint.strokeWidth = 1f
        canvas.drawLine(0f, 0.5f, w, 0.5f, linePaint)
        canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, linePaint)
        canvas.drawLine(0.5f, 0f, 0.5f, h, linePaint)
        canvas.drawLine(w - 0.5f, 0f, w - 0.5f, h, linePaint)

        for (i in 0 until 5) {
            val top = i * zoneHeight
            val bottom = (i + 1) * zoneHeight
            val zonePx = ZONE_PX[i]

            if (i > 0) {
                linePaint.strokeWidth = 1f
                linePaint.alpha = 100
                canvas.drawLine(0f, top, w, top, linePaint)
                linePaint.alpha = 255
            }

            if (zonePx > 1f) {
                linePaint.strokeWidth = zonePx
                val offset = zonePx / 2f
                canvas.drawLine(offset, top, offset, bottom, linePaint)
                canvas.drawLine(w - offset, top, w - offset, bottom, linePaint)
            }
        }
    }

    // 精度模式文字：分区标题/副标题、退出倒计时、纯净模式签名
    private fun drawAdvancedTexts(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val zoneHeight = h / 5f
        val textScale = (zoneHeight / 260f).coerceAtMost(1f)
        val centerX = w / 2f

        val animDuration = 150f
        val elapsed = System.currentTimeMillis() - countdownStartTime
        val fadeAlpha = if (countdownStartTime > 0L) (elapsed.toFloat() / animDuration).coerceIn(0f, 1f) else 1f
        if (elapsed < animDuration && remainingSeconds > 0) {
            postInvalidateOnAnimation()
        }

        for (i in 0 until 5) {
            val centerYZone = i * zoneHeight + zoneHeight / 2f
            val zoneTitle = ZONE_TITLES[i]
            val zoneSubtitle = getZoneSubtitle(i)

            textPaint.color = bodyTextColor()
            if (i == 2) {
                // 机型与主标题由 drawCenterTitles 画；小屏上副标题会掉出下沿 ⇒ 两条一起上提（上限 55×ts）
                val subtitleDescent = 35f * textScale * 0.3f
                val overflow = 180f * textScale + subtitleDescent - zoneHeight / 2f
                val lift = overflow.coerceIn(0f, 55f * textScale)

                textPaint.isFakeBoldText = false
                textPaint.textSize = 45f * textScale
                canvas.drawText(zoneTitle, centerX, centerYZone + 120f * textScale - lift, textPaint)
                textPaint.textSize = 35f * textScale
                textPaint.alpha = 180
                canvas.drawText(zoneSubtitle, centerX, centerYZone + 180f * textScale - lift, textPaint)
                textPaint.alpha = 255
            } else {
                textPaint.isFakeBoldText = true
                textPaint.textSize = 50f * textScale
                canvas.drawText(zoneTitle, centerX, centerYZone - 20f * textScale, textPaint)
                textPaint.isFakeBoldText = false
                textPaint.textSize = 35f * textScale
                textPaint.alpha = 180
                canvas.drawText(zoneSubtitle, centerX, centerYZone + 50f * textScale, textPaint)
                textPaint.alpha = 255
            }
        }

        if (remainingSeconds > 0) {
            textPaint.color = bodyTextColor()
            textPaint.textSize = 45f * textScale
            textPaint.isFakeBoldText = true
            textPaint.alpha = (220 * fadeAlpha).toInt()
            // 倒计时文案淡入时轻微缩放
            val py = h - 80f * textScale
            val count = canvas.save()
            scaleAroundPoint(canvas, centerX, py, 0.9f + 0.1f * fadeAlpha)
            canvas.drawText("请继续按住 $remainingSeconds 秒...", centerX, py, textPaint)
            canvas.restoreToCount(count)
            textPaint.alpha = 255
            lastCountdownText = "请继续按住 $remainingSeconds 秒..."
            countdownHideAt = 0L
        } else {
            // 松手后：倒计时文案缩小淡出
            val text = lastCountdownText
            if (text != null) {
                if (countdownHideAt == 0L) countdownHideAt = System.currentTimeMillis()
                val dt = ((System.currentTimeMillis() - countdownHideAt) / promptFadeMs).coerceIn(0f, 1f)
                if (dt < 1f) {
                    postInvalidateOnAnimation()
                    textPaint.color = bodyTextColor()
                    textPaint.textSize = 45f * textScale
                    textPaint.isFakeBoldText = true
                    textPaint.alpha = (220 * (1f - dt)).toInt()
                    val py = h - 80f * textScale
                    val count = canvas.save()
                    scaleAroundPoint(canvas, centerX, py, 1f - 0.1f * dt)
                    canvas.drawText(text, centerX, py, textPaint)
                    canvas.restoreToCount(count)
                    textPaint.alpha = 255
                } else {
                    lastCountdownText = null
                    countdownHideAt = 0L
                }
            }
        }

        // 签名：纯净模式由这里画（非纯净模式由 drawSignature 画）。
        // 两份签名可见度互补：signatureVisibility 返回的是 morph 版的，这里要取反
        if (ThemeSettings.isCompactModeEnabled) {
            val vis = 1f - signatureVisibility()
            if (vis > 0f) {
                textPaint.textSize = 32f * textScale
                textPaint.isFakeBoldText = false
                textPaint.color = bodyTextColor()
                textPaint.alpha = (100 * vis).toInt()
                val sy = h - 30f * textScale
                val count = canvas.save()
                scaleAroundPoint(canvas, centerX, sy, 0.9f + 0.1f * vis)
                canvas.drawText("@byHydrogen", centerX, sy, textPaint)
                canvas.restoreToCount(count)
                textPaint.alpha = 255
            }
        }
    }

    // 机型 + 主标题：两种模式之间做字号/位置/颜色插值（morph）
    private fun drawCenterTitles(canvas: Canvas, p: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val textScale = (h / 5f / 260f).coerceAtMost(1f)
        // 精度端机型与主标题的间距：分区高富余时加大
        val gapBoost = ((h / 5f - 340f) / 2f).coerceIn(0f, 30f)

        // 浓度系数：100% = 原始；上段斜率更陡，保证 120% 能到 255
        val pct = ThemeSettings.deviceNameOpacityPct
        val k = if (pct >= 100) 1f + (pct - 100) / 48f else pct / 100f

        // 机型：圆角模式 55f @ centerY-160 → 精度模式 65f*ts @ centerY-(80+gapBoost)*ts
        textPaint.isFakeBoldText = true
        textPaint.color = bodyTextColor()
        textPaint.textSize = lerp(55f, 65f * textScale, p)
        textPaint.alpha = lerp(180f * k, 255f * k, p).toInt().coerceIn(0, 255)
        canvas.drawText(
            DeviceUtils.getMarketName(), centerX,
            lerp(centerY - 160f, centerY - (80f + gapBoost) * textScale, p),
            textPaint
        )

        // 主标题：跟随线条颜色（单独开了"跟随文字色"才用文字色）；圆角 90f → 精度 75f*ts
        textPaint.color = if (!ThemeSettings.textFollowsLine && ThemeSettings.titleFollowsText) {
            ThemeSettings.testLineTextColor
        } else {
            ThemeSettings.testLineColor
        }
        textPaint.textSize = lerp(90f, 75f * textScale, p)
        textPaint.isFakeBoldText = true
        textPaint.alpha = 255
        canvas.drawText("黑边遮挡测试", centerX, centerY, textPaint)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    // 签名显隐系数（纯净模式开关时 150ms 过渡）：1 = 显示
    private fun signatureVisibility(): Float {
        val compact = ThemeSettings.isCompactModeEnabled
        if (compact != sigCompactLast) {
            sigCompactLast = compact
            sigFadeStart = System.currentTimeMillis()
        }
        val t = if (sigFadeStart == 0L) 1f
        else ((System.currentTimeMillis() - sigFadeStart) / promptFadeMs).coerceIn(0f, 1f)
        if (t < 1f) postInvalidateOnAnimation()
        return if (compact) 1f - t else t
    }

    // 签名：圆角↔精度之间平移；纯净模式开关时淡出/淡入
    private fun drawSignature(canvas: Canvas, p: Float) {
        val visible = signatureVisibility()
        if (visible <= 0f) return

        val h = height.toFloat()
        val textScale = (h / 5f / 260f).coerceAtMost(1f)
        val centerX = width / 2f
        // 圆角模式签名的实际位置（按圆角参数分支）
        val normalY = if (ThemeSettings.useCustomRadius) {
            val uniform = ThemeSettings.radiusTL == ThemeSettings.radiusTR &&
                    ThemeSettings.radiusTR == ThemeSettings.radiusBL &&
                    ThemeSettings.radiusBL == ThemeSettings.radiusBR
            if (uniform) h - 80f else h - 44f
        } else {
            h - 80f
        }
        textPaint.color = bodyTextColor()
        textPaint.textSize = lerp(32f, 32f * textScale, p)
        textPaint.isFakeBoldText = false
        textPaint.alpha = (lerp(if (ThemeSettings.useCustomRadius) 75f else 100f, 100f, p) * visible).toInt()
        val y = lerp(normalY, h - 30f * textScale, p)
        val count = canvas.save()
        scaleAroundPoint(canvas, centerX, y, 0.9f + 0.1f * visible)
        canvas.drawText("@byHydrogen", centerX, y, textPaint)
        canvas.restoreToCount(count)
        textPaint.alpha = 255
    }

    private fun lerpColor(c0: Int, c1: Int, t: Float): Int =
        android.animation.ArgbEvaluator().evaluate(t, c0, c1) as Int

    // 以屏幕中心为原点缩放画布
    private fun scaleAround(canvas: Canvas, s: Float) =
        scaleAroundPoint(canvas, width / 2f, height / 2f, s)

    // 以任意点为原点缩放画布
    private fun scaleAroundPoint(canvas: Canvas, px: Float, py: Float, s: Float) {
        canvas.translate(px, py)
        canvas.scale(s, s)
        canvas.translate(-px, -py)
    }

    private fun drawArcToSystemDefault(
        canvas: Canvas,
        rect: RectF,
        insets: android.view.WindowInsets
    ) {
        // 统一走公共几何
        borderPath = buildScreenBorderPath(width.toFloat(), height.toFloat(), insets, linePaint.strokeWidth, context)
        canvas.drawPath(borderPath, linePaint)
    }

    // === 精度模式分区数据与动态换算 ===

    // 缓存按当前设备 xdpi 格式化好的副标题，xdpi 变化时才重建
    private var cachedSubtitles: Array<String>? = null
    private var cachedXdpi: Float = -1f

    private fun getZoneSubtitle(index: Int): String {
        val xdpi = resources.displayMetrics.xdpi
        if (cachedSubtitles == null || cachedXdpi != xdpi) {
            cachedXdpi = xdpi
            cachedSubtitles = Array(ZONE_PX.size) { i ->
                ZONE_SUBTITLE_PREFIX + formatPxAsMm(ZONE_PX[i], xdpi) + "mm"
            }
        }
        return cachedSubtitles!![index]
    }

    // px → mm 换算：小于 0.1mm 保留 3 位小数，否则 2 位
    private fun formatPxAsMm(px: Float, xdpi: Float): String {
        val dpi = if (xdpi > 0f) xdpi else resources.displayMetrics.density * 160f // xdpi 异常兜底
        val mm = px / dpi * 25.4f
        return if (mm < 0.1f) String.format("%.3f", mm) else String.format("%.2f", mm)
    }

    private companion object {
        // 精度模式各分区的像素宽度
        val ZONE_PX = floatArrayOf(2f, 4f, 1f, 6f, 8f)

        // 各分区标题
        val ZONE_TITLES = arrayOf(
            "此区域左右边缘 2 像素",
            "此区域左右边缘 4 像素",
            "四周 1 像素线条看得见吗？",
            "此区域左右边缘 6 像素",
            "此区域左右边缘 8 像素"
        )

        // 各分区副标题共用的前缀，mm 数值由 formatPxAsMm 按设备 DPI 动态生成
        const val ZONE_SUBTITLE_PREFIX = "如果左右刚好看不到，左右遮挡宽度各"
    }
}