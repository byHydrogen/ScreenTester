package com.hydrogen.screentester

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class CornerCalibrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 渲染进摄像头挖孔区域，避免全屏时挖孔处出现黑条（等效系统"刘海屏：自动匹配"）
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowInsetsControllerCompat(window, view).apply {
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }

            MaterialTheme(colorScheme = dynamicLightColorScheme(LocalContext.current)) {
                CalibrationScreen { finish() }
            }
        }
    }
}

@Composable
fun CalibrationScreen(onExit: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // 教程状态
    val tutorialTargets = remember { mutableStateMapOf<Int, Rect>() }
    var tutorialActive by remember { mutableStateOf(!isTutorialShown(context)) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    // 双击返回键拦截机制
    var lastBackTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackTime < 2000) {
            onExit() // 两秒内连按两次，执行退出
        } else {
            lastBackTime = currentTime
            Toast.makeText(context, "再按一次返回键退出校准车间\n若未保存将丢失此次更改", Toast.LENGTH_SHORT).show()
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) // 震动反馈提示
        }
    }

    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    val insets = LocalView.current.rootWindowInsets
    val systemRadius = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 100f

    val tlAnim = remember { Animatable(if (ThemeSettings.radiusTL < 0f) systemRadius else ThemeSettings.radiusTL) }
    val trAnim = remember { Animatable(if (ThemeSettings.radiusTR < 0f) systemRadius else ThemeSettings.radiusTR) }
    val blAnim = remember { Animatable(if (ThemeSettings.radiusBL < 0f) systemRadius else ThemeSettings.radiusBL) }
    val brAnim = remember { Animatable(if (ThemeSettings.radiusBR < 0f) systemRadius else ThemeSettings.radiusBR) }

    val tlXAnim = remember { Animatable(prefs.getFloat("r_tl_x", 0f)) }
    val trXAnim = remember { Animatable(prefs.getFloat("r_tr_x", 0f)) }
    val blXAnim = remember { Animatable(prefs.getFloat("r_bl_x", 0f)) }
    val brXAnim = remember { Animatable(prefs.getFloat("r_br_x", 0f)) }

    val tlYAnim = remember { Animatable(prefs.getFloat("r_tl_y", 0f)) }
    val trYAnim = remember { Animatable(prefs.getFloat("r_tr_y", 0f)) }
    val blYAnim = remember { Animatable(prefs.getFloat("r_bl_y", 0f)) }
    val brYAnim = remember { Animatable(prefs.getFloat("r_br_y", 0f)) }

    // G2 平滑圆角状态管理开关
    var isG2Enabled by remember { mutableStateOf(prefs.getBoolean("is_g2_enabled", false)) }
    var isLinked by remember { mutableStateOf(true) }
    var activeSection by remember { mutableStateOf<Int?>(0) }
    var pinnedSections by remember { mutableStateOf(setOf<Int>()) }

    // 教程定位时自动展开对应 SectionCard
    LaunchedEffect(tutorialActive, tutorialStep) {
        if (tutorialActive) {
            // 每次切换教程步骤时，立刻清除卡片的固定（图钉）状态
            pinnedSections = emptySet()

            // 切换到 G2圆角高亮（Step 1）及以后时，强制恢复到“全局同步”模式
            if (tutorialStep >= 1) {
                isLinked = true
            }

            when (tutorialStep) {
                0, 1, 2, 3 -> activeSection = 0 // 步骤0、1、2、3 都默认保持首个“基础圆角半径”卡片展开
                4 -> activeSection = 1          // 步骤4 展开 X轴
                5 -> activeSection = 2          // 步骤5 展开 Y轴
            }
        } else {
            // 教程结束后，恢复默认状态，也拔掉图钉
            activeSection = 0
            pinnedSections = emptySet()
            isLinked = true
        }
    }

    val sliderSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.White)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = ThemeSettings.testLineThickness
            val offset = strokeW / 2f

            val L = offset
            val T = offset
            val R = size.width - offset
            val B = size.height - offset

            val tlx = (tlAnim.value + tlXAnim.value).coerceAtLeast(0f)
            val tly = (tlAnim.value + tlYAnim.value).coerceAtLeast(0f)
            val trx = (trAnim.value + trXAnim.value).coerceAtLeast(0f)
            val tryY = (trAnim.value + trYAnim.value).coerceAtLeast(0f)
            val brx = (brAnim.value + brXAnim.value).coerceAtLeast(0f)
            val bry = (brAnim.value + brYAnim.value).coerceAtLeast(0f)
            val blx = (blAnim.value + blXAnim.value).coerceAtLeast(0f)
            val bly = (blAnim.value + blYAnim.value).coerceAtLeast(0f)

            drawPath(
                path = Path().apply {
                    if (isG2Enabled) {
                        // 高精度 G2 连续曲率超椭圆数学重绘（使用单边双阶控制因子平滑消阶）
                        val p = 1.4f  // 缓进斜率基数
                        val c = 0.45f // 连续性平滑修正点

                        moveTo(L + p * tlx, T)
                        lineTo(R - p * trx, T)
                        // 右上角曲率衔接
                        cubicTo(R - c * trx, T, R, T + c * tryY, R, T + p * tryY)
                        lineTo(R, B - p * bry)
                        // 右下角曲率衔接
                        cubicTo(R, B - c * bry, R - c * brx, B, R - p * brx, B)
                        lineTo(L + p * blx, B)
                        // 左下角曲率衔接
                        cubicTo(L + c * blx, B, L, B - c * bly, L, B - p * bly)
                        lineTo(L, T + p * tly)
                        // 左上角曲率衔接
                        cubicTo(L, T + c * tly, L + c * tlx, T, L + p * tlx, T)
                        close()
                    } else {
                        // 经典标准普通圆角（G1 衔接）
                        addRoundRect(RoundRect(
                            left = L, top = T, right = R, bottom = B,
                            topLeftCornerRadius = CornerRadius(tlx, tly),
                            topRightCornerRadius = CornerRadius(trx, tryY),
                            bottomRightCornerRadius = CornerRadius(brx, bry),
                            bottomLeftCornerRadius = CornerRadius(blx, bly)
                        ))
                    }
                },
                color = Color.Red,
                style = Stroke(width = strokeW)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            tutorialTargets[0] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                        }
                        .weight(1.2f).height(44.dp),
                    onClick = {
                        isLinked = !isLinked
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        focusManager.clearFocus()
                        if (isLinked) {
                            scope.launch { trAnim.animateTo(tlAnim.value, sliderSpec) }; scope.launch { blAnim.animateTo(tlAnim.value, sliderSpec) }; scope.launch { brAnim.animateTo(tlAnim.value, sliderSpec) }
                            scope.launch { trXAnim.animateTo(tlXAnim.value, sliderSpec) }; scope.launch { blXAnim.animateTo(tlXAnim.value, sliderSpec) }; scope.launch { brXAnim.animateTo(tlXAnim.value, sliderSpec) }
                            scope.launch { trYAnim.animateTo(tlYAnim.value, sliderSpec) }; scope.launch { blYAnim.animateTo(tlYAnim.value, sliderSpec) }; scope.launch { brYAnim.animateTo(tlYAnim.value, sliderSpec) }
                        }
                    },
                    shape = G2Shapes.gridCard
                ) {
                    Text(text = if (isLinked) "全局同步调节" else "四角独立调节", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                val g2ButtonContainerColor by animateColorAsState(
                    targetValue = if (isG2Enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(durationMillis = 300)
                )
                val g2ButtonContentColor by animateColorAsState(
                    targetValue = if (isG2Enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 300)
                )

                Button(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            tutorialTargets[1] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                        }
                        .weight(0.8f).height(44.dp),
                    onClick = {
                        isG2Enabled = !isG2Enabled
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    shape = G2Shapes.gridCard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = g2ButtonContainerColor,
                        contentColor = g2ButtonContentColor
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(text = if (isG2Enabled) "G2平滑:开" else "G2平滑:关", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[2] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }) {
                SectionCard(
                    title = "基础圆角半径",
                    isExpanded = activeSection == 0 || 0 in pinnedSections,
                    isPinned = 0 in pinnedSections,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (0 in pinnedSections) {
                            pinnedSections = pinnedSections - 0
                        } else if (activeSection == 0) {
                            activeSection = null
                        } else {
                            activeSection = 0
                        }
                    },
                    onLongClick = { pinnedSections = if (0 in pinnedSections) pinnedSections - 0 else pinnedSections + 0 }
                ) {
                    CalibrationSliderGroup(isLinked, tlAnim, trAnim, blAnim, brAnim, systemRadius, sliderSpec, 0f..300f, scope)
                }
                } // end Box (target 2)

                Box(modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[3] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }) {
                SectionCard(
                    title = "横向 (X轴) 曲率修正",
                    isExpanded = activeSection == 1 || 1 in pinnedSections,
                    isPinned = 1 in pinnedSections,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (1 in pinnedSections) {
                            pinnedSections = pinnedSections - 1
                        } else if (activeSection == 1) {
                            activeSection = null
                        } else {
                            activeSection = 1
                        }
                    },
                    onLongClick = { pinnedSections = if (1 in pinnedSections) pinnedSections - 1 else pinnedSections + 1 }
                ) {
                    CalibrationSliderGroup(isLinked, tlXAnim, trXAnim, blXAnim, brXAnim, 0f, sliderSpec, -150f..150f, scope)
                }
                } // end Box (target 3)

                Box(modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    tutorialTargets[4] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                }) {
                SectionCard(
                    title = "纵向 (Y轴) 曲率修正",
                    isExpanded = activeSection == 2 || 2 in pinnedSections,
                    isPinned = 2 in pinnedSections,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (2 in pinnedSections) {
                            pinnedSections = pinnedSections - 2
                        } else if (activeSection == 2) {
                            activeSection = null
                        } else {
                            activeSection = 2
                        }
                    },
                    onLongClick = { pinnedSections = if (2 in pinnedSections) pinnedSections - 2 else pinnedSections + 2 }
                ) {
                    CalibrationSliderGroup(isLinked, tlYAnim, trYAnim, blYAnim, brYAnim, 0f, sliderSpec, -150f..150f, scope)
                }
                } // end Box (target 4)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                FilledTonalButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onExit()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = G2Shapes.gridCard,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("取消", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        ThemeSettings.saveCustomRadius(context, true, tlAnim.targetValue, trAnim.targetValue, blAnim.targetValue, brAnim.targetValue)

                        prefs.edit().apply {
                            putFloat("r_tl_x", tlXAnim.targetValue)
                            putFloat("r_tr_x", trXAnim.targetValue)
                            putFloat("r_bl_x", blXAnim.targetValue)
                            putFloat("r_br_x", brXAnim.targetValue)
                            putFloat("r_tl_y", tlYAnim.targetValue)
                            putFloat("r_tr_y", trYAnim.targetValue)
                            putFloat("r_bl_y", blYAnim.targetValue)
                            putFloat("r_br_y", brYAnim.targetValue)
                            putBoolean("is_g2_enabled", isG2Enabled)
                            apply()
                        }
                        onExit()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = G2Shapes.gridCard,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) { Text("保存并应用", fontWeight = FontWeight.Bold) }
            }
        }

        // 教程高亮引导
        if (tutorialActive) {
            val steps = listOf(
                "调节模式" to "点击可切换调节模式\n全局同步调节四角圆角一致，若您的屏幕四角曲率不同可点击按钮切换为四角独立调节，该模式下可分别调整四个角至贴合手机屏幕圆角",
                "G2 平滑圆角" to "开启后使用 G2 连续曲率算法，圆角线条更加圆润流畅，贴合屏幕物理曲率",
                "基础圆角半径" to "您可通过拖动滑块，点击 +/- 按钮或点击数字调起键盘输入数字调节屏幕四个角的圆角半径大小，实时预览线条变化",
                "卡片与调节操作说明" to "长按卡片可固定，点击重置按钮可恢复默认，点击 +/- 按钮可 ± 0.1，长按 +/- 按钮可快速连续加减",
                "横向 X 轴曲率修正" to "微调圆角在水平方向的偏移量",
                "纵向 Y 轴曲率修正" to "微调圆角在垂直方向的偏移量"
            )

            val targetIndex = when (tutorialStep) {
                0 -> 0
                1 -> 1
                2, 3 -> 2
                4 -> 3
                5 -> 4
                else -> 0
            }

            TutorialOverlay(
                currentStep = tutorialStep,
                totalSteps = 6,
                targetBounds = tutorialTargets[targetIndex],
                title = steps[tutorialStep].first,
                description = steps[tutorialStep].second,
                onPrevious = if (tutorialStep > 0) {{ tutorialStep-- }} else null,
                onNext = {
                    if (tutorialStep < 5) {
                        tutorialStep++
                    } else {
                        tutorialActive = false
                        markTutorialShown(context)
                    }
                },
                onSkip = {
                    tutorialActive = false
                    markTutorialShown(context)
                },
                isLastStep = tutorialStep == 5
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SectionCard(
    title: String,
    isExpanded: Boolean,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )
    val pinAlpha by animateFloatAsState(
        targetValue = if (isPinned) 1f else 0f,
        label = "pinAlpha"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = G2Shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                // 固定图标
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已固定",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { alpha = pinAlpha },
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
fun CalibrationSliderGroup(
    isLinked: Boolean,
    tl: Animatable<Float, *>, tr: Animatable<Float, *>, bl: Animatable<Float, *>, br: Animatable<Float, *>,
    defaultVal: Float, sliderSpec: AnimationSpec<Float>, valueRange: ClosedFloatingPointRange<Float>,
    scope: CoroutineScope
) {
    AnimatedContent(
        targetState = isLinked,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = ""
    ) { linked ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (linked) {
                CalibrationItem("同步调节", tl, defaultVal, valueRange) { t, imm ->
                    scope.launch {
                        if (imm) { tl.snapTo(t); tr.snapTo(t); bl.snapTo(t); br.snapTo(t) }
                        else { launch { tl.animateTo(t, sliderSpec) }; launch { tr.animateTo(t, sliderSpec) }; launch { bl.animateTo(t, sliderSpec) }; launch { br.animateTo(t, sliderSpec) } }
                    }
                }
            } else {
                CalibrationItem("左上角", tl, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) tl.snapTo(t) else tl.animateTo(t, sliderSpec) } }
                CalibrationItem("右上角", tr, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) tr.snapTo(t) else tr.animateTo(t, sliderSpec) } }
                CalibrationItem("左下角", bl, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) bl.snapTo(t) else bl.animateTo(t, sliderSpec) } }
                CalibrationItem("右下角", br, defaultVal, valueRange) { t, imm -> scope.launch { if(imm) br.snapTo(t) else br.animateTo(t, sliderSpec) } }
            }
        }
    }
}

@Composable
fun CalibrationItem(
    label: String,
    animatable: Animatable<Float, *>,
    systemDefault: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..300f,
    onAnimate: (Float, Boolean) -> Unit
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }

    val displayValue = if (isFocused) typedText else String.format(java.util.Locale.US, "%.1f", animatable.value)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                IconButton(
                    onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); focusManager.clearFocus(); onAnimate(systemDefault, false) },
                    modifier = Modifier.size(30.dp)
                ) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // - 按钮
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(valueRange) {
                            coroutineScope {
                                var pressed = false
                                launch {
                                    detectTapGestures(
                                        onPress = {
                                            pressed = true
                                            focusManager.clearFocus()
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onAnimate((animatable.value - 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                            tryAwaitRelease()
                                            pressed = false
                                        }
                                    )
                                }
                                launch {
                                    while (true) {
                                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                                        kotlinx.coroutines.delay(500.milliseconds)
                                        if (pressed) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            while (pressed) {
                                                onAnimate((animatable.value - 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                kotlinx.coroutines.delay(80.milliseconds)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = displayValue,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal == "-" || newVal.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                                if (newVal.length <= 6) {
                                    typedText = newVal
                                    val num = newVal.toFloatOrNull() ?: 0f
                                    onAnimate(num.coerceIn(valueRange.start, valueRange.endInclusive), false)
                                }
                            }
                        },
                        modifier = Modifier
                            .width(64.dp)
                            .onFocusChanged {
                                isFocused = it.isFocused
                                if (it.isFocused) typedText = String.format(java.util.Locale.US, "%.1f", animatable.value)
                            },
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                    Text("px", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                }
                // + 按钮
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(valueRange) {
                            coroutineScope {
                                var pressed = false
                                launch {
                                    detectTapGestures(
                                        onPress = {
                                            pressed = true
                                            focusManager.clearFocus()
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onAnimate((animatable.value + 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                            tryAwaitRelease()
                                            pressed = false
                                        }
                                    )
                                }
                                launch {
                                    while (true) {
                                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                                        kotlinx.coroutines.delay(500.milliseconds)
                                        if (pressed) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            while (pressed) {
                                                onAnimate((animatable.value + 0.1f).coerceIn(valueRange.start, valueRange.endInclusive), true)
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                kotlinx.coroutines.delay(80.milliseconds)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Slider(
            value = animatable.value,
            valueRange = valueRange,
            onValueChange = { newValue ->
                focusManager.clearFocus()
                onAnimate(newValue, true)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}