package com.autoclick.macro.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import com.autoclick.macro.model.ScreenSize
import kotlin.math.abs

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** 当前屏幕的绝对像素尺寸（含系统栏，随旋转变化）。 */
@Suppress("DEPRECATION")
fun currentScreenSize(context: Context): ScreenSize {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
    val metrics = DisplayMetrics()
    display.getRealMetrics(metrics)
    return ScreenSize(metrics.widthPixels, metrics.heightPixels)
}

fun roundedBackground(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokeColor != null) setStroke(strokePx, strokeColor)
    }

fun Context.pillButton(label: String, fillColor: Int, textColor: Int = Color.WHITE): TextView =
    TextView(this).apply {
        text = label
        setTextColor(textColor)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setBackground(roundedBackground(fillColor, dp(20).toFloat()))
        isClickable = true
    }

/** 可从任意空白处拖动的容器；按钮等子视图的点击不受影响。 */
class DragLinearLayout(context: Context) : LinearLayout(context) {
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                lastX = downX; lastY = downY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                    dragging = true
                    lastX = ev.rawX; lastY = ev.rawY
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                lastX = downX; lastY = downY
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    onDrag?.invoke((ev.rawX - lastX).toInt(), (ev.rawY - lastY).toInt())
                    lastX = ev.rawX; lastY = ev.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
