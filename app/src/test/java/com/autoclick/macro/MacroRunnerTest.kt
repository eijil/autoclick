package com.autoclick.macro

import com.autoclick.macro.engine.ClickDispatcher
import com.autoclick.macro.engine.MacroRunner
import com.autoclick.macro.engine.RunEvent
import com.autoclick.macro.engine.RunState
import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.Step
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroRunnerTest {
    private data class Click(val x: Int, val y: Int, val hold: Long, val at: Long)

    private class Recorder(private val scope: TestScope, var failAt: Int = -1) : ClickDispatcher {
        val clicks = mutableListOf<Click>()
        override suspend fun click(x: Int, y: Int, holdMs: Long): Boolean {
            clicks += Click(x, y, holdMs, scope.currentTime)
            return clicks.size != failAt
        }
    }

    private fun plan(loops: Int = 1) = Plan(
        id = "p", name = "测试", loopCount = loops,
        steps = listOf(
            Step(id = "1", x = 10, y = 20, delayMs = 500),
            Step(id = "2", x = 30, y = 40, delayMs = 200, holdMs = 700),
        ),
    )

    @Test
    fun `按顺序执行并遵守倒计时与延迟，末步延迟被跳过`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        assertTrue(runner.start(plan(), countdownSeconds = 3))
        advanceUntilIdle()
        assertEquals(
            listOf(Click(10, 20, 0, 3000), Click(30, 40, 700, 3500)),
            rec.clicks,
        )
        assertEquals(3500L, currentTime)
        assertEquals(RunState.Idle, runner.state.value)
    }

    @Test
    fun `循环多次，轮与轮之间保留末步延迟`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        runner.start(plan(loops = 3), countdownSeconds = 0)
        advanceUntilIdle()
        assertEquals(6, rec.clicks.size)
        assertEquals(listOf(0L, 500L, 700L, 1200L, 1400L, 1900L), rec.clicks.map { it.at })
    }

    @Test
    fun `完成后发出 Finished 事件`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        val events = mutableListOf<RunEvent>()
        backgroundScope.launch { runner.events.toList(events) }
        runCurrent()
        runner.start(plan(), 0)
        advanceUntilIdle()
        runCurrent()
        assertTrue(events.single() is RunEvent.Finished)
    }

    @Test
    fun `倒计时期间停止则不会点击`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        val events = mutableListOf<RunEvent>()
        backgroundScope.launch { runner.events.toList(events) }
        runCurrent()
        runner.start(plan(), countdownSeconds = 5)
        runCurrent()
        assertEquals(RunState.Countdown(5), runner.state.value)
        advanceTimeBy(2500)
        assertEquals(RunState.Countdown(3), runner.state.value)
        runner.stop()
        advanceUntilIdle()
        runCurrent()
        assertTrue(rec.clicks.isEmpty())
        assertEquals(RunState.Idle, runner.state.value)
        assertTrue(events.single() is RunEvent.Stopped)
    }

    @Test
    fun `执行中随时可停止`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        runner.start(plan(loops = 100), 0)
        advanceTimeBy(1000)
        runner.stop()
        advanceUntilIdle()
        val count = rec.clicks.size
        assertTrue(count in 1..4)
        advanceTimeBy(60_000)
        assertEquals(count, rec.clicks.size)
        assertFalse(runner.isActive)
    }

    @Test
    fun `无限循环持续执行直到停止`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        runner.start(plan(loops = 0), 0)
        advanceTimeBy(10_000)
        assertTrue(rec.clicks.size > 10)
        assertTrue(runner.state.value is RunState.Running)
        assertEquals(0, (runner.state.value as RunState.Running).totalLoops)
        runner.stop()
        advanceUntilIdle()
        assertEquals(RunState.Idle, runner.state.value)
    }

    @Test
    fun `点击失败时中止并发出 Failed`() = runTest {
        val rec = Recorder(this, failAt = 2)
        val runner = MacroRunner(this, rec)
        val events = mutableListOf<RunEvent>()
        backgroundScope.launch { runner.events.toList(events) }
        runCurrent()
        runner.start(plan(loops = 5), 0)
        advanceUntilIdle()
        runCurrent()
        assertEquals(2, rec.clicks.size)
        assertTrue(events.single() is RunEvent.Failed)
        assertEquals(RunState.Idle, runner.state.value)
    }

    @Test
    fun `空方案或已在运行时不能再次启动`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        assertFalse(runner.start(Plan(name = "空"), 0))
        assertTrue(runner.start(plan(), 2))
        assertFalse(runner.start(plan(), 0))
        advanceUntilIdle()
        assertTrue(runner.start(plan(), 0))
    }

    @Test
    fun `运行状态反映轮次与步骤`() = runTest {
        val rec = Recorder(this)
        val runner = MacroRunner(this, rec)
        runner.start(plan(loops = 2), 0)
        runCurrent()
        assertEquals(RunState.Running(1, 2, 1, 2), runner.state.value)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(RunState.Running(1, 2, 2, 2), runner.state.value)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(RunState.Running(2, 2, 1, 2), runner.state.value)
        runner.stop()
        advanceUntilIdle()
    }
}
