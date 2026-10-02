package com.autoclick.macro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.autoclick.macro.MainActivity
import com.autoclick.macro.R
import com.autoclick.macro.engine.MacroRunner
import com.autoclick.macro.engine.RunEvent
import com.autoclick.macro.engine.RunState
import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.ScreenCheck
import com.autoclick.macro.model.Step
import com.autoclick.macro.model.addStep
import com.autoclick.macro.model.checkScreen
import com.autoclick.macro.model.removeStep
import com.autoclick.macro.model.updatePlan
import com.autoclick.macro.overlay.CountdownOverlay
import com.autoclick.macro.overlay.FloatingBar
import com.autoclick.macro.overlay.PickerController
import com.autoclick.macro.overlay.barStyle
import com.autoclick.macro.overlay.currentScreenSize
import com.autoclick.macro.planStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 承载悬浮控制条、取点覆盖层与宏执行器的前台服务。 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var runner: MacroRunner
    private lateinit var bar: FloatingBar
    private lateinit var countdown: CountdownOverlay
    private var picker: PickerController? = null
    private var pickingPlanId: String? = null
    private var data = AppData()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        runner = MacroRunner(scope, AccessibilityClickDispatcher)
        countdown = CountdownOverlay(this, wm)
        bar = FloatingBar(
            context = this,
            wm = wm,
            onToggleRun = ::toggleRun,
        )
        _running.value = true
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "缺少悬浮窗权限，请先在应用中授权", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_PICK -> intent.getStringExtra(EXTRA_PLAN_ID)?.let(::startPicking)
            else -> if (picker == null) bar.show()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runner.stop()
        countdown.hide()
        picker?.hide()
        bar.hide()
        scope.cancel()
        _running.value = false
        super.onDestroy()
    }

    private fun observe() {
        scope.launch {
            applicationContext.planStore.data.collect { latest ->
                data = latest
                bar.applyStyle(latest.barStyle())
                countdown.style = latest.barStyle()
                val planId = pickingPlanId
                if (planId != null) {
                    picker?.updateSteps(latest.plans.firstOrNull { it.id == planId }?.steps.orEmpty())
                }
            }
        }
        scope.launch {
            runner.state.collect { state ->
                bar.render(state)
                if (state is RunState.Countdown && state.remainingSeconds > 0) {
                    countdown.show(state.remainingSeconds)
                } else {
                    countdown.hide()
                }
            }
        }
        scope.launch {
            runner.events.collect { event ->
                val message = when (event) {
                    is RunEvent.Finished -> "「${event.plan.name}」执行完成"
                    is RunEvent.Stopped -> "已停止「${event.plan.name}」"
                    is RunEvent.Failed -> event.reason
                }
                toast(message)
            }
        }
    }

    private fun toggleRun() {
        if (runner.isActive) {
            runner.stop()
            return
        }
        val plan = data.selectedPlan
        when {
            plan == null -> toast("请先在应用中选择一个方案")
            plan.steps.isEmpty() -> toast("「${plan.name}」还没有步骤，请先取点或添加步骤")
            ClickAccessibilityService.instance == null -> toast("无障碍服务未开启，请回到应用完成授权")
            else -> {
                val check = plan.checkScreen(currentScreenSize(this))
                if (check is ScreenCheck.Mismatch) {
                    toast(check.message())
                } else {
                    runner.start(plan, data.countdownSeconds)
                }
            }
        }
    }

    private fun startPicking(planId: String) {
        if (runner.isActive) runner.stop()
        picker?.hide()
        pickingPlanId = planId
        bar.hide()
        val controller = PickerController(
            context = this,
            wm = wm,
            onTap = { x, y -> recordTap(planId, x, y) },
            onUndo = { undoLast(planId) },
            onDone = { finishPicking() },
        )
        picker = controller
        controller.updateSteps(data.plans.firstOrNull { it.id == planId }?.steps.orEmpty())
        controller.show()
    }

    private fun recordTap(planId: String, x: Int, y: Int) {
        val screen = currentScreenSize(this)
        val plan = data.plans.firstOrNull { it.id == planId } ?: return
        val check = plan.checkScreen(screen)
        if (check is ScreenCheck.Mismatch) {
            picker?.showWarning(check.message() + "请在方案编辑页校准或清空步骤后重试。")
            return
        }
        picker?.showWarning(null)
        scope.launch {
            applicationContext.planStore.update { app ->
                app.updatePlan(planId) { it.addStep(Step(x = x, y = y), screen, System.currentTimeMillis()) }
            }
        }
    }

    private fun undoLast(planId: String) {
        val last = data.plans.firstOrNull { it.id == planId }?.steps?.lastOrNull() ?: return
        scope.launch {
            applicationContext.planStore.update { app ->
                app.updatePlan(planId) { it.removeStep(last.id, System.currentTimeMillis()) }
            }
        }
    }

    private fun finishPicking() {
        val count = data.plans.firstOrNull { it.id == pickingPlanId }?.steps?.size ?: 0
        picker?.hide()
        picker = null
        pickingPlanId = null
        bar.show()
        toast("取点结束，当前共 $count 个步骤。回到应用可调整延迟与顺序")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮控制条", NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("屏幕点击宏")
            .setContentText("悬浮控制条运行中")
            .setContentIntent(openApp)
            .addAction(0, "关闭悬浮条", stop)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.autoclick.macro.action.STOP"
        private const val ACTION_PICK = "com.autoclick.macro.action.PICK"
        private const val EXTRA_PLAN_ID = "plan_id"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun showBar(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun startPicking(context: Context, planId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).setAction(ACTION_PICK).putExtra(EXTRA_PLAN_ID, planId),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
