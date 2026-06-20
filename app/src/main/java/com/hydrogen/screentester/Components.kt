package com.hydrogen.screentester

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
