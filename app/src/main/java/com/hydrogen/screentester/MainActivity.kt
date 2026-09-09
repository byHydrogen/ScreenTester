@file:OptIn(ExperimentalHazeMaterialsApi::class)

package com.hydrogen.screentester

import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

// 可缓存的 G2 Shape 类
class CachedG2Shape(
    private val radiusDp: Float,
    private val curvatureFactor: Float = 0.45f
) : androidx.compose.ui.graphics.Shape {
    // 缓存上一次计算的尺寸和结果
    private var cachedSize: androidx.compose.ui.geometry.Size? = null
    private var cachedOutline: androidx.compose.ui.graphics.Outline? = null

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        // 检查是否可以使用缓存
        if (cachedSize == size && cachedOutline != null) {
            return cachedOutline!!
        }

        // 重新计算
        val path = Path()
        val w = size.width
        val h = size.height
        val radius = with(density) { radiusDp.dp.toPx() }
        val p = (1.4f * radius).coerceAtMost(h / 2f)
        val safeRadius = p / 1.4f
        val c = curvatureFactor * safeRadius

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

        val outline = androidx.compose.ui.graphics.Outline.Generic(path)

        // 更新缓存
        cachedSize = size
        cachedOutline = outline

        return outline
    }
}

// 可缓存的 SmoothCorner Shape 类
class CachedSmoothCornerShape(
    private val radius: androidx.compose.ui.unit.Dp
) : androidx.compose.ui.graphics.Shape {
    private var cachedSize: androidx.compose.ui.geometry.Size? = null
    private var cachedOutline: androidx.compose.ui.graphics.Outline? = null

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        if (cachedSize == size && cachedOutline != null) {
            return cachedOutline!!
        }

        val r = with(density) { radius.toPx() }
        val w = size.width
        val h = size.height
        val maxR = minOf(w / 2f, h / 2f)
        val finalR = if (r > maxR) maxR else r

        val path = Path().apply {
            val factor = 1.52f
            val c = finalR * factor
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            cubicTo(w - finalR * 0.55f, 0f, w, finalR * 0.55f, w, c)
            lineTo(w, h - c)
            cubicTo(w, h - finalR * 0.55f, w - finalR * 0.55f, h, w - c, h)
            lineTo(c, h)
            cubicTo(finalR * 0.55f, h, 0f, h - finalR * 0.55f, 0f, h - c)
            lineTo(0f, c)
            cubicTo(0f, finalR * 0.55f, finalR * 0.55f, 0f, c, 0f)
            close()
        }

        val outline = androidx.compose.ui.graphics.Outline.Generic(path)
        cachedSize = size
        cachedOutline = outline
        return outline
    }
}

// 预定义的常用 G2 Shape 实例
object G2Shapes {
    val navBar = CachedG2Shape(37f, 0.55f)
    val indicator = CachedG2Shape(28f, 0.55f)
    val card = CachedG2Shape(21f, 0.45f)
    val largeCard = CachedG2Shape(21f, 0.45f)
    val button = CachedG2Shape(13f, 0.45f)
    val icon = CachedG2Shape(12f, 0.45f)
    val searchBox = CachedG2Shape(32f, 0.55f)
    val gridCard = CachedG2Shape(16f, 0.45f)
    val aboutCard = CachedG2Shape(32f, 0.45f)
    val aboutButton = CachedG2Shape(13f, 0.45f)
    val newVersionCard = CachedG2Shape(20f, 0.45f)
    val logo = CachedG2Shape(24f, 0.45f)
    val hdrCard = CachedSmoothCornerShape(24.dp)
    val hdrInnerCard = CachedSmoothCornerShape(16.dp)
}

// 获取系统圆角半径
@Composable
fun getSystemCornerRadius(): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val context = LocalContext.current

    return try {
        val view = (context as? android.app.Activity)?.window?.decorView
        val insets = view?.rootWindowInsets
        val cornerRadius = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
        if (cornerRadius != null) {
            with(density) { cornerRadius.radius.toDp() }
        } else {
            28.dp
        }
    } catch (e: Exception) {
        28.dp
    }
}

// 更新状态
object GlobalUpdateState {
    var hasNewVersion by mutableStateOf(false)
    var latestVersionName by mutableStateOf("")
    var latestChangelog by mutableStateOf("")
    var latestDownloadUrl by mutableStateOf<String?>(null)
    val downloadState = DownloadState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeSettings.loadConfig(this)

        // 应用启动时静默检查更新（可在设置里关闭）
        if (ThemeSettings.autoCheckUpdateEnabled) {
            UpdateManager.checkUpdate(
                context = this,
                onResult = { hasUpdate, version, changelog, downloadUrl ->
                    if (hasUpdate && version != null) {
                        // 确保远程版本确实更高
                        val pInfo = packageManager.getPackageInfo(packageName, 0)
                        val localVersion = pInfo.versionName ?: ""
                        if (UpdateManager.isVersionGreater(version, localVersion)) {
                            GlobalUpdateState.hasNewVersion = true
                            GlobalUpdateState.latestVersionName = version
                            GlobalUpdateState.latestChangelog = changelog ?: ""
                            GlobalUpdateState.latestDownloadUrl = downloadUrl
                        }
                    }
                }
            )
        }

        setContent {
            ScreenTesterTheme {
                // 状态栏图标颜色跟随应用内深色/浅色设置，而非系统
                val view = LocalView.current
                val isDark = when (ThemeSettings.darkModeState) {
                    DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    DarkModeConfig.LIGHT -> false
                    DarkModeConfig.DARK -> true
                }
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        androidx.core.view.WindowInsetsControllerCompat(window, view).apply {
                            isAppearanceLightStatusBars = !isDark
                            isAppearanceLightNavigationBars = !isDark
                            // 关闭三键导航栏的半透明对比遮罩
                            window.isNavigationBarContrastEnforced = false
                        }
                    }
                    // 启动时检测圆心数据可用性（仅首次，结果存 SharedPreferences）
                    LaunchedEffect(Unit) {
                        ThemeSettings.checkCenterDataAvailability(view)
                    }
                }

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
    val hazeState = remember { HazeState() }

    // 跨页面导航请求：滑动切页 → 切页完成后触发设置页引导
    LaunchedEffect(SettingsNavRequests.pageRequestId) {
        if (SettingsNavRequests.pageRequestId == 0) return@LaunchedEffect
        val target = SettingsNavRequests.targetPage ?: return@LaunchedEffect
        // 滑入期间先粗定位（此刻设置页不可见）
        if (target == 1) SettingsNavRequests.preScrollRequestId++
        pagerState.animateScrollToPage(target)
        if (target == 1) SettingsNavRequests.guideRequestId++
    }

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
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            beyondViewportPageCount = 2,
            userScrollEnabled = true
        ) { pageIndex ->
            when (pageIndex) {
                0 -> HomePage()
                1 -> SettingsPage()
                2 -> AboutPage(pageVisible = pagerState.currentPage == 2)
            }
        }

        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // thin 样式在组合期计算（HazeMaterials.thin 为 @Composable）；blurRadius 在块内读取状态，滑杆/输入/重置实时生效
        val thinStyle = HazeMaterials.thin(containerColor = MaterialTheme.colorScheme.background)
        // 底栏模糊开关的过渡进度：模糊层与不透明底色交叉淡入淡出，避免开关时背景「闪现」
        val navBarBlurProgress by animateFloatAsState(
            targetValue = if (ThemeSettings.navBarBlurEnabled) 1f else 0f,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "navBarBlurProgress"
        )
        AnimatedScrubbingNavBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxOf(32.dp, navBarInset))
                .then(
                    if (navBarBlurProgress > 0f) {
                        Modifier
                            .clip(G2Shapes.navBar) // 先裁剪成底栏 G2 形状，模糊效果随之被裁进圆角内
                            .hazeEffect(hazeState) {
                                // blurRadius 在块内读取状态：滑杆/输入/重置的变化实时生效
                                style = thinStyle.copy(blurRadius = ThemeSettings.navBarBlurRadius.dp)
                            }
                    } else {
                        Modifier
                    }
                ),
            // 只让「底色」跟着过渡：模糊层画在底色之下，底色淡出的同时模糊自然露出来。
            // 注意不能把 alpha 加在整条底栏上——那会把图标文字一起淡掉。
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f * (1f - navBarBlurProgress)),
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
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    currentPage: Int,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTabClick: (Int) -> Unit
) {
    val items = listOf("主页" to Icons.Default.Home, "设置" to Icons.Default.Settings, "关于" to Icons.Default.Info)
    val configuration = LocalConfiguration.current
    // 大屏上限：不加限制的话在平板上会变成一条横跨整屏的超长条。
    val navBarWidth = minOf(configuration.screenWidthDp.dp * 0.85f, DeviceUtils.NavBarMaxWidth)
    val tabWidth = navBarWidth / items.size
    val density = LocalDensity.current.density

    val indicatorOffset by animateDpAsState(
        targetValue = tabWidth * currentPage,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "nav_blob"
    )

    val navBarG2Shape = G2Shapes.navBar
    val indicatorG2Shape = G2Shapes.indicator

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
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
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
