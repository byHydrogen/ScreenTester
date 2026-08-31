package com.hydrogen.screentester

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 主页卡片数据模型：originalIndex 为全量列表中的稳定下标，作为 LazyVerticalGrid 的 key（animateItem 依赖稳定 key）
private class HomeCard(
    val originalIndex: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val activityClass: Class<*>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage() {
    val context = LocalContext.current
    val view = LocalView.current
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
    val textBaselineShift = remember { Animatable(0f) }

    // 提示文字（Placeholder）的淡出动画
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (isFocused || textFieldValue.text.isNotEmpty()) 0f else 0.6f,
        animationSpec = tween(durationMillis = 250),
        label = "placeholderAlpha"
    )

    // 精准计算当前帧新文字的实际透明度和上浮偏移
    val currentAlpha = if (isNewCharFirstFrame) 0f else textAlpha.value
    val currentShift = if (isNewCharFirstFrame) -0.3f else textBaselineShift.value
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val displayTextFieldValue = remember(textFieldValue, animStartIndex, currentAlpha, currentShift, onSurfaceColor) {
        val text = textFieldValue.text
        val annotatedString = buildAnnotatedString {
            if (animStartIndex in 0..text.length) {
                withStyle(SpanStyle(color = onSurfaceColor)) {
                    append(text.substring(0, animStartIndex))
                }
                withStyle(SpanStyle(color = onSurfaceColor.copy(alpha = currentAlpha), baselineShift = androidx.compose.ui.text.style.BaselineShift(currentShift))) {
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

    // 动画状态核心管理
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

    // 清空搜索框的函数
    val clearSearch = {
        searchQuery = ""
        textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
        previousText = ""
        animStartIndex = -1
        isNewCharFirstFrame = false
        animJob?.cancel()
    }
    val searchBoxG2Shape = G2Shapes.searchBox
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current

    // 屏幕宽度检测（用于判断页面是否可见）
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    var wasVisible by remember { mutableStateOf(false) }
    var lastX by remember { mutableFloatStateOf(Float.NaN) }

    // 测试项数据列表
    val testItems = listOf(
        Triple("屏幕黑边遮挡测试", "适配 Android 12+ 物理圆角", Icons.Default.ScreenshotMonitor) to TestActivity::class.java,
        Triple("屏幕色彩与坏点测试", "纯色背景检测坏点与漏光", Icons.Default.FormatColorFill) to ColorTestActivity::class.java,
        Triple("屏幕灰阶测试", "检测屏幕色彩过渡与暗部细节", Icons.Default.Gradient) to GrayscaleTestActivity::class.java,
        Triple("屏幕白平衡测试", "多级离散灰阶检测各亮度下的色偏情况", Icons.Default.Tonality) to WhiteBalanceTestActivity::class.java,
        Triple("屏幕彩条测试", "显示 EBU/SMPTE 标准电视信号测试图", Icons.Default.ViewColumn) to ColorBarTestActivity::class.java,
        Triple("屏幕 HDR 检测", "检测是否支持显示 HDR", Icons.Default.WbSunny) to HDRTestActivity::class.java,
        Triple("屏幕触控测试", "通过网格填充检测屏幕断触与死角", Icons.Default.Gesture) to TouchTestActivity::class.java,
        Triple("多指触控检测", "检测屏幕支持的最大同时触控点数", Icons.Default.TouchApp) to MultiTouchActivity::class.java,
        Triple("触控采样率测试", "实时检测屏幕触控响应频率 (Hz)", Icons.Default.Speed) to TouchSamplingActivity::class.java
    )

    // 搜索关键词映射
    val searchKeywords = listOf(
        listOf("屏幕黑边遮挡测试"),
        listOf("屏幕色彩与坏点测试", "坏点"),
        listOf("屏幕灰阶测试", "灰阶"),
        listOf("屏幕白平衡测试", "平衡"),
        listOf("屏幕彩条测试", "彩条"),
        listOf("屏幕HDR检测", "屏幕 HDR 检测"),
        listOf("屏幕触控测试", "断触"),
        listOf("多指触控检测", "多点"),
        listOf("触控采样率测试", "采样率", "Hz")
    )

    // 卡片模型与过滤结果：搜索时驱动网格重排（key 为稳定原始下标）
    val allCards = testItems.mapIndexed { index, item ->
        HomeCard(index, item.first.first, item.first.second, item.first.third, item.second)
    }
    val visibleCards = allCards.filter { card ->
        searchKeywords[card.originalIndex].any { it.contains(searchQuery, true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (ThemeSettings.isGridView) 2 else 1),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .onGloballyPositioned { coords ->
                    val currentX = coords.positionInWindow().x
                    if (currentX != lastX) {
                        val isVisibleNow = currentX > -screenWidthPx / 2 && currentX < screenWidthPx / 2
                        if (isVisibleNow && !wasVisible) {
                            // 从 设置/关于 Tab 切回主页时，清空搜索框并重新触发瀑布流浮出
                            clearSearch()
                            animationTrigger++
                        }
                        wasVisible = isVisibleNow
                        lastX = currentX
                    }
                }
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.statusBarsPadding())
                    Spacer(modifier = Modifier.height(80.dp))
                    Text(text = "ScreenTester", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(20.dp))
                    // 使用 Box 包裹，实现搜索框变长动画
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 搜索框宽度动画
                        val windowInfo = LocalWindowInfo.current
                        val screenWidthPx = windowInfo.containerSize.width
                        val density = LocalDensity.current
                        val screenWidthDp = with(density) { screenWidthPx.toDp() }
                        val searchBoxWidth by animateDpAsState(
                            targetValue = if (isFocused) {
                                // 聚焦时：占据整个宽度
                                screenWidthDp - 48.dp // 减去左右 padding
                            } else {
                                // 未聚焦时：占据部分宽度（留出按钮空间）
                                screenWidthDp - 48.dp - 56.dp - 12.dp // 减去 padding、按钮宽度、间距
                            },
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = 300f
                            ),
                            label = "searchBoxWidth"
                        )

                        Surface(
                            modifier = Modifier
                                .width(searchBoxWidth)
                                .height(56.dp),
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
                                                if (ThemeSettings.isAnimationEnabled) {
                                                    animJob = scope.launch {
                                                        textAlpha.snapTo(0f)
                                                        textBaselineShift.snapTo(-0.3f)
                                                        isNewCharFirstFrame = false
                                                        launch { textAlpha.animateTo(1f, tween(250)) }
                                                        textBaselineShift.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                                    }
                                                } else {
                                                    isNewCharFirstFrame = false
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

                                // 清除按钮
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = searchQuery.isNotEmpty(),
                                    enter = fadeIn(tween(200)),
                                    exit = fadeOut(tween(200))
                                ) {
                                    IconButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            clearSearch()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "清除搜索",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 视图切换按钮（搜索框聚焦时隐藏）
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isFocused,
                            enter = fadeIn(tween(250)),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(searchBoxG2Shape)  // 裁切阴影到按钮形状
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        ThemeSettings.saveGridViewConfig(context, !ThemeSettings.isGridView)
                                    },
                                shape = searchBoxG2Shape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(
                                        targetState = ThemeSettings.isGridView,
                                        transitionSpec = {
                                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                        },
                                        label = "viewModeIcon"
                                        ) { isGrid ->
                                        Icon(
                                            imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                            contentDescription = if (isGrid) "切换到列表视图" else "切换到网格视图",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 更新提示横幅
                    androidx.compose.animation.AnimatedVisibility(
                        visible = GlobalUpdateState.hasNewVersion,
                        enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                    ) {
                        var showUpdateDialog by remember { mutableStateOf(false) }
                        var showLinkDialog by remember { mutableStateOf<String?>(null) }
                        val bannerG2Shape = G2Shapes.card

                        // 更新横幅
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(bannerG2Shape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showUpdateDialog = true
                                },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = bannerG2Shape
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "发现新版本：${GlobalUpdateState.latestVersionName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "点击查看详情",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        }

                        val systemCornerRadius = getSystemCornerRadius()

                        // 更新弹窗
                        if (showUpdateDialog) {
                            UpdateSheetDialog(onDismiss = { showUpdateDialog = false })
                        }
                    }

                    // QQ加群横幅（首次进入主页显示，仅一次，30秒未点击自动消失）
                    val qqPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    var showQQBanner by remember { mutableStateOf(!qqPrefs.getBoolean("qq_group_dialog_shown", false)) }
                    var showQQDialog by remember { mutableStateOf(false) }

                    val markQQShown = {
                        showQQBanner = false
                        qqPrefs.edit().putBoolean("qq_group_dialog_shown", true).apply()
                    }

                    LaunchedEffect(showQQBanner) {
                        if (showQQBanner) {
                            delay(30_000)
                            markQQShown()
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showQQBanner,
                        enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                    ) {
                        Column {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(G2Shapes.card)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        showQQDialog = true
                                    },
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = G2Shapes.card
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QuestionAnswer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "加入QQ交流群",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "群号：1035224343",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    if (showQQDialog) {
                        QQGroupDialog(onDismiss = { showQQDialog = false; markQQShown() })
                    }
                }
            }


            // 测试卡片：animateItem 管平滑归位与淡出，手写瀑布管入场
            itemsIndexed(
                items = visibleCards,
                key = { _, card -> "card_${card.originalIndex}" },
                span = { _, _ -> if (ThemeSettings.isGridView) GridItemSpan(1) else GridItemSpan(maxLineSpan) }
            ) { visibleIndex, card ->
                if (!ThemeSettings.isAnimationEnabled) {
                    // 动画关闭：直接渲染
                    Box(modifier = Modifier.fillMaxWidth()) {
                        HomeCardItem(card) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            context.startActivity(Intent(context, card.activityClass))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = null,
                                placementSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = 350f,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                ),
                                fadeOutSpec = tween(200, easing = FastOutSlowInEasing) // 被搜索过滤时淡出
                            )
                            .clipToBounds()
                    ) {
                        val alpha = remember { Animatable(0f) }
                        val offsetY = remember { Animatable(30f) }

                        LaunchedEffect(animationTrigger) {
                            if (alpha.value > 0.1f) {
                                launch { alpha.animateTo(0f, tween(150)) }
                                offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                            } else {
                                offsetY.snapTo(30f)
                            }

                            // 用过滤后的序号做瀑布延迟，避免搜索结果出现空拍
                            delay(visibleIndex * 40L)
                            launch { alpha.animateTo(1f, tween(300)) }
                            offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                        }
                        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                            HomeCardItem(card) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                context.startActivity(Intent(context, card.activityClass))
                            }
                        }
                    }
                }
            }

            // 搜索无结果提示
            if (searchQuery.isNotEmpty()) {
            item(key = "no_result", span = { GridItemSpan(maxLineSpan) }) {
                    val isNoResult = visibleCards.isEmpty()

                    // 使用动画状态来控制显示
                    val showNoResult = remember { mutableStateOf(false) }
                    LaunchedEffect(isNoResult, searchQuery) {
                        if (isNoResult) {
                            if (ThemeSettings.isAnimationEnabled) {
                                // 动画开启：延迟一小段时间再显示，确保动画能播放
                                delay(50)
                            }
                            showNoResult.value = true
                        } else {
                            showNoResult.value = false
                        }
                    }

                    // 根据动效开关决定是否使用动画
                    if (ThemeSettings.isAnimationEnabled) {
                        AnimatedVisibility(
                            visible = showNoResult.value,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ),
                            exit = fadeOut(tween(200)) + slideOutVertically(
                                targetOffsetY = { it / 2 },
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            )
                        ) {
                            NoResultContent(searchQuery)
                        }
                    } else {
                        // 动画关闭：直接显示，无动画
                        if (showNoResult.value) {
                            NoResultContent(searchQuery)
                        }
                    }
                }
            }

            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(200.dp)) }
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

// 按视图模式渲染卡片（动效开=变形卡连续过渡；关=直接切换）
@Composable
private fun HomeCardItem(card: HomeCard, onClick: () -> Unit) {
    if (!ThemeSettings.isAnimationEnabled) {
        // 动效关闭：直接切换
        if (ThemeSettings.isGridView) {
            TestItemGrid(card.title, card.subtitle, card.icon, onClick)
        } else {
            TestItemRow(card.title, card.subtitle, card.icon, onClick)
        }
    } else {
        MorphingHomeCard(card, onClick)
    }
}

// 网格/列表共用的变形卡
@Composable
private fun MorphingHomeCard(card: HomeCard, onClick: () -> Unit) {
    val isGrid = ThemeSettings.isGridView
    val density = LocalDensity.current
    var textBlockHeightDp by remember { mutableStateOf(24.dp) } // 文字块实测高度（首帧用估算值）

    val floatSpec = tween<Float>(250, easing = FastOutSlowInEasing)
    val fontSpec = tween<Float>(250, easing = FastOutSlowInEasing) // 字号随形态连续缩放
    val dpSpec = tween<Dp>(250, easing = FastOutSlowInEasing)
    val heightSpec = spring<Dp>(dampingRatio = 0.75f, stiffness = 400f) // 弹性高度，提供 Q 弹手感
    val widthSpec = tween<Dp>(250, easing = FastOutSlowInEasing)

    val cardHeight by animateDpAsState(if (isGrid) 150.dp else 84.dp, heightSpec, label = "cardHeight")
    val cardBg by animateColorAsState(
        if (isGrid) lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant, 0.5f)
        else MaterialTheme.colorScheme.background,
        tween(250, easing = FastOutSlowInEasing),
        label = "cardBg"
    )
    val iconBoxSize by animateDpAsState(if (isGrid) 48.dp else 52.dp, dpSpec, label = "iconBox")
    val iconSize by animateDpAsState(if (isGrid) 24.dp else 28.dp, dpSpec, label = "icon")
    val titleSize by animateFloatAsState(if (isGrid) 13f else 16f, fontSpec, label = "titleSize")
    val subtitleSize by animateFloatAsState(if (isGrid) 11f else 13f, fontSpec, label = "subtitleSize")
    val textStart by animateDpAsState(if (isGrid) 16.dp else 84.dp, dpSpec, label = "textStart")
    val textEnd by animateDpAsState(if (isGrid) 16.dp else 56.dp, dpSpec, label = "textEnd")
    val textOffsetY by animateDpAsState(if (isGrid) 59.dp - textBlockHeightDp / 2 else 0.dp, dpSpec, label = "textOffsetY")
    val spacerHeight by animateDpAsState(if (isGrid) 4.dp else 0.dp, dpSpec, label = "spacerH")
    val chevronAlpha by animateFloatAsState(if (isGrid) 0f else 1f, floatSpec, label = "chevron")

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 卡片宽度连续过渡
        val cardWidth by animateDpAsState(maxWidth, widthSpec, label = "cardWidth")

        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true) // 超宽时靠左放置而非居中，避免图标被挤出屏幕左侧
                .width(cardWidth)
                .height(cardHeight)
                .clip(G2Shapes.gridCard)
                .background(cardBg)
                .clickable { onClick() }
    ) {
        // 文字块
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = textStart, end = textEnd)
                .onSizeChanged { with(density) { textBlockHeightDp = it.height.toDp() } }
                .offset(y = textOffsetY)
        ) {
            Text(
                text = card.title,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(spacerHeight))
            Text(
                text = card.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = subtitleSize.sp,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
        // 图标
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 16.dp)

                .size(iconBoxSize)
                .background(MaterialTheme.colorScheme.primaryContainer, G2Shapes.icon),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = card.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(iconSize)
            )
        }
        // 列表态右箭头
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f * chevronAlpha),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .graphicsLayer { this.alpha = chevronAlpha }
        )
        }
    }
}

@Composable
fun TestItemRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, cardModifier: Modifier = Modifier, titleModifier: Modifier = Modifier, subtitleModifier: Modifier = Modifier, iconBoxModifier: Modifier = Modifier, iconModifier: Modifier = Modifier) {
    Surface(modifier = cardModifier.fillMaxWidth().clip(G2Shapes.gridCard).clickable { onClick() }, color = Color.Transparent) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconG2Shape = G2Shapes.icon

            Box(iconBoxModifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, iconG2Shape), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = iconModifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(text = title, fontWeight = FontWeight.Bold, modifier = titleModifier)
                Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = subtitleModifier)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

@Composable
fun TestItemGrid(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, cardModifier: Modifier = Modifier, titleModifier: Modifier = Modifier, subtitleModifier: Modifier = Modifier, iconBoxModifier: Modifier = Modifier, iconModifier: Modifier = Modifier) {
    val iconG2Shape = G2Shapes.gridCard

    val iconShape = G2Shapes.icon

    Card(
        modifier = cardModifier
            .fillMaxWidth()
            .height(150.dp)  // 固定高度，确保所有卡片一致
            .clip(iconG2Shape)  // 裁切阴影到卡片形状
            .clickable { onClick() },
        shape = iconG2Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                iconBoxModifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, iconShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = iconModifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start  // 左对齐
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Start,  // 左对齐
                    maxLines = 2,
                    modifier = titleModifier
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Start,  // 左对齐
                    maxLines = 2,
                    lineHeight = 14.sp,
                    modifier = subtitleModifier
                )
            }
        }
    }
}

// 搜索无结果提示内容组件
@Composable
fun NoResultContent(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "没有找到 \"$searchQuery\" 相关的测试项",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheetDialog(
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemCornerRadius = getSystemCornerRadius()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadState = GlobalUpdateState.downloadState
    DownloadProgressPoller(downloadState)

    // Android 13+ 通知权限：开始下载前请求一次
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startDownloadWithPermission(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        downloadState.start(context, url, "ScreenTester_${GlobalUpdateState.latestVersionName}.apk")
    }
    // 数字上浮动画：拆分十位和个位
    val downloadPercent by animateFloatAsState(
        targetValue = downloadState.progress,
        animationSpec = tween(200),
        label = "dlProgressAnim"
    )
    var showLinkDialog by remember { mutableStateOf<String?>(null) }
    var showDownloadConfirm by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = systemCornerRadius, topEnd = systemCornerRadius),
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp).width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        val sheetNestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (available.y > 0) available.copy(x = 0f) else Offset.Zero
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                    if (available.y > 0) available.copy(x = 0f) else Velocity.Zero
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                .nestedScroll(sheetNestedScrollConnection)
        ) {
            Text(
                text = "发现新版本 ${GlobalUpdateState.latestVersionName}",
                fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))
            MarkdownText(
                text = GlobalUpdateState.latestChangelog,
                fontSize = 14.sp, lineHeight = 20.sp,
                textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                onLinkClick = { showLinkDialog = it },
                modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).verticalScroll(rememberScrollState())
            )
            // 浏览器下载
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(G2Shapes.button)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateManager.releasePageUrl())))
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "浏览器下载",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            // 稍后 + 忽略
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch { sheetState.hide(); onDismiss() }
                    },
                    modifier = Modifier.weight(1f).height(48.dp), shape = G2Shapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { Text("稍后", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch { sheetState.hide(); onDismiss() }
                        GlobalUpdateState.hasNewVersion = false
                        UpdateManager.ignoreVersion(context, GlobalUpdateState.latestVersionName)
                    },
                    modifier = Modifier.weight(1f).height(48.dp), shape = G2Shapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { Text("忽略此版本", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(12.dp))
            // 下载按钮
            val isDownloading = downloadState.status == DownloadStatus.Downloading
            val isPaused = downloadState.status == DownloadStatus.Paused
            val isError = downloadState.status == DownloadStatus.Error
            val dlModifier = when {
                isPaused -> Modifier
                    .fillMaxWidth().height(48.dp).clip(G2Shapes.button)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                startDownloadWithPermission(GlobalUpdateState.latestDownloadUrl ?: UpdateManager.releasePageUrl())
                            },
                            onLongPress = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                downloadState.cancel(context)
                            }
                        )
                    }
                isDownloading -> Modifier
                    .fillMaxWidth().height(48.dp).clip(G2Shapes.button)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        downloadState.pause()
                    }
                else -> Modifier
                    .fillMaxWidth().height(48.dp).clip(G2Shapes.button)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        when (downloadState.status) {
                            DownloadStatus.Idle, DownloadStatus.Error -> {
                                val url = GlobalUpdateState.latestDownloadUrl ?: UpdateManager.releasePageUrl()
                                val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                                if (cm?.isActiveNetworkMetered == true) {
                                    pendingDownloadUrl = url; showDownloadConfirm = true
                                } else {
                                    startDownloadWithPermission(url)
                                }
                            }
                            DownloadStatus.Done -> downloadState.install(context, "ScreenTester_${GlobalUpdateState.latestVersionName}.apk")
                            else -> { }
                        }
                    }
            }
            Box(modifier = dlModifier, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    AnimatedContent(
                        targetState = when (downloadState.status) {
                            DownloadStatus.Downloading -> "downloading"
                            DownloadStatus.Done -> "done"
                            DownloadStatus.Paused -> "paused"
                            DownloadStatus.Error -> "error"
                            else -> "idle"
                        },
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "downloadLabel"
                    ) { state ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (state) {
                                "idle" -> Text("下载", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                "error" -> Text("下载失败", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                "downloading" -> {
                                    Text("已下载 ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    val pct = (downloadState.progress * 100).toInt()
                                    AnimatedContent(
                                        targetState = pct,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                (slideInVertically { it / 4 } + fadeIn(tween(200))) togetherWith
                                                (slideOutVertically { -it / 4 } + fadeOut(tween(200)))
                                            } else if (targetState < initialState) {
                                                (slideInVertically { -it / 4 } + fadeIn(tween(200))) togetherWith
                                                (slideOutVertically { it / 4 } + fadeOut(tween(200)))
                                            } else {
                                                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                            }
                                        },
                                        label = "pctDigit"
                                    ) { n ->
                                        Text("$n", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    Text("%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                                "done" -> Text("安装", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                "paused" -> Text("已暂停", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    if (isDownloading) {
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(
                            progress = { downloadPercent },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // 流量下载确认弹窗
    if (showDownloadConfirm) {
        val confirmSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDownloadConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = systemCornerRadius, topEnd = systemCornerRadius),
            sheetState = confirmSheetState,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            dragHandle = {
                Box(Modifier.padding(vertical = 12.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("流量提醒", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("当前为移动网络，是否继续下载？", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch { confirmSheetState.hide(); showDownloadConfirm = false }
                    }, modifier = Modifier.weight(1f).height(48.dp), shape = G2Shapes.button) { Text("取消") }
                    Button(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch { confirmSheetState.hide(); showDownloadConfirm = false }
                        startDownloadWithPermission(pendingDownloadUrl)
                    }, modifier = Modifier.weight(1f).height(48.dp), shape = G2Shapes.button) { Text("继续", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 链接确认弹窗
    if (showLinkDialog != null) {
        LinkConfirmDialog(url = showLinkDialog ?: "", onDismiss = { showLinkDialog = null })
    }
}
