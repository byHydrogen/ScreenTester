package com.hydrogen.screentester

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                            // 从 设置/关于 Tab 切回主页时，清空搜索框并重新触发瀑布流浮出
                            clearSearch()
                            animationTrigger++
                        }
                        wasVisible = isVisibleNow
                        lastX = currentX
                    }
                }
        ) {
            item { Spacer(Modifier.statusBarsPadding()); Spacer(modifier = Modifier.height(80.dp)) }
            item { Text(text = "ScreenTester", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Spacer(modifier = Modifier.height(20.dp)) }
            item {
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
                            AnimatedVisibility(
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

                    // 视图切换按钮（搜索框聚焦时隐藏，带动画）
                    AnimatedVisibility(
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
                AnimatedVisibility(
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

                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                        ModalBottomSheet(
                            onDismissRequest = { showUpdateDialog = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = systemCornerRadius, topEnd = systemCornerRadius),
                            sheetState = sheetState,
                            scrimColor = Color.Black.copy(alpha = 0.5f),
                            dragHandle = {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,  // 禁用触摸反馈
                                            onClick = {}
                                        )
                                )
                            }
                        ) {
                            // 防止内容区域滑动触发弹窗收起，只有拖动手柄能控制弹窗
                            val sheetNestedScrollConnection = remember {
                                object : NestedScrollConnection {
                                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                        return if (available.y > 0) available.copy(x = 0f) else Offset.Zero
                                    }
                                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                        return if (available.y > 0) available.copy(x = 0f) else Velocity.Zero
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .nestedScroll(sheetNestedScrollConnection)
                            ) {
                                // 标题
                                Text(
                                    text = "发现新版本 ${GlobalUpdateState.latestVersionName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 分隔线
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 更新日志（支持 Markdown）
                                MarkdownText(
                                    text = GlobalUpdateState.latestChangelog,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                                    linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                                    onLinkClick = { showLinkDialog = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 250.dp)
                                        .verticalScroll(rememberScrollState())
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // 按钮
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 稍后和忽略此版本
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 稍后
                                        Button(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                scope.launch {
                                                    sheetState.hide()
                                                    showUpdateDialog = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            shape = G2Shapes.button,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Text("稍后", fontWeight = FontWeight.Bold)
                                        }

                                        // 忽略此版本
                                        Button(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                scope.launch {
                                                    sheetState.hide()
                                                    showUpdateDialog = false
                                                    GlobalUpdateState.hasNewVersion = false
                                                    UpdateManager.ignoreVersion(context, GlobalUpdateState.latestVersionName)
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            shape = G2Shapes.button,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Text("忽略此版本", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // 去下载
                                    Button(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            scope.launch {
                                                sheetState.hide()
                                                showUpdateDialog = false
                                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/byHydrogen/ScreenTester/releases"))
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = G2Shapes.button,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("去下载", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    // 链接确认弹窗
                    if (showLinkDialog != null) {
                        LinkConfirmDialog(
                            url = showLinkDialog ?: "",
                            onDismiss = { showLinkDialog = null }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ==================== 【视觉显示类测试】 ====================

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

            // 根据视图模式显示测试项（统一使用懒加载优化）
            if (ThemeSettings.isGridView) {
                // 网格模式：每行显示2个测试项
                val chunkedItems = testItems.mapIndexed { index, item ->
                    Triple(index, item.first, item.second)
                }.chunked(2)

                items(chunkedItems.size) { rowIndex ->
                    val rowItems = chunkedItems[rowIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for ((index, itemData, activityClass) in rowItems) {
                            val (title, subtitle, icon) = itemData
                            val keywords = searchKeywords[index]
                            val isVisible = keywords.any { it.contains(searchQuery, true) }

                            // 根据动画开关决定是否创建动画状态
                            if (ThemeSettings.isAnimationEnabled && isVisible) {
                                // 动画开启：创建动画状态
                                AnimatedVisibility(
                                    visible = isVisible,
                                    enter = EnterTransition.None,
                                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val alpha = remember { Animatable(0f) }
                                    val offsetY = remember { Animatable(30f) }

                                    LaunchedEffect(animationTrigger, searchQuery) {
                                        if (alpha.value > 0.1f) {
                                            launch { alpha.animateTo(0f, tween(150)) }
                                            offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                                        } else {
                                            offsetY.snapTo(30f)
                                        }

                                        delay(index * 40L)
                                        launch { alpha.animateTo(1f, tween(300)) }
                                        offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                                    }
                                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                                        TestItemGrid(title, subtitle, icon) {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            context.startActivity(Intent(context, activityClass))
                                        }
                                    }
                                }
                            } else {
                                // 动画关闭或不可见：直接显示，不创建动画状态
                                if (isVisible) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TestItemGrid(title, subtitle, icon) {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            context.startActivity(Intent(context, activityClass))
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        // 如果这一行只有一个 item，添加一个空的 Spacer 占位
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                // 列表模式：使用 items 统一懒加载
                items(testItems.size) { index ->
                    val (itemData, activityClass) = testItems[index]
                    val (title, subtitle, icon) = itemData
                    val keywords = searchKeywords[index]
                    val isVisible = keywords.any { it.contains(searchQuery, true) }

                    // 根据动画开关决定是否创建动画状态
                    if (ThemeSettings.isAnimationEnabled && isVisible) {
                        // 动画开启：创建动画状态
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = EnterTransition.None,
                            exit = fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { 30 }
                        ) {
                            val alpha = remember { Animatable(0f) }
                            val offsetY = remember { Animatable(30f) }

                            LaunchedEffect(animationTrigger, searchQuery) {
                                if (alpha.value > 0.1f) {
                                    launch { alpha.animateTo(0f, tween(150)) }
                                    offsetY.animateTo(30f, tween(150, easing = FastOutSlowInEasing))
                                } else {
                                    offsetY.snapTo(30f)
                                }

                                delay(index * 40L)
                                launch { alpha.animateTo(1f, tween(300)) }
                                offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                            }
                            Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value; this.translationY = offsetY.value }) {
                                Column {
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    TestItemRow(title, subtitle, icon) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        context.startActivity(Intent(context, activityClass))
                                    }
                                }
                            }
                        }
                    } else {
                        // 动画关闭或不可见：直接显示，不创建动画状态
                        if (isVisible) {
                            Column {
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                TestItemRow(title, subtitle, icon) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    context.startActivity(Intent(context, activityClass))
                                }
                            }
                        }
                    }
                }
            }

            // 搜索无结果提示
            item {
                if (searchQuery.isNotEmpty()) {
                    val hasAnyResult = searchKeywords.any { keywords ->
                        keywords.any { it.contains(searchQuery, true) }
                    }

                    // 使用动画状态来控制显示
                    val showNoResult = remember { mutableStateOf(false) }
                    LaunchedEffect(hasAnyResult, searchQuery) {
                        if (!hasAnyResult) {
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
fun TestItemRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onClick() }, color = Color.Transparent) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconG2Shape = G2Shapes.icon

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

@Composable
fun TestItemGrid(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val iconG2Shape = G2Shapes.gridCard

    val iconShape = G2Shapes.icon

    Card(
        modifier = Modifier
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
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, iconShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
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
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Start,  // 左对齐
                    maxLines = 2,
                    lineHeight = 14.sp
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
