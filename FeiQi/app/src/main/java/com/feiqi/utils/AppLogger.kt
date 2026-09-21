package com.feiqi.utils

import android.util.Log
import com.feiqi.BuildConfig

/**
 * 轻量日志封装：仅在 debug 构建输出，release 自动静默，避免泄露实现细节。
 * 统一 TAG 前缀便于在 Logcat 过滤。不引入第三方日志库，零额外依赖。
 *
 * 用法：AppLogger.d("Schedule", "刷新首页数据")
 */
object AppLogger {

    private const val TAG = "FeiQi"

    fun d(scope: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[$scope] $message")
    }

    fun i(scope: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "[$scope] $message")
    }

    fun w(scope: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, "[$scope] $message", throwable)
    }

    fun e(scope: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, "[$scope] $message", throwable)
    }
}
