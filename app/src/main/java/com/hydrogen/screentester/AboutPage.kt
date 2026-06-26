package com.hydrogen.screentester

import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    // 根据系统莫奈取色的色相，自动切换背景混色方案
    val backgroundBrush = DeviceUtils.backgroundBrush(isDark)

    val cardG2Shape = G2Shapes.aboutCard

    var isChangelogExp by remember { mutableStateOf(false) }
    val changelogArrowRotation by animateFloatAsState(targetValue = if (isChangelogExp) 180f else 0f, label = "changelogArrow")

    var isCreditsExp by remember { mutableStateOf(false) }
    val creditsArrowRotation by animateFloatAsState(targetValue = if (isCreditsExp) 180f else 0f, label = "creditsArrow")

    // === 更新状态管理 ===
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var hasNewVersion by GlobalUpdateState::hasNewVersion
    var latestVersionName by GlobalUpdateState::latestVersionName
    var latestChangelog by GlobalUpdateState::latestChangelog
    var checkButtonText by remember { mutableStateOf("检测更新") }
    var showLinkDialog by remember { mutableStateOf<String?>(null) }

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
            // 第一屏：主界面
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
                    val logoG2Shape = G2Shapes.logo

                    Box(
                        Modifier
                            .size(110.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = logoG2Shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
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
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
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

            // 第二屏：开发者卡片
            val maxScrollVal = scrollState.maxValue.toFloat()
            val curScrollVal = scrollState.value.toFloat()
            val scrollProgress = if (maxScrollVal > 0) (curScrollVal / maxScrollVal * 1.2f).coerceIn(0f, 1f) else 1f

            var devCardVisible by remember { mutableStateOf(false) }
            var donateCardVisible by remember { mutableStateOf(false) }
            var qqCardVisible by remember { mutableStateOf(false) }
            var projCardVisible by remember { mutableStateOf(false) }
            var changelogCardVisible by remember { mutableStateOf(false) }
            var creditsCardVisible by remember { mutableStateOf(false) }

            LaunchedEffect(scrollProgress) {
                if (scrollProgress >= 0.10f) devCardVisible = true
                else if (scrollProgress <= 0.04f) devCardVisible = false
                if (scrollProgress >= 0.25f) projCardVisible = true
                else if (scrollProgress <= 0.18f) projCardVisible = false
                if (scrollProgress >= 0.40f) changelogCardVisible = true
                else if (scrollProgress <= 0.32f) changelogCardVisible = false
                if (scrollProgress >= 0.55f) creditsCardVisible = true
                else if (scrollProgress <= 0.46f) creditsCardVisible = false
                if (scrollProgress >= 0.68f) donateCardVisible = true
                else if (scrollProgress <= 0.58f) donateCardVisible = false
                if (scrollProgress >= 0.78f) qqCardVisible = true
                else if (scrollProgress <= 0.68f) qqCardVisible = false
            }

            val devCardAlpha by animateFloatAsState(targetValue = if (devCardVisible) 1f else 0f, animationSpec = tween(400), label = "devCardAlpha")
            val devCardTransY by animateFloatAsState(targetValue = if (devCardVisible) 0f else 60f, animationSpec = tween(400), label = "devCardTransY")
            val donateCardAlpha by animateFloatAsState(targetValue = if (donateCardVisible) 1f else 0f, animationSpec = tween(400), label = "donateCardAlpha")
            val donateCardTransY by animateFloatAsState(targetValue = if (donateCardVisible) 0f else 60f, animationSpec = tween(400), label = "donateCardTransY")
            val qqCardAlpha by animateFloatAsState(targetValue = if (qqCardVisible) 1f else 0f, animationSpec = tween(400), label = "qqCardAlpha")
            val qqCardTransY by animateFloatAsState(targetValue = if (qqCardVisible) 0f else 60f, animationSpec = tween(400), label = "qqCardTransY")
            val projCardAlpha by animateFloatAsState(targetValue = if (projCardVisible) 1f else 0f, animationSpec = tween(400), label = "projCardAlpha")
            val projCardTransY by animateFloatAsState(targetValue = if (projCardVisible) 0f else 60f, animationSpec = tween(400), label = "projCardTransY")
            val changelogCardAlpha by animateFloatAsState(targetValue = if (changelogCardVisible) 1f else 0f, animationSpec = tween(400), label = "changelogCardAlpha")
            val changelogCardTransY by animateFloatAsState(targetValue = if (changelogCardVisible) 0f else 60f, animationSpec = tween(400), label = "changelogCardTransY")
            val creditsCardAlpha by animateFloatAsState(targetValue = if (creditsCardVisible) 1f else 0f, animationSpec = tween(400), label = "creditsCardAlpha")
            val creditsCardTransY by animateFloatAsState(targetValue = if (creditsCardVisible) 0f else 60f, animationSpec = tween(400), label = "creditsCardTransY")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(y = (-102).dp)
            ) {
                Box(modifier = Modifier.graphicsLayer { alpha = devCardAlpha; translationY = devCardTransY.dp.toPx() }) {
                    DeveloperProfileCard(isDark = isDark)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.graphicsLayer { alpha = projCardAlpha; translationY = projCardTransY.dp.toPx() }) {
                    ProjectSourceCard(isDark = isDark)
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 更新日志板块
                Box(modifier = Modifier.graphicsLayer { alpha = changelogCardAlpha; translationY = changelogCardTransY.dp.toPx() }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardG2Shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
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
                                val aboutButtonG2Shape = G2Shapes.aboutButton

                                // 版本更新卡片
                                val newVersionCardShape = G2Shapes.newVersionCard

                                AnimatedVisibility(visible = hasNewVersion) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        shape = newVersionCardShape,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            Text(text = "发现新版本：$latestVersionName", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(8.dp))
                                            MarkdownText(text = latestChangelog, fontSize = 13.sp, lineHeight = 18.sp, textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(), linkColor = MaterialTheme.colorScheme.primary.toArgb(), onLinkClick = { showLinkDialog = it })
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
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("去下载", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 当前版本信息
                                Text(text = "版本 2.1", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "新增 OOBE 圆角校准步骤 支持内嵌校准卡片（无需跳转校准车间）\n新增 主页 加入QQ交流群弹窗（仅弹一次）\n新增 关于页 加入QQ交流群卡片\n新增 校准车间页 +/- 按钮（点击后 ±0.1 ，长按可快速加减）\n新增 校准车间页 固定卡片功能（长按卡片可固定，固定后卡片不会被折叠，固定后点击/长按卡片标题栏可取消固定）\n新增 校准车间页 卡片展开/折叠时的箭头旋转动效\n优化 校准车间页 卡片圆角\n优化 屏幕灰阶测试 & 屏幕彩条测试 悬浮提示胶囊圆角\n优化 触控采样率测试 触控采样率算法\n优化 触控采样率测试 “稳定峰值”卡片圆角\n修复 自定义深/浅色模式下的状态栏反色问题\n修复 了一些已知问题",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                // 检测更新按钮
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        if (!isCheckingUpdate && checkButtonText != "已是最新版本") {
                                            isCheckingUpdate = true
                                            checkButtonText = "正在检查..."
                                            UpdateManager.checkUpdate(
                                                context,
                                                isManual = true,
                                                onResult = { hasUpdate, version, changelog ->
                                                    isCheckingUpdate = false
                                                    if (hasUpdate && version != null) {
                                                        if (UpdateManager.isVersionGreater(version, versionName)) {
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
                                                },
                                                onError = {
                                                    isCheckingUpdate = false
                                                    checkButtonText = "检查失败"
                                                    scope.launch {
                                                        delay(2000)
                                                        if (checkButtonText == "检查失败") {
                                                            checkButtonText = "检测更新"
                                                        }
                                                    }
                                                }
                                            )
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 致谢板块
                Box(modifier = Modifier.graphicsLayer { alpha = creditsCardAlpha; translationY = creditsCardTransY.dp.toPx() }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardG2Shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    isCreditsExp = !isCreditsExp
                                }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "致谢",
                                modifier = Modifier.weight(1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = creditsArrowRotation },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        AnimatedVisibility(visible = isCreditsExp) {
                            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(4.dp))

                                val credits = listOf(
                                    Triple("AndroidX Core KTX", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/core"),
                                    Triple("AndroidX Lifecycle", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
                                    Triple("Jetpack Compose", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/compose"),
                                    Triple("AndroidX Activity", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/activity"),
                                    Triple("AndroidX AppCompat", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/appcompat"),
                                    Triple("Google Material Design", "Google", "https://github.com/material-components/material-components-android"),
                                    Triple("AndroidX ConstraintLayout", "The Android Open Source Project", "https://github.com/androidx/constraintlayout"),
                                    Triple("Compose Material Icons", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx/releases/compose")
                                )

                                credits.forEach { (name, author, url) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(G2Shapes.gridCard),
                                        color = Color.Transparent,
                                        shadowElevation = 0.dp,
                                        tonalElevation = 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                                    } catch (_: Exception) {}
                                                }
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                                                Text(
                                                    text = name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = author,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "打开链接",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            }
                        }
                    }
                }

                // 支持开发者卡片
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.graphicsLayer { alpha = donateCardAlpha; translationY = donateCardTransY.dp.toPx() }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardG2Shape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                context.startActivity(Intent(context, DonateActivity::class.java))
                            },
                        shape = cardG2Shape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.LocalCafe, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "支持开发者",
                                modifier = Modifier.weight(1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // 加入QQ交流群卡片
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.graphicsLayer { alpha = qqCardAlpha; translationY = qqCardTransY.dp.toPx() }) {
                    var showQQDialog by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardG2Shape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showQQDialog = true
                            },
                        shape = cardG2Shape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.QuestionAnswer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "加入QQ交流群",
                                modifier = Modifier.weight(1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (showQQDialog) {
                        QQGroupDialog(onDismiss = { showQQDialog = false })
                    }
                }

                Spacer(modifier = Modifier.height(130.dp))
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
}

// 开发者卡片组件
@Composable
fun DeveloperProfileCard(isDark: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val cardG2Shape = G2Shapes.aboutCard

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardG2Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
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
    val cardG2Shape = G2Shapes.aboutCard

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardG2Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Transparent)
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

