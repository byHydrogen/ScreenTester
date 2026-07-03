package com.hydrogen.screentester

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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

// "打开链接"确认弹窗组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkConfirmDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cornerRadius = getSystemCornerRadius()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
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
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            )
        }
    ) {
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
            Text("是否前往", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(url, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
                            scope.launch { sheetState.hide(); onDismiss() }
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
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                } catch (_: Exception) {}
                            }
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
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cornerRadius = getSystemCornerRadius()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
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
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            )
        }
    ) {
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
                        scope.launch { sheetState.hide(); onDismiss() }
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
                        scope.launch { sheetState.hide(); onDismiss() }
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
    isLastStep: Boolean
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

    LaunchedEffect(targetBounds) {
        if (targetBounds != null) {
            coroutineScope {
                launch { leftAnim.animateTo(targetBounds.left, tween(350, easing = FastOutSlowInEasing)) }
                launch { topAnim.animateTo(targetBounds.top, tween(350, easing = FastOutSlowInEasing)) }
                launch { rightAnim.animateTo(targetBounds.right, tween(350, easing = FastOutSlowInEasing)) }
                launch { bottomAnim.animateTo(targetBounds.bottom, tween(350, easing = FastOutSlowInEasing)) }
            }
        }
    }

    val hasTarget = targetBounds != null
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
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
                drawPath(path = g2Path, color = Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
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
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == currentStep) 8.dp else 6.dp)
                            .background(if (i == currentStep) Color.White else Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(animTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(animDesc, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 22.sp)
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
        .edit().putBoolean("calibration_tutorial_shown", true).apply()
}

fun isTutorialShown(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        .getBoolean("calibration_tutorial_shown", false)
}
