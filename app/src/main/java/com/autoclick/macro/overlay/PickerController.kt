package com.autoclick.macro.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.autoclick.macro.model.Step

/** 全屏覆盖层：绘制已有步骤的编号标记；取点开启时拦截触摸并回传屏幕绝对坐标。 */
class PickerView(context: Context) : View(context) {
    var steps: List<Step> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var capturing: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var onTap: ((x: Int, y: Int) -> Unit)? = null

    private val radius = context.dp(18).toFloat()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5FF6B35") }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.dp(14).toFloat()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val location = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        if (capturing) canvas.drawColor(Color.parseColor("#59000000"))
        getLocationOnScreen(location)
        val path = Path()
        steps.forEachIndexed { i, s ->
            val px = (s.x - location[0]).toFloat()
            val py = (s.y - location[1]).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (steps.size > 1) canvas.drawPath(path, line)
        steps.forEachIndexed { i, s ->
            val px = (s.x - location[0]).toFloat()
            val py = (s.y - location[1]).toFloat()
            canvas.drawCircle(px, py, radius, fill)
            canvas.drawCircle(px, py, radius, ring)
            canvas.drawText((i + 1).toString(), px, py - (label.descent() + label.ascent()) / 2, label)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!capturing) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            onTap?.invoke(event.rawX.toInt(), event.rawY.toInt())
        }
        return true
    }
}

/**
 * 取点模式：一个全屏覆盖层 + 一个可拖动的小控制面板。
 * 初始为“暂停”（触摸穿透），方便先切换到目标界面，再点“开始取点”。
 */
class PickerController(
    private val context: Context,
    private val wm: WindowManager,
    private val onTap: (x: Int, y: Int) -> Unit,
    private val onUndo: () -> Unit,
    private val onDone: () -> Unit,
) {
    private val view = PickerView(context).apply {
        onTap = { x, y -> this@PickerController.onTap(x, y) }
    }

    private val overlayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val panelParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = context.dp(12)
        y = context.dp(72)
    }

    private val statusText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        maxWidth = context.dp(280)
    }
    private val captureButton = context.pillButton("开始取点", Color.parseColor("#2E9E5B"))
    private val undoButton = context.pillButton("撤销", Color.parseColor("#4A5568"))
    private val doneButton = context.pillButton("完成", Color.parseColor("#3B82F6"))

    private val panel = DragLinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        background = roundedBackground(Color.parseColor("#EB1F2430"), context.dp(18).toFloat())
        onDrag = { dx, dy ->
            val size = currentScreenSize(context)
            panelParams.x = (panelParams.x + dx).coerceIn(0, (size.width - width).coerceAtLeast(0))
            panelParams.y = (panelParams.y + dy).coerceIn(0, (size.height - height).coerceAtLeast(0))
            if (attached) wm.updateViewLayout(this, panelParams)
        }
        addView(statusText)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) } }
                addView(captureButton, lp())
                addView(undoButton, lp())
                addView(doneButton, lp())
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = context.dp(8) },
        )
    }

    private var attached = false
    private var capturing = false
    private var stepCount = 0
    private var warning: String? = null

    init {
        captureButton.setOnClickListener { setCapturing(!capturing) }
        undoButton.setOnClickListener { onUndo() }
        doneButton.setOnClickListener { onDone() }
        refreshText()
    }

    fun show() {
        if (attached) return
        wm.addView(view, overlayParams)
        wm.addView(panel, panelParams)
        attached = true
    }

    fun hide() {
        if (!attached) return
        runCatching { wm.removeView(panel) }
        runCatching { wm.removeView(view) }
        attached = false
    }

    fun updateSteps(steps: List<Step>) {
        stepCount = steps.size
        view.steps = steps
        if (attached) refreshText()
    }

    fun showWarning(message: String?) {
        warning = message
        refreshText()
    }

    private fun setCapturing(value: Boolean) {
        capturing = value
        warning = null
        view.capturing = value
        overlayParams.flags = if (value) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (attached) wm.updateViewLayout(view, overlayParams)
        captureButton.text = if (value) "暂停取点" else "开始取点"
        refreshText()
    }

    private fun refreshText() {
        val base = if (capturing) {
            "取点中：点击屏幕记录坐标，已记录 $stepCount 个"
        } else {
            "已暂停（触摸穿透）：先切换到目标界面，再点“开始取点”。已记录 $stepCount 个"
        }
        statusText.text = warning?.let { "$it\n$base" } ?: base
        statusText.setTextColor(Color.WHITE)
        if (warning != null) {
            val spannable = android.text.SpannableString(statusText.text)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.parseColor("#FFB4A8")),
                0, warning!!.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            statusText.text = spannable
        }
        if (attached) wm.updateViewLayout(panel, panelParams)
    }

    private companion object {
        const val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
