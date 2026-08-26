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
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import android.view.View

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

    private val borderPath = Path()

    // 用于追踪倒计时淡入的时间戳
    private var countdownStartTime: Long = 0L

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

    // 控制当前显示哪种测试模式
    var isAdvancedMode: Boolean = false
        set(value) {
            if (field != value) {
                isHapticFeedbackEnabled = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            field = value
            invalidate()
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
        canvas.drawColor(Color.BLACK) // 背景涂黑

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

        if (isAdvancedMode) {
            drawAdvancedMode(canvas)
        } else {
            drawNormalMode(canvas)
        }
    }

    // 圆角边框模式
    private fun drawNormalMode(canvas: Canvas) {
        val currentThickness = ThemeSettings.testLineThickness
        linePaint.strokeWidth = currentThickness

        val w = width.toFloat()
        val h = height.toFloat()
        val inset = currentThickness / 2f
        val rect = RectF(inset, inset, w - inset, h - inset)
        val totalDuration = 250f
        val elapsed = System.currentTimeMillis() - countdownStartTime
        if (elapsed < totalDuration && countdownStartTime > 0L) {
            postInvalidateOnAnimation() // 持续驱动高刷重绘
        }

        val centerX = w / 2f
        val centerY = h / 2f

        if (ThemeSettings.useCustomRadius) {
            // A. 使用手动校准的数据
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isG2Enabled = prefs.getBoolean("is_g2_enabled", false)

            val tl = ThemeSettings.radiusTL.coerceAtLeast(0f)
            val tr = ThemeSettings.radiusTR.coerceAtLeast(0f)
            val bl = ThemeSettings.radiusBL.coerceAtLeast(0f)
            val br = ThemeSettings.radiusBR.coerceAtLeast(0f)

            // 叠加 X、Y 轴修正值
            val tlX = (tl + prefs.getFloat("r_tl_x", 0f)).coerceAtLeast(0f)
            val tlY = (tl + prefs.getFloat("r_tl_y", 0f)).coerceAtLeast(0f)
            val trX = (tr + prefs.getFloat("r_tr_x", 0f)).coerceAtLeast(0f)
            val trY = (tr + prefs.getFloat("r_tr_y", 0f)).coerceAtLeast(0f)
            val brX = (br + prefs.getFloat("r_br_x", 0f)).coerceAtLeast(0f)
            val brY = (br + prefs.getFloat("r_br_y", 0f)).coerceAtLeast(0f)
            val blX = (bl + prefs.getFloat("r_bl_x", 0f)).coerceAtLeast(0f)
            val blY = (bl + prefs.getFloat("r_bl_y", 0f)).coerceAtLeast(0f)

            borderPath.reset()

            if (isG2Enabled) {
                val p = 1.4f
                val c = 0.45f
                borderPath.moveTo(rect.left + p * tlX, rect.top)
                borderPath.lineTo(rect.right - p * trX, rect.top)
                borderPath.cubicTo(rect.right - c * trX, rect.top, rect.right, rect.top + c * trY, rect.right, rect.top + p * trY)
                borderPath.lineTo(rect.right, rect.bottom - p * brY)
                borderPath.cubicTo(rect.right, rect.bottom - c * brY, rect.right - c * brX, rect.bottom, rect.right - p * brX, rect.bottom)
                borderPath.lineTo(rect.left + p * blX, rect.bottom)
                borderPath.cubicTo(rect.left + c * blX, rect.bottom, rect.left, rect.bottom - c * blY, rect.left, rect.bottom - p * blY)
                borderPath.lineTo(rect.left, rect.top + p * tlY)
                borderPath.cubicTo(rect.left, rect.top + c * tlY, rect.left + c * tlX, rect.top, rect.left + p * tlX, rect.top)
                borderPath.close()
            } else {
                borderPath.addRoundRect(rect, floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY), Path.Direction.CW)
            }
            canvas.drawPath(borderPath, linePaint)

            // 精简模式下隐藏手动校准圆角半径参数文本
            if (!ThemeSettings.isCompactModeEnabled) {
                // 判断是否为四角同步
                val isUniform = (tl == tr && tr == bl && bl == br)
                textPaint.textSize = 32f
                textPaint.isFakeBoldText = false

                if (isUniform) {
                    textPaint.alpha = 100
                    val g2Prefix = if (isG2Enabled) "G2平滑 | " else ""
                    val radiusDetails = "$g2Prefix${tl.toInt()} px"
                    canvas.drawText("圆角半径(手动校准): $radiusDetails | 线条粗细: ${String.format("%.1f", currentThickness)} px", centerX, h - 120f, textPaint)

                    textPaint.alpha = 75
                    canvas.drawText("@byHydrogen", centerX, h - 80f, textPaint)
                } else {
                    val lineSpacing = 42f
                    val startY = h - 170f

                    textPaint.alpha = 130
                    val line1 = "圆角半径(手动校准): ${if (isG2Enabled) "G2平滑" else "普通圆角"}"
                    canvas.drawText(line1, centerX, startY, textPaint)

                    textPaint.alpha = 100
                    val line2 = "左上:${tl.toInt()} 右上:${tr.toInt()} 左下:${bl.toInt()} 右下:${br.toInt()} px"
                    canvas.drawText(line2, centerX, startY + lineSpacing, textPaint)

                    textPaint.alpha = 75
                    val line3 = "线条粗细: ${String.format("%.1f", currentThickness)} px"
                    canvas.drawText(line3, centerX, startY + lineSpacing * 2, textPaint)

                    textPaint.alpha = 75
                    canvas.drawText("@byHydrogen", centerX, startY + lineSpacing * 3, textPaint)
                }
            }
        } else {
            val insets = rootWindowInsets
            if (!ThemeSettings.isLegacyCurveEnabled && insets != null) {
                drawArcToSystemDefault(canvas, rect, insets)
            } else {
                val systemR = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
                canvas.drawRoundRect(rect, systemR, systemR, linePaint)
            }

            // 精简模式下隐藏系统默认圆角半径参数文本
            if (!ThemeSettings.isCompactModeEnabled) {
                val systemR = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
                textPaint.textSize = 32f
                textPaint.alpha = 100
                canvas.drawText("圆角半径(系统默认): ${systemR.toInt()} px | 线条粗细: ${String.format("%.1f", currentThickness)} px", centerX, h - 120f, textPaint)

                textPaint.textSize = 32f
                textPaint.isFakeBoldText = false
                textPaint.alpha = 100
                canvas.drawText("@byHydrogen", centerX, h - 80f, textPaint)
            }
        }

        // 绘制中央文字信息
        val deviceDisplayName = DeviceUtils.getMarketName()

        textPaint.textSize = 55f
        textPaint.isFakeBoldText = true
        textPaint.alpha = 180
        textPaint.color = Color.WHITE
        canvas.drawText(deviceDisplayName, centerX, centerY - 160f, textPaint)

        textPaint.textSize = 90f
        textPaint.isFakeBoldText = true
        textPaint.alpha = 255
        textPaint.color = ThemeSettings.testLineColor
        canvas.drawText("黑边遮挡测试", centerX, centerY, textPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 42f
        textPaint.isFakeBoldText = false

        if (ThemeSettings.isCompactModeEnabled) {
            if (remainingSeconds > 0) {
                val animDuration = 150f
                val fadeProgress = if (countdownStartTime > 0L) (elapsed.toFloat() / animDuration).coerceIn(0f, 1f) else 1f
                textPaint.alpha = (140 * fadeProgress).toInt()
                canvas.drawText("请继续按住 $remainingSeconds 秒...", centerX, centerY + 140f, textPaint)
                textPaint.alpha = (140 * (1f - fadeProgress)).toInt()
                canvas.drawText("@byHydrogen", centerX, centerY + 140f, textPaint)
            } else {
                textPaint.alpha = 140
                canvas.drawText("@byHydrogen", centerX, centerY + 140f, textPaint)
            }
        } else {
            // 长按退出提示文案：随设置动态变化
            val exitHint = if (ThemeSettings.longPressExitEnabled) {
                "按返回键 或 长按${ThemeSettings.longPressExitSeconds}秒 退出"
            } else {
                "按返回键退出"
            }
            if (remainingSeconds > 0) {
                if (countdownStartTime > 0L && elapsed < totalDuration) {
                    if (elapsed < 100f) {
                        val outProgress = (1f - (elapsed / 100f)).coerceIn(0f, 1f)
                        textPaint.alpha = (140 * outProgress).toInt()
                        canvas.drawText(exitHint, centerX, centerY + 140f, textPaint)
                    } else {
                        val inProgress = ((elapsed - 100f) / 150f).coerceIn(0f, 1f)
                        textPaint.alpha = (140 * inProgress).toInt()
                        canvas.drawText("请继续按住 $remainingSeconds 秒...", centerX, centerY + 140f, textPaint)
                    }
                } else {
                    textPaint.alpha = 140
                    canvas.drawText("请继续按住 $remainingSeconds 秒...", centerX, centerY + 140f, textPaint)
                }
            } else {
                textPaint.alpha = 140
                canvas.drawText(exitHint, centerX, centerY + 140f, textPaint)
            }
        }
    }

    // 像素级精度测试模式
    private fun drawAdvancedMode(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val zoneHeight = h / 5f
        val centerX = w / 2f
        val color = ThemeSettings.testLineColor

        val animDuration = 150f
        val elapsed = System.currentTimeMillis() - countdownStartTime
        val fadeAlpha = if (countdownStartTime > 0L) (elapsed.toFloat() / animDuration).coerceIn(0f, 1f) else 1f
        if (elapsed < animDuration && remainingSeconds > 0) {
            postInvalidateOnAnimation()
        }

        linePaint.color = color
        linePaint.strokeWidth = 1f
        canvas.drawLine(0f, 0.5f, w, 0.5f, linePaint)
        canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, linePaint)
        canvas.drawLine(0.5f, 0f, 0.5f, h, linePaint)
        canvas.drawLine(w - 0.5f, 0f, w - 0.5f, h, linePaint)

        for (i in 0 until 5) {
            val top = i * zoneHeight
            val bottom = (i + 1) * zoneHeight
            val centerYZone = top + zoneHeight / 2f
            val zonePx = ZONE_PX[i]
            val zoneTitle = ZONE_TITLES[i]
            val zoneSubtitle = getZoneSubtitle(i)

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

            textPaint.color = Color.WHITE
            if (i == 2) {
                textPaint.isFakeBoldText = true
                textPaint.textSize = 65f
                canvas.drawText(DeviceUtils.getMarketName(), centerX, centerYZone - 80f, textPaint)
                textPaint.color = color
                textPaint.textSize = 75f
                canvas.drawText("带黑边膜挡屏测试", centerX, centerYZone, textPaint)
                textPaint.color = Color.WHITE
                textPaint.textSize = 45f
                textPaint.isFakeBoldText = false
                canvas.drawText(zoneTitle, centerX, centerYZone + 120f, textPaint)
                textPaint.textSize = 35f
                textPaint.alpha = 180
                canvas.drawText(zoneSubtitle, centerX, centerYZone + 180f, textPaint)
            } else {
                textPaint.isFakeBoldText = true
                textPaint.textSize = 50f
                canvas.drawText(zoneTitle, centerX, centerYZone - 20f, textPaint)
                textPaint.isFakeBoldText = false
                textPaint.textSize = 35f
                textPaint.alpha = 180
                canvas.drawText(zoneSubtitle, centerX, centerYZone + 50f, textPaint)
                textPaint.alpha = 255
            }
        }

        if (remainingSeconds > 0) {
            textPaint.color = color
            textPaint.textSize = 50f
            textPaint.isFakeBoldText = true
            textPaint.alpha = (255 * fadeAlpha).toInt()
            canvas.drawText("退出倒计时: $remainingSeconds 秒", centerX, h - 80f, textPaint)
            textPaint.alpha = 255
        }

        textPaint.textSize = 32f
        textPaint.isFakeBoldText = false
        textPaint.alpha = 100
        canvas.drawText("@byHydrogen", centerX, h - 30f, textPaint)
    }

    private fun drawArcToSystemDefault(
        canvas: Canvas,
        rect: RectF,
        insets: android.view.WindowInsets
    ) {
        val tl = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        val tr = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
        val br = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
        val bl = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)

        val L = rect.left; val T = rect.top; val R = rect.right; val B = rect.bottom
        val adj = rect.left  // = currentThickness / 2f

        fun cx(rc: RoundedCorner?, fallback: Float): Float =
            rc?.center?.x?.toFloat() ?: fallback
        fun cy(rc: RoundedCorner?, fallback: Float): Float =
            rc?.center?.y?.toFloat() ?: fallback
        fun rx(rc: RoundedCorner?): Float =
            ((rc?.radius?.toFloat() ?: 100f) - adj).coerceAtLeast(0f)

        val tlCX = cx(tl, L + 100f); val tlCY = cy(tl, T + 100f); val tlR = rx(tl)
        val trCX = cx(tr, R - 100f); val trCY = cy(tr, T + 100f); val trR = rx(tr)
        val brCX = cx(br, R - 100f); val brCY = cy(br, B - 100f); val brR = rx(br)
        val blCX = cx(bl, L + 100f); val blCY = cy(bl, B - 100f); val blR = rx(bl)

        borderPath.reset()

        // top edge → top-right arcTo
        borderPath.moveTo(tlCX, T)
        borderPath.lineTo(trCX, T)
        borderPath.arcTo(trCX - trR, trCY - trR, trCX + trR, trCY + trR, 270f, 90f, false)
        // right edge → bottom-right arcTo
        borderPath.lineTo(R, brCY)
        borderPath.arcTo(brCX - brR, brCY - brR, brCX + brR, brCY + brR, 0f, 90f, false)
        // bottom edge → bottom-left arcTo
        borderPath.lineTo(blCX, B)
        borderPath.arcTo(blCX - blR, blCY - blR, blCX + blR, blCY + blR, 90f, 90f, false)
        // left edge → top-left arcTo
        borderPath.lineTo(L, tlCY)
        borderPath.arcTo(tlCX - tlR, tlCY - tlR, tlCX + tlR, tlCY + tlR, 180f, 90f, false)
        borderPath.close()

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
                ZONE_SUBTITLE_PREFIXES[i] + formatPxAsMm(ZONE_PX[i], xdpi) + "mm"
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

        // 各分区副标题前缀，mm 数值由 formatPxAsMm 按设备 DPI 动态生成
        val ZONE_SUBTITLE_PREFIXES = arrayOf(
            "如果左右刚好看不到，左右遮挡宽度各",
            "如果左右刚刚好看不到，左右遮挡宽度各",
            "如果左右刚刚好看不到，左右遮挡宽度各",
            "如果左右刚好看不到，左右遮挡宽度各",
            "如果左右刚好看不到，左右遮挡宽度各"
        )
    }
}