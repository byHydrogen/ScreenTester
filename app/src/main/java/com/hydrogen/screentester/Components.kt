package com.hydrogen.screentester

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.core.content.edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@Composable
fun HapticSlider(
    l: String, c: Color, v: Float,
    interactionSource: MutableInteractionSource? = null,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    steps: Int = 0,
    onV: (Float) -> Unit
) {
    val view = LocalView.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val totalPositions = if (steps > 0) steps + 2 else 100
    var lastS by remember { mutableIntStateOf((v * totalPositions).toInt()) }

    // 检测拖动开始/结束
    LaunchedEffect(source) {
        launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> onDragStart?.invoke()
                    is DragInteraction.Stop, is DragInteraction.Cancel -> onDragEnd?.invoke()
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (l.isNotEmpty()) { Text(text = l, color = c, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp)) }
        Slider(
            value = v,
            interactionSource = source,
            onValueChange = { val cur = (it * totalPositions).toInt(); if (cur != lastS) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); lastS = cur }; onV(it) },
            colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c),
            modifier = Modifier.weight(1f),
            steps = steps
        )
    }
}

// 弹窗窗口的装饰处理：关窗口自带动画、关系统压暗层（压暗由自绘遮罩负责）、系统栏透明沉浸。
// statusBarColor/navigationBarColor 在 Android 15（API 35）标记弃用——edge-to-edge 下系统不再绘制
// 色条、setter 无效果；但 API < 35 的弹窗窗口仍靠它们做透明沉浸，保留并压制弃用告警
@Suppress("DEPRECATION")
private fun applyDialogWindowDecor(window: android.view.Window) {
    window.setWindowAnimations(-1)
    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(0f)
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

/** 弹窗被把手拉长的高度（px）；内容可据此增高（如"长日志区"） */
val LocalSheetExpandPx = compositionLocalOf { 0f }

/**
 * 自绘的模态底部弹窗容器，替代 M3 ModalBottomSheet：
 * 观感对齐（遮罩、顶部圆角、把手、上滑入场/下滑退场），但去掉半展开等易出问题的部分。
 * 交互：把手居中、按住放大、按住把手可上下拖动（拖过阈值松手关闭，否则回弹）；
 * 传入 [expandMaxPx] 后把手还可拉长内容（上拖变长、下拖变短，到 0 后继续下拖才是关闭手势）；
 * 预测性返回跟手（手势进行中弹窗下移、遮罩变淡，取消回弹、确认关闭；无预测性的设备退化为按返回直接关闭）。
 * 退场动画播完才回调 [onDismissRequest]，调用方直接把"移除弹窗"的代码写在里面即可；
 * 内容里需要主动关闭时（按钮），调用 content 回调参数 [onClose]。
 */
@Composable
fun G2BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    scrimColor: Color = Color.Black.copy(alpha = 0.5f),
    sheetMaxWidth: Dp = 640.dp,
    dragHandle: (@Composable () -> Unit)? = null,
    /** 把手可拉长内容的上限（px）；null = 不可拉长（把手只用于关闭手势） */
    expandMaxPx: Float? = null,
    content: @Composable ColumnScope.(onClose: () -> Unit) -> Unit
) {
    // shown=false → 播放下滑退场动画 → 动画结束才真正回调 onDismissRequest
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 拖拽/预测返回导致的下移位移（px，向下为正），换算关闭进度时用到 sheet 高度
    val dragOffset = remember { Animatable(0f) }
    // 把手拉长的当前高度（px）
    var expandPx by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { shown = true }
    fun requestClose() {
        if (!closing) {
            closing = true
            shown = false
        }
    }
    // 退场动画（230ms）播完再移除弹窗，遮罩淡出时长与退场对齐，避免窗口消失瞬间压暗跳变
    LaunchedEffect(closing) {
        if (closing) {
            delay(260)
            onDismissRequest()
        }
    }
    // 关闭进度：让遮罩随拖拽/预测返回一起变淡
    val dismissFraction = if (sheetHeightPx > 0f) (dragOffset.value / sheetHeightPx).coerceIn(0f, 1f) else 0f

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // 必须在 Dialog 内容里取 LocalView 才能拿到弹窗自己的窗口：
        // 禁用窗口自带动画和系统压暗层（FLAG_DIM_BEHIND）——压暗由下方自绘遮罩负责。
        // 若留着系统压暗层，它不跟随任何动画，窗口销毁瞬间直接消失 = 压暗"最后闪一下"
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { applyDialogWindowDecor(it) }
        }
        val scrimAlpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(230, easing = FastOutSlowInEasing),
            label = "sheetScrimAlpha"
        )
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：点按关闭；随拖拽/预测返回的关闭进度同步变淡
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha * (1f - dismissFraction) }
                    .background(scrimColor)
                    .pointerInput(Unit) { detectTapGestures { requestClose() } }
            )
            AnimatedVisibility(
                visible = shown,
                modifier = modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(230, easing = FastOutSlowInEasing)) { it }
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = sheetMaxWidth)
                        .fillMaxWidth()
                        .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                        .graphicsLayer { translationY = dragOffset.value }
                        .clip(shape)
                        .background(containerColor)
                        // Text/空白区不消费点击
                        .pointerInput(Unit) { detectTapGestures { } }
                        .navigationBarsPadding()
                ) {
                    if (dragHandle != null) {
                        // 把手：居中放置；按住放大（按下到抬起全程保持，拖动不缩小）；
                        // 按住可上下拖动弹窗（下拖过阈值松手关闭，否则回弹）
                        var handlePressed by remember { mutableStateOf(false) }
                        val handleScale by animateFloatAsState(
                            targetValue = if (handlePressed) 1.5f else 1f,
                            animationSpec = tween(150),
                            label = "sheetHandleScale"
                        )
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { scaleX = handleScale; scaleY = handleScale }
                                    .pointerInput(Unit) {
                                        val dismissThreshold = 120.dp.toPx()
                                        val minOffset = -60.dp.toPx()  // 允许稍微往上拖一点
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            handlePressed = true
                                            var dragging = false
                                            var overSlop = 0f
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.pressed } ?: break
                                                if (!dragging) {
                                                    overSlop += change.positionChange().y
                                                    if (kotlin.math.abs(overSlop) > viewConfiguration.touchSlop) dragging = true
                                                }
                                                if (dragging) {
                                                    val delta = change.positionChange().y
                                                    change.consume()
                                                    val cap = expandMaxPx
                                                    val down = dragOffset.value
                                                    if (cap != null && down > 0f) {
                                                        // 弹窗已被下拖：无论拖向哪边，先把位移收回（弹窗先回原位）
                                                        scope.launch {
                                                            dragOffset.snapTo((down + delta).coerceAtLeast(0f))
                                                        }
                                                    } else if (cap != null && (expandPx > 0f || delta < 0f)) {
                                                        // 上拖不封顶（空间够就一直增高，松手吸附回上限）；已拉长时下拖变短
                                                        expandPx = (expandPx - delta).coerceAtLeast(0f)
                                                    } else {
                                                        scope.launch {
                                                            dragOffset.snapTo((dragOffset.value + delta).coerceIn(minOffset, Float.MAX_VALUE))
                                                        }
                                                    }
                                                }
                                            }
                                            handlePressed = false
                                            if (dragging) {
                                                // 拉长吸附到档位：拖过半 ⇒ 拉满，否则收回
                                                val cap = expandMaxPx
                                                if (cap != null && expandPx != 0f && expandPx != cap) {
                                                    val target = if (expandPx > cap / 2f) cap else 0f
                                                    scope.launch {
                                                        androidx.compose.animation.core.animate(
                                                            initialValue = expandPx,
                                                            targetValue = target,
                                                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                                                        ) { value, _ -> expandPx = value }
                                                    }
                                                }
                                                if (dragOffset.value > dismissThreshold) requestClose()
                                                else scope.launch { dragOffset.animateTo(0f, tween(200)) }
                                            }
                                        }
                                    }
                            ) { dragHandle() }
                        }
                    }
                    CompositionLocalProvider(LocalSheetExpandPx provides expandPx) {
                        content { requestClose() }
                    }
                }
            }

            // 预测性返回：手势进行中弹窗跟手下移、遮罩同步变淡；取消回弹，确认关闭。
            // 必须放在 Dialog 内容里，才能注册到弹窗窗口自己的返回分发器
            PredictiveBackHandler(enabled = !closing) { progress ->
                try {
                    progress.collect { event ->
                        dragOffset.snapTo(event.progress * sheetHeightPx)
                    }
                    // 手势完成：提交关闭
                    requestClose()
                } catch (e: CancellationException) {
                    // 手势取消：回弹
                    scope.launch { dragOffset.animateTo(0f, tween(200)) }
                    throw e
                }
            }
        }
    }
}

// "打开链接"确认弹窗组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkConfirmDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val cornerRadius = getSystemCornerRadius()

    G2BottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) { onClose ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "打开链接",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "是否前往", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = (-12).dp).padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = url,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                // 长按复制网址；涟漪按 G2 圆角裁切
                // 两侧同样缩进保证涟漪左右一致；整块左移 12 抵消缩进，排版与原来一致
                modifier = Modifier
                    .offset(x = (-12).dp)
                    .clip(G2Shapes.button)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                            android.widget.Toast.makeText(context, "已复制网址", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClose()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = G2Shapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("取消", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            } catch (_: Exception) {}
                            onClose()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = G2Shapes.button,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("前往", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// "加入QQ交流群"弹窗组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QQGroupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val cornerRadius = getSystemCornerRadius()

    G2BottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) { onClose ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("加入QQ交流群", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("欢迎大家加入 ScreenTester QQ交流群\n新版本更新也会在群里发布\n群号：1035224343", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClose()
                    },
                    modifier = Modifier.weight(1f).height(48.dp), shape = G2Shapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { Text("关闭", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        // 复制群号
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ群号", "1035224343"))
                        // 尝试打开QQ
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&card_type=group&uin=1035224343")))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://qm.qq.com/cgi-bin/qm/qr?k=1035224343")))
                            } catch (_: Exception) {}
                        }
                        onClose()
                    },
                    modifier = Modifier.weight(1f).height(48.dp), shape = G2Shapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("复制并打开QQ", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// 教程引导组件
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
// 教程描述文本里的 ↻/×/✓ 用矢量图标内联渲染 —— 部分设备字体缺这些字形，图标不依赖字体
private val tutorialSymbolToIconId = mapOf('↻' to "ic_reset", '×' to "ic_close", '✓' to "ic_check")

private fun buildDescriptionWithIcons(description: String): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val annotated = buildAnnotatedString {
        description.forEach { ch ->
            val id = tutorialSymbolToIconId[ch]
            if (id != null) appendInlineContent(id, "[icon]") else append(ch)
        }
    }
    val inlineContent = mapOf(
        "ic_reset" to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
        },
        "ic_close" to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
        },
        "ic_check" to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
        }
    )
    return annotated to inlineContent
}

@Composable
fun TutorialOverlay(
    currentStep: Int,
    totalSteps: Int,
    targetBounds: Rect?,
    title: String,
    description: String,
    onPrevious: (() -> Unit)? = null,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    isLastStep: Boolean,
    showDragDemo: Boolean = false,
    dragDemoAnchor: Rect? = null,
    isUserDragging: Boolean = false
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val padPx = with(density) { 10.dp.toPx() }
    val configuration = LocalConfiguration.current

    val scope = rememberCoroutineScope()

    val g2RadiusPx = with(density) { 24.dp.toPx() }

    val appearAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appearAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    val initLeft = targetBounds?.left ?: 0f
    val initTop = targetBounds?.top ?: 0f
    val initRight = targetBounds?.right ?: 0f
    val initBottom = targetBounds?.bottom ?: 0f

    val leftAnim = remember { Animatable(initLeft) }
    val topAnim = remember { Animatable(initTop) }
    val rightAnim = remember { Animatable(initRight) }
    val bottomAnim = remember { Animatable(initBottom) }

    // 进入“拖一下就能看到变化”这一步时，先从上一页的高亮框平滑衔接到新的初始框，
    // 不直接 snap，避免步骤切换瞬间跳变。进入之后拖动，框再实时跟手。
    var dragDemoEntryInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(showDragDemo, targetBounds != null) {
        if (!showDragDemo) {
            dragDemoEntryInitialized = false
        } else if (targetBounds != null && !dragDemoEntryInitialized) {
            dragDemoEntryInitialized = true
            coroutineScope {
                launch { leftAnim.animateTo(targetBounds.left, tween(280, easing = FastOutSlowInEasing)) }
                launch { topAnim.animateTo(targetBounds.top, tween(280, easing = FastOutSlowInEasing)) }
                launch { rightAnim.animateTo(targetBounds.right, tween(280, easing = FastOutSlowInEasing)) }
                launch { bottomAnim.animateTo(targetBounds.bottom, tween(280, easing = FastOutSlowInEasing)) }
            }
        }
    }

    // 拖动时高亮框与控制点 1:1 跟手；普通教程步骤之间仍保持原来的平滑切换。
    LaunchedEffect(targetBounds, isUserDragging, showDragDemo) {
        if (targetBounds != null) {
            if (showDragDemo && isUserDragging) {
                leftAnim.snapTo(targetBounds.left)
                topAnim.snapTo(targetBounds.top)
                rightAnim.snapTo(targetBounds.right)
                bottomAnim.snapTo(targetBounds.bottom)
            } else if (!showDragDemo) {
                coroutineScope {
                    launch { leftAnim.animateTo(targetBounds.left, tween(300, easing = FastOutSlowInEasing)) }
                    launch { topAnim.animateTo(targetBounds.top, tween(300, easing = FastOutSlowInEasing)) }
                    launch { rightAnim.animateTo(targetBounds.right, tween(300, easing = FastOutSlowInEasing)) }
                    launch { bottomAnim.animateTo(targetBounds.bottom, tween(300, easing = FastOutSlowInEasing)) }
                }
            }
        }
    }

    val hasTarget = targetBounds != null
    // 箭头始终以当前控制点的真实圆心为起点。拖动时隐藏，松手后从新位置重新淡入。
    val dragDemoProgress = remember { Animatable(0f) }
    LaunchedEffect(showDragDemo, isUserDragging) {
        if (!showDragDemo) {
            dragDemoProgress.snapTo(0f)
        } else if (isUserDragging) {
            // 正在拖动：完全隐藏动画，并在松手后从短箭头重新开始。
            dragDemoProgress.snapTo(0f)
        } else {
            dragDemoProgress.snapTo(0f)
            while (true) {
                dragDemoProgress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                dragDemoProgress.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            }
        }
    }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val arrowColor = MaterialTheme.colorScheme.primary
    val arrowAlpha by animateFloatAsState(
        targetValue = if (isUserDragging) 0f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "dragDemoArrowAlpha"
    )
    val targetCenterY = (topAnim.value + bottomAnim.value) / 2
    val isTargetInTopHalf = targetCenterY < (screenHeightPx / 2)

    val verticalBias by animateFloatAsState(
        targetValue = if (isTargetInTopHalf) 1f else -1f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "CardVerticalBias"
    )
    val paddingTop by animateDpAsState(
        targetValue = if (isTargetInTopHalf) 0.dp else 100.dp,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "CardPaddingTop"
    )
    val paddingBottom by animateDpAsState(
        targetValue = if (isTargetInTopHalf) 100.dp else 0.dp,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "CardPaddingBottom"
    )

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = appearAlpha.value }) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.75f), size = size)
            if (hasTarget) {
                val pl = leftAnim.value - padPx
                val pt = topAnim.value - padPx
                val pr = rightAnim.value + padPx
                val pb = bottomAnim.value + padPx
                val w = pr - pl
                val h = pb - pt

                val g2Path = androidx.compose.ui.graphics.Path().apply {
                    val p = (1.4f * g2RadiusPx).coerceAtMost(minOf(w, h) / 2f)
                    val safeRadius = p / 1.4f
                    val c = 0.45f * safeRadius
                    moveTo(pl + p, pt)
                    lineTo(pr - p, pt)
                    cubicTo(pr - c, pt, pr, pt + c, pr, pt + p)
                    lineTo(pr, pb - p)
                    cubicTo(pr, pb - c, pr - c, pb, pr - p, pb)
                    lineTo(pl + p, pb)
                    cubicTo(pl + c, pb, pl, pb - c, pl, pb - p)
                    lineTo(pl, pt + p)
                    cubicTo(pl, pt + c, pl + c, pt, pl + p, pt)
                    close()
                }
                drawPath(path = g2Path, color = Color.Black, blendMode = BlendMode.Clear)
                // “拖一下就能看到变化”这一页隐藏白色描边，只保留挖空区域和拖拽箭头，
                // 让视觉重点落在实际拖动轨迹上；其他教程步骤仍沿用原来的白色描边。
                if (!showDragDemo) {
                    drawPath(
                        path = g2Path,
                        color = Color.White,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }

                if (showDragDemo && hasTarget && dragDemoAnchor != null) {
                    // 直接对准控制点圆心；控制点拖到哪里，箭头松手后就从哪里重新出现。
                    val start = dragDemoAnchor.center
                    val distance = 42.dp.toPx() * dragDemoProgress.value
                    val direction = Offset(1f, 1f)
                    val length = sqrt(direction.x * direction.x + direction.y * direction.y)
                    val unit = Offset(direction.x / length, direction.y / length)
                    val end = start + unit * distance
                    // 起点用“手指按住”的圆点提示
                    drawCircle(
                        color = arrowColor.copy(alpha = 0.22f * arrowAlpha),
                        radius = 13.dp.toPx(),
                        center = start
                    )
                    drawCircle(
                        color = arrowColor.copy(alpha = arrowAlpha),
                        radius = 5.dp.toPx(),
                        center = start
                    )

                    drawLine(
                        color = arrowColor.copy(alpha = arrowAlpha),
                        start = start,
                        end = end - unit * 8.dp.toPx(),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    val perpendicular = Offset(-unit.y, unit.x)
                    val headBase = end - unit * 12.dp.toPx()
                    val headLeft = headBase + perpendicular * 7.dp.toPx()
                    val headRight = headBase - perpendicular * 7.dp.toPx()
                    val arrowHead = androidx.compose.ui.graphics.Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(headLeft.x, headLeft.y)
                        lineTo(headRight.x, headRight.y)
                        close()
                    }
                    drawPath(
                        path = arrowHead,
                        color = arrowColor.copy(alpha = arrowAlpha)
                    )

                    drawCircle(
                        color = arrowColor.copy(alpha = 0.16f * arrowAlpha),
                        radius = 8.dp.toPx(),
                        center = end
                    )
                }
            }
        }

        if (hasTarget) {
            val pl = leftAnim.value - padPx
            val pt = topAnim.value - padPx
            val pr = rightAnim.value + padPx
            val pb = bottomAnim.value + padPx

            androidx.compose.ui.layout.Layout(
                content = {
                    val swallowTouch = Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
                    Box(modifier = swallowTouch); Box(modifier = swallowTouch)
                    Box(modifier = swallowTouch); Box(modifier = swallowTouch)
                }
            ) { measurables, constraints ->
                val w = constraints.maxWidth; val h = constraints.maxHeight
                val topH = pt.toInt().coerceIn(0, h); val botY = pb.toInt().coerceIn(0, h)
                val botH = h - botY; val midH = botY - topH
                val leftW = pl.toInt().coerceIn(0, w); val rightX = pr.toInt().coerceIn(0, w)
                val rightW = w - rightX

                val pTop = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(w, topH))
                val pBot = measurables[1].measure(androidx.compose.ui.unit.Constraints.fixed(w, botH))
                val pLeft = measurables[2].measure(androidx.compose.ui.unit.Constraints.fixed(leftW, midH))
                val pRight = measurables[3].measure(androidx.compose.ui.unit.Constraints.fixed(rightW, midH))

                layout(w, h) {
                    pTop.place(0, 0); pBot.place(0, botY)
                    pLeft.place(0, topH); pRight.place(rightX, topH)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while(true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() } } }
            })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(androidx.compose.ui.BiasAlignment(horizontalBias = 0f, verticalBias = verticalBias))
                .padding(horizontal = 24.dp)
                .padding(top = paddingTop, bottom = paddingBottom)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
                repeat(totalSteps) { i ->
                    val isActive = i == currentStep
                    val dotSize by animateDpAsState(
                        targetValue = if (isActive) 8.dp else 6.dp,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "TutorialDotSize"
                    )
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.4f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "TutorialDotAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(dotSize)
                            .background(Color.White.copy(alpha = dotAlpha), RoundedCornerShape(50))
                    )
                }
            }

            AnimatedContent(
                targetState = Triple(currentStep, title, description),
                transitionSpec = {
                    val direction = if (targetState.first > initialState.first) 1 else -1
                    val enter = slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { width -> direction * (width / 2) } + fadeIn(tween(400))
                    val exit = slideOutHorizontally(tween(400, easing = FastOutSlowInEasing)) { width -> -direction * (width / 2) } + fadeOut(tween(400))
                    enter togetherWith exit
                },
                label = "TutorialTextAnim"
            ) { (_, animTitle, animDesc) ->
                // 每个动画内容各自解析：退场的那份仍用旧描述自己的图标
                val (annotatedDesc, descInline) = remember(animDesc) { buildDescriptionWithIcons(animDesc) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(animTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        annotatedDesc,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        inlineContent = descInline
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch {
                            appearAlpha.animateTo(0f, tween(300))
                            onSkip()
                        }
                    },
                    modifier = Modifier.weight(0.3f)
                ) {
                    Text("跳过", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                }

                if (onPrevious != null) {
                    TextButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onPrevious() }, modifier = Modifier.weight(0.3f)) {
                        Text("上一步", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    }
                } else { Spacer(modifier = Modifier.weight(0.3f)) }

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isLastStep) {
                            scope.launch {
                                appearAlpha.animateTo(0f, tween(300))
                                onNext()
                            }
                        } else {
                            onNext()
                        }
                    },
                    modifier = Modifier.weight(0.4f).height(48.dp),
                    shape = G2Shapes.button,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    AnimatedContent(targetState = isLastStep, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "BtnTextAnim") { last ->
                        Text(if (last) "完成" else "下一步", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// 教程已看状态管理
fun markTutorialShown(context: android.content.Context) {
    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        .edit { putBoolean("calibration_tutorial_shown", true) }
}

fun isTutorialShown(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        .getBoolean("calibration_tutorial_shown", false)
}

fun markDragTutorialShown(context: android.content.Context) {
    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        .edit { putBoolean("drag_adjust_tutorial_shown", true) }
}

fun isDragTutorialShown(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        .getBoolean("drag_adjust_tutorial_shown", false)
}

/** 跨页面导航请求（自增 id 触发；不用"可清空的标志"当 LaunchedEffect 的 key） */
object SettingsNavRequests {
    /** 请求切到指定页 */
    var pageRequestId by mutableStateOf(0)
    var targetPage by mutableStateOf<Int?>(null)

    /** 请求设置页展开"自定义黑边遮挡测试"并高亮"使用旧版曲线" */
    var guideRequestId by mutableStateOf(0)

    /** 切页滑入期间先做一次粗定位 */
    var preScrollRequestId by mutableStateOf(0)

    /** 跳转时跳过设置页入场淡入 */
    var skipEnterAnimation by mutableStateOf(false)
}

/** "更多设置"入口：跳转线条与配色页（设置页卡片、OOBE 测试线条步骤共用） */
@Composable
fun MoreSettingsEntry(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(G2Shapes.aboutButton)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                context.startActivity(Intent(context, TestLineAppearanceActivity::class.java))
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("更多设置", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
