package com.autoclick.macro.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.autoclick.macro.engine.RunState
import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.PlanLimits

/** 悬浮按钮外观：大小 / 不透明度（百分比）与待机（开始）时的底色（0xRRGGBB）。 */
data class BarStyle(
    val scalePercent: Int = PlanLimits.DEFAULT_BAR_SCALE,
    val opacityPercent: Int = PlanLimits.DEFAULT_BAR_OPACITY,
    val colorRgb: Int = PlanLimits.DEFAULT_BAR_COLOR,
) {
    val scale: Float get() = scalePercent / 100f
    val alpha: Float get() = opacityPercent / 100f
    val colorArgb: Int get() = Color.rgb((colorRgb shr 16) and 0xFF, (colorRgb shr 8) and 0xFF, colorRgb and 0xFF)
}

fun AppData.barStyle() = BarStyle(barScalePercent, barOpacityPercent, barColor)

/** 底色较亮时用深色图标，否则用白色图标。 */
fun iconColorOn(fill: Int): Int =
    if (ColorUtils.calculateLuminance(fill) > 0.6) Color.parseColor("#2A3340") else Color.WHITE

/**
 * 圆形悬浮按钮：待机显示“开始”三角，执行/倒计时显示“停止”方块并改为红色。
 * 两种状态大小完全一致，只有图标和颜色不同。可从按钮上任意位置拖动。
 */
class FloatingBar(
    private val context: Context,
    private val wm: WindowManager,
    private val onToggleRun: () -> Unit,
) {
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = context.dp(16)
        y = context.dp(120)
    }

    private val root = DragLinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        onDrag = { dx, dy -> moveBy(dx, dy) }
    }

    private val button = RunButtonView(context).apply {
        isClickable = true
        setOnClickListener { onToggleRun() }
    }

    private var attached = false
    private var style = BarStyle()
    private var running = false

    init {
        root.addView(button, LinearLayout.LayoutParams(diameterPx(), diameterPx()))
        refresh()
    }

    fun show() {
        if (attached) return
        wm.addView(root, params)
        attached = true
    }

    fun hide() {
        if (!attached) return
        wm.removeView(root)
        attached = false
    }

    fun applyStyle(newStyle: BarStyle) {
        if (newStyle == style) return
        style = newStyle
        button.layoutParams = LinearLayout.LayoutParams(diameterPx(), diameterPx())
        refresh()
        if (attached) {
            wm.updateViewLayout(root, params)
            // 尺寸变化后确保仍在屏幕内。
            root.post { moveBy(0, 0) }
        }
    }

    fun render(state: RunState) {
        val nowRunning = state != RunState.Idle
        if (nowRunning == running) return
        running = nowRunning
        refresh()
    }

    private fun diameterPx(): Int = (56f * style.scale * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun refresh() {
        root.alpha = style.alpha
        val fill = if (running) STOP_COLOR else style.colorArgb
        button.update(running, fill, iconColorOn(fill))
    }

    private fun moveBy(dx: Int, dy: Int) {
        val size = currentScreenSize(context)
        params.x = (params.x + dx).coerceIn(0, (size.width - root.width).coerceAtLeast(0))
        params.y = (params.y + dy).coerceIn(0, (size.height - root.height).coerceAtLeast(0))
        if (attached) wm.updateViewLayout(root, params)
    }

    private companion object {
        val STOP_COLOR: Int = Color.parseColor("#D64545")
    }
}

/** 自绘的圆形按钮：圆底 + 播放三角 / 停止方块。 */
private class RunButtonView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var running = false
    private var fill = Color.TRANSPARENT
    private var iconColor = Color.WHITE

    fun update(running: Boolean, fill: Int, iconColor: Int) {
        this.running = running
        this.fill = fill
        this.iconColor = iconColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f

        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawCircle(cx, cy, d / 2f, paint)

        paint.color = iconColor
        if (running) {
            val half = d * 0.17f
            rect.set(cx - half, cy - half, cx + half, cy + half)
            canvas.drawRoundRect(rect, d * 0.04f, d * 0.04f, paint)
        } else {
            // 三角形重心在底边 1/3 处，整体略向右补偿，视觉上居中。
            val half = d * 0.2f
            val left = cx - half * 0.5f
            path.reset()
            path.moveTo(left, cy - half)
            path.lineTo(left + half * 1.8f, cy)
            path.lineTo(left, cy + half)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
