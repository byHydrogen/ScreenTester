package com.hydrogen.screentester

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.ui.platform.LocalDensity

// ======== 更新状态 ========
object GlobalUpdateState {
    var hasNewVersion by mutableStateOf(false)
    var latestVersionName by mutableStateOf("")
    var latestChangelog by mutableStateOf("")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeSettings.loadConfig(this)

        // ======== 应用启动时静默检查更新 ========
        UpdateManager.checkUpdate(this) { hasUpdate, version, changelog ->
            if (hasUpdate) {
                GlobalUpdateState.hasNewVersion = true
                GlobalUpdateState.latestVersionName = version ?: ""
                GlobalUpdateState.latestChangelog = changelog ?: ""
            }
        }

        setContent {
            ScreenTesterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainContainer()
                }
            }
        }
    }
}

@Composable
fun MainContainer() {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 点击空白处清空焦点，收起键盘
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            userScrollEnabled = true
        ) { pageIndex ->
            when (pageIndex) {
                0 -> HomePage()
                1 -> SettingsPage()
                2 -> AboutPage()
            }
        }

        AnimatedScrubbingNavBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            currentPage = pagerState.currentPage,
            onDrag = { deltaX ->
                // 手指往右(deltaX为正)，Pager向左滚(-deltaX)，实现同步
                pagerState.dispatchRawDelta(deltaX * 3.5f)
            },
            onDragEnd = {
                scope.launch { pagerState.animateScrollToPage(pagerState.settledPage) }
            },
            onTabClick = { index ->
                focusManager.clearFocus()
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )
    }
}

@Composable
fun AnimatedScrubbingNavBar(
    modifier: Modifier = Modifier,
    currentPage: Int,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTabClick: (Int) -> Unit
) {
    val items = listOf("主页" to Icons.Default.Home, "设置" to Icons.Default.Settings, "关于" to Icons.Default.Info)
    val configuration = LocalConfiguration.current
    val navBarWidth = configuration.screenWidthDp.dp * 0.85f
    val tabWidth = navBarWidth / items.size
    val density = LocalDensity.current.density

    val indicatorOffset by animateDpAsState(
        targetValue = tabWidth * currentPage,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "nav_blob"
    )

    val indicatorPadding = 9.dp

    val navBarG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 37.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f
                val c = 0.55f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }

    val indicatorG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 28.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f
                val c = 0.55f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }

    Surface(
        modifier = modifier
            .width(navBarWidth)
            .height(74.dp)
            .clip(navBarG2Shape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        shape = navBarG2Shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), indicatorG2Shape)
            )

            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    val isSelected = currentPage == index
                    val animProgress by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 250f),
                        label = ""
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.graphicsLayer {
                                translationY = (10f * (1f - animProgress) + 2f * animProgress) * density
                            }
                        ) {
                            Icon(item.second, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.6f), modifier = Modifier.size(24.dp))
                            Text(
                                text = item.first, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 1.dp).graphicsLayer { alpha = animProgress; scaleX = 0.85f + (0.15f * animProgress); scaleY = 0.85f + (0.15f * animProgress) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===================== 调色板 =====================

@Composable
fun ColorPickerSection() {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val thicknessAnim = remember { Animatable(ThemeSettings.testLineThickness) }

    // --- 1. 线条粗细的状态管理 ---
    var thicknessInput by remember { mutableStateOf(String.format("%.1f", ThemeSettings.testLineThickness)) }
    var isThicknessFocused by remember { mutableStateOf(false) }
    LaunchedEffect(thicknessAnim.value) {
        if (!isThicknessFocused) {
            thicknessInput = String.format("%.1f", thicknessAnim.value)
        }
        ThemeSettings.saveLineThickness(context, thicknessAnim.value)
    }

    val initialColor = Color(ThemeSettings.testLineColor)
    val redAnim = remember { Animatable(initialColor.red) }
    val greenAnim = remember { Animatable(initialColor.green) }
    val blueAnim = remember { Animatable(initialColor.blue) }

    // 当前真实的滑块颜色
    val currentColorArgb = android.graphics.Color.rgb(redAnim.value, greenAnim.value, blueAnim.value)
    // 瞬间目标色（用于解决打勾动画延迟）
    var instantTargetColor by remember { mutableStateOf<Int?>(null) }
    val effectiveArgb = instantTargetColor ?: currentColorArgb

    val hexS = String.format("#%02X%02X%02X", (redAnim.value * 255).toInt(), (greenAnim.value * 255).toInt(), (blueAnim.value * 255).toInt())
    var hexText by remember { mutableStateOf(hexS) }
    var isHexFocused by remember { mutableStateOf(false) }

    LaunchedEffect(redAnim.value, greenAnim.value, blueAnim.value) {
        if (!isHexFocused) hexText = hexS
        ThemeSettings.saveLineColor(context, currentColorArgb)
    }

    // 合并并去重预设颜色
    val defaultPresets = listOf(Color.White, Color(0xFF72A7FF), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error)
    val userPresetsColors = ThemeSettings.userPresets.map { Color(it) }
    val allPresets = ThemeSettings.userPresets.map { Color(it) }

    val isCurrentInPresets = allPresets.any { it.toArgb() == effectiveArgb }
    var isPresetsExpanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        // ===================== 粗细调节区 =====================
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
                        // 1. 限制长度，防止输入过长
                        if (newVal.length <= 4) {
                            // 2. 只有当输入为空，或者是合法数字时才处理
                            val num = newVal.toFloatOrNull()
                            if (num != null) {
                                if (num > 15f) {
                                    // 如果输入的数大于 15
                                    thicknessInput = "15" // 输入框显示 15
                                    scope.launch { thicknessAnim.animateTo(15f, tween(400)) } // 滑块滑到 15
                                } else {
                                    // 正常范围：1-15 之间
                                    thicknessInput = newVal
                                    scope.launch { thicknessAnim.animateTo(num, tween(400)) }
                                }
                            } else if (newVal.isEmpty()) {
                                // 允许删空，方便用户重新输入
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
            v = (thicknessAnim.value - 1f) / 14f
        ) {
            focusManager.clearFocus()
            val newValue = it * 14f + 1f
            scope.launch { thicknessAnim.snapTo(newValue) }
        }

        Spacer(Modifier.height(24.dp))

        val colorPreviewG2Shape = remember {
            object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density
                ): androidx.compose.ui.graphics.Outline {
                    val path = Path()
                    val w = size.width
                    val h = size.height
                    val radius = with(density) { 12.dp.toPx() }
                    val p = (1.4f * radius).coerceAtMost(h / 2f)
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
                hexText = newValue
                try {
                    val parsed = android.graphics.Color.parseColor(newValue)
                    instantTargetColor = parsed
                    scope.launch { redAnim.animateTo(android.graphics.Color.red(parsed)/255f, tween(400)) }
                    scope.launch { greenAnim.animateTo(android.graphics.Color.green(parsed)/255f, tween(400)) }
                    scope.launch { blueAnim.animateTo(android.graphics.Color.blue(parsed)/255f, tween(400)) }
                } catch (_: Exception) {}
            },
            label = { Text("颜色代码 (HEX)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { isHexFocused = it.isFocused },
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(20.dp)); Text("RGB 自定义调色", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        HapticSlider("R", Color.Red, redAnim.value) { focusManager.clearFocus(); instantTargetColor = null; scope.launch { redAnim.snapTo(it) } }
        Spacer(Modifier.height(10.dp))
        HapticSlider("G", Color(0xFF00AA00), greenAnim.value) { focusManager.clearFocus(); instantTargetColor = null; scope.launch { greenAnim.snapTo(it) } }
        Spacer(Modifier.height(10.dp))
        HapticSlider("B", Color.Blue, blueAnim.value) { focusManager.clearFocus(); instantTargetColor = null; scope.launch { blueAnim.snapTo(it) } }

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
                        instantTargetColor = currentColorArgb
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
                                    instantTargetColor = preset.toArgb()

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
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isPresetsExpanded = !isPresetsExpanded
                }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
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

@Composable
fun HomePage() {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isNewCharFirstFrame by remember { mutableStateOf(false) }

    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var previousText by remember { mutableStateOf("") }
    var animStartIndex by remember { mutableIntStateOf(-1) }
    val textAlpha = remember { Animatable(1f) }

    // 提示文字（Placeholder）的淡出动画
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (isFocused || textFieldValue.text.isNotEmpty()) 0f else 0.6f,
        animationSpec = tween(durationMillis = 250),
        label = "placeholderAlpha"
    )

    // 精准计算当前帧新文字的实际透明度
    val currentAlpha = if (isNewCharFirstFrame) 0f else textAlpha.value
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val displayTextFieldValue = remember(textFieldValue, animStartIndex, currentAlpha, onSurfaceColor) {
        val text = textFieldValue.text
        val annotatedString = buildAnnotatedString {
            if (animStartIndex in 0..text.length) {
                withStyle(SpanStyle(color = onSurfaceColor)) {
                    append(text.substring(0, animStartIndex))
                }
                withStyle(SpanStyle(color = onSurfaceColor.copy(alpha = currentAlpha))) {
                    append(text.substring(animStartIndex))
                }
            } else {
                withStyle(SpanStyle(color = onSurfaceColor)) {
                    append(text)
                }
            }
        }
        textFieldValue.copy(annotatedString = annotatedString)
    }

    // ================= 动画状态核心管理 =================
    var animationTrigger by remember { mutableIntStateOf(0) }
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
    var initialX by remember { mutableStateOf<Float?>(null) }
    var wasOffScreen by remember { mutableStateOf(false) }
    val searchBoxG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 32.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f
                val c = 0.55f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }
    val isDark = isSystemInDarkTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .onGloballyPositioned { coords ->
                    val currentX = coords.positionInWindow().x
                    if (initialX == null) {
                        if (currentX >= 0f) initialX = currentX
                    } else {
                        if (currentX != initialX) {
                            wasOffScreen = true
                        } else if (wasOffScreen && currentX == initialX) {
                            wasOffScreen = false
                            // 确保从 设置/关于 Tab 切回主页时，能重新触发瀑布流浮出
                            animationTrigger++
                        }
                    }
                }
        ) {
            item { Spacer(Modifier.statusBarsPadding()); Spacer(modifier = Modifier.height(80.dp)) }
            item { Text(text = "ScreenTester", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Spacer(modifier = Modifier.height(20.dp)) }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = searchBoxG2Shape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "搜索测试项",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alpha(placeholderAlpha)
                            )

                            BasicTextField(
                                value = displayTextFieldValue,
                                onValueChange = { newValue ->
                                    val currentText = newValue.text

                                    if (currentText.length > previousText.length && currentText.startsWith(previousText)) {
                                        animStartIndex = previousText.length
                                        isNewCharFirstFrame = true

                                        animJob?.cancel()
                                        animJob = scope.launch {
                                            textAlpha.snapTo(0f)
                                            isNewCharFirstFrame = false
                                            textAlpha.animateTo(1f, tween(250))
                                        }
                                    } else {
                                        animStartIndex = -1
                                        isNewCharFirstFrame = false
                                        animJob?.cancel()
                                    }

                                    textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                        text = newValue.text,
                                        selection = newValue.selection,
                                        composition = newValue.composition
                                    )
                                    previousText = currentText
                                    searchQuery = currentText
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.Unspecified,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            var currentCardIndex = 1

            // ==================== 【视觉显示类测试】 ====================

            // 1. 屏幕黑边遮挡测试
            val index1 = currentCardIndex++
            item {
                val isVisible1 = "屏幕黑边遮挡测试".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible1,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible1) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index1 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        TestItemRow("屏幕黑边遮挡测试", "适配 Android 12+ 物理圆角", Icons.Default.ScreenshotMonitor) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            context.startActivity(Intent(context, TestActivity::class.java))
                        }
                    }
                }
            }

            // 2. 屏幕色彩与坏点测试
            val index2 = currentCardIndex++
            item {
                val isVisible2 = "屏幕色彩与坏点测试".contains(searchQuery, true) || "坏点".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible2,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible2) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index2 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("屏幕色彩与坏点测试", "纯色背景检测坏点与漏光", Icons.Default.FormatColorFill) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, ColorTestActivity::class.java))
                            }
                        }
                    }
                }
            }

            // 3. 屏幕灰阶测试
            val index3 = currentCardIndex++
            item {
                val isVisible3 = "屏幕灰阶测试".contains(searchQuery, true) || "灰阶".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible3,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible3) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index3 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("屏幕灰阶测试", "检测屏幕色彩过渡与暗部细节", Icons.Default.Gradient) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, GrayscaleTestActivity::class.java))
                            }
                        }
                    }
                }
            }

            // 4. 屏幕白平衡测试
            val index4 = currentCardIndex++
            item {
                val isVisible4 = "屏幕白平衡测试".contains(searchQuery, true) || "平衡".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible4,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible4) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index4 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("屏幕白平衡测试", "多级离散灰阶检测各亮度下的色偏情况", Icons.Default.Tonality) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, WhiteBalanceTestActivity::class.java))
                            }
                        }
                    }
                }
            }

            // 5. 屏幕彩条测试
            val index5 = currentCardIndex++
            item {
                val isVisible5 = "屏幕彩条测试".contains(searchQuery, true) || "彩条".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible5,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible5) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index5 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("屏幕彩条测试", "显示 EBU/SMPTE 标准电视信号测试图", Icons.Default.ViewColumn) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, ColorBarTestActivity::class.java))
                            }
                        }
                    }
                }
            }

            // 6. 屏幕 HDR 测试
            val index6 = currentCardIndex++
            item {
                val isVisible6 = "屏幕HDR检测".contains(searchQuery, true) || "屏幕 HDR 检测".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible6,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible6) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index6 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("屏幕 HDR 检测", "检测是否支持显示 HDR", Icons.Default.WbSunny) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, HDRTestActivity::class.java))
                            }
                        }
                    }
                }
            }

            // ==================== 【交互触控类测试】 ====================

            // 7. 屏幕触控测试
            val index7 = currentCardIndex++
            item {
                val isVisible7 = "屏幕触控测试".contains(searchQuery, true) || "断触".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible7,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible7) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index7 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("屏幕触控测试", "通过网格填充检测屏幕断触与死角", Icons.Default.Gesture) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, TouchTestActivity::class.java))
                            }
                        }
                    }
                }
            }

            // 8. 多指触控检测
            val index8 = currentCardIndex++
            item {
                val isVisible8 = "多指触控检测".contains(searchQuery, true) || "多点".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible8,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible8) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index8 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("多指触控检测", "检测屏幕支持的最大同时触控点数", Icons.Default.TouchApp) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, MultiTouchActivity::class.java))
                            }
                        }
                    }
                }
            }

            // 9. 触控采样率测试
            val index9 = currentCardIndex++
            item {
                val isVisible9 = "触控采样率测试".contains(searchQuery, true) || "采样率".contains(searchQuery, true) || "Hz".contains(searchQuery, true)
                AnimatedVisibility(
                    visible = isVisible9,
                    enter = EnterTransition.None,
                    exit = if (ThemeSettings.isAnimationEnabled) {
                        fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                    } else {
                        ExitTransition.None
                    }
                ) {
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(30f) }

                    LaunchedEffect(animationTrigger, searchQuery) {
                        if (isVisible9) {
                            if (!ThemeSettings.isAnimationEnabled) {
                                alpha.snapTo(1f)
                                offsetY.snapTo(0f)
                                return@LaunchedEffect
                            }

                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            delay(index9 * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            TestItemRow("触控采样率测试", "实时检测屏幕触控响应频率 (Hz)", Icons.Default.Speed) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, TouchSamplingActivity::class.java))
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(200.dp)) }
    }
        val backgroundColor = MaterialTheme.colorScheme.background

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = 60.dp)))
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

        // 原有的满血版级联动画逻辑
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
    val g2CardShape = remember(density) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width
                val h = size.height
                val radius = with(density) { 21.dp.toPx() }
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

    // 外观模式小卡片圆角
    val g2LargeShape = remember(density) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width
                val h = size.height
                val radius = with(density) { 21.dp.toPx() }
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

    // 校准车间按钮圆角
    val buttonG2Shape = remember(density) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width
                val h = size.height
                val radius = with(density) { 13.dp.toPx() }
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

@Composable
fun HapticSlider(l: String, c: Color, v: Float, interactionSource: MutableInteractionSource? = null, onV: (Float) -> Unit) {
    val view = LocalView.current
    var lastS by remember { mutableIntStateOf((v * 100).toInt()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (l.isNotEmpty()) { Text(text = l, color = c, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp)) }
        Slider(
            value = v,
            interactionSource = interactionSource ?: remember { MutableInteractionSource() },
            onValueChange = { val cur = (it * 100).toInt(); if (cur != lastS) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); lastS = cur }; onV(it) },
            colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AboutPage() {
    val isDark = when (ThemeSettings.darkModeState) {
        DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkModeConfig.LIGHT -> false
        DarkModeConfig.DARK -> true
    }
    val scrollState = rememberScrollState()
    val marketName = remember { DeviceUtils.getMarketName() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val backgroundBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1E172B), Color(0xFF2D1929), Color(0xFF161E2E)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFDE8E9), Color(0xFFE3E1FB), Color(0xFFD6E3F9)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    val cardG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 32.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }

    var isChangelogExp by remember { mutableStateOf(false) }
    val changelogArrowRotation by animateFloatAsState(targetValue = if (isChangelogExp) 180f else 0f, label = "changelogArrow")

    // === 更新状态管理 ===
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var hasNewVersion by GlobalUpdateState::hasNewVersion
    var latestVersionName by GlobalUpdateState::latestVersionName
    var latestChangelog by GlobalUpdateState::latestChangelog
    var checkButtonText by remember { mutableStateOf("检测更新") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        val screenHeight = this.maxHeight
        val context = LocalContext.current
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0.0"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ==================== 第一屏：主界面 ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenHeight)
            ) {
                // 顶部 Logo 部分
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val logoG2Shape = remember {
                        object : androidx.compose.ui.graphics.Shape {
                            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                                val path = Path()
                                val w = size.width; val h = size.height
                                val radius = with(density) { 24.dp.toPx() }
                                val p = (1.4f * radius).coerceAtMost(h / 2f)
                                val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                                return androidx.compose.ui.graphics.Outline.Generic(path)
                            }
                        }
                    }

                    Box(
                        Modifier
                            .size(110.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = logoG2Shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(70.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = "ScreenTester",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color(0xFFF8E7F0) else MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "$versionName | by Hydrogen",
                        fontSize = 16.sp,
                        color = if (isDark) Color.Gray.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f).heightIn(20.dp))

                // 设备信息卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 120.dp),
                    shape = cardG2Shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                ) {
                    Column(Modifier.padding(28.dp)) {
                        Text(
                            text = marketName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(24.dp))
                        DeviceInfoItem(label = "设备型号", value = Build.MODEL)
                        DeviceInfoItem(label = "Android 版本", value = Build.VERSION.RELEASE)
                        DeviceInfoItem(label = "OS 版本", value = DeviceUtils.getOSVersion())
                    }
                }
            }

            // ==================== 第二屏：开发者卡片 ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(y = (-102).dp)
                    .graphicsLayer {
                        val maxScroll = scrollState.maxValue.toFloat()
                        val currentScroll = scrollState.value.toFloat()
                        val progress = if (maxScroll > 0) {
                            (currentScroll / maxScroll * 1.2f).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        alpha = progress
                        translationY = (1f - progress) * 60.dp.toPx()
                    }
            ) {
                DeveloperProfileCard(isDark = isDark)
                Spacer(modifier = Modifier.height(16.dp))
                ProjectSourceCard(isDark = isDark)
                Spacer(modifier = Modifier.height(16.dp))

                // === 更新日志板块 ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardG2Shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    isChangelogExp = !isChangelogExp
                                }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("更新日志", modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            if (hasNewVersion && !isChangelogExp) {
                                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = changelogArrowRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        AnimatedVisibility(visible = isChangelogExp) {
                            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                val aboutButtonG2Shape = remember {
                                    object : androidx.compose.ui.graphics.Shape {
                                        override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                                            val path = Path()
                                            val w = size.width; val h = size.height
                                            val radius = with(density) { 13.dp.toPx() }
                                            val p = (1.4f * radius).coerceAtMost(h / 2f)
                                            val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                                            path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                                            return androidx.compose.ui.graphics.Outline.Generic(path)
                                        }
                                    }
                                }

                                // === 版本更新卡片 ===
                                val newVersionCardShape = remember {
                                    object : androidx.compose.ui.graphics.Shape {
                                        override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                                            val path = Path(); val w = size.width; val h = size.height
                                            val radius = with(density) { 20.dp.toPx() }
                                            val p = (1.4f * radius).coerceAtMost(h / 2f); val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                                            path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                                            return androidx.compose.ui.graphics.Outline.Generic(path)
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = hasNewVersion) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        shape = newVersionCardShape,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            Text(text = "发现新版本：$latestVersionName", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(8.dp))
                                            Text(text = latestChangelog, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(16.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(
                                                    onClick = {
                                                        hasNewVersion = false
                                                        UpdateManager.ignoreVersion(context, latestVersionName)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("忽略此版本", fontSize = 13.sp)
                                                }
                                                Button(
                                                    onClick = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/byHydrogen/ScreenTester/releases")))
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = aboutButtonG2Shape,
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                                ) {
                                                    Text("去下载", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 当前版本信息
                                Text(text = "版本 1.2.9.1", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "新增 关于页 开源项目地址卡片\n新增 关于页 更新日志板块\n新增 关于页 检查更新按钮\n优化 主页 顶部渐变效果\n优化 设置页 顶部渐变效果",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                // === 检测更新按钮 ===
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        if (!isCheckingUpdate && checkButtonText != "已是最新版本") {
                                            isCheckingUpdate = true
                                            checkButtonText = "正在检查..."
                                            UpdateManager.checkUpdate(context, isManual = true) { hasUpdate, version, changelog ->
                                                isCheckingUpdate = false
                                                if (hasUpdate && version != null) {
                                                    if (isVersionGreater(version, versionName)) {
                                                        hasNewVersion = true
                                                        latestVersionName = version
                                                        latestChangelog = changelog ?: ""
                                                        checkButtonText = "发现新版本"
                                                    } else {
                                                        checkButtonText = "已是最新版本"
                                                        scope.launch {
                                                            delay(2000)
                                                            if (checkButtonText == "已是最新版本") {
                                                                checkButtonText = "检测更新"
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    checkButtonText = "已是最新版本"
                                                    scope.launch {
                                                        delay(2000)
                                                        if (checkButtonText == "已是最新版本") {
                                                            checkButtonText = "检测更新"
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    shape = aboutButtonG2Shape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    AnimatedContent(
                                        targetState = checkButtonText,
                                        transitionSpec = {
                                            val enter = slideInVertically(
                                                initialOffsetY = { it },
                                                animationSpec = tween(durationMillis = 300)
                                            ) + fadeIn(animationSpec = tween(300))

                                            val exit = slideOutVertically(
                                                targetOffsetY = { -it },
                                                animationSpec = tween(durationMillis = 300)
                                            ) + fadeOut(animationSpec = tween(300))

                                            enter togetherWith exit
                                        },
                                        label = "UpdateButtonTextAnimation"
                                    ) { currentText ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (currentText == "正在检查...") {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(text = currentText, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // 历史更新按钮
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        context.startActivity(Intent(context, AllChangelogActivity::class.java))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = aboutButtonG2Shape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = "查看历史更新日志",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(130.dp))
            }
        }
    }
}

// 开发者卡片组件
@Composable
fun DeveloperProfileCard(isDark: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val cardG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 32.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardG2Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        val uri = android.net.Uri.parse("https://www.coolapk.com/u/18917701")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(brush = Brush.linearGradient(colors = listOf(color1, color2)), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "氢", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Hydrogen氢", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "前往酷安作者主页", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }

            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
        }
    }
}

// 项目地址卡片组件
@Composable
fun ProjectSourceCard(isDark: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val cardG2Shape = remember {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val path = Path()
                val w = size.width; val h = size.height
                val radius = with(density) { 32.dp.toPx() }
                val p = (1.4f * radius).coerceAtMost(h / 2f)
                val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                return androidx.compose.ui.graphics.Outline.Generic(path)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardG2Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        val uri = android.net.Uri.parse("https://github.com/byHydrogen/ScreenTester/")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(color = if (isDark) Color(0xFF2D333B) else Color(0xFF24292F), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // 使用自带的 Code 图标代表开源代码
                Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "开源项目地址", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "前往 GitHub 查阅源码", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }

            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun DeviceInfoItem(label: String, value: String) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsRadioItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)).clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(label, modifier = Modifier.weight(1f)); RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
fun TestItemRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onClick() }, color = Color.Transparent) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconG2Shape = remember {
                object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                        val path = Path()
                        val w = size.width; val h = size.height
                        val radius = with(density) { 12.dp.toPx() }
                        val p = (1.4f * radius).coerceAtMost(h / 2f)
                        val safeRadius = p / 1.4f; val c = 0.45f * safeRadius
                        path.moveTo(p, 0f); path.lineTo(w - p, 0f); path.cubicTo(w - c, 0f, w, c, w, p); path.lineTo(w, h - p); path.cubicTo(w, h - c, w - c, h, w - p, h); path.lineTo(p, h); path.cubicTo(c, h, 0f, h - c, 0f, h - p); path.lineTo(0f, p); path.cubicTo(0f, c, c, 0f, p, 0f); path.close()
                        return androidx.compose.ui.graphics.Outline.Generic(path)
                    }
                }
            }

            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, iconG2Shape), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(text = title, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

fun isVersionGreater(remoteVersion: String, localVersion: String): Boolean {
    val remoteParts = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val localParts = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val length = maxOf(remoteParts.size, localParts.size)

    for (i in 0 until length) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }
    return false
}