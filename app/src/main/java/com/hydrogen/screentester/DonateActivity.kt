package com.hydrogen.screentester

import android.annotation.SuppressLint
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrogen.screentester.ui.theme.ScreenTesterTheme
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

class DonateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false // 关闭三键导航栏的半透明对比遮罩
        super.onCreate(savedInstanceState)
        setContent {
            ScreenTesterTheme {
                val isDark = when (ThemeSettings.darkModeState) {
                    DarkModeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    DarkModeConfig.LIGHT -> false
                    DarkModeConfig.DARK -> true
                }
                DonateScreen(isDark = isDark) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DonateScreen(isDark: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val backgroundBrush = DeviceUtils.backgroundBrush(isDark)
    val cardShape = G2Shapes.aboutCard
    var showSaveDialog by remember { mutableStateOf(false) }

    // 保存图片到相册
    @SuppressLint("ResourceType")
    fun saveImageToGallery() {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "ScreenTester_donate_qr.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenTester")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    context.resources.openRawResource(R.drawable.donate_qr).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 保存确认弹窗
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存图片", fontWeight = FontWeight.Bold) },
            text = { Text("将赞赏二维码保存到相册？") },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showSaveDialog = false
                    saveImageToGallery()
                }) { Text("保存", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // 顶栏模糊源：背景层与滚动内容都录进去，纯模糊（无染色层）的快照才不透明、不会重影
    val donateHazeState = remember { HazeState() }
    // 横屏两栏：左图标、右卡片
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        // 背景层：动态混色（跟随全局开关）或原静态渐变；同时注册为顶栏模糊源
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ThemeSettings.topBarGradientBlur) Modifier.hazeSource(donateHazeState) else Modifier)
                .then(
                    if (ThemeSettings.aboutDynamicMixEnabled) Modifier.dynamicMixBackground(isDark, running = true)
                    else Modifier.background(backgroundBrush)
                )
        )

        // 竖屏单列滚动 + 限宽；横屏两栏
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 关模糊 + 开混色时顶栏完全透明，用内容渐隐代替铺色
                .then(
                    if (!ThemeSettings.topBarGradientBlur && ThemeSettings.aboutDynamicMixEnabled) {
                        Modifier.fadeOutAtTop(
                            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp
                        )
                    } else Modifier
                )
                .then(
                    if (!isLandscape) Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = DeviceUtils.NavBarMaxWidth)
                        .verticalScroll(rememberScrollState())
                    else Modifier
                )
                .fillMaxSize()
                .then(if (ThemeSettings.topBarGradientBlur) Modifier.hazeSource(donateHazeState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部留白仅竖屏需要
            if (!isLandscape) {
                Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp))
            }

            // 头部抽成 lambda：横屏进左栏，竖屏在流内
            val headerBlock: @Composable () -> Unit = {
                // 咖啡图标
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = G2Shapes.logo
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalCafe,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "请我喝杯咖啡",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "如果这个应用对你有帮助\n欢迎请作者喝杯咖啡 ☕",
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 赞赏二维码卡片
            val bodyBlock: @Composable () -> Unit = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.donate_qr),
                            contentDescription = "赞赏二维码",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        showSaveDialog = true
                                    }
                                ),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "微信赞赏码",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            text = "长按图片可保存到相册",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                }
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = (configuration.screenWidthDp - 64).dp * 0.5f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    // 卡片限宽 420
                    Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        bodyBlock()
                    }
                    Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    // 必须包一层 Column：直接放进 Box 会重叠
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { headerBlock() }
                }
            }
        } else {
            headerBlock()
            Spacer(modifier = Modifier.height(40.dp))
            bodyBlock()
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp))
        }
        }

        // 顶栏 overlay：三种形态——
        // 渐变模糊开：纯模糊无染色层，与历史更新日志页一致；
        // 渐变模糊关 + 动态混色开：完全透明，不压混色背景；
        // 其余：原同色渐变
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(
                    when {
                        // 模糊分支不加 28dp 尾巴：hazeEffect 铺满节点高度
                        ThemeSettings.topBarGradientBlur -> Modifier.hazeEffect(donateHazeState) {
                            style = HazeStyle(tints = emptyList(), noiseFactor = 0f)
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                        ThemeSettings.aboutDynamicMixEnabled -> Modifier.padding(bottom = 28.dp)
                        else -> {
                            val startColor = DeviceUtils.backgroundBaseColor(isDark)
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        startColor,
                                        startColor.copy(alpha = 0.95f),
                                        startColor.copy(alpha = 0.60f),
                                        startColor.copy(alpha = 0.20f),
                                        startColor.copy(alpha = 0f)
                                    )
                                )
                            ).padding(bottom = 28.dp)
                        }
                    }
                )
        ) {
            TopAppBar(
                title = { Text("赞赏", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    }
}
