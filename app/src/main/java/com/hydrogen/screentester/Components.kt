package com.hydrogen.screentester

import android.content.Intent
import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
