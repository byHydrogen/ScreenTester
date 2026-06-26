package com.hydrogen.screentester

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// OOBE 状态管理
object OOBEState {
    var currentStep by mutableIntStateOf(0)
    var isCompleted by mutableStateOf(false)
}

class OOBEActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeSettings.loadConfig(this)

        // 键盘弹起时不缩小窗口，防止 Canvas 红线被推上去
        // 输入框通过 imePadding() 保证可滚动到可见区域
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 检查是否已完成 OOBE
        val prefs = getSharedPreferences("oobe_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("oobe_completed", false)) {
            goToMain()
            return
        }

        OOBEState.currentStep = 0
        OOBEState.isCompleted = false

        setContent {
            ScreenTesterTheme {
                OOBEContent(
                    onComplete = { completeOOBE() },
                    onSkip = { completeOOBE() }
                )
            }
        }
    }

    private fun completeOOBE() {
        getSharedPreferences("oobe_prefs", MODE_PRIVATE)
            .edit().putBoolean("oobe_completed", true).apply()
        OOBEState.isCompleted = true
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OOBEContent(onComplete: () -> Unit, onSkip: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 6 })
    val view = LocalView.current
    val context = LocalContext.current

    var isCalibrating by remember { mutableStateOf(ThemeSettings.useCustomRadius) }

    // 校准状态
    val calibPrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var isG2Enabled by remember { mutableStateOf(calibPrefs.getBoolean("is_g2_enabled", false)) }
    val calibTLX = remember { Animatable(calibPrefs.getFloat("r_tl_x", 0f)) }
    val calibTRX = remember { Animatable(calibPrefs.getFloat("r_tr_x", 0f)) }
    val calibBLX = remember { Animatable(calibPrefs.getFloat("r_bl_x", 0f)) }
    val calibBRX = remember { Animatable(calibPrefs.getFloat("r_br_x", 0f)) }
    val calibTLY = remember { Animatable(calibPrefs.getFloat("r_tl_y", 0f)) }
    val calibTRY = remember { Animatable(calibPrefs.getFloat("r_tr_y", 0f)) }
    val calibBLY = remember { Animatable(calibPrefs.getFloat("r_bl_y", 0f)) }
    val calibBRY = remember { Animatable(calibPrefs.getFloat("r_br_y", 0f)) }

    LaunchedEffect(pagerState.currentPage) {
        OOBEState.currentStep = pagerState.currentPage
    }

    val isDark = when (ThemeSettings.darkModeState) {
        DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkModeConfig.LIGHT -> false
        DarkModeConfig.DARK -> true
    }

    val backgroundBrush = DeviceUtils.backgroundBrush(isDark)

    // 校准模式背景渐变动画
    val whiteOverlayAlpha by animateFloatAsState(
        targetValue = if (isCalibrating && pagerState.currentPage == 1) 1f else 0f,
        animationSpec = tween(500),
        label = "whiteOverlay"
    )

    // 底部导航浅色主题混合
    val isLightMode = isCalibrating && pagerState.currentPage == 1
    val oobeLightScheme = remember { dynamicLightColorScheme(context) }
    val oobeDarkScheme = MaterialTheme.colorScheme
    val oobeBlend by animateFloatAsState(if (isLightMode) 1f else 0f, tween(500), label = "oobeBlend")
    val oobeBlendedScheme = oobeDarkScheme.copy(
        surface = lerp(oobeDarkScheme.surface, oobeLightScheme.surface, oobeBlend),
        onSurface = lerp(oobeDarkScheme.onSurface, oobeLightScheme.onSurface, oobeBlend),
        onSurfaceVariant = lerp(oobeDarkScheme.onSurfaceVariant, oobeLightScheme.onSurfaceVariant, oobeBlend),
        primary = lerp(oobeDarkScheme.primary, oobeLightScheme.primary, oobeBlend),
        onPrimary = lerp(oobeDarkScheme.onPrimary, oobeLightScheme.onPrimary, oobeBlend),
        primaryContainer = lerp(oobeDarkScheme.primaryContainer, oobeLightScheme.primaryContainer, oobeBlend),
        onPrimaryContainer = lerp(oobeDarkScheme.onPrimaryContainer, oobeLightScheme.onPrimaryContainer, oobeBlend),
        surfaceVariant = lerp(oobeDarkScheme.surfaceVariant, oobeLightScheme.surfaceVariant, oobeBlend),
        secondary = lerp(oobeDarkScheme.secondary, oobeLightScheme.secondary, oobeBlend),
        onSecondary = lerp(oobeDarkScheme.onSecondary, oobeLightScheme.onSecondary, oobeBlend),
        secondaryContainer = lerp(oobeDarkScheme.secondaryContainer, oobeLightScheme.secondaryContainer, oobeBlend),
        onSecondaryContainer = lerp(oobeDarkScheme.onSecondaryContainer, oobeLightScheme.onSecondaryContainer, oobeBlend),
    )

    val skipAlpha by animateFloatAsState(
        targetValue = if (pagerState.currentPage in 1..5) 1f else 0f,
        animationSpec = tween(300),
        label = "skipAlpha"
    )

    val buttonText by remember { derivedStateOf {
        when (pagerState.currentPage) {
            0 -> "开始"
            5 -> "开始使用"
            else -> "下一步"
        }
    } }
    val buttonWidth by animateDpAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> 100.dp
            5 -> 150.dp
            else -> 120.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "buttonWidth"
    )

    // 欢迎页按钮淡入
    val animEnabled = ThemeSettings.isAnimationEnabled
    var welcomeAnimPlayed by remember { mutableStateOf(false) }
    val buttonAlpha = remember { Animatable(if (animEnabled && pagerState.currentPage == 0) 0f else 1f) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0 && animEnabled && !welcomeAnimPlayed) {
            welcomeAnimPlayed = true
            buttonAlpha.snapTo(0f)
            delay(440)
            buttonAlpha.animateTo(1f, tween(300))
        } else {
            buttonAlpha.snapTo(1f)
        }
    }

    var realtimeThickness by remember { mutableFloatStateOf(ThemeSettings.testLineThickness) }
    var realtimeSegmentLength by remember { mutableFloatStateOf(if (ThemeSettings.multiColorSegmentLength == 0f) 1f else ThemeSettings.multiColorSegmentLength) }
    var isDragging by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        // 校准模式浅灰色背景叠加层
        if (whiteOverlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFFDFDFDF).copy(alpha = whiteOverlayAlpha) else Color.White.copy(alpha = whiteOverlayAlpha))
            )
        }

        // 层级 1：基础内容层
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 5,
                userScrollEnabled = true
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> OOBEWelcomeStep(isDark)
                    1 -> OOBECornerCalibrationStep(
                        isDark,
                        onCalibrationChanged = { isCalibrating = it },
                        isG2Enabled = isG2Enabled,
                        onG2Changed = { isG2Enabled = it },
                        tlXAnim = calibTLX, trXAnim = calibTRX, blXAnim = calibBLX, brXAnim = calibBRX,
                        tlYAnim = calibTLY, trYAnim = calibTRY, blYAnim = calibBLY, brYAnim = calibBRY
                    )
                    2 -> OOBETestLineStep(isDark,
                        onThicknessChange = { realtimeThickness = it },
                        onSegmentLengthChange = { realtimeSegmentLength = it },
                        onDraggingChange = { isDragging = it }
                    )
                    3 -> OOBEPureModeStep(isDark)
                    4 -> OOBECardAnimationStep(isDark)
                    5 -> OOBECompletionStep(isDark, isActive = pagerState.currentPage == 5)
                }
            }

            // 底部导航区
            MaterialTheme(colorScheme = oobeBlendedScheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.alpha(skipAlpha)) {
                        if (pagerState.currentPage in 1..5) {
                            TextButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            ) {
                                Text("上一步", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        } else {
                            Spacer(Modifier.width(64.dp))
                        }
                    }

                    // 开始 / 下一步 / 开始使用
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (pagerState.currentPage < 5) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier.width(buttonWidth).graphicsLayer { alpha = buttonAlpha.value },
                        shape = G2Shapes.button,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        AnimatedContent(
                            targetState = buttonText,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "btnText"
                        ) { text ->
                            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 进度指示器
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(6) { index ->
                        val isActive = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isActive) 24.dp else 8.dp,
                            animationSpec = tween(200),
                            label = "dotWidth"
                        )
                        val alpha by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0.3f,
                            animationSpec = tween(200),
                            label = "dotAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(width)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
            }

        // 层级 2：全局圆角和边框预览
        OOBELiveBorderPreview(
            pagerState, realtimeThickness, realtimeSegmentLength, isDragging,
            isG2Enabled = isG2Enabled,
            primaryColor = oobeBlendedScheme.primary,
            offTLX = calibTLX.value, offTLY = calibTLY.value,
            offTRX = calibTRX.value, offTRY = calibTRY.value,
            offBLX = calibBLX.value, offBLY = calibBLY.value,
            offBRX = calibBRX.value, offBRY = calibBRY.value
        )

        // 层级 3：全屏彩带
        AnimatedVisibility(
            visible = pagerState.currentPage == 5,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(800))
        ) {
            ConfettiFireworks(
                modifier = Modifier.fillMaxSize(),
                isActive = true
            )
        }
    }
}

// 卡片颜色
@Composable
fun oobeCardColor(isDark: Boolean) = DeviceUtils.cardContainerColor(isDark)
fun oobeCardBorder(isDark: Boolean) = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent

// 1.欢迎页
@Composable
fun OOBEWelcomeStep(isDark: Boolean) {
    val animEnabled = ThemeSettings.isAnimationEnabled
    val logoAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val logoOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val titleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val titleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val subtitleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val subtitleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }

    val slideSpec = tween<Float>(400, easing = FastOutSlowInEasing)

    LaunchedEffect(Unit) {
        if (!animEnabled) return@LaunchedEffect
        launch {
            launch { logoAlpha.animateTo(1f, tween(400)) }
            logoOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch {
            launch { titleAlpha.animateTo(1f, tween(400)) }
            titleOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch { subtitleAlpha.animateTo(1f, tween(400)) }
        subtitleOffset.animateTo(0f, slideSpec)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer { alpha = logoAlpha.value; translationY = logoOffset.value * density }
                .background(color = MaterialTheme.colorScheme.onPrimaryContainer, shape = G2Shapes.logo),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "欢迎使用 ScreenTester",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha.value; translationY = titleOffset.value * density }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "让我们花一分钟完成初始设置",
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = subtitleAlpha.value; translationY = subtitleOffset.value * density }
        )
    }
}

// 2.圆角校准
@Composable
fun OOBECornerCalibrationStep(
    isDark: Boolean,
    onCalibrationChanged: (Boolean) -> Unit,
    isG2Enabled: Boolean,
    onG2Changed: (Boolean) -> Unit,
    tlXAnim: Animatable<Float, *>, trXAnim: Animatable<Float, *>,
    blXAnim: Animatable<Float, *>, brXAnim: Animatable<Float, *>,
    tlYAnim: Animatable<Float, *>, trYAnim: Animatable<Float, *>,
    blYAnim: Animatable<Float, *>, brYAnim: Animatable<Float, *>
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val systemRadius = try {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets
        insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
    } catch (_: Exception) { 100f }

    var useCustom by remember { mutableStateOf(ThemeSettings.useCustomRadius) }

    // 校准状态
    val tlAnim = remember { Animatable(if (ThemeSettings.radiusTL < 0f) systemRadius else ThemeSettings.radiusTL) }
    val trAnim = remember { Animatable(if (ThemeSettings.radiusTR < 0f) systemRadius else ThemeSettings.radiusTR) }
    val blAnim = remember { Animatable(if (ThemeSettings.radiusBL < 0f) systemRadius else ThemeSettings.radiusBL) }
    val brAnim = remember { Animatable(if (ThemeSettings.radiusBR < 0f) systemRadius else ThemeSettings.radiusBR) }
    var isLinked by remember { mutableStateOf(true) }
    var activeSection by remember { mutableStateOf<Int?>(0) }
    var pinnedSections by remember { mutableStateOf(setOf<Int>()) }
    val sliderSpec = tween<Float>(800, easing = FastOutSlowInEasing)

    // 卡片颜色
    val lightCardColor = oobeCardColor(false)
    val darkCardColor = oobeCardColor(isDark)
    val animatedCardColor by animateColorAsState(
        targetValue = if (useCustom) lightCardColor else darkCardColor,
        animationSpec = tween(500),
        label = "cardColor"
    )
    val animatedCardBorder by animateColorAsState(
        targetValue = if (useCustom) Color.Transparent else oobeCardBorder(isDark),
        animationSpec = tween(500),
        label = "cardBorder"
    )
    // 校准卡片（SectionCard）背景色渐变
    val sectionCardColor by animateColorAsState(
        targetValue = if (useCustom) oobeCardColor(false) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(500),
        label = "sectionCardColor"
    )

    // 实时持久化 + 内存更新
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val _trigger = tlAnim.value + trAnim.value + blAnim.value + brAnim.value +
            tlXAnim.value + trXAnim.value + blXAnim.value + brXAnim.value +
            tlYAnim.value + trYAnim.value + blYAnim.value + brYAnim.value
    SideEffect {
        if (useCustom) {
            ThemeSettings.useCustomRadius = true
            ThemeSettings.radiusTL = tlAnim.value; ThemeSettings.radiusTR = trAnim.value
            ThemeSettings.radiusBL = blAnim.value; ThemeSettings.radiusBR = brAnim.value
        }
        ThemeSettings.saveCustomRadius(context, useCustom, tlAnim.value, trAnim.value, blAnim.value, brAnim.value)
        prefs.edit().apply {
            putFloat("r_tl_x", tlXAnim.value); putFloat("r_tr_x", trXAnim.value)
            putFloat("r_bl_x", blXAnim.value); putFloat("r_br_x", brXAnim.value)
            putFloat("r_tl_y", tlYAnim.value); putFloat("r_tr_y", trYAnim.value)
            putFloat("r_bl_y", blYAnim.value); putFloat("r_br_y", brYAnim.value)
            putBoolean("is_g2_enabled", isG2Enabled)
            apply()
        }
    }

    // 浅色主题平滑过渡
    val lightScheme = remember { dynamicLightColorScheme(context) }
    val darkScheme = MaterialTheme.colorScheme
    val blendFactor by animateFloatAsState(
        targetValue = if (useCustom) 1f else 0f,
        animationSpec = tween(500),
        label = "themeBlend"
    )
    val blendedScheme = darkScheme.copy(
        surfaceVariant = lerp(darkScheme.surfaceVariant, lightScheme.surfaceVariant, blendFactor),
        surface = lerp(darkScheme.surface, lightScheme.surface, blendFactor),
        onSurface = lerp(darkScheme.onSurface, lightScheme.onSurface, blendFactor),
        onSurfaceVariant = lerp(darkScheme.onSurfaceVariant, lightScheme.onSurfaceVariant, blendFactor),
        primary = lerp(darkScheme.primary, lightScheme.primary, blendFactor),
        onPrimary = lerp(darkScheme.onPrimary, lightScheme.onPrimary, blendFactor),
        primaryContainer = lerp(darkScheme.primaryContainer, lightScheme.primaryContainer, blendFactor),
        onPrimaryContainer = lerp(darkScheme.onPrimaryContainer, lightScheme.onPrimaryContainer, blendFactor),
        secondary = lerp(darkScheme.secondary, lightScheme.secondary, blendFactor),
        onSecondary = lerp(darkScheme.onSecondary, lightScheme.onSecondary, blendFactor),
        secondaryContainer = lerp(darkScheme.secondaryContainer, lightScheme.secondaryContainer, blendFactor),
        onSecondaryContainer = lerp(darkScheme.onSecondaryContainer, lightScheme.onSecondaryContainer, blendFactor),
        outline = lerp(darkScheme.outline, lightScheme.outline, blendFactor),
        outlineVariant = lerp(darkScheme.outlineVariant, lightScheme.outlineVariant, blendFactor),
    )

    MaterialTheme(colorScheme = blendedScheme) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("圆角校准", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("绘制的线条圆角贴合你的手机屏幕圆角吗\n若不一致可打开开关手动校准", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = animatedCardColor),
            border = BorderStroke(1.dp, animatedCardBorder)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自定义圆角半径", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        val displayRadius = if (useCustom) {
                            "当前自定义圆角：${String.format(java.util.Locale.US, "%.1f", tlAnim.value)} px"
                        } else {
                            "当前系统圆角：${String.format(java.util.Locale.US, "%.1f", systemRadius)} px"
                        }
                        Text(displayRadius, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = useCustom,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            useCustom = it
                            onCalibrationChanged(it)
                            if (it) {
                                ThemeSettings.useCustomRadius = true
                                ThemeSettings.radiusTL = tlAnim.value; ThemeSettings.radiusTR = trAnim.value
                                ThemeSettings.radiusBL = blAnim.value; ThemeSettings.radiusBR = brAnim.value
                            } else {
                                ThemeSettings.useCustomRadius = false
                                ThemeSettings.radiusTL = -1f; ThemeSettings.radiusTR = -1f
                                ThemeSettings.radiusBL = -1f; ThemeSettings.radiusBR = -1f
                            }
                        }
                    )
                }
            }
        }

        // 嵌入式校准控件
        AnimatedVisibility(visible = useCustom) {
            Column {
                Spacer(Modifier.height(12.dp))

                // G2 平滑 + 全局同步 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            isLinked = !isLinked
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            focusManager.clearFocus()
                            if (isLinked) {
                                scope.launch { trAnim.animateTo(tlAnim.value, sliderSpec) }
                                scope.launch { blAnim.animateTo(tlAnim.value, sliderSpec) }
                                scope.launch { brAnim.animateTo(tlAnim.value, sliderSpec) }
                                scope.launch { trXAnim.animateTo(tlXAnim.value, sliderSpec) }
                                scope.launch { blXAnim.animateTo(tlXAnim.value, sliderSpec) }
                                scope.launch { brXAnim.animateTo(tlXAnim.value, sliderSpec) }
                                scope.launch { trYAnim.animateTo(tlYAnim.value, sliderSpec) }
                                scope.launch { blYAnim.animateTo(tlYAnim.value, sliderSpec) }
                                scope.launch { brYAnim.animateTo(tlYAnim.value, sliderSpec) }
                            }
                        },
                        shape = G2Shapes.gridCard,
                        modifier = Modifier.weight(1.2f).height(44.dp)
                    ) {
                        Text(
                            text = if (isLinked) "全局同步调节" else "四角独立调节",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                    }

                    val g2BgColor by animateColorAsState(
                        targetValue = if (isG2Enabled) lightScheme.primary else lightScheme.surfaceVariant,
                        animationSpec = tween(300), label = "g2Bg"
                    )
                    val g2ContentColor by animateColorAsState(
                        targetValue = if (isG2Enabled) lightScheme.onPrimary else lightScheme.onSurfaceVariant,
                        animationSpec = tween(300), label = "g2Ct"
                    )
                    Button(
                        onClick = {
                            onG2Changed(!isG2Enabled)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        shape = G2Shapes.gridCard,
                        colors = ButtonDefaults.buttonColors(containerColor = g2BgColor, contentColor = g2ContentColor),
                        modifier = Modifier.weight(0.8f).height(44.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = if (isG2Enabled) "G2平滑:开" else "G2平滑:关",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(
                    title = "基础圆角半径",
                    isExpanded = activeSection == 0 || 0 in pinnedSections,
                    isPinned = 0 in pinnedSections,
                    containerColor = sectionCardColor,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (0 in pinnedSections) pinnedSections = pinnedSections - 0
                        else if (activeSection == 0) activeSection = null
                        else activeSection = 0
                    },
                    onLongClick = {
                        pinnedSections = if (0 in pinnedSections) pinnedSections - 0 else pinnedSections + 0
                    }
                ) {
                    CalibrationSliderGroup(isLinked, tlAnim, trAnim, blAnim, brAnim, systemRadius, sliderSpec, 0f..300f, scope)
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(
                    title = "横向 (X轴) 曲率修正",
                    isExpanded = activeSection == 1 || 1 in pinnedSections,
                    isPinned = 1 in pinnedSections,
                    containerColor = sectionCardColor,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (1 in pinnedSections) pinnedSections = pinnedSections - 1
                        else if (activeSection == 1) activeSection = null
                        else activeSection = 1
                    },
                    onLongClick = {
                        pinnedSections = if (1 in pinnedSections) pinnedSections - 1 else pinnedSections + 1
                    }
                ) {
                    CalibrationSliderGroup(isLinked, tlXAnim, trXAnim, blXAnim, brXAnim, 0f, sliderSpec, -150f..150f, scope)
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(
                    title = "纵向 (Y轴) 曲率修正",
                    isExpanded = activeSection == 2 || 2 in pinnedSections,
                    isPinned = 2 in pinnedSections,
                    containerColor = sectionCardColor,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (2 in pinnedSections) pinnedSections = pinnedSections - 2
                        else if (activeSection == 2) activeSection = null
                        else activeSection = 2
                    },
                    onLongClick = {
                        pinnedSections = if (2 in pinnedSections) pinnedSections - 2 else pinnedSections + 2
                    }
                ) {
                    CalibrationSliderGroup(isLinked, tlYAnim, trYAnim, blYAnim, brYAnim, 0f, sliderSpec, -150f..150f, scope)
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
    }
}

// 3.测试线条
@Composable
fun OOBETestLineStep(
    isDark: Boolean,
    onThicknessChange: ((Float) -> Unit)? = null,
    onSegmentLengthChange: ((Float) -> Unit)? = null,
    onDraggingChange: ((Boolean) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("测试线条", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("自定义测试线条的粗细和颜色", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            ColorPickerSection(
                showTopDivider = false,
                onThicknessChange = onThicknessChange,
                onSegmentLengthChange = onSegmentLengthChange,
                onDraggingChange = onDraggingChange
            )
        }
    }
}

// 4.纯净模式
@Composable
fun OOBEPureModeStep(isDark: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("纯净模式", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "开启后将隐藏测试页中的部分文案和底部参数\n隐藏右上角切换按钮，改为双击屏幕切换模式",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        var isEnabled by remember { mutableStateOf(ThemeSettings.isCompactModeEnabled) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用纯净模式", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) "已开启 - 测试页将显示纯净界面" else "已关闭 - 测试页将显示完整信息",
                        fontSize = 13.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isEnabled = it
                        ThemeSettings.saveCompactModeConfig(context, it)
                    }
                )
            }
        }
    }
}

// 5.卡片动效
@Composable
fun OOBECardAnimationStep(isDark: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("卡片动效", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "为主页和设置页的卡片添加淡入动画\n让界面切换更加流畅自然",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        var isEnabled by remember { mutableStateOf(ThemeSettings.isAnimationEnabled) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = G2Shapes.card,
            colors = CardDefaults.cardColors(containerColor = oobeCardColor(isDark)),
            border = BorderStroke(1.dp, oobeCardBorder(isDark))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用卡片动效", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) "已开启 - 卡片将带有淡入淡出效果" else "已关闭 - 卡片将直接显示",
                        fontSize = 13.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isEnabled = it
                        ThemeSettings.saveAnimationConfig(context, it)
                    }
                )
            }
        }
    }
}

// 6.完成页
@Composable
fun OOBECompletionStep(isDark: Boolean, isActive: Boolean = false) {
    val animEnabled = ThemeSettings.isAnimationEnabled
    var animPlayed by remember { mutableStateOf(false) }
    val iconAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val iconOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val titleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val titleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }
    val subtitleAlpha = remember { Animatable(if (animEnabled) 0f else 1f) }
    val subtitleOffset = remember { Animatable(if (animEnabled) 40f else 0f) }

    val slideSpec = tween<Float>(400, easing = FastOutSlowInEasing)

    LaunchedEffect(isActive) {
        if (!isActive || animPlayed) return@LaunchedEffect
        if (!animEnabled) {
            iconAlpha.snapTo(1f); iconOffset.snapTo(0f)
            titleAlpha.snapTo(1f); titleOffset.snapTo(0f)
            subtitleAlpha.snapTo(1f); subtitleOffset.snapTo(0f)
            animPlayed = true
            return@LaunchedEffect
        }
        animPlayed = true
        launch {
            launch { iconAlpha.animateTo(1f, tween(400)) }
            iconOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch {
            launch { titleAlpha.animateTo(1f, tween(400)) }
            titleOffset.animateTo(0f, slideSpec)
        }
        delay(120)
        launch { subtitleAlpha.animateTo(1f, tween(400)) }
        subtitleOffset.animateTo(0f, slideSpec)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer { alpha = iconAlpha.value; translationY = iconOffset.value * density },
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "设置完成",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha.value; translationY = titleOffset.value * density }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "一切准备就绪\n开始测试你的屏幕吧",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha.value; translationY = subtitleOffset.value * density }
            )
        }
    }
}

// 彩带动画
@Composable
fun ConfettiFireworks(modifier: Modifier = Modifier, isActive: Boolean = true) {
    val particles = remember { mutableStateListOf<ConfettiParticle>() }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val colors = listOf(
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
            Color(0xFFA8E6CF), Color(0xFFFF8B94), Color(0xFF6C5CE7),
            Color(0xFF00B894), Color(0xFFFD79A8), Color(0xFFE17055),
            Color(0xFF0984E3), Color(0xFF00CEC9), Color(0xFFFDCB6E),
            Color(0xFFFF4757), Color(0xFF2ED573), Color(0xFF1E90FF)
        )
        val random = Random(System.currentTimeMillis())

        val leftX = screenWidthPx * 0.1f
        val rightX = screenWidthPx * 0.9f
        val burstCount = if (ThemeSettings.isAnimationEnabled) 12 else 6  // 低性能设备彩带对半砍
        for (burst in 0..burstCount) {
            delay(150L + random.nextInt(250).toLong())
            for (side in 0..1) {
                val burstX = if (side == 0) leftX else rightX
                for (i in 0..24) {
                    // 三种角度并行：每颗粒子随机命中一种
                    val type = random.nextInt(20)
                    val rawAngle = when {
                        type < 7  -> -90f + (random.nextFloat() - 0.5f) * 20f     // 垂直向上 ×7
                        type < 14 -> -45f + (random.nextFloat() - 0.5f) * 20f     // 45° 斜喷 ×7
                        else      -> -70f                                         // 70° 固定 ×6
                    }
                    val angle = if (side == 0) rawAngle else -180f - rawAngle
                    val speed = if (type < 14) 1800f + random.nextFloat() * 1200f else 1200f + random.nextFloat() * 1000f  // 垂直/45° ≥3/4屏，70° ≥3/5屏
                    val rad = Math.toRadians(angle.toDouble()).toFloat()
                    particles.add(ConfettiParticle(
                        x = burstX + (random.nextFloat() - 0.5f) * 30f,
                        y = screenHeightPx,
                        vx = cos(rad) * speed,
                        vy = sin(rad) * speed,
                        color = colors[random.nextInt(colors.size)],
                        size = 3f + random.nextFloat() * 5f,
                        sizeH = 15f + random.nextFloat() * 25f,
                        rotation = random.nextFloat() * 360f,
                        rotationSpeed = (random.nextFloat() - 0.5f) * 1440f,
                        alpha = 1f,
                        lifetime = 5000f + random.nextFloat() * 3000f,
                        age = 0f
                    ))
                }
            }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(16)
            val toRemove = mutableListOf<ConfettiParticle>()
            for (i in particles.indices) {
                val p = particles[i]
                val newAge = p.age + 16f
                if (newAge >= p.lifetime) { toRemove.add(p); continue }
                val lifeRatio = newAge / p.lifetime
                particles[i] = p.copy(
                    x = p.x + p.vx * 0.016f,
                    y = p.y + p.vy * 0.016f,
                    vx = p.vx * 0.992f,
                    vy = p.vy * 0.992f + 12f,
                    rotation = p.rotation + p.rotationSpeed * 0.016f,
                    alpha = if (lifeRatio > 0.7f) 1f - ((lifeRatio - 0.7f) / 0.3f) else 1f,
                    age = newAge
                )
            }
            if (toRemove.isNotEmpty()) particles.removeAll(toRemove)
            tick++
        }
    }

    val particleSnapshot = particles.toList()
    Canvas(modifier = modifier) {
        for (p in particleSnapshot) {
            rotate(p.rotation, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color.copy(alpha = p.alpha),
                    topLeft = Offset(p.x - p.size / 2, p.y - p.sizeH / 2),
                    size = Size(p.size, p.sizeH)
                )
            }
        }
    }
}

data class ConfettiParticle(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val color: Color, val size: Float,
    val sizeH: Float = size * 3f,
    val rotation: Float, val rotationSpeed: Float,
    val alpha: Float, val lifetime: Float, val age: Float
)

// 圆角边框预览
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OOBELiveBorderPreview(
    pagerState: androidx.compose.foundation.pager.PagerState,
    realtimeThickness: Float = ThemeSettings.testLineThickness,
    realtimeSegmentLength: Float = if (ThemeSettings.multiColorSegmentLength == 0f) 1f else ThemeSettings.multiColorSegmentLength,
    isDragging: Boolean = false,
    isG2Enabled: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    offTLX: Float = 0f, offTLY: Float = 0f,
    offTRX: Float = 0f, offTRY: Float = 0f,
    offBLX: Float = 0f, offBLY: Float = 0f,
    offBRX: Float = 0f, offBRY: Float = 0f
) {
    val targetAlpha = if (pagerState.currentPage in 1..2) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(500),
        label = "borderAlpha"
    )

    if (alpha == 0f) return

    val context = LocalContext.current

    val systemRadius = try {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets
        insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f
    } catch (_: Exception) { 100f }

    val baseTL = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusTL.coerceAtLeast(0f) else systemRadius
    val baseTR = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusTR.coerceAtLeast(0f) else systemRadius
    val baseBL = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusBL.coerceAtLeast(0f) else systemRadius
    val baseBR = if (ThemeSettings.useCustomRadius) ThemeSettings.radiusBR.coerceAtLeast(0f) else systemRadius

    val tlX = (baseTL + if (ThemeSettings.useCustomRadius) offTLX else 0f).coerceAtLeast(0f)
    val tlY = (baseTL + if (ThemeSettings.useCustomRadius) offTLY else 0f).coerceAtLeast(0f)
    val trX = (baseTR + if (ThemeSettings.useCustomRadius) offTRX else 0f).coerceAtLeast(0f)
    val trY = (baseTR + if (ThemeSettings.useCustomRadius) offTRY else 0f).coerceAtLeast(0f)
    val blX = (baseBL + if (ThemeSettings.useCustomRadius) offBLX else 0f).coerceAtLeast(0f)
    val blY = (baseBL + if (ThemeSettings.useCustomRadius) offBLY else 0f).coerceAtLeast(0f)
    val brX = (baseBR + if (ThemeSettings.useCustomRadius) offBRX else 0f).coerceAtLeast(0f)
    val brY = (baseBR + if (ThemeSettings.useCustomRadius) offBRY else 0f).coerceAtLeast(0f)

    val isCalibrationPage = pagerState.currentPage == 1

    // 拖动时实时更新，页面切换时保留动画
    val animatedStrokeW by animateFloatAsState(
        targetValue = if (isCalibrationPage && !ThemeSettings.useCustomRadius) 10f else realtimeThickness,
        animationSpec = if (isDragging) tween(0) else tween(400),
        label = "strokeWAnimation"
    )

    val transitionT by animateFloatAsState(
        targetValue = if (isCalibrationPage) 1f else 0f,
        animationSpec = tween(400),
        label = "colorTransition"
    )

    // 读取渐变/单色状态
    val isMultiColor = ThemeSettings.isMultiColorMode
    val multiColors = ThemeSettings.multiColorSelectedColors
    val singleColor = ThemeSettings.testLineColor

    Canvas(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        val offset = animatedStrokeW / 2f
        val L = offset
        val T = offset
        val R = size.width - offset
        val B = size.height - offset

        val path = androidx.compose.ui.graphics.Path()

        if (isG2Enabled && ThemeSettings.useCustomRadius) {
            val p = 1.4f
            val c = 0.45f
            path.moveTo(L + p * tlX, T)
            path.lineTo(R - p * trX, T)
            path.cubicTo(R - c * trX, T, R, T + c * trY, R, T + p * trY)
            path.lineTo(R, B - p * brY)
            path.cubicTo(R, B - c * brY, R - c * brX, B, R - p * brX, B)
            path.lineTo(L + p * blX, B)
            path.cubicTo(L + c * blX, B, L, B - c * blY, L, B - p * blY)
            path.lineTo(L, T + p * tlY)
            path.cubicTo(L, T + c * tlY, L + c * tlX, T, L + p * tlX, T)
            path.close()
        } else {
            path.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = L, top = T, right = R, bottom = B,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(tlX, tlY),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(trX, trY),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(brX, brY),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(blX, blY)
                )
            )
        }

        // 绘制测试线条颜色
        if (transitionT < 1f) {
            val testBrush = if (isMultiColor && multiColors.size >= 2) {
                val segmentLength = if (realtimeSegmentLength == 0f) 1.0f else realtimeSegmentLength
                val totalLength = size.width.coerceAtLeast(size.height)
                val repeatCount = (totalLength / (segmentLength * 200f)).toInt().coerceAtLeast(1)

                val colorsList = mutableListOf<Color>()
                val colorStopsList = mutableListOf<Float>()

                for (i in 0 until repeatCount) {
                    for ((index, color) in multiColors.withIndex()) {
                        colorsList.add(Color(color))
                        colorStopsList.add((i * multiColors.size + index).toFloat() / (repeatCount * multiColors.size))
                    }
                }
                colorsList.add(Color(multiColors.last()))
                colorStopsList.add(1.0f)

                val colorStopsArray = colorStopsList.zip(colorsList).toTypedArray()

                Brush.linearGradient(
                    *colorStopsArray,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            } else {
                val fallbackColor = if (isMultiColor && multiColors.isNotEmpty()) multiColors.first() else singleColor
                androidx.compose.ui.graphics.SolidColor(Color(fallbackColor))
            }

            drawPath(
                path = path,
                brush = testBrush,
                alpha = 1f - transitionT,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = animatedStrokeW)
            )
        }

        // 绘制校准车间的主题色
        if (transitionT > 0f) {
            drawPath(
                path = path,
                brush = androidx.compose.ui.graphics.SolidColor(primaryColor),
                alpha = transitionT,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = animatedStrokeW)
            )
        }
    }
}