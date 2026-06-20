package com.hydrogen.screentester

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch

class CornerCalibrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val g2TopButtonShape = remember {
                    object : androidx.compose.ui.graphics.Shape {
                        override fun createOutline(
                            size: androidx.compose.ui.geometry.Size,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                            density: androidx.compose.ui.unit.Density
                        ): androidx.compose.ui.graphics.Outline {
                            val path = Path()
                            val w = size.width
                            val h = size.height
                            val targetRadius = with(density) { 16.dp.toPx() }
                            val p = (1.4f * targetRadius).coerceAtMost(h / 2f)
                            val safeRadius = p / 1.4f
                            val c = 0.45f * safeRadius

                            path.moveTo(p, 0f)
                            path.lineTo(w - p, 0f)
                            path.cubicTo(w - c, 0f, w, c, w, p)
                            path.lineTo(w, h - p)
                            path.cubicTo(w, h - c, w - c, h, w - p, h)
                            path.lineTo(p, h)
                            path.cubicTo(c, h, 0f, h - c, 0f, h - p)
                            path.lineTo(0f, p)
                            path.cubicTo(0f, c, c, 0f, p, 0f)
                            path.close()
                            return androidx.compose.ui.graphics.Outline.Generic(path)
                        }
                    }
                }

                FilledTonalButton(
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
                    shape = g2TopButtonShape,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp)
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
                    onClick = {
                        isG2Enabled = !isG2Enabled
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    shape = g2TopButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = g2ButtonContainerColor,
                        contentColor = g2ButtonContentColor
                    ),
                    modifier = Modifier
                        .weight(0.8f)
                        .height(44.dp),
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
                SectionCard("基础圆角半径", activeSection == 0, {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); activeSection = if (activeSection == 0) null else 0
                }) {
                    CalibrationSliderGroup(isLinked, tlAnim, trAnim, blAnim, brAnim, systemRadius, sliderSpec, 0f..300f, scope)
                }

                SectionCard("横向 (X轴) 曲率修正", activeSection == 1, {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); activeSection = if (activeSection == 1) null else 1
                }) {
                    CalibrationSliderGroup(isLinked, tlXAnim, trXAnim, blXAnim, brXAnim, 0f, sliderSpec, -150f..150f, scope)
                }

                SectionCard("纵向 (Y轴) 曲率修正", activeSection == 2, {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); activeSection = if (activeSection == 2) null else 2
                }) {
                    CalibrationSliderGroup(isLinked, tlYAnim, trYAnim, blYAnim, brYAnim, 0f, sliderSpec, -150f..150f, scope)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val bottomButtonG2Shape = remember {
                    object : androidx.compose.ui.graphics.Shape {
                        override fun createOutline(
                            size: androidx.compose.ui.geometry.Size,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                            density: androidx.compose.ui.unit.Density
                        ): androidx.compose.ui.graphics.Outline {
                            val path = Path()
                            val w = size.width
                            val h = size.height
                            val radius = with(density) { 16.dp.toPx() }
                            val p = 1.4f * radius
                            val c = 0.45f * radius

                            path.moveTo(p, 0f)
                            path.lineTo(w - p, 0f)
                            path.cubicTo(w - c, 0f, w, c, w, p)
                            path.lineTo(w, h - p)
                            path.cubicTo(w, h - c, w - c, h, w - p, h)
                            path.lineTo(p, h)
                            path.cubicTo(c, h, 0f, h - c, 0f, h - p)
                            path.lineTo(0f, p)
                            path.cubicTo(0f, c, c, 0f, p, 0f)
                            path.close()
                            return androidx.compose.ui.graphics.Outline.Generic(path)
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onExit()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = bottomButtonG2Shape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("取消", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        ThemeSettings.saveCustomRadius(context, true, tlAnim.value, trAnim.value, blAnim.value, brAnim.value)

                        prefs.edit().apply {
                            putFloat("r_tl_x", tlXAnim.value)
                            putFloat("r_tr_x", trXAnim.value)
                            putFloat("r_bl_x", blXAnim.value)
                            putFloat("r_br_x", brXAnim.value)
                            putFloat("r_tl_y", tlYAnim.value)
                            putFloat("r_tr_y", trYAnim.value)
                            putFloat("r_bl_y", blYAnim.value)
                            putFloat("r_br_y", brYAnim.value)
                            putBoolean("is_g2_enabled", isG2Enabled)
                            apply()
                        }
                        onExit()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = bottomButtonG2Shape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) { Text("保存并应用", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
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
                CalibrationItem("同步调节", tl, defaultVal, sliderSpec, valueRange) { t, imm ->
                    scope.launch {
                        if (imm) { tl.snapTo(t); tr.snapTo(t); bl.snapTo(t); br.snapTo(t) }
                        else { launch { tl.animateTo(t, sliderSpec) }; launch { tr.animateTo(t, sliderSpec) }; launch { bl.animateTo(t, sliderSpec) }; launch { br.animateTo(t, sliderSpec) } }
                    }
                }
            } else {
                CalibrationItem("左上角", tl, defaultVal, sliderSpec, valueRange) { t, imm -> scope.launch { if(imm) tl.snapTo(t) else tl.animateTo(t, sliderSpec) } }
                CalibrationItem("右上角", tr, defaultVal, sliderSpec, valueRange) { t, imm -> scope.launch { if(imm) tr.snapTo(t) else tr.animateTo(t, sliderSpec) } }
                CalibrationItem("左下角", bl, defaultVal, sliderSpec, valueRange) { t, imm -> scope.launch { if(imm) bl.snapTo(t) else bl.animateTo(t, sliderSpec) } }
                CalibrationItem("右下角", br, defaultVal, sliderSpec, valueRange) { t, imm -> scope.launch { if(imm) br.snapTo(t) else br.animateTo(t, sliderSpec) } }
            }
        }
    }
}

@Composable
fun CalibrationItem(
    label: String,
    animatable: Animatable<Float, *>,
    systemDefault: Float,
    sliderSpec: AnimationSpec<Float>,
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