package com.autoclick.macro.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.autoclick.macro.engine.RunState
import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.PlanLimits

/** 悬浮条外观：大小 / 不透明度（百分比）与背景色（0xRRGGBB）。 */
data class BarStyle(
    val scalePercent: Int = PlanLimits.DEFAULT_BAR_SCALE,
    val opacityPercent: Int = PlanLimits.DEFAULT_BAR_OPACITY,
    val colorRgb: Int = PlanLimits.DEFAULT_BAR_COLOR,
) {
    val scale: Float get() = scalePercent / 100f
    val alpha: Float get() = opacityPercent / 100f
    val colorArgb: Int get() = Color.rgb((colorRgb shr 16) and 0xFF, (colorRgb shr 8) and 0xFF, colorRgb and 0xFF)

    /** 背景较亮时用深色文字，否则用浅色文字。 */
    val textColor: Int
        get() = if (ColorUtils.calculateLuminance(colorArgb) > 0.5) Color.parseColor("#2A3340") else Color.parseColor("#E6ECF5")
}

fun AppData.barStyle() = BarStyle(barScalePercent, barOpacityPercent, barColor)

/** 可拖动的悬浮控制条：开始 / 停止，执行时显示进度。大小、透明度、颜色由应用内设置。 */
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
        gravity = Gravity.CENTER_VERTICAL
        onDrag = { dx, dy -> moveBy(dx, dy) }
    }

    /** 拖动把手。 */
    private val grip = TextView(context).apply {
        text = "⋮"
        gravity = Gravity.CENTER
    }

    private val statusText = TextView(context).apply {
        maxLines = 1
        visibility = View.GONE
    }

    private val runButton = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        setOnClickListener { onToggleRun() }
    }

    private var attached = false
    private var style = BarStyle()
    private var state: RunState = RunState.Idle

    init {
        root.addView(grip)
        root.addView(statusText)
        root.addView(runButton)
        applyLayout()
        renderState()
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
        applyLayout()
        renderState()
        if (attached) {
            wm.updateViewLayout(root, params)
            // 尺寸变化后确保仍在屏幕内。
            root.post { moveBy(0, 0) }
        }
    }

    fun render(state: RunState) {
        this.state = state
        renderState()
        if (attached) wm.updateViewLayout(root, params)
    }

    private fun px(value: Float): Int = (value * style.scale * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun setSp(view: TextView, sp: Float) = view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * style.scale)

    /** 按当前样式刷新尺寸、颜色、透明度。 */
    private fun applyLayout() {
        root.alpha = style.alpha
        root.setPadding(px(4f), px(4f), px(8f), px(4f))
        root.background = roundedBackground(style.colorArgb, px(22f).toFloat())

        setSp(grip, 18f)
        grip.setTextColor(style.textColor)
        grip.alpha = 0.6f
        grip.setPadding(px(6f), 0, px(4f), 0)

        setSp(statusText, 12f)
        statusText.setTextColor(style.textColor)
        statusText.setPadding(px(4f), 0, px(8f), 0)

        setSp(runButton, 14f)
        runButton.setPadding(px(14f), px(7f), px(14f), px(7f))
    }

    private fun renderState() {
        val running = state != RunState.Idle
        statusText.visibility = if (running) View.VISIBLE else View.GONE
        statusText.text = when (val s = state) {
            RunState.Idle -> ""
            is RunState.Countdown -> "即将开始 ${s.remainingSeconds}"
            is RunState.Running -> {
                val loops = if (s.totalLoops == 0) "∞" else s.totalLoops.toString()
                "${s.loop}/$loops · ${s.step}/${s.stepCount}"
            }
        }
        runButton.text = if (running) "停止" else "开始"
        runButton.background = roundedBackground(
            Color.parseColor(if (running) "#D64545" else "#2E9E5B"),
            px(18f).toFloat(),
        )
    }

    private fun moveBy(dx: Int, dy: Int) {
        val size = currentScreenSize(context)
        params.x = (params.x + dx).coerceIn(0, (size.width - root.width).coerceAtLeast(0))
        params.y = (params.y + dy).coerceIn(0, (size.height - root.height).coerceAtLeast(0))
        if (attached) wm.updateViewLayout(root, params)
    }
}
