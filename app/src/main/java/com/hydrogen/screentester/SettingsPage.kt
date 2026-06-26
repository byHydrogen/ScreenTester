package com.hydrogen.screentester

import android.content.Intent
import android.view.HapticFeedbackConstants
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ColorPickerSection(
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

    // 线条粗细的状态管理
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

    LaunchedEffect(redAnim.value, greenAnim.value, blueAnim.value) {
        if (!isHexFocused) hexText = hexS
        ThemeSettings.saveLineColor(context, currentColorArgb)
    }

    // 合并并去重预设颜色
    val defaultPresets = listOf(Color.White, Color(0xFF72A7FF), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error)
    val allPresets = ThemeSettings.userPresets.map { Color(it) }

    val isCurrentInPresets = allPresets.any { it.toArgb() == effectiveArgb }
    var isPresetsExpanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (showTopDivider) {
            HorizontalDivider(Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }
        // 粗细调节区
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("线条粗细", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            // 重置按钮
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

            // 数值编辑框
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                BasicTextField(
                    value = thicknessInput,
                    onValueChange = { newVal ->
                        // 限制长度，防止输入过长
                        if (newVal.length <= 4) {
                            // 只有当输入为空，或者是合法数字时才处理
                            val num = newVal.toFloatOrNull()
                            if (num != null) {
                                if (num > 15f) {
                                    // 如果输入的数大于 15
                                    thicknessInput = "15"
                                    scope.launch { thicknessAnim.animateTo(15f, tween(400)) } // 滑块滑到 15
                                } else {
                                    // 正常范围：1-15 之间
                                    thicknessInput = newVal
                                    scope.launch { thicknessAnim.animateTo(num, tween(400)) }
                                }
                            } else if (newVal.isEmpty()) {
                                // 允许删空
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

        // 渐变色条开关
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

        // 单色模式面板
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
                onPresetsExpandedChange = { isPresetsExpanded = it }
            )
        }

        // 渐变色条面板
        AnimatedVisibility(visible = ThemeSettings.isMultiColorMode) {
            MultiColorModePanel(onSegmentLengthChange = onSegmentLengthChange, onDraggingChange = onDraggingChange)
        }
    }
}

// 单色模式面板
@Composable
fun SingleColorModePanel(
    redAnim: Animatable<Float, *>,
    greenAnim: Animatable<Float, *>,
    blueAnim: Animatable<Float, *>,
    currentColorArgb: Int,
    instantTargetColor: Int?,
    effectiveArgb: Int,
    hexText: String,
    isHexFocused: Boolean,
    onHexChange: (String) -> Unit,
    onHexFocusChange: (Boolean) -> Unit,
    onInstantColorChange: (Int?) -> Unit,
    allPresets: List<Color>,
    defaultPresets: List<Color>,
    isCurrentInPresets: Boolean,
    isPresetsExpanded: Boolean,
    onPresetsExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val colorPreviewG2Shape = G2Shapes.icon

    Column {
        Box(
            Modifier
                .fillMaxWidth()
            .height(54.dp)
            .background(Color(redAnim.value, greenAnim.value, blueAnim.value), colorPreviewG2Shape)
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = hexText,
        onValueChange = { newValue ->
            onHexChange(newValue)
            try {
                val parsed = android.graphics.Color.parseColor(newValue)
                onInstantColorChange(parsed)
                scope.launch { redAnim.animateTo(android.graphics.Color.red(parsed)/255f, tween(400)) }
                scope.launch { greenAnim.animateTo(android.graphics.Color.green(parsed)/255f, tween(400)) }
                scope.launch { blueAnim.animateTo(android.graphics.Color.blue(parsed)/255f, tween(400)) }
            } catch (_: Exception) {}
        },
        label = { Text("颜色代码 (HEX)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { onHexFocusChange(it.isFocused) },
        shape = RoundedCornerShape(14.dp)
    )

    Spacer(Modifier.height(20.dp)); Text("RGB 自定义调色", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    HapticSlider("R", Color.Red, redAnim.value) { focusManager.clearFocus(); onInstantColorChange(null); scope.launch { redAnim.snapTo(it) } }
    Spacer(Modifier.height(10.dp))
    HapticSlider("G", Color(0xFF00AA00), greenAnim.value) { focusManager.clearFocus(); onInstantColorChange(null); scope.launch { greenAnim.snapTo(it) } }
    Spacer(Modifier.height(10.dp))
    HapticSlider("B", Color.Blue, blueAnim.value) { focusManager.clearFocus(); onInstantColorChange(null); scope.launch { blueAnim.snapTo(it) } }

    Spacer(Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("色彩预设", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        AnimatedVisibility(visible = !isCurrentInPresets && effectiveArgb != 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    ThemeSettings.addUserPreset(context, currentColorArgb)
                    onInstantColorChange(currentColorArgb)
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("保存为预设", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    val maxCollapsedCount = 12
    val visiblePresets = if (isPresetsExpanded) allPresets else allPresets.take(maxCollapsedCount)
    val chunkedRows = visiblePresets.chunked(6)

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        for (rowIndex in chunkedRows.indices) {
            val rowColors = chunkedRows[rowIndex]
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Start) {
                for (colIndex in rowColors.indices) {
                    val preset = rowColors[colIndex]
                    val isSelected = effectiveArgb == preset.toArgb()
                    val isDefault = defaultPresets.contains(preset)

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PresetColorCircle(
                            preset = preset,
                            isSelected = isSelected,
                            isDefault = isDefault,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                focusManager.clearFocus()
                                onInstantColorChange(preset.toArgb())

                                val spec = tween<Float>(800, easing = FastOutSlowInEasing)
                                scope.launch { redAnim.animateTo(preset.red, spec) }
                                scope.launch { greenAnim.animateTo(preset.green, spec) }
                                scope.launch { blueAnim.animateTo(preset.blue, spec) }
                            },
                            onLongClick = {
                                if (!isDefault) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    ThemeSettings.removeUserPreset(context, preset.toArgb())
                                }
                            }
                        )
                    }
                }
                for (i in 0 until (6 - rowColors.size)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (allPresets.size > maxCollapsedCount) {
        Text(
            text = if (isPresetsExpanded) "收起预设" else "展开全部 (${allPresets.size})",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onPresetsExpandedChange(!isPresetsExpanded)
            }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
    }
}

// 渐变色条面板
@Composable
fun MultiColorModePanel(
    onSegmentLengthChange: ((Float) -> Unit)? = null,
    onDraggingChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val cardG2Shape = G2Shapes.card
    val gradientShape = G2Shapes.icon

    Column {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val totalLengthPx = with(density) { configuration.screenWidthDp.dp.toPx().coerceAtLeast(configuration.screenHeightDp.dp.toPx()) }
        val sliderMax = totalLengthPx / 200f  // repeatCount=1 的临界值

        // 50 档非线性滑块：右边（1圈附近）档位更密集，左边（紧密）档位更稀疏
        val TOTAL_STEPS = 50
        val MAX_RC = 20  // 最大重复圈数
        // 非线性映射：step → repeatCount（用二次曲线，右边更密集）
        fun stepToRepeatCount(step: Int): Int {
            val pos = step.toFloat() / (TOTAL_STEPS - 1).coerceAtLeast(1)
            return (MAX_RC - (MAX_RC - 1) * pos * pos).roundToInt().coerceIn(1, MAX_RC)
        }
        fun repeatCountToStep(rc: Int): Int {
            val clampedRc = rc.coerceIn(1, MAX_RC)
            val pos = kotlin.math.sqrt((MAX_RC - clampedRc).toFloat() / (MAX_RC - 1).coerceAtLeast(1))
            return (pos * (TOTAL_STEPS - 1)).roundToInt().coerceIn(0, TOTAL_STEPS - 1)
        }
        fun repeatCountToSegmentLength(rc: Int) = sliderMax / rc.coerceAtLeast(1)
        fun segmentLengthToRepeatCount(sl: Float) = (sliderMax / sl.coerceAtLeast(0.01f)).roundToInt().coerceIn(1, MAX_RC)

        val defaultLength = sliderMax  // 默认 = 1圈（最右边）
        val initialValue = if (ThemeSettings.multiColorSegmentLength == 0f) defaultLength else ThemeSettings.multiColorSegmentLength
        val segmentLengthAnim = remember { Animatable(initialValue) }

        // 当前档位
        val currentRepeatCount = segmentLengthToRepeatCount(segmentLengthAnim.value)
        val currentStep = repeatCountToStep(currentRepeatCount)
        val sliderValue = currentStep.toFloat() / (TOTAL_STEPS - 1).coerceAtLeast(1)

        LaunchedEffect(segmentLengthAnim.value) {
            ThemeSettings.saveMultiColorSegmentLength(context, segmentLengthAnim.value)
            onSegmentLengthChange?.invoke(segmentLengthAnim.value)
        }

        LaunchedEffect(ThemeSettings.multiColorSegmentLength) {
            if (segmentLengthAnim.value != ThemeSettings.multiColorSegmentLength && ThemeSettings.multiColorSegmentLength != 0f) {
                segmentLengthAnim.animateTo(
                    targetValue = ThemeSettings.multiColorSegmentLength,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                )
            }
        }

        // 渐变颜色长度滑块
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("渐变颜色长度", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            // 重置按钮
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scope.launch {
                        segmentLengthAnim.animateTo(
                            targetValue = defaultLength,
                            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                        )
                    }
                    focusManager.clearFocus()
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
        }
        Spacer(Modifier.height(8.dp))

        HapticSlider("",
            MaterialTheme.colorScheme.primary,
            sliderValue,
            onDragStart = { onDraggingChange?.invoke(true) },
            onDragEnd = { onDraggingChange?.invoke(false) }
        ) { rawValue ->
            focusManager.clearFocus()
            val step = (rawValue * (TOTAL_STEPS - 1)).roundToInt().coerceIn(0, TOTAL_STEPS - 1)
            val rc = stepToRepeatCount(step)
            val newSegmentLength = repeatCountToSegmentLength(rc)
            scope.launch { segmentLengthAnim.snapTo(newSegmentLength) }
        }

        Spacer(Modifier.height(24.dp))

        // 预设方案
        Text("预设方案", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

    val presetSchemes = listOf(
        Triple("彩虹色", PresetScheme.RAINBOW, listOf(Color.Red, Color(0xFFFF6600), Color.Yellow, Color.Green, Color.Blue, Color(0xFF4B0082), Color(0xFF8B00FF))),
        Triple("暖色", PresetScheme.WARM, listOf(Color.Red, Color(0xFFFF6600), Color.Yellow)),
        Triple("冷色", PresetScheme.COOL, listOf(Color.Blue, Color.Green, Color(0xFF8B00FF))),
        Triple("高对比", PresetScheme.HIGH_CONTRAST, listOf(Color.Red, Color.Green, Color.Blue)),
        Triple("莫奈", PresetScheme.MONET, listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.error))
    )

    // 当前选中的预设方案（根据已保存的颜色自动检测）
    var selectedPresetScheme by remember {
        val current = ThemeSettings.multiColorSelectedColors
        mutableStateOf(
            when {
                current.size == 7 && current == listOf(-65536, -23296, -256, -16711936, -16776961, -11842750, -7536641) -> PresetScheme.RAINBOW
                current.size == 3 && current == listOf(-65536, -23296, -256) -> PresetScheme.WARM
                current.size == 3 && current == listOf(-16776961, -16711936, -7536641) -> PresetScheme.COOL
                current.size == 3 && current == listOf(-65536, -16711936, -16776961) -> PresetScheme.HIGH_CONTRAST
                else -> null
            }
        )
    }

    // 使用 AnimatedVisibility 实现动画
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 内置预设方案
            for ((name, scheme, colors) in presetSchemes) {
                val isSelected = selectedPresetScheme == scheme
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "presetSchemeBg"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardG2Shape)
                        .combinedClickable(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedPresetScheme = scheme
                                ThemeSettings.applyPresetScheme(context, scheme)
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        ),
                    shape = cardG2Shape,
                    color = backgroundColor
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(60.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(ThemeSettings.testLineThickness.dp + 4.dp)
                                .clip(gradientShape)
                                .background(Brush.horizontalGradient(colors))
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PresetColorCircle(preset: Color, isSelected: Boolean, isDefault: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ckScale by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.6f), label = "")
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(preset)
            .border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(0.3f), shape = CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check, null,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = ckScale; scaleY = ckScale; alpha = ckScale },
            tint = if ((preset.red*0.299 + preset.green*0.587 + preset.blue*0.114) > 0.5) Color.Black else Color.White
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    var isAppExp by remember { mutableStateOf(false) }
    var isBrightExp by remember { mutableStateOf(false) }
    var isBlackBorderTestExp by remember { mutableStateOf(false) }
    var showPureModeHelp by remember { mutableStateOf(false) }
    var isColorExp by remember { mutableStateOf(false) }
    var isAnimExp by remember { mutableStateOf(false) }

    // 动画触发器
    var animationTrigger by remember { mutableIntStateOf(0) }

    // 屏幕宽度检测
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    var wasVisible by remember { mutableStateOf(false) }
    var lastX by remember { mutableFloatStateOf(Float.NaN) }

    // 顶级动画状态管理
    val cardAlphas = remember { List(5) { Animatable(0f) } }
    val cardOffsetsY = remember { List(5) { Animatable(30f) } }

    // 统一瀑布流入场动画（仅在进入页面/切回时触发）
    LaunchedEffect(animationTrigger) {
        if (!ThemeSettings.isAnimationEnabled) {
            for (i in 0 until 5) {
                cardAlphas[i].snapTo(1f)
                cardOffsetsY[i].snapTo(0f)
            }
            return@LaunchedEffect
        }

        for (i in 0 until 5) {
            launch {
                val alpha = cardAlphas[i]
                val offsetY = cardOffsetsY[i]
                val index = i + 1

                if (alpha.value > 0.1f) {
                    launch { alpha.animateTo(0f, tween(150)) }
                    offsetY.animateTo(30f,
                        tween(150, easing = FastOutSlowInEasing)
                    )
                } else {
                    offsetY.snapTo(30f)
                }
                delay(index * 40L)
                launch { alpha.animateTo(1f, tween(300)) }
                offsetY.animateTo(0f,
                    tween(300, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // 处理后台回到前台
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                animationTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 大卡片圆角
    val g2CardShape = G2Shapes.card

    // 外观模式小卡片圆角
    val g2LargeShape = G2Shapes.largeCard

    // 校准车间按钮圆角
    val buttonG2Shape = G2Shapes.button

    val appArrowRotation by animateFloatAsState(targetValue = if (isAppExp) 180f else 0f, label = "appArrow")
    val brightArrowRotation by animateFloatAsState(targetValue = if (isBrightExp) 180f else 0f, label = "brightArrow")
    val blackBorderArrowRotation by animateFloatAsState(targetValue = if (isBlackBorderTestExp) 180f else 0f, label = "compactArrow")
    val colorArrowRotation by animateFloatAsState(targetValue = if (isColorExp) 180f else 0f, label = "colorArrow")
    val animArrowRotation by animateFloatAsState(targetValue = if (isAnimExp) 180f else 0f, label = "animArrow")

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .onGloballyPositioned { coords ->
                    val currentX = coords.positionInWindow().x
                    if (currentX != lastX) {
                        val isVisibleNow = currentX > -screenWidthPx / 2 && currentX < screenWidthPx / 2
                        if (isVisibleNow && !wasVisible) {
                            animationTrigger++
                        }
                        wasVisible = isVisibleNow
                        lastX = currentX
                    }
                }
        ) {
            item { Spacer(Modifier.statusBarsPadding()); Spacer(modifier = Modifier.height(80.dp)) }
            item { Text(text = "设置", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Spacer(modifier = Modifier.height(24.dp)) }

            // 1. 外观模式
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer { this.alpha = cardAlphas[0].value; this.translationY = cardOffsetsY[0].value },
                    shape = g2CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isAppExp = !isAppExp
                            }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BrightnessMedium, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("外观模式", Modifier.weight(1f), fontWeight = FontWeight.Bold)

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = appArrowRotation }
                            )
                        }

                        AnimatedVisibility(visible = isAppExp) {
                            Column(Modifier.padding(bottom = 16.dp)) {
                                HorizontalDivider(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.1f))

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val modes = listOf(
                                        Triple("跟随系统", Icons.Default.BrightnessAuto, DarkModeConfig.FOLLOW_SYSTEM),
                                        Triple("浅色模式", Icons.Default.LightMode, DarkModeConfig.LIGHT),
                                        Triple("深色模式", Icons.Default.DarkMode, DarkModeConfig.DARK)
                                    )

                                    modes.forEach { (label, icon, config) ->
                                        val isSelected = ThemeSettings.darkModeState == config
                                        val animatedContainerColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            animationSpec = tween(durationMillis = 250),
                                            label = "containerColorAnim"
                                        )

                                        val animatedContentColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            animationSpec = tween(durationMillis = 250),
                                            label = "contentColorAnim"
                                        )

                                        val animatedTextColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            animationSpec = tween(durationMillis = 250),
                                            label = "textColorAnim"
                                        )

                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(g2LargeShape)
                                                .clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    ThemeSettings.saveConfig(context, config)
                                                },
                                            shape = g2LargeShape,
                                            colors = CardDefaults.cardColors(containerColor = animatedContainerColor)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(imageVector = icon, contentDescription = null, tint = animatedContentColor)
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = animatedTextColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. 测试亮度设置
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer { this.alpha = cardAlphas[1].value; this.translationY = cardOffsetsY[1].value },
                    shape = g2CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isBrightExp = !isBrightExp
                            }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FlashOn, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("测试亮度设置", Modifier.weight(1f), fontWeight = FontWeight.Bold)

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = brightArrowRotation }
                            )
                        }
                        AnimatedVisibility(visible = isBrightExp) { BrightnessSettingsContent() }
                    }
                }
            }

            // 3. 自定义黑边遮挡测试
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer { this.alpha = cardAlphas[2].value; this.translationY = cardOffsetsY[2].value },
                    shape = g2CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    isBlackBorderTestExp = !isBlackBorderTestExp
                                }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CropFree, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("自定义黑边遮挡测试", Modifier.weight(1f), fontWeight = FontWeight.Bold)

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = blackBorderArrowRotation }
                            )
                        }

                        AnimatedVisibility(visible = isBlackBorderTestExp) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(bottom = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )

                                // 精准圆角校准
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("自定义圆角半径", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("屏蔽系统错误数据，手动打磨完美弧度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.useCustomRadius,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveCustomRadius(context, it, ThemeSettings.radiusTL, ThemeSettings.radiusTR, ThemeSettings.radiusBL, ThemeSettings.radiusBR)
                                        }
                                    )
                                }

                                AnimatedVisibility(visible = ThemeSettings.useCustomRadius) {
                                    Column {
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                context.startActivity(Intent(context, CornerCalibrationActivity::class.java))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = buttonG2Shape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("进入校准车间", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                // 纯净模式
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("纯净模式", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                contentDescription = "帮助",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        showPureModeHelp = !showPureModeHelp
                                                    }
                                                    .padding(4.dp)
                                            )
                                        }

                                        // 默认隐藏，点击图标后显示
                                        AnimatedVisibility(visible = showPureModeHelp) {
                                            Text(
                                                "开启后将隐藏测试页圆角模式中部部分文案和底部参数\n隐藏右上角切换按钮，改为双击屏幕任意位置切换模式",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = ThemeSettings.isCompactModeEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveCompactModeConfig(context, it)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 自定义测试线条粗细和颜色
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer { this.alpha = cardAlphas[3].value; this.translationY = cardOffsetsY[3].value },
                    shape = g2CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isColorExp = !isColorExp
                            }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("自定义测试线条粗细和颜色", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = colorArrowRotation }
                            )
                        }
                        AnimatedVisibility(visible = isColorExp) { ColorPickerSection() }
                    }
                }
            }

            // 5. 界面高级动效
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer { this.alpha = cardAlphas[4].value; this.translationY = cardOffsetsY[4].value },
                    shape = g2CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isAnimExp = !isAnimExp
                            }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("界面高级动效", Modifier.weight(1f), fontWeight = FontWeight.Bold)

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = animArrowRotation }
                            )
                        }

                        AnimatedVisibility(visible = isAnimExp) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                HorizontalDivider(Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("卡片高级动效", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(text = if (ThemeSettings.isAnimationEnabled) "关闭后将禁用卡片淡入效果" else "开启后将拥有卡片淡入效果", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.isAnimationEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveAnimationConfig(context, it)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(200.dp)) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = 60.dp)))
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.9f),
                            backgroundColor.copy(alpha = 0.8f),
                            backgroundColor.copy(alpha = 0.6f),
                            backgroundColor.copy(alpha = 0.4f),
                            backgroundColor.copy(alpha = 0.2f),
                            backgroundColor.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun BrightnessSettingsContent() {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val brightnessAnim = remember { Animatable(ThemeSettings.testBrightnessValue) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var inputString by remember { mutableStateOf((brightnessAnim.value * 100).toInt().toString()) }
    LaunchedEffect(brightnessAnim.value) {
        if (!isTextFieldFocused) { inputString = (brightnessAnim.value * 100).toInt().toString() }
        ThemeSettings.saveTestBrightnessValue(context, brightnessAnim.value)
    }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Row(verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("自定义测试亮度", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("开启后进入测试页将自动应用设定亮度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = ThemeSettings.isMaxBrightnessEnabled, onCheckedChange = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); ThemeSettings.saveMaxBrightness(context, it) }) }
        AnimatedVisibility(visible = ThemeSettings.isMaxBrightnessEnabled) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("当前亮度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        BasicTextField(
                            value = inputString,
                            onValueChange = { newVal ->
                                if (newVal.all { it.isDigit() } && newVal.length <= 3) {
                                    val num = newVal.toIntOrNull() ?: 0
                                    if (num <= 100) { inputString = newVal; scope.launch { brightnessAnim.animateTo(num / 100f, tween(700)) } }
                                }
                            },
                            modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 28.dp).onFocusChanged { isTextFieldFocused = it.isFocused },
                            textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                        Text("%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                val interactionSource = remember { MutableInteractionSource() }
                val isDragged by interactionSource.collectIsDraggedAsState()
                if (isDragged) { SideEffect { focusManager.clearFocus() } }
                HapticSlider("", MaterialTheme.colorScheme.primary, brightnessAnim.value, interactionSource = interactionSource) { newVal -> scope.launch { brightnessAnim.snapTo(newVal) } }
            }
        }
    }
}
