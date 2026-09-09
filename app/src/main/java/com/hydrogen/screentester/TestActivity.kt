package com.hydrogen.screentester

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.GestureDetector
import android.widget.Button
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class TestActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var testView: TestFrameView? = null
    private var gestureDetector: GestureDetector? = null

    // 计时相关
    private var currentTimer = 5
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentTimer > 0) {
                currentTimer--
                testView?.remainingSeconds = currentTimer
                if (currentTimer <= 0) {
                    finish()
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    /** 切换/初始化模式按钮的半透明底色（跟随线条测试的自定义背景色；animate=true 时做颜色渐变） */
    private var switchBtnRef: android.widget.TextView? = null
    private lateinit var settingsPrefs: android.content.SharedPreferences
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var switchBtnColor = android.graphics.Color.TRANSPARENT
    private lateinit var rootLayout: android.widget.FrameLayout

    // 根布局底色跟随"精度模式用自定义背景"开关。模式切换有整层 alpha 渐变，底色与画布不同色会闪一下
    private fun applyRootBackground() {
        if (!this::rootLayout.isInitialized) return
        rootLayout.setBackgroundColor(
            if (ThemeSettings.precisionUsesCustomBg) ThemeSettings.testLineBgColor else Color.BLACK
        )
    }

    private fun applySwitchBtnColor(btn: android.widget.TextView?, animate: Boolean) {
        // 按钮底色按模式取：圆角模式 = 自定义背景+α0x44；精度模式 = 黑27%（开了"精度模式用自定义背景"后同色）
        val precisionFollows = ThemeSettings.precisionUsesCustomBg
        val target = if (testView?.isAdvancedMode == true && !precisionFollows) {
            0x44000000
        } else {
            (ThemeSettings.testLineBgColor and 0x00FFFFFF) or 0x44000000
        }
        if (btn == null) return
        val drawable = (btn.background as? android.graphics.drawable.GradientDrawable)
            ?: android.graphics.drawable.GradientDrawable().also {
                it.cornerRadius = 16f * resources.displayMetrics.density
                btn.background = it
            }
        if (!animate) {
            drawable.setColor(target)
            // 文字颜色也跟随字体色设置
            btn.setTextColor(if (ThemeSettings.textFollowsLine) ThemeSettings.testLineColor else ThemeSettings.testLineTextColor)
            return
        }
        val from = switchBtnColor
        switchBtnColor = target
        android.animation.ValueAnimator.ofArgb(from, target).apply {
            duration = 320
            addUpdateListener { anim ->
                drawable.setColor(anim.animatedValue as Int)
            }
            // 文字颜色直接切到目标色
            btn.setTextColor(if (ThemeSettings.textFollowsLine) ThemeSettings.testLineColor else ThemeSettings.testLineTextColor)
            start()
        }
    }

    private fun toggleTestMode() {
        testView?.let { tv ->
            // 模式切换由 View 内部的 modeProgress 动画驱动
            tv.isAdvancedMode = !tv.isAdvancedMode
            applySwitchBtnColor(switchBtnRef, animate = true)   // 按钮底色跟随目标模式的背景一起渐变
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 横屏需显式声明铺进短边挖孔区，线条才能真正贴边
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        applyTestWindowSettings()

        rootLayout = FrameLayout(this)
        applyRootBackground()

        testView = TestFrameView(this)
        // 必须显式给 MATCH_PARENT，否则 View 不铺满整屏
        rootLayout.addView(
            testView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 初始化手势检测器
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 开启精简模式双击屏幕任意位置切换状态
                if (ThemeSettings.isCompactModeEnabled) {
                    toggleTestMode()
                    return true
                }
                return false
            }
        })

        // 监听设置变化（预览页等入口改的），让测试页立即重绘
        settingsPrefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            applyRootBackground()
            testView?.postInvalidate()
            // 模式按钮的底色/文字颜色跟着设置变
            applySwitchBtnColor(switchBtnRef, animate = false)
        }
        settingsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val switchBtn = TextView(this).apply {
            text = "切换精度模式"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            val padH = (12f * resources.displayMetrics.density).toInt()
            val padV = (6f * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            // 半透明层跟随"线条测试的自定义背景色"（α 与原实现一致）
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10f * resources.displayMetrics.density
                setColor((ThemeSettings.testLineBgColor and 0x00FFFFFF) or 0x44000000)
            }
            setOnClickListener {
                toggleTestMode() // 使用统一的切换方法
                this.text = if (testView?.isAdvancedMode == true) "切换圆角模式" else "切换精度模式"
            }
            visibility = if (ThemeSettings.isCompactModeEnabled) View.GONE else View.VISIBLE
        }
        switchBtnRef = switchBtn
        switchBtnColor = (ThemeSettings.testLineBgColor and 0x00FFFFFF) or 0x44000000

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 150
            rightMargin = 50
        }
        rootLayout.addView(switchBtn, btnParams)

        setContentView(rootLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)

        // 长按倒计时逻辑（设置里可关闭）
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (ThemeSettings.longPressExitEnabled) {
                    currentTimer = ThemeSettings.longPressExitSeconds
                    testView?.remainingSeconds = currentTimer
                    handler.removeCallbacks(timerRunnable)
                    handler.postDelayed(timerRunnable, 1000)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(timerRunnable)
                currentTimer = 0
                testView?.remainingSeconds = 0
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        // 回到测试页时同步一次按钮与画面
        applySwitchBtnColor(switchBtnRef, animate = false)
        testView?.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        prefsListener?.let { settingsPrefs.unregisterOnSharedPreferenceChangeListener(it) }
    }
}