package com.autoclick.macro.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** 执行前倒计时：屏幕中央的数字，不拦截触摸；大小与透明度跟随悬浮条设置。 */
class CountdownOverlay(private val context: Context, private val wm: WindowManager) {
    private var view: TextView? = null
    var style = BarStyle()

    fun show(seconds: Int) {
        val tv = view ?: TextView(context).apply {
            val s = style.scale
            textSize = 48f * s
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = style.alpha
            setShadowLayer(12f, 0f, 0f, Color.BLACK)
            setPadding(context.dp(24), context.dp(12), context.dp(24), context.dp(12))
            background = roundedBackground(Color.parseColor("#80000000"), context.dp(20).toFloat())
        }.also {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER }
            wm.addView(it, params)
            view = it
        }
        tv.text = seconds.toString()
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
