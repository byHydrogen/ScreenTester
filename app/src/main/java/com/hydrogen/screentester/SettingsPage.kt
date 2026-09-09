@file:OptIn(ExperimentalHazeMaterialsApi::class)

package com.hydrogen.screentester

import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// RGB 三色滑块
@Composable
private fun RgbSliders(
    r: Animatable<Float, *>,
    g: Animatable<Float, *>,
    b: Animatable<Float, *>,
    spacing: Int = 10,
    onEachChange: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    HapticSlider("R", Color.Red, r.value) { focusManager.clearFocus(); onEachChange?.invoke(); scope.launch { r.snapTo(it) } }
    Spacer(Modifier.height(spacing.dp))
    HapticSlider("G", Color(0xFF00AA00), g.value) { focusManager.clearFocus(); onEachChange?.invoke(); scope.launch { g.snapTo(it) } }
    Spacer(Modifier.height(spacing.dp))
    HapticSlider("B", Color.Blue, b.value) { focusManager.clearFocus(); onEachChange?.invoke(); scope.launch { b.snapTo(it) } }
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
    onPresetsExpandedChange: (Boolean) -> Unit,
    // 保存/删除预设的目标：不传则默认操作线条色的 userPresets
    onSavePreset: ((Int) -> Unit)? = null,
    onRemovePreset: ((Int) -> Unit)? = null,
    // 不允许选择的颜色（如线条面板要避开背景色）：该预设变灰、点击时提示，不会切换
    blockedColor: Int? = null
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
                val hexSpec = tween<Float>(800, easing = FastOutSlowInEasing)
                scope.launch { redAnim.animateTo(android.graphics.Color.red(parsed)/255f, hexSpec) }
                scope.launch { greenAnim.animateTo(android.graphics.Color.green(parsed)/255f, hexSpec) }
                scope.launch { blueAnim.animateTo(android.graphics.Color.blue(parsed)/255f, hexSpec) }
            } catch (_: Exception) {}
        },
        label = { Text("颜色代码 (HEX)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { onHexFocusChange(it.isFocused) },
        shape = RoundedCornerShape(14.dp)
    )

    Spacer(Modifier.height(20.dp)); Text("RGB 自定义调色", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    RgbSliders(redAnim, greenAnim, blueAnim, spacing = 10) { onInstantColorChange(null) }

    Spacer(Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("色彩预设", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        AnimatedVisibility(visible = !isCurrentInPresets && effectiveArgb != 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 保存目标可由调用方指定（默认：线条色的预设）
                    if (onSavePreset != null) onSavePreset(currentColorArgb)
                    else ThemeSettings.addUserPreset(context, currentColorArgb)
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
                    val presetArgb = preset.toArgb()
                    val isSelected = effectiveArgb == presetArgb
                    val isDefault = defaultPresets.contains(preset)
                    val isBlocked = blockedColor != null && presetArgb == blockedColor

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PresetColorCircle(
                            preset = preset,
                            isSelected = isSelected,
                            isDefault = isDefault,
                            isDisabled = isBlocked,
                            onClick = {
                                if (isBlocked) {
                                    Toast.makeText(context, "该颜色与背景相同，如需使用该线条颜色请先更换背景颜色", Toast.LENGTH_SHORT).show()
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    focusManager.clearFocus()
                                    onInstantColorChange(presetArgb)

                                    val spec = tween<Float>(800, easing = FastOutSlowInEasing)
                                    scope.launch { redAnim.animateTo(preset.red, spec) }
                                    scope.launch { greenAnim.animateTo(preset.green, spec) }
                                    scope.launch { blueAnim.animateTo(preset.blue, spec) }
                                }
                            },
                            onLongClick = {
                                if (!isDefault) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    // 删除目标可由调用方指定（默认：线条色的预设）
                                    if (onRemovePreset != null) onRemovePreset(preset.toArgb())
                                    else ThemeSettings.removeUserPreset(context, preset.toArgb())
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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

    var deletedPresets by remember {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val str = prefs.getString("deleted_preset_schemes", "") ?: ""
        mutableStateOf(
            if (str.isEmpty()) emptySet<PresetScheme>()
            else str.split(",").mapNotNull {
                try { PresetScheme.valueOf(it) } catch (e: Exception) { null }
            }.toSet()
        )
    }

    Column {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val totalLengthPx = with(density) { configuration.screenWidthDp.dp.toPx().coerceAtLeast(configuration.screenHeightDp.dp.toPx()) }
        val sliderMax = totalLengthPx / 200f

        val TOTAL_STEPS = 50
        val MAX_RC = 20
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

        val defaultLength = sliderMax
        val initialValue = if (ThemeSettings.multiColorSegmentLength == 0f) defaultLength else ThemeSettings.multiColorSegmentLength
        val segmentLengthAnim = remember { Animatable(initialValue) }

        val currentRepeatCount = segmentLengthToRepeatCount(segmentLengthAnim.value)
        val currentStep = repeatCountToStep(currentRepeatCount)
        val sliderValue = currentStep.toFloat() / (TOTAL_STEPS - 1).coerceAtLeast(1)

        LaunchedEffect(segmentLengthAnim.value) {
            ThemeSettings.saveMultiColorSegmentLength(context, segmentLengthAnim.value)
            onSegmentLengthChange?.invoke(segmentLengthAnim.value)
        }
        LaunchedEffect(ThemeSettings.multiColorSegmentLength) {
            if (ThemeSettings.multiColorSegmentLength != 0f && segmentLengthAnim.value != ThemeSettings.multiColorSegmentLength) {
                segmentLengthAnim.animateTo(ThemeSettings.multiColorSegmentLength, tween(800, easing = FastOutSlowInEasing))
            }
        }

        // 渐变颜色长度滑块
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("渐变颜色长度", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scope.launch { segmentLengthAnim.animateTo(defaultLength, tween(800, easing = FastOutSlowInEasing)) }
                    focusManager.clearFocus()
                },
                modifier = Modifier.size(30.dp)
            ) { Icon(Icons.Default.Refresh, "重置", Modifier.size(16.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) }
        }
        Spacer(Modifier.height(8.dp))
        HapticSlider("", MaterialTheme.colorScheme.primary, sliderValue, onDragStart = { onDraggingChange?.invoke(true) }, onDragEnd = { onDraggingChange?.invoke(false) }) { rawValue ->
            focusManager.clearFocus()
            val step = (rawValue * (TOTAL_STEPS - 1)).roundToInt().coerceIn(0, TOTAL_STEPS - 1)
            val rc = stepToRepeatCount(step)
            scope.launch { segmentLengthAnim.snapTo(repeatCountToSegmentLength(rc)) }
        }

        Spacer(Modifier.height(24.dp))

        // 预设方案列表
        Text("预设方案", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        // 莫奈及其他方案高精度色彩映射
        val presetSchemes = listOf(
            Triple("彩虹色", PresetScheme.RAINBOW, listOf(Color(android.graphics.Color.RED), Color(android.graphics.Color.rgb(255, 165, 0)), Color(android.graphics.Color.YELLOW), Color(0xFF00FF00), Color(0xFF0000FF), Color(android.graphics.Color.rgb(75, 0, 130)), Color(android.graphics.Color.rgb(139, 0, 255)))),
            Triple("暖色", PresetScheme.WARM, listOf(Color(android.graphics.Color.RED), Color(android.graphics.Color.rgb(255, 165, 0)), Color(android.graphics.Color.YELLOW))),
            Triple("冷色", PresetScheme.COOL, listOf(Color(0xFF0000FF), Color(0xFF00FF00), Color(android.graphics.Color.rgb(139, 0, 255)))),
            Triple("高对比", PresetScheme.HIGH_CONTRAST, listOf(Color(android.graphics.Color.RED), Color(0xFF00FF00), Color(0xFF0000FF))),
            Triple("蓝粉", PresetScheme.BLUE_PINK, listOf(Color(0xFF72A7FF), Color(0xFFFF83B6))),
            Triple("海洋", PresetScheme.OCEAN, listOf(Color(0xFF008BFF), Color(0xFF008B9E), Color(0xFF00779D)))
        )

        val currentColors = ThemeSettings.multiColorSelectedColors

        // 渲染列表
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.animateContentSize()) {
            // 内置方案（过滤掉已被本地删除的预设）
            val visiblePresetSchemes = presetSchemes.filter { it.second !in deletedPresets }

            for ((name, scheme, colors) in visiblePresetSchemes) {
                val isSelected = currentColors == colors.map { it.toArgb() }

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    animationSpec = tween(300), label = "presetSchemeBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(300), label = "presetSchemeText"
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().clip(cardG2Shape).combinedClickable(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            ThemeSettings.applyPresetScheme(context, scheme)
                        },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val newSet = deletedPresets + scheme
                            deletedPresets = newSet
                            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                                .putString("deleted_preset_schemes", newSet.joinToString(",") { it.name })
                                .apply()
                        }
                    ),
                    shape = cardG2Shape,
                    color = backgroundColor
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            fontSize = 14.sp,
                            color = contentColor,
                            modifier = Modifier.widthIn(min=60.dp, max=80.dp),
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(16.dp))
                        Box(Modifier.weight(1f).height(ThemeSettings.testLineThickness.dp + 4.dp).clip(gradientShape).background(Brush.horizontalGradient(colors)))
                    }
                }
            }

            // 用户保存的自定义方案
            for ((index, customSchemePair) in ThemeSettings.customGradientSchemes.withIndex()) {
                val schemeName = customSchemePair.first
                val customColors = customSchemePair.second
                val isSelected = currentColors == customColors

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    animationSpec = tween(300), label = "customSchemeBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(300), label = "customSchemeText"
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().clip(cardG2Shape).combinedClickable(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            ThemeSettings.saveMultiColorSelectedColors(context, customColors)
                        },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val newList = ThemeSettings.customGradientSchemes.toMutableList().apply { removeAt(index) }
                            ThemeSettings.saveCustomGradientSchemes(context, newList)
                        }
                    ),
                    shape = cardG2Shape,
                    color = backgroundColor
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            schemeName,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            fontSize = 14.sp,
                            color = contentColor,
                            modifier = Modifier.widthIn(min=60.dp, max=80.dp),
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(16.dp))
                        val displayColors = if (customColors.size >= 2) customColors.map { Color(it) } else listOf(Color.Transparent, Color.Transparent)
                        Box(Modifier.weight(1f).height(ThemeSettings.testLineThickness.dp + 4.dp).clip(gradientShape).background(Brush.horizontalGradient(displayColors)))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 添加新的自定义渐变方案
        var isCreatingScheme by remember { mutableStateOf(false) }

        AnimatedVisibility(visible = !isCreatingScheme) {
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isCreatingScheme = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = G2Shapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加新的预设方案", fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = isCreatingScheme,
            enter = expandVertically(tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
            exit = shrinkVertically(tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
        ) {
            var tempSequence by remember { mutableStateOf<List<Int>>(emptyList()) }
            var schemeNameInput by remember { mutableStateOf("") }

            val customRed = remember { Animatable(1f) }
            val customGreen = remember { Animatable(1f) }
            val customBlue = remember { Animatable(1f) }
            val currentCustomArgb = android.graphics.Color.rgb(customRed.value, customGreen.value, customBlue.value)

            val hexS = String.format(Locale.US, "#%02X%02X%02X", (customRed.value * 255).toInt(), (customGreen.value * 255).toInt(), (customBlue.value * 255).toInt())
            var hexText by remember { mutableStateOf(hexS) }
            var isHexFocused by remember { mutableStateOf(false) }
            LaunchedEffect(customRed.value, customGreen.value, customBlue.value) { if (!isHexFocused) hexText = hexS }

            val isSequenceAlreadyExists = remember(tempSequence, ThemeSettings.customGradientSchemes, deletedPresets) {
                val builtInMatch = presetSchemes
                    .filter { it.second !in deletedPresets }
                    .any { (_, _, colors) ->
                        tempSequence == colors.map { it.toArgb() }
                    }
                val customMatch = ThemeSettings.customGradientSchemes.any { (_, customColors) ->
                    tempSequence == customColors
                }
                builtInMatch || customMatch
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardG2Shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("创建渐变方案", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isCreatingScheme = false
                                tempSequence = emptyList()
                                schemeNameInput = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // 调色区
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(G2Shapes.icon).background(Color(currentCustomArgb)).border(1.dp, Color.Gray.copy(0.3f), G2Shapes.icon))
                        Spacer(Modifier.width(16.dp))
                        OutlinedTextField(
                            value = hexText,
                            onValueChange = { newValue ->
                                hexText = newValue
                                try {
                                    val parsed = android.graphics.Color.parseColor(newValue)
                                    val hexSpec = tween<Float>(800, easing = FastOutSlowInEasing)
                                    scope.launch { customRed.animateTo(android.graphics.Color.red(parsed)/255f, hexSpec) }
                                    scope.launch { customGreen.animateTo(android.graphics.Color.green(parsed)/255f, hexSpec) }
                                    scope.launch { customBlue.animateTo(android.graphics.Color.blue(parsed)/255f, hexSpec) }
                                } catch (_: Exception) {}
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(52.dp).onFocusChanged { isHexFocused = it.isFocused },
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    RgbSliders(customRed, customGreen, customBlue, spacing = 8)

                    val isColorInPresets = ThemeSettings.userPresets.contains(currentCustomArgb)
                    AnimatedVisibility(
                        visible = !isColorInPresets,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    ThemeSettings.addUserPreset(context, currentCustomArgb)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = G2Shapes.button,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text("保存为色彩预设", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // 选色组合区
                    Text("色彩预设库 (点击以依次加入渐变序列)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeSettings.userPresets.forEach { presetInt ->
                            val isSelected = tempSequence.contains(presetInt)
                            PresetColorCircle(
                                preset = Color(presetInt),
                                isSelected = isSelected,
                                isDefault = false,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val newSeq = if (isSelected) {
                                        tempSequence - presetInt
                                    } else {
                                        if (tempSequence.size < 8) tempSequence + presetInt else tempSequence
                                    }
                                    tempSequence = newSeq
                                },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    ThemeSettings.removeUserPreset(context, presetInt)
                                    tempSequence = tempSequence - presetInt
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 实时预览与保存为新方案
                    Text("实时渐变预览", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        AnimatedContent(
                            targetState = tempSequence.size >= 2,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "previewAnim"
                        ) { isReady ->
                            if (isReady) {
                                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                    val previewColors = if (tempSequence.size >= 2) tempSequence.map { Color(it) } else listOf(Color.Transparent, Color.Transparent)
                                    Box(Modifier.fillMaxWidth().height(32.dp).clip(gradientShape).background(Brush.horizontalGradient(previewColors)))

                                    Spacer(Modifier.height(16.dp))

                                    // 如果不重复，才展示输入框和保存按钮
                                    AnimatedVisibility(
                                        visible = !isSequenceAlreadyExists,
                                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                    ) {
                                        Column {
                                            OutlinedTextField(
                                                value = schemeNameInput,
                                                onValueChange = { if(it.length <= 10) schemeNameInput = it },
                                                label = { Text("方案名称（选填，最多10字符）", fontSize = 12.sp) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                )
                                            )

                                            Spacer(Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    focusManager.clearFocus()

                                                    val finalName = schemeNameInput.ifBlank { "自定义 ${ThemeSettings.customGradientSchemes.size + 1}" }

                                                    val newList = ThemeSettings.customGradientSchemes + (finalName to tempSequence)
                                                    ThemeSettings.saveCustomGradientSchemes(context, newList)
                                                    ThemeSettings.saveMultiColorSelectedColors(context, tempSequence)

                                                    isCreatingScheme = false
                                                    tempSequence = emptyList()
                                                    schemeNameInput = ""
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = G2Shapes.button,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("保存方案并应用", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // 如果方案重复，不显示输入框和按钮，显示一行文案提示
                                    AnimatedVisibility(
                                        visible = isSequenceAlreadyExists,
                                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .clip(G2Shapes.button)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "当前组合已存在于预设方案中",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(32.dp).clip(gradientShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("请至少选择 2 种颜色构成渐变", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PresetColorCircle(
    preset: Color, isSelected: Boolean, isDefault: Boolean,
    /** 不允许选择的颜色（如线条要避开背景色）：颜色照原样显示，上面加一道斜杠 */
    isDisabled: Boolean = false,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val ckScale by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.6f), label = "")
    // 对比色（按圆点亮度取黑/白）：斜杠与禁用圈共用
    val contrastColor = if ((preset.red*0.299 + preset.green*0.587 + preset.blue*0.114) > 0.5) Color.Black else Color.White
    val outlineWidth = when {
        isSelected -> 3.dp
        isDisabled -> 2.dp
        else -> 1.dp
    }
    val outlineColor = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isDisabled -> contrastColor.copy(alpha = 0.72f)
        else -> Color.Gray.copy(0.3f)
    }
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(preset)
            .border(width = outlineWidth, color = outlineColor, shape = CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDisabled) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 3.dp)
                    .rotate(-45f)
                    .background(contrastColor.copy(alpha = 0.72f), RoundedCornerShape(1.5.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check, null,
                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = ckScale; scaleY = ckScale; alpha = ckScale },
                tint = contrastColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    var isAppExp by rememberSaveable { mutableStateOf(false) }
    var isBrightExp by rememberSaveable { mutableStateOf(false) }
    var isBlackBorderTestExp by rememberSaveable { mutableStateOf(false) }
    var showPureModeHelp by remember { mutableStateOf(false) }
    var isColorExp by rememberSaveable { mutableStateOf(false) }
    var isAnimExp by rememberSaveable { mutableStateOf(false) }

    // 实时线条预览
    var previewVisible by remember { mutableStateOf(false) }
    val previewAlpha by animateFloatAsState(
        targetValue = if (previewVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "previewAlpha"
    )
    var realtimeThickness by remember { mutableFloatStateOf(ThemeSettings.testLineThickness) }
    var realtimeSegmentLength by remember { mutableFloatStateOf(if (ThemeSettings.multiColorSegmentLength == 0f) 1f else ThemeSettings.multiColorSegmentLength) }
    var isDragging by remember { mutableStateOf(false) }
    val isG2Enabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("is_g2_enabled", false)

    // 动画触发器
    var animationTrigger by remember { mutableIntStateOf(0) }

    // 屏幕宽度检测
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    var wasVisible by remember { mutableStateOf(false) }
    var lastX by remember { mutableFloatStateOf(Float.NaN) }

    // 顶级动画状态管理
    val cardAlphas = remember { List(6) { Animatable(0f) } }
    val cardOffsetsY = remember { List(6) { Animatable(30f) } }

    // 统一瀑布流入场动画（仅在进入页面/切回时触发）
    LaunchedEffect(animationTrigger) {
        // 关动效，或从横幅跳转过来（跳过这一次淡入）⇒ 直接到位
        if (!ThemeSettings.isAnimationEnabled || SettingsNavRequests.skipEnterAnimation) {
            for (i in 0 until 6) {
                cardAlphas[i].snapTo(1f)
                cardOffsetsY[i].snapTo(0f)
            }
            return@LaunchedEffect
        }

        for (i in 0 until 6) {
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

    // 监听设置变化
    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(
                realtimeThickness,
                ThemeSettings.testLineColor,
                ThemeSettings.isMultiColorMode,
                ThemeSettings.multiColorSelectedColors,
                realtimeSegmentLength,
                ThemeSettings.isLegacyCurveEnabled
            )
        }.drop(1).collect {
            previewVisible = true
            delay(1000)
            previewVisible = false
        }
    }

    // 大卡片圆角
    val g2CardShape = G2Shapes.card

    // 外观设置小卡片圆角
    val g2LargeShape = G2Shapes.largeCard

    // 校准车间按钮圆角
    val buttonG2Shape = G2Shapes.button

    val appArrowRotation by animateFloatAsState(targetValue = if (isAppExp) 180f else 0f, label = "appArrow")
    val brightArrowRotation by animateFloatAsState(targetValue = if (isBrightExp) 180f else 0f, label = "brightArrow")
    val blackBorderArrowRotation by animateFloatAsState(targetValue = if (isBlackBorderTestExp) 180f else 0f, label = "compactArrow")
    val colorArrowRotation by animateFloatAsState(targetValue = if (isColorExp) 180f else 0f, label = "colorArrow")
    val animArrowRotation by animateFloatAsState(targetValue = if (isAnimExp) 180f else 0f, label = "animArrow")

    val backgroundColor = MaterialTheme.colorScheme.background

    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    // "使用旧版曲线"开关高亮：灰度矩形铺满卡片内容区，呼吸式淡入淡出
    var legacyCurveFlash by remember { mutableStateOf(false) }
    var legacyCurveFlashOn by remember { mutableStateOf(false) }
    val legacyCurveFlashAlpha by animateFloatAsState(
        targetValue = if (legacyCurveFlashOn) 0.10f else 0f,
        animationSpec = tween(if (legacyCurveFlashOn) 220 else 700, easing = FastOutSlowInEasing),
        label = "legacyCurveFlashAlpha"
    )
    val legacyCurveFlashColor = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(legacyCurveFlash) {
        if (legacyCurveFlash) {
            repeat(3) {
                legacyCurveFlashOn = true
                delay(420)
                legacyCurveFlashOn = false
                delay(900)
            }
            legacyCurveFlash = false
        }
    }

    // "使用旧版曲线"开关行的窗口 y（滚动定位用）
    var legacySwitchY by remember { mutableStateOf(0f) }

    // 粗定位：切页滑入期间执行（此刻不可见）
    LaunchedEffect(SettingsNavRequests.preScrollRequestId) {
        if (SettingsNavRequests.preScrollRequestId == 0) return@LaunchedEffect
        // item 4 = "自定义黑边遮挡测试"卡片（0=Spacer、1=标题、2=外观设置、3=测试亮度、4=本卡片）
        listState.scrollToItem(4)
    }

    // 横幅引导：展开板块 → 动画滚动 → 停稳后高亮"使用旧版曲线"开关
    LaunchedEffect(SettingsNavRequests.guideRequestId) {
        if (SettingsNavRequests.guideRequestId == 0) return@LaunchedEffect
        SettingsNavRequests.skipEnterAnimation = false
        isBlackBorderTestExp = true
        delay(450)                                    // 等展开动画结束
        val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
        // 屏幕较矮时开关可能仍在视口下方：继续下滚
        var guard = 0
        while (guard < 6 && (legacySwitchY <= 0f || legacySwitchY > screenH * 0.8f)) {
            legacySwitchY = Float.MAX_VALUE           // 哨兵：未上报说明还没进视口
            listState.animateScrollBy(screenH * 0.3f, spring(dampingRatio = 0.95f, stiffness = 260f))
            delay(150)
            guard++
        }
        // 对齐到屏幕上方 1/3
        val delta = legacySwitchY - screenH / 3f
        if (legacySwitchY < Float.MAX_VALUE && kotlin.math.abs(delta) > 1f) {
            listState.animateScrollBy(delta, spring(dampingRatio = 0.95f, stiffness = 260f))
        }
        delay(300)                                    // 停稳后再高亮
        legacyCurveFlash = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tab 可见性检测放在满宽 Box 上
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                // 大屏适配：内容限宽居中
                .widthIn(max = DeviceUtils.FormMaxWidth)
                .hazeSource(hazeState)
                .padding(horizontal = 24.dp)
        ) {
            item { Spacer(Modifier.statusBarsPadding()); Spacer(modifier = Modifier.height(80.dp)) }
            item { Text(text = "设置", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Spacer(modifier = Modifier.height(24.dp)) }

            // 1. 外观设置
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
                            Text("外观设置", Modifier.weight(1f), fontWeight = FontWeight.Bold)

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

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.1f))

                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("顶栏渐变模糊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(9.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "实验性",
                                                        modifier = Modifier.padding(horizontal = 6.5.dp),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                        }
                                        Text(text = if (ThemeSettings.topBarGradientBlur) "顶栏使用渐变模糊呈现" else "开启后顶栏将使用渐变模糊呈现", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.topBarGradientBlur,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveTopBarGradientBlurConfig(context, it)
                                        }
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("底栏模糊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(text = if (ThemeSettings.navBarBlurEnabled) "底栏背景实时模糊显示下方内容" else "底栏背景实时模糊显示下方内容", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.navBarBlurEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveNavBarBlurConfig(context, it)
                                        }
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                // 关于页卡片毛玻璃
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("关于页卡片模糊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(9.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "实验性",
                                                        modifier = Modifier.padding(horizontal = 6.5.dp),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                        }
                                        Text(text = "关于页卡片背景毛玻璃效果", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.aboutCardBlurEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveAboutCardBlurConfig(context, it)
                                        }
                                    )
                                }


                                // 任一模糊开启时显示强度滑块，同时作用于底栏与关于页卡片
                                AnimatedVisibility(visible = ThemeSettings.navBarBlurEnabled || ThemeSettings.aboutCardBlurEnabled) {
                                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                                        val blurScope = rememberCoroutineScope()
                                        val blurFocusManager = LocalFocusManager.current
                                        val blurAnim = remember { Animatable(ThemeSettings.navBarBlurRadius) }
                                        var blurInput by remember { mutableStateOf(ThemeSettings.navBarBlurRadius.roundToInt().toString()) }
                                        var isBlurFocused by remember { mutableStateOf(false) }

                                        LaunchedEffect(blurAnim.value) {
                                            if (!isBlurFocused) {
                                                blurInput = blurAnim.value.roundToInt().toString()
                                            }
                                            ThemeSettings.saveNavBarBlurRadius(context, blurAnim.value)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("模糊强度", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            // 重置按钮
                                            IconButton(
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    blurInput = "20"
                                                    blurScope.launch {
                                                        blurAnim.animateTo(
                                                            targetValue = 20f,
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
                                                    value = blurInput,
                                                    onValueChange = { newVal ->
                                                        if (newVal.length <= 2) {
                                                            val num = newVal.toIntOrNull()
                                                            if (num != null) {
                                                                if (num > 32) {
                                                                    blurInput = "32"
                                                                    blurScope.launch { blurAnim.animateTo(32f, tween(400, easing = FastOutSlowInEasing)) }
                                                                } else {
                                                                    blurInput = newVal
                                                                    blurScope.launch { blurAnim.animateTo(num.toFloat().coerceAtLeast(6f), tween(400, easing = FastOutSlowInEasing)) }
                                                                }
                                                            } else if (newVal.isEmpty()) {
                                                                blurScope.launch { blurAnim.snapTo(6f) }
                                                                ThemeSettings.saveNavBarBlurRadius(context, 6f)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .width(IntrinsicSize.Min)
                                                        .widthIn(min = 35.dp)
                                                        .onFocusChanged { isBlurFocused = it.isFocused },
                                                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { blurFocusManager.clearFocus() }),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                                )
                                                Text("dp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        HapticSlider(
                                            l = "",
                                            c = MaterialTheme.colorScheme.primary,
                                            v = (blurAnim.value - 6f) / 26f
                                        ) {
                                            blurFocusManager.clearFocus()
                                            val newValue = it * 26f + 6f
                                            blurScope.launch { blurAnim.snapTo(newValue) }
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

                                Spacer(Modifier.height(16.dp))

                                // 使用旧版曲线 (仅系统默认模式)
                                val legacyDisabled = ThemeSettings.useCustomRadius || !ThemeSettings.isCenterDataAvailable
                                val legacyAlpha by animateFloatAsState(
                                    targetValue = if (legacyDisabled) 0.4f else 1f,
                                    animationSpec = tween(300),
                                    label = "legacyAlpha"
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        // 记录窗口 y，供滚动定位
                                        .onGloballyPositioned { legacySwitchY = it.positionInWindow().y }
                                        // 高亮：灰度矩形，横向铺到卡片内容边缘
                                        .drawBehind {
                                            if (legacyCurveFlashAlpha > 0f) {
                                                val insetH = 20.dp.toPx()
                                                val insetV = 10.dp.toPx()
                                                drawRect(
                                                    color = legacyCurveFlashColor.copy(alpha = legacyCurveFlashAlpha),
                                                    topLeft = Offset(-insetH, -insetV),
                                                    size = Size(size.width + insetH * 2, size.height + insetV * 2)
                                                )
                                            }
                                        }
                                        .alpha(legacyAlpha)
                                ) {
                                    Column(modifier = Modifier.weight(1f).animateContentSize()) {
                                        Text("使用旧版曲线", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        AnimatedVisibility(
                                            visible = !ThemeSettings.isCenterDataAvailable,
                                            enter = slideInVertically(tween(300)) + fadeIn(tween(300)),
                                            exit = slideOutVertically(tween(300)) + fadeOut(tween(300))
                                        ) {
                                            Text(
                                                "设备不支持新曲线，已自动启用旧版曲线",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        AnimatedVisibility(
                                            visible = ThemeSettings.isCenterDataAvailable && ThemeSettings.useCustomRadius,
                                            enter = slideInVertically(tween(300)) + fadeIn(tween(300)),
                                            exit = slideOutVertically(tween(300)) + fadeOut(tween(300))
                                        ) {
                                            Text(
                                                "仅系统默认模式生效，请先关闭「自定义圆角半径」",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = ThemeSettings.isLegacyCurveEnabled,
                                        enabled = !legacyDisabled,
                                        onCheckedChange = { enabled ->
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveLegacyCurveConfig(context, enabled)
                                        }
                                    )
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

                                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                // 长按退出
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("长按退出", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("关闭后只能按返回键退出黑边遮挡测试", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.longPressExitEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveLongPressExitConfig(context, it)
                                        }
                                    )
                                }

                                // 长按退出秒数
                                AnimatedVisibility(visible = ThemeSettings.longPressExitEnabled) {
                                    Column(Modifier.padding(top = 12.dp)) {
                                        val exitScope = rememberCoroutineScope()
                                        val exitFocusManager = LocalFocusManager.current
                                        val exitAnim = remember { Animatable(ThemeSettings.longPressExitSeconds.toFloat()) }
                                        var exitInput by remember { mutableStateOf(ThemeSettings.longPressExitSeconds.toString()) }
                                        var isExitFocused by remember { mutableStateOf(false) }

                                        LaunchedEffect(exitAnim.value) {
                                            if (!isExitFocused) {
                                                exitInput = exitAnim.value.roundToInt().toString()
                                            }
                                            ThemeSettings.saveLongPressExitSeconds(context, exitAnim.value.roundToInt())
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("长按退出时长", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            // 重置按钮
                                            IconButton(
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    exitInput = "5"
                                                    exitScope.launch {
                                                        exitAnim.animateTo(
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
                                                    value = exitInput,
                                                    onValueChange = { newVal ->
                                                        if (newVal.length <= 2) {
                                                            val num = newVal.toIntOrNull()
                                                            if (num != null) {
                                                                if (num > 20) {
                                                                    exitInput = "20"
                                                                    exitScope.launch { exitAnim.animateTo(20f, tween(400, easing = FastOutSlowInEasing)) }
                                                                } else {
                                                                    exitInput = newVal
                                                                    exitScope.launch { exitAnim.animateTo(num.toFloat().coerceAtLeast(3f), tween(400, easing = FastOutSlowInEasing)) }
                                                                }
                                                            } else if (newVal.isEmpty()) {
                                                                exitInput = ""
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .width(IntrinsicSize.Min)
                                                        .widthIn(min = 35.dp)
                                                        .onFocusChanged { isExitFocused = it.isFocused },
                                                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { exitFocusManager.clearFocus() }),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                                )
                                                Text("秒", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        HapticSlider(
                                            l = "",
                                            c = MaterialTheme.colorScheme.primary,
                                            v = (exitAnim.value - 3f) / 17f
                                        ) {
                                            exitFocusManager.clearFocus()
                                            val newValue = it * 17f + 3f
                                            exitScope.launch { exitAnim.snapTo(newValue) }
                                        }
                                    }
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
                        AnimatedVisibility(visible = isColorExp) {
                            Column {
                                TestLineSettingsPanel(
                                    onThicknessChange = { realtimeThickness = it },
                                    onSegmentLengthChange = { realtimeSegmentLength = it },
                                    onDraggingChange = { isDragging = it }
                                )
                                // 进入「线条与配色」页（可调线条/背景/字体，并实时预览）
                                MoreSettingsEntry(
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                                )
                            }
                        }
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
                                        Text(text = if (ThemeSettings.isAnimationEnabled) "关闭后将禁用卡片动画效果" else "开启后将拥有卡片动画效果", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.isAnimationEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveAnimationConfig(context, it)
                                        }
                                    )
                                }

                                Spacer(Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("设置页线条预览", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(text = if (ThemeSettings.isSettingsLinePreviewEnabled) "关闭后将不再显示线条预览" else "开启后将显示实时线条预览", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.isSettingsLinePreviewEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveSettingsLinePreviewConfig(context, it)
                                        }
                                    )
                                }

                                Spacer(Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("背景动态混色", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(text = "关于页背景缓慢流动混色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.aboutDynamicMixEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveAboutDynamicMixConfig(context, it)
                                        }
                                    )
                                }


                            }
                        }
                    }
                }
            }

            // 6. 下载与更新
            item {
                var isDownloadExp by rememberSaveable { mutableStateOf(false) }
                val downloadArrowRotation by animateFloatAsState(targetValue = if (isDownloadExp) 180f else 0f, label = "downloadArrow")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer { this.alpha = cardAlphas[5].value; this.translationY = cardOffsetsY[5].value },
                    shape = g2CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isDownloadExp = !isDownloadExp
                            }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("下载与更新", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.graphicsLayer { rotationZ = downloadArrowRotation })
                        }
                        AnimatedVisibility(visible = isDownloadExp) {
                            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                                HorizontalDivider(Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.1f))

                                // 自动检查更新
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("自动检查更新", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("关闭后只能在关于页手动检查更新", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = ThemeSettings.autoCheckUpdateEnabled,
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            ThemeSettings.saveAutoCheckUpdate(context, it)
                                        }
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Text("更新下载源", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(10.dp))
                                // 指示块可滑动、松手弹簧吸附
                                SegmentedSlideSelector(
                                    items = listOf("gitee" to "Gitee", "github" to "GitHub"),
                                    selected = ThemeSettings.updateDownloadSource ?: "gitee",
                                    onSelect = { source ->
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        ThemeSettings.saveUpdateSource(context, source)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(200.dp)) }
        }

        // 顶栏：上划越过小阈值后整体淡入（固定时长动画，与滑动距离解耦）。
        // 列表第 0 项是顶部留白 Spacer，index>0 时至少已滚过一个 Spacer，
        // 因此再叠加 offset>30 让上划一开始就出现；时长放慢到 450ms，避免突兀
        val topBarAlpha by animateFloatAsState(
            targetValue = if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 30) 1f else 0f,
            animationSpec = tween(450),
            label = "topBarAlpha"
        )
        // thin 样式在组合期计算
        val topBarThinStyle = HazeMaterials.thin(containerColor = MaterialTheme.colorScheme.background)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // graphicsLayer 必须在模糊/背景之前（外层）：让 alpha 同时作用于模糊色带与文字
                .graphicsLayer { this.alpha = topBarAlpha }
                .then(
                    if (ThemeSettings.topBarGradientBlur) {
                        Modifier.hazeEffect(hazeState) {
                            style = topBarThinStyle
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                    } else {
                        Modifier
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                        0.14f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                        0.30f to MaterialTheme.colorScheme.background.copy(alpha = 0.84f),
                                        0.46f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                        0.62f to MaterialTheme.colorScheme.background.copy(alpha = 0.50f),
                                        0.80f to MaterialTheme.colorScheme.background.copy(alpha = 0.20f),
                                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                    )
                                )
                            )
                            .padding(bottom = 32.dp)
                    }
                )
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .padding(start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }



        // 实时边框预览
        if (ThemeSettings.isSettingsLinePreviewEnabled) {
            val dummyPagerState = rememberPagerState(pageCount = { 1 })
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            OOBELiveBorderPreview(
                pagerState = dummyPagerState,
                fixedAlpha = previewAlpha,
                realtimeThickness = realtimeThickness,
                realtimeSegmentLength = realtimeSegmentLength,
                isDragging = isDragging,
                isG2Enabled = isG2Enabled,
                isLegacyCurveEnabled = ThemeSettings.isLegacyCurveEnabled,
                offTLX = prefs.getFloat("r_tl_x", 0f), offTLY = prefs.getFloat("r_tl_y", 0f),
                offTRX = prefs.getFloat("r_tr_x", 0f), offTRY = prefs.getFloat("r_tr_y", 0f),
                offBLX = prefs.getFloat("r_bl_x", 0f), offBLY = prefs.getFloat("r_bl_y", 0f),
                offBRX = prefs.getFloat("r_br_x", 0f), offBRY = prefs.getFloat("r_br_y", 0f)
            )
        }
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

        // 测试页屏幕常亮开关
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("测试页屏幕常亮", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("开启后进入除「HDR检测」外的测试页将保持屏幕不熄灭", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,modifier = Modifier.padding(top = 4.dp))
            }
            Switch(
                checked = ThemeSettings.isKeepScreenOnEnabled,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    ThemeSettings.saveKeepScreenOn(context, it)
                }
            )
        }
    }
}
