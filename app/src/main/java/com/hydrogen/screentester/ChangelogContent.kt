package com.hydrogen.screentester

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants

// 更新日志的分类顺序：先功能页，测试项统一排最后
// （"触控测试"最宽泛，必须排在其它测试项之后，否则会吞掉"多指触控检测""触控采样率测试"）
private val CHANGELOG_CATEGORIES = listOf(
    "OOBE", "校准车间", "线条与配色", "主页", "设置页", "关于页", "历史更新日志", "赞赏页", "完成页",
    "黑边遮挡测试", "色彩与坏点测试", "灰阶测试", "白平衡测试", "彩条测试",
    "HDR 检测", "触控采样率测试", "多指触控检测", "触控测试"
)

private fun changelogCategoryOf(text: String): String =
    CHANGELOG_CATEGORIES.firstOrNull { text.contains(it) } ?: "通用"

// 一行日志 → 标签 + 正文 + 可选括号补充
internal fun parseChangelogItems(logText: String): List<LogLineItem> {
    val lines = logText.split("\n")
    val result = mutableListOf<LogLineItem>()
    var idx = 0
    val tags = listOf("新增", "优化", "重构", "修复", "调整", "补充", "修改", "移除")

    while (idx < lines.size) {
        val line = lines[idx].trim()
        // "#" 开头的是元信息标记（如 #归类），不参与展示
        if (line.isEmpty() || line.startsWith("#")) { idx++; continue }

        if (line.startsWith("（") && line.endsWith("）") && !line.contains("X轴") && !line.contains("Y轴") && result.isNotEmpty()) {
            val last = result.removeAt(result.size - 1)
            result.add(last.copy(subText = line.substring(1, line.length - 1).trim()))
            idx++
            continue
        }

        var foundTag: String? = null
        var remainingText = line
        for (t in tags) {
            if (line.startsWith(t)) {
                foundTag = t
                remainingText = line.substring(t.length).trim()
                break
            }
        }

        var mainText = remainingText
        var subText: String? = null
        val openIdx = remainingText.indexOf("（")
        val closeIdx = remainingText.lastIndexOf("）")
        if (openIdx != -1 && closeIdx != -1 && closeIdx > openIdx) {
            val inside = remainingText.substring(openIdx + 1, closeIdx)
            if (inside != "X轴" && inside != "Y轴") {
                mainText = remainingText.substring(0, openIdx).trim()
                subText = inside.trim()
            }
        }

        result.add(LogLineItem(foundTag, mainText, subText))
        idx++
    }
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangelogCategoryChips(
    groups: List<Pair<String, Int>>,
    compact: Boolean,
    selected: Set<String>,
    showAll: Boolean,
    accent: Color,
    onSelect: (String?) -> Unit,
    onLongSelect: (String) -> Unit
) {
    // null 表示"全部"；分类按固定顺序排在后面
    val chips = listOf<Triple<String?, String, Int?>>(Triple(null, "全部", null)) +
            groups.map { Triple(it.first as String?, it.first, it.second as Int?) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (key, label, count) ->
            val isSelected = if (key == null) showAll else !showAll && key in selected
            val chipBackground by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "changelogChipBg"
            )
            val chipContentColor by animateColorAsState(
                targetValue = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "changelogChipText"
            )
            Box(
                modifier = Modifier
                    .clip(G2Shapes.gridCard)
                    .background(chipBackground)
                    .combinedClickable(
                        onClick = { onSelect(key) },
                        // 长按分类可叠加多选（"全部"除外）
                        onLongClick = { if (key != null) onLongSelect(key) }
                    )
                    .padding(
                        horizontal = if (compact) 9.dp else 12.dp,
                        vertical = if (compact) 4.dp else 6.dp
                    )
            ) {
                Text(
                    text = if (count == null) label else "$label $count",
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = chipContentColor
                )
            }
        }
    }
}

/**
 * 更新日志正文：日志文本首行写 `#归类` 时按模块分类展示，否则平铺。
 * 历史更新日志页与关于页共用。
 */
@Composable
internal fun ChangelogGroupedContent(
    logText: String,
    version: String,
    isDark: Boolean,
    accent: Color,
    useTagBadge: Boolean = true,
    compactStyle: Boolean = false,
    plainLine: Boolean = false,
    bodyFontSize: TextUnit = 14.sp,
    bodyLineHeight: TextUnit = 20.sp,
    bodyColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val groupingEnabled = remember(logText) { logText.split("\n").any { it.trim() == "#归类" } }
    val parsedItems = remember(logText) { parseChangelogItems(logText) }
    // 紧凑排版：关于页用它贴近纯文本观感
    val rowSpacing = if (compactStyle) 3.dp else 12.dp

    if (!groupingEnabled) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
            parsedItems.forEach { ChangelogRowRenderer(
                            item = it, isDark = isDark, useTagBadge = useTagBadge, plainLine = plainLine,
                            bodyFontSize = bodyFontSize, bodyLineHeight = bodyLineHeight, bodyColor = bodyColor
                        ) }
        }
        return
    }

    val groups = remember(parsedItems) {
        parsedItems.groupBy { changelogCategoryOf(it.mainText) }
            .entries
            .sortedBy { e ->
                val i = CHANGELOG_CATEGORIES.indexOf(e.key)
                if (i < 0) CHANGELOG_CATEGORIES.size else i
            }
            .map { it.key to it.value }
    }

    // 点分类 = 只显示它；点"全部" = 全展开；长按分类 = 叠加多选
    var showAllCategories by rememberSaveable(version) { mutableStateOf(false) }
    var selectedCategoriesRaw by rememberSaveable(version) { mutableStateOf("") }
    val selectedCategories = remember(selectedCategoriesRaw) {
        if (selectedCategoriesRaw.isEmpty()) {
            groups.firstOrNull()?.first?.let { setOf(it) } ?: emptySet()
        } else {
            selectedCategoriesRaw.split(",").toSet()
        }
    }
    val visibleCategories =
        if (showAllCategories) groups.map { it.first }.toSet() else selectedCategories

    Column(modifier = modifier) {
        ChangelogCategoryChips(
            groups = groups.map { it.first to it.second.size },
            compact = compactStyle,
            selected = selectedCategories,
            showAll = showAllCategories,
            accent = accent,
            onSelect = { category ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (category == null) {
                    showAllCategories = true
                } else {
                    showAllCategories = false
                    selectedCategoriesRaw = category
                }
            },
            onLongSelect = { category ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAllCategories = false
                val next = if (category in selectedCategories) selectedCategories - category
                else selectedCategories + category
                // 至少保留一个，避免分类区整块空白
                if (next.isNotEmpty()) selectedCategoriesRaw = next.joinToString(",")
            }
        )

        groups.forEach { (category, items) ->
            AnimatedVisibility(visible = category in visibleCategories) {
                Column(modifier = Modifier.padding(top = if (compactStyle) 5.dp else 16.dp)) {
                    Text(
                        text = "$category · ${items.size} 条",
                        fontSize = if (compactStyle) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Spacer(modifier = Modifier.height(rowSpacing))
                    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                        items.forEach { ChangelogRowRenderer(
                            item = it, isDark = isDark, useTagBadge = useTagBadge, plainLine = plainLine,
                            bodyFontSize = bodyFontSize, bodyLineHeight = bodyLineHeight, bodyColor = bodyColor
                        ) }
                    }
                }
            }
        }
    }
}
