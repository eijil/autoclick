package com.autoclick.macro.engine

import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.PlanLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 执行一次点击。返回 false 表示点击没有成功派发。 */
fun interface ClickDispatcher {
    suspend fun click(x: Int, y: Int, holdMs: Long): Boolean
}

sealed interface RunState {
    data object Idle : RunState
    data class Countdown(val remainingSeconds: Int) : RunState

    /** [loop]、[step] 均从 1 开始；[totalLoops] 为 0 表示无限循环。 */
    data class Running(val loop: Int, val totalLoops: Int, val step: Int, val stepCount: Int) : RunState
}

sealed interface RunEvent {
    data class Finished(val plan: Plan) : RunEvent
    data class Stopped(val plan: Plan) : RunEvent
    data class Failed(val plan: Plan, val reason: String) : RunEvent
}

class MacroRunner(
    private val scope: CoroutineScope,
    private val dispatcher: ClickDispatcher,
) {
    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RunEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RunEvent> = _events.asSharedFlow()

    private var job: Job? = null

    val isActive: Boolean get() = _state.value != RunState.Idle

    /** 开始执行；已有任务在运行或方案为空时返回 false。 */
    fun start(plan: Plan, countdownSeconds: Int): Boolean {
        if (isActive || plan.steps.isEmpty()) return false
        _state.value = RunState.Countdown(countdownSeconds.coerceAtLeast(0))
        job = scope.launch(start = CoroutineStart.ATOMIC) { run(plan, countdownSeconds.coerceAtLeast(0)) }
        return true
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun run(plan: Plan, countdownSeconds: Int) {
        try {
            for (remaining in countdownSeconds downTo 1) {
                _state.value = RunState.Countdown(remaining)
                delay(1000)
            }
            val totalLoops = plan.loopCount
            var loop = 1
            while (totalLoops == PlanLimits.LOOP_INFINITE || loop <= totalLoops) {
                val isLastLoop = totalLoops != PlanLimits.LOOP_INFINITE && loop == totalLoops
                plan.steps.forEachIndexed { index, step ->
                    _state.value = RunState.Running(loop, totalLoops, index + 1, plan.steps.size)
                    if (!dispatcher.click(step.x, step.y, step.holdMs)) {
                        _events.tryEmit(RunEvent.Failed(plan, "点击未能执行，请确认无障碍服务仍在运行"))
                        return
                    }
                    val isVeryLast = isLastLoop && index == plan.steps.lastIndex
                    if (!isVeryLast && step.delayMs > 0) delay(step.delayMs)
                }
                loop++
                // 无限循环且全部延迟为 0 时，让出时间片，避免空转占满主线程
                if (totalLoops == PlanLimits.LOOP_INFINITE) delay(1)
            }
            _events.tryEmit(RunEvent.Finished(plan))
        } catch (e: kotlinx.coroutines.CancellationException) {
            _events.tryEmit(RunEvent.Stopped(plan))
            throw e
        } finally {
            _state.value = RunState.Idle
        }
    }
}
