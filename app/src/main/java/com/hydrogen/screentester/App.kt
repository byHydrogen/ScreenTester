package com.hydrogen.screentester

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle

/**
 * 按屏幕尺寸统一决定方向：大屏（sw ≥ 600dp）跟随用户旋转，手机锁竖屏（MainActivity 例外）。
 * manifest 里不声明方向 —— 低版本系统会对声明了方向的应用做 letterbox 兼容。
 * 用 LifecycleCallbacks 是为了不用改每个 Activity 的 onCreate。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                // 方向统一在这里决定：大屏跟随旋转；手机锁竖屏，MainActivity 例外
                if (activity.resources.configuration.smallestScreenWidthDp >= DeviceUtils.LARGE_SCREEN_MIN_WIDTH_DP) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                } else if (activity is MainActivity) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
