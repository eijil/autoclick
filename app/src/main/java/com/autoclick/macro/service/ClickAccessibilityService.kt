package com.autoclick.macro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.autoclick.macro.engine.ClickDispatcher
import com.autoclick.macro.model.PlanLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 仅用于通过 dispatchGesture 派发点击/长按手势。
 * 不读取、不监听任何屏幕内容（配置中未声明事件类型，也未请求窗口内容检索）。
 */
class ClickAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clearInstance()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearInstance()
        super.onDestroy()
    }

    private fun clearInstance() {
        if (instance === this) instance = null
        _connected.value = false
    }

    /** 在 (x, y) 处按下 [holdMs] 毫秒（0 表示普通点击）。手势完成返回 true，被取消或派发失败返回 false。 */
    suspend fun performClick(x: Int, y: Int, holdMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val duration = maxOf(holdMs, PlanLimits.TAP_DURATION_MS)
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (!dispatchGesture(gesture, callback, null) && cont.isActive) cont.resume(false)
        }

    companion object {
        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }
}

/** 通过无障碍服务派发点击；服务未连接时返回 false。 */
object AccessibilityClickDispatcher : ClickDispatcher {
    override suspend fun click(x: Int, y: Int, holdMs: Long): Boolean =
        ClickAccessibilityService.instance?.performClick(x, y, holdMs) ?: false
}
