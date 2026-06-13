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

    private fun toggleTestMode() {
        testView?.let { tv ->
            tv.isAdvancedMode = !tv.isAdvancedMode
            tv.animate().cancel()
            tv.alpha = 0f
            tv.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction(null)
                .start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (ThemeSettings.isMaxBrightnessEnabled) {
            val lp = window.attributes
            lp.screenBrightness = ThemeSettings.testBrightnessValue
            window.attributes = lp
        }

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        testView = TestFrameView(this)
        rootLayout.addView(testView)

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

        val switchBtn = Button(this).apply {
            text = "切换精度模式"
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(Color.parseColor("#44000000"))
            setOnClickListener {
                toggleTestMode() // 使用统一的切换方法
                this.text = if (testView?.isAdvancedMode == true) "切换圆角模式" else "切换精度模式"
            }
            visibility = if (ThemeSettings.isCompactModeEnabled) View.GONE else View.VISIBLE
        }

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

        // 长按倒计时逻辑
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentTimer = 5
                testView?.remainingSeconds = currentTimer
                handler.removeCallbacks(timerRunnable)
                handler.postDelayed(timerRunnable, 1000)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(timerRunnable)
                currentTimer = 0
                testView?.remainingSeconds = 0
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }
}