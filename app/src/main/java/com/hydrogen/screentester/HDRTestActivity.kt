package com.hydrogen.screentester

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.PorterDuff
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class HDRTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT

        setContent {
            ScreenTesterTheme {
                val isDarkTheme = when (ThemeSettings.darkModeState) {
                    DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    DarkModeConfig.LIGHT -> false
                    DarkModeConfig.DARK -> true
                }

                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val currentWindow = (view.context as Activity).window
                        val insetsController = WindowInsetsControllerCompat(currentWindow, view)
                        insetsController.isAppearanceLightStatusBars = !isDarkTheme
                        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }

                HDRTestScreen(isDarkTheme = isDarkTheme) { finish() }
            }
        }
    }
}

class SmoothCornerShape(private val radius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { radius.toPx() }
        val w = size.width
        val h = size.height

        val maxR = minOf(w / 2f, h / 2f)
        val finalR = if (r > maxR) maxR else r

        val path = Path().apply {
            reset()
            val factor = 1.52f
            val c = finalR * factor
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            cubicTo(w - finalR * 0.55f, 0f, w, finalR * 0.55f, w, c)
            lineTo(w, h - c)
            cubicTo(w, h - finalR * 0.55f, w - finalR * 0.55f, h, w - c, h)
            lineTo(c, h)
            cubicTo(finalR * 0.55f, h, 0f, h - finalR * 0.55f, 0f, h - c)
            lineTo(0f, c)
            cubicTo(0f, finalR * 0.55f, finalR * 0.55f, 0f, c, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HDRTestScreen(isDarkTheme: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    var isHDRActivated by remember { mutableStateOf(false) }
    var hasStartedTesting by remember { mutableStateOf(false) }
    var currentHDRRatio by remember { mutableFloatStateOf(1.0f) }
    var bgTransitionReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentHDRRatio = activity?.display?.hdrSdrRatio ?: 1.0f
        }
    }

    LaunchedEffect(isHDRActivated) {
        if (activity != null) {
            activity.window.colorMode = if (isHDRActivated) {
                ActivityInfo.COLOR_MODE_HDR
            } else {
                ActivityInfo.COLOR_MODE_DEFAULT
            }
            activity.window.attributes = activity.window.attributes
        }

        if (isHDRActivated) {
            delay(200)
            bgTransitionReady = true

            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val display = activity.display
                if (display != null) {
                    while (isActive) {
                        currentHDRRatio = display.hdrSdrRatio
                        delay(16)
                    }
                }
            }
        } else {
            bgTransitionReady = false
            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                currentHDRRatio = activity.display?.hdrSdrRatio ?: 1.0f
            }
        }
    }

    DisposableEffect(activity) {
        val display = activity?.display
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            onDispose {}
        } else {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == display.displayId) {
                        currentHDRRatio = display.hdrSdrRatio
                    }
                }
            }
            displayManager.registerDisplayListener(listener, null)
            onDispose {
                displayManager.unregisterDisplayListener(listener)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕 HDR 检测", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onExit()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val targetRefBgColor = if (isHDRActivated) {
                    Color.White
                } else {
                    Color.Gray.copy(alpha = 0.2f)
                }

                val animatedRefBgColor by animateColorAsState(
                    targetValue = targetRefBgColor,
                    animationSpec = if (isHDRActivated) tween(durationMillis = 200, easing = FastOutSlowInEasing) else tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "SdrRefBgAnimation"
                )

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = SmoothCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SDR 参照", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(SmoothCornerShape(16.dp))
                                .background(animatedRefBgColor)
                        ) {
                            val sdrTextColor by animateColorAsState(
                                targetValue = if (isHDRActivated) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                animationSpec = tween(300),
                                label = "sdrTextColor"
                            )
                            Text(
                                text = "普通动态范围\n标准亮度",
                                modifier = Modifier.align(Alignment.Center),
                                textAlign = TextAlign.Center,
                                color = sdrTextColor
                            )
                        }

                        AnimatedVisibility(
                            visible = hasStartedTesting,
                            enter = fadeIn(animationSpec = tween(500, easing = FastOutSlowInEasing)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 3 },
                                        animationSpec = tween(500, easing = FastOutSlowInEasing)
                                    ) +
                                    expandVertically(
                                        animationSpec = tween(500, easing = FastOutSlowInEasing),
                                        expandFrom = Alignment.Top
                                    ),
                            exit = fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(
                                        targetOffsetY = { it / 3 },
                                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                                    ) +
                                    shrinkVertically(
                                        animationSpec = tween(400, easing = FastOutSlowInEasing),
                                        shrinkTowards = Alignment.Top
                                    )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(24.dp))
                                RenderHardwareParameters(activity, isDarkTheme)
                            }
                        }
                    }
                }

                val targetBgColor = if (isHDRActivated) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }

                val animatedBgColor by animateColorAsState(
                    targetValue = targetBgColor,
                    animationSpec = if (isHDRActivated) tween(durationMillis = 200, easing = FastOutSlowInEasing) else tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "CardBgAnimation"
                )

                val glowProgress by animateFloatAsState(
                    targetValue = if (bgTransitionReady) 1f else 0f,
                    animationSpec = snap(),
                    label = "HDRGlowProgress"
                )

                val buttonBias by animateFloatAsState(
                    targetValue = if (hasStartedTesting) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.70f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "ButtonBiasAnimation"
                )

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = SmoothCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(containerColor = animatedBgColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp, horizontal = 12.dp)
                    ) {
                        if (hasStartedTesting) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 72.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    HDRIcon(
                                        resId = R.drawable.ic_app_logo,
                                        sizeDp = 72,
                                        glowProgress = glowProgress
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HDRText(
                                        text = "ScreenTester",
                                        fontSizeSp = 16,
                                        glowProgress = glowProgress,
                                        isDarkTheme = isDarkTheme
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HDRText(
                                        text = "HDR",
                                        fontSizeSp = 46,
                                        glowProgress = glowProgress,
                                        isBold = true,
                                        isDarkTheme = isDarkTheme
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                RenderHDRDisplayParameters(activity, currentHDRRatio, isDarkTheme, isHDRActivated)
                            }
                        }

                        // HDR 激活时背景为白色，按钮用浅色莫奈取色
                        val lightColorScheme = remember { dynamicLightColorScheme(context) }
                        val animatedContainerColor by animateColorAsState(
                            targetValue = if (isHDRActivated) lightColorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            animationSpec = tween(300),
                            label = "btnContainer"
                        )
                        val animatedContentColor by animateColorAsState(
                            targetValue = if (isHDRActivated) lightColorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            animationSpec = tween(300),
                            label = "btnContent"
                        )

                        FilledTonalButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                if (!hasStartedTesting) {
                                    hasStartedTesting = true
                                    isHDRActivated = true
                                } else {
                                    isHDRActivated = !isHDRActivated
                                }
                            },
                            modifier = Modifier.align(BiasAlignment(horizontalBias = 0f, verticalBias = buttonBias)),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = animatedContainerColor,
                                contentColor = animatedContentColor
                            )
                        ) {
                            if (!hasStartedTesting) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("点亮 HDR", fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    text = if (isHDRActivated) "停止 HDR 测试" else "开始 HDR 测试",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "数据源自系统底层 API，受限于屏幕材质差异，实际视觉效果请以肉眼为准。",
                fontSize = 10.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun RenderHardwareParameters(activity: Activity?, isDarkTheme: Boolean) {
    val display = activity?.display ?: return

    val isWide = display.preferredWideGamutColorSpace != null
    val wideGamutStr = if (isWide) "支持广色域\n(Wide Color Gamut)" else "仅支持标准色域\n(sRGB)"

    val hdrCaps = display.hdrCapabilities ?: return

    val maxLuminance = hdrCaps.desiredMaxLuminance
    val maxAvgLuminance = hdrCaps.desiredMaxAverageLuminance

    val contentColor = if (isDarkTheme) Color.LightGray else Color.DarkGray
    val descColor = Color.Gray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("广色域硬件支持:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            Text(wideGamutStr, fontSize = 11.sp, color = descColor, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("激发峰值亮度:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            Text("$maxLuminance nits", fontSize = 11.sp, color = descColor, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("最大平均亮度:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            Text("$maxAvgLuminance nits", fontSize = 11.sp, color = descColor, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun RenderHDRDisplayParameters(activity: Activity?, currentRatio: Float, isDarkTheme: Boolean, isHDRActivated: Boolean) {
    val display = activity?.display ?: return

    val currentModeStr = when (activity.window.colorMode) {
        ActivityInfo.COLOR_MODE_DEFAULT -> "标准色域模式\n(SDR)"
        ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT -> "广色域模式\n(WCG)"
        ActivityInfo.COLOR_MODE_HDR -> "高动态范围模式\n(HDR)"
        else -> "未知模式"
    }

    val typeNames = mutableListOf<String>()
    val hdrCaps = display.hdrCapabilities
    if (hdrCaps != null) {
        for (type in hdrCaps.supportedHdrTypes) {
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> typeNames.add("杜比视界")
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> typeNames.add("HDR10")
                Display.HdrCapabilities.HDR_TYPE_HLG -> typeNames.add("HLG")
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> typeNames.add("HDR10+")
            }
        }
    }

    // HDR 激活时背景为白色用深色文字，关闭时跟随深色模式
    val targetContentColor = if (isHDRActivated) Color.DarkGray else if (isDarkTheme) Color.LightGray else Color.DarkGray
    val targetDescColor = if (isHDRActivated) Color.Gray else Color.Gray
    val contentColor by animateColorAsState(targetValue = targetContentColor, animationSpec = tween(300), label = "hdrContentColor")
    val descColor by animateColorAsState(targetValue = targetDescColor, animationSpec = tween(300), label = "hdrDescColor")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("当前色彩模式:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            Text(currentModeStr, fontSize = 11.sp, color = descColor, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HDR类型支持:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (typeNames.isEmpty()) "硬件不支持HDR" else typeNames.joinToString("\n"),
                fontSize = 11.sp,
                color = descColor,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HDR 比率:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            val ratioText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                "$currentRatio"
            } else {
                val maxLuminance = display.hdrCapabilities?.desiredMaxLuminance ?: 0f
                if (maxLuminance > 0f) "${maxLuminance / 100f}" else "未知"
            }
            Text(ratioText, fontSize = 11.sp, color = descColor, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}

@Composable
fun HDRText(
    text: String,
    fontSizeSp: Int,
    glowProgress: Float,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    isBold: Boolean = false
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    val densityFloat = density.density

    val (bitmap, sizeDp) = remember(text, fontSizePx, isBold, isDarkTheme, densityFloat) {
        val paint = Paint().apply {
            textSize = fontSizePx
            isAntiAlias = true
            isFilterBitmap = true
            textAlign = Paint.Align.LEFT
            if (isBold) isFakeBoldText = true
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padding = 16
        val width = maxOf(bounds.width() + padding * 2, 10)
        val height = maxOf(bounds.height() + padding * 2, 10)

        val colorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
        val hdrBitmap = createBitmap(width, height, Bitmap.Config.RGBA_F16, true, colorSpace)
        val canvas = android.graphics.Canvas(hdrBitmap)

        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        paint.setColor(android.graphics.Color.valueOf(1.0f, 1.0f, 1.0f, 1.0f, colorSpace).pack())
        canvas.drawText(text, padding.toFloat() - bounds.left, padding.toFloat() - bounds.top, paint)

        val widthDp = width / densityFloat
        val heightDp = height / densityFloat
        Pair(hdrBitmap, Pair(widthDp, heightDp))
    }

    val colorFilter = remember(glowProgress, isDarkTheme) {
        val targetHDR = 4.0f
        val baseNormal = if (isDarkTheme) 1.0f else 0.0f
        val currentGain = baseNormal + (targetHDR - baseNormal) * glowProgress

        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    currentGain, 0f, 0f, 0f, 0f,
                    0f, currentGain, 0f, 0f, 0f,
                    0f, 0f, currentGain, 0f, 0f,
                    0f, 0f, 0f, glowProgress, 0f
                )
            )
        )
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = text,
        colorFilter = colorFilter,
        modifier = modifier.size(sizeDp.first.dp, sizeDp.second.dp)
    )
}

@Composable
fun HDRIcon(
    resId: Int,
    sizeDp: Int,
    glowProgress: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bitmap = remember(resId, sizeDp) {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val colorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
        val hdrBitmap = createBitmap(sizePx, sizePx, Bitmap.Config.RGBA_F16, true, colorSpace)
        val canvas = android.graphics.Canvas(hdrBitmap)

        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val drawable = ContextCompat.getDrawable(context, resId)
        drawable?.setBounds(0, 0, sizePx, sizePx)

        val sdrTmp = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cTmp = android.graphics.Canvas(sdrTmp)
        drawable?.draw(cTmp)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(sdrTmp, 0f, 0f, paint)
        sdrTmp.recycle()

        hdrBitmap
    }

    val colorFilter = remember(glowProgress) {
        val currentGain = 1.0f + 3.0f * glowProgress
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    currentGain, 0f, 0f, 0f, 0f,
                    0f, currentGain, 0f, 0f, 0f,
                    0f, 0f, currentGain, 0f, 0f,
                    0f, 0f, 0f, glowProgress, 0f
                )
            )
        )
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "HDR Icon",
        colorFilter = colorFilter,
        modifier = modifier.size(sizeDp.dp)
    )
}