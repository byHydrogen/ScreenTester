package com.hydrogen.screentester

import android.view.HapticFeedbackConstants
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.launch

// 线条设置面板：粗细 + 渐变色条开关 + 单色/渐变调色盘（自带状态，写入 ThemeSettings）
@Composable
internal fun TestLineSettingsPanel(
    showTopDivider: Boolean = true,
    onThicknessChange: ((Float) -> Unit)? = null,
    onSegmentLengthChange: ((Float) -> Unit)? = null,
    onDraggingChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val thicknessAnim = remember { Animatable(ThemeSettings.testLineThickness) }

    var thicknessInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", ThemeSettings.testLineThickness)) }
    var isThicknessFocused by remember { mutableStateOf(false) }
    LaunchedEffect(thicknessAnim.value) {
        if (!isThicknessFocused) {
            thicknessInput = String.format(Locale.US, "%.1f", thicknessAnim.value)
        }
        ThemeSettings.saveLineThickness(context, thicknessAnim.value)
        onThicknessChange?.invoke(thicknessAnim.value)
    }

    val initialColor = Color(ThemeSettings.testLineColor)
    val redAnim = remember { Animatable(initialColor.red) }
    val greenAnim = remember { Animatable(initialColor.green) }
    val blueAnim = remember { Animatable(initialColor.blue) }

    val currentColorArgb = android.graphics.Color.rgb(redAnim.value, greenAnim.value, blueAnim.value)
    var instantTargetColor by remember { mutableStateOf<Int?>(null) }
    val effectiveArgb = instantTargetColor ?: currentColorArgb

    val hexS = String.format(Locale.US, "#%02X%02X%02X", (redAnim.value * 255).toInt(), (greenAnim.value * 255).toInt(), (blueAnim.value * 255).toInt())
    var hexText by remember { mutableStateOf(hexS) }
    var isHexFocused by remember { mutableStateOf(false) }
    var warnedSameAsBg by remember { mutableStateOf(false) }

    LaunchedEffect(redAnim.value, greenAnim.value, blueAnim.value) {
        if (!isHexFocused) hexText = hexS
        ThemeSettings.saveLineColor(context, currentColorArgb)
        // 滑块/hex 仍可把线条调成背景色（不硬拦，免得拖动卡手）：只在刚变成同色时提示一次
        val sameAsBg = ThemeSettings.testLineBgColor == currentColorArgb
        if (sameAsBg && !warnedSameAsBg) {
            android.widget.Toast.makeText(context, "线条与背景颜色相同，建议调整颜色或更换背景", android.widget.Toast.LENGTH_SHORT).show()
            warnedSameAsBg = true
        } else if (!sameAsBg) {
            warnedSameAsBg = false
        }
    }

    // 外部入口改了存储值后同步滑块：Animatable 只在首次组合读值，不补这段会显示旧值
    LaunchedEffect(ThemeSettings.testLineThickness) {
        if (ThemeSettings.testLineThickness != thicknessAnim.value) {
            thicknessAnim.snapTo(ThemeSettings.testLineThickness)
        }
    }
    LaunchedEffect(ThemeSettings.testLineColor) {
        val target = Color(ThemeSettings.testLineColor)
        if (target.toArgb() != currentColorArgb) {
            instantTargetColor = null
            redAnim.snapTo(target.red)
            greenAnim.snapTo(target.green)
            blueAnim.snapTo(target.blue)
        }
    }

    val defaultPresets = listOf(Color.White, Color(0xFF72A7FF), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error)
    val allPresets = ThemeSettings.userPresets.map { Color(it) }

    val isCurrentInPresets = allPresets.any { it.toArgb() == effectiveArgb }
    var isPresetsExpanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (showTopDivider) {
            HorizontalDivider(Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("线条粗细", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    thicknessInput = "5.0"
                    scope.launch {
                        thicknessAnim.animateTo(
                            targetValue = 5f,
                            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                        )
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重置",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                BasicTextField(
                    value = thicknessInput,
                    onValueChange = { newVal ->
                        if (newVal.length <= 4) {
                            val num = newVal.toFloatOrNull()
                            if (num != null) {
                                when {
                                    num > 15f -> {
                                        thicknessInput = "15"
                                        scope.launch { thicknessAnim.animateTo(15f, tween(400, easing = FastOutSlowInEasing)) }
                                    }
                                    num < 1f -> {
                                        thicknessInput = "1"
                                        scope.launch { thicknessAnim.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
                                    }
                                    else -> {
                                        thicknessInput = newVal
                                        scope.launch { thicknessAnim.animateTo(num, tween(400, easing = FastOutSlowInEasing)) }
                                    }
                                }
                            } else if (newVal.isEmpty()) {
                                thicknessInput = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 35.dp)
                        .onFocusChanged { isThicknessFocused = it.isFocused },
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
                Text("px", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        HapticSlider(
            l = "",
            c = MaterialTheme.colorScheme.primary,
            v = (thicknessAnim.value - 1f) / 14f,
            onDragStart = { onDraggingChange?.invoke(true) },
            onDragEnd = { onDraggingChange?.invoke(false) }
        ) {
            focusManager.clearFocus()
            val newValue = it * 14f + 1f
            scope.launch { thicknessAnim.snapTo(newValue) }
            onThicknessChange?.invoke(newValue)
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("渐变色条", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("开启后可选择多种颜色渐变线条", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = ThemeSettings.isMultiColorMode,
                onCheckedChange = { enabled ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    ThemeSettings.saveMultiColorMode(context, enabled)
                    // 开启时如果没有选中颜色，默认选中彩虹色预设
                    if (enabled && ThemeSettings.multiColorSelectedColors.isEmpty()) {
                        ThemeSettings.applyPresetScheme(context, PresetScheme.RAINBOW)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = !ThemeSettings.isMultiColorMode) {
            SingleColorModePanel(
                redAnim = redAnim,
                greenAnim = greenAnim,
                blueAnim = blueAnim,
                currentColorArgb = currentColorArgb,
                instantTargetColor = instantTargetColor,
                effectiveArgb = effectiveArgb,
                hexText = hexText,
                isHexFocused = isHexFocused,
                onHexChange = { hexText = it },
                onHexFocusChange = { isHexFocused = it },
                onInstantColorChange = { instantTargetColor = it },
                allPresets = allPresets,
                defaultPresets = defaultPresets,
                isCurrentInPresets = isCurrentInPresets,
                isPresetsExpanded = isPresetsExpanded,
                onPresetsExpandedChange = { isPresetsExpanded = it },
                // 线条色不能等于背景色（会看不见）：预设里对应的圆点变灰、点击给提示
                blockedColor = ThemeSettings.testLineBgColor
            )
        }

        AnimatedVisibility(visible = ThemeSettings.isMultiColorMode) {
            MultiColorModePanel(onSegmentLengthChange = onSegmentLengthChange, onDraggingChange = onDraggingChange)
        }
    }
}
