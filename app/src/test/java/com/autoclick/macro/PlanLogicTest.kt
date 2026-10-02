package com.autoclick.macro

import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.PlanLimits
import com.autoclick.macro.model.ScreenCheck
import com.autoclick.macro.model.ScreenSize
import com.autoclick.macro.model.Step
import com.autoclick.macro.model.addPlan
import com.autoclick.macro.model.addStep
import com.autoclick.macro.model.checkScreen
import com.autoclick.macro.model.deletePlan
import com.autoclick.macro.model.duplicatePlan
import com.autoclick.macro.model.estimatedDurationMs
import com.autoclick.macro.model.formatDuration
import com.autoclick.macro.model.moveStep
import com.autoclick.macro.model.outOfBoundsSteps
import com.autoclick.macro.model.recalibrate
import com.autoclick.macro.model.removeStep
import com.autoclick.macro.model.rename
import com.autoclick.macro.model.sanitized
import com.autoclick.macro.model.select
import com.autoclick.macro.model.updatePlan
import com.autoclick.macro.model.updateStep
import com.autoclick.macro.model.withBarColor
import com.autoclick.macro.model.withBarOpacity
import com.autoclick.macro.model.withBarScale
import com.autoclick.macro.model.withCountdown
import com.autoclick.macro.model.withLoopCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLogicTest {
    private val portrait = ScreenSize(1080, 2400)
    private val landscape = ScreenSize(2400, 1080)

    private fun plan(vararg points: Pair<Int, Int>): Plan =
        points.foldIndexed(Plan(id = "p", name = "测试")) { i, acc, (x, y) ->
            acc.addStep(Step(id = "s$i", x = x, y = y), portrait)
        }

    @Test
    fun `首个步骤记录屏幕尺寸且后续不覆盖`() {
        val p = Plan(id = "p", name = "a")
        assertNull(p.screen)
        val one = p.addStep(Step(x = 10, y = 20), portrait)
        assertEquals(portrait, one.screen)
        val two = one.addStep(Step(x = 30, y = 40), landscape)
        assertEquals(portrait, two.screen)
        assertEquals(listOf(10 to 20, 30 to 40), two.steps.map { it.x to it.y })
    }

    @Test
    fun `添加步骤时数值被规范化`() {
        val p = Plan(name = "a").addStep(
            Step(x = -5, y = 7, delayMs = -1, holdMs = 10_000_000),
            portrait,
        )
        val s = p.steps.single()
        assertEquals(0, s.x)
        assertEquals(7, s.y)
        assertEquals(0L, s.delayMs)
        assertEquals(PlanLimits.MAX_HOLD_MS, s.holdMs)
    }

    @Test
    fun `编辑步骤保留 id 并限制范围`() {
        val p = plan(1 to 1, 2 to 2).updateStep("s1") {
            it.copy(id = "hack", x = 500, delayMs = 99_999_999L, holdMs = 800)
        }
        assertEquals(listOf("s0", "s1"), p.steps.map { it.id })
        val edited = p.steps[1]
        assertEquals(500, edited.x)
        assertEquals(PlanLimits.MAX_DELAY_MS, edited.delayMs)
        assertEquals(800L, edited.holdMs)
    }

    @Test
    fun `编辑不存在的步骤返回原对象`() {
        val p = plan(1 to 1)
        assertSame(p, p.updateStep("nope") { it.copy(x = 9) })
    }

    @Test
    fun `步骤排序上移下移与边界`() {
        val p = plan(1 to 1, 2 to 2, 3 to 3)
        assertEquals(listOf("s1", "s0", "s2"), p.moveStep("s1", -1).steps.map { it.id })
        assertEquals(listOf("s1", "s2", "s0"), p.moveStep("s0", 2).steps.map { it.id })
        assertEquals(listOf("s0", "s1", "s2"), p.moveStep("s0", -1).steps.map { it.id })
        assertEquals(listOf("s2", "s0", "s1"), p.moveStep("s2", -10).steps.map { it.id })
        assertSame(p, p.moveStep("missing", 1))
    }

    @Test
    fun `删除步骤，删光后清除屏幕记录`() {
        val p = plan(1 to 1, 2 to 2)
        val one = p.removeStep("s0")
        assertEquals(listOf("s1"), one.steps.map { it.id })
        assertEquals(portrait, one.screen)
        val none = one.removeStep("s1")
        assertTrue(none.steps.isEmpty())
        assertNull(none.screen)
        assertSame(none, none.removeStep("s1"))
    }

    @Test
    fun `循环次数限制范围且 0 表示无限`() {
        val p = Plan(name = "a")
        assertEquals(1, p.loopCount)
        assertEquals(5, p.withLoopCount(5).loopCount)
        assertEquals(PlanLimits.MAX_LOOP_COUNT, p.withLoopCount(1_000_000).loopCount)
        assertEquals(0, p.withLoopCount(-3).loopCount)
        assertTrue(p.withLoopCount(0).isInfinite)
        assertFalse(p.withLoopCount(1).isInfinite)
    }

    @Test
    fun `耗时估算包含长按与循环`() {
        val p = Plan(
            name = "a",
            steps = listOf(
                Step(x = 1, y = 1, delayMs = 500),
                Step(x = 2, y = 2, delayMs = 100, holdMs = 1000),
            ),
            loopCount = 3,
        )
        val single = (PlanLimits.TAP_DURATION_MS + 500) + (1000 + 100)
        assertEquals(single, p.estimatedDurationMs()!! / 3)
        assertEquals(single * 3, p.estimatedDurationMs())
        assertNull(p.withLoopCount(0).estimatedDurationMs())
    }

    @Test
    fun `时长格式化`() {
        assertEquals("500 毫秒", formatDuration(500))
        assertEquals("1.5 秒", formatDuration(1500))
        assertEquals("2 分 5 秒", formatDuration(125_000))
    }

    @Test
    fun `屏幕尺寸校验`() {
        val empty = Plan(name = "a")
        assertEquals(ScreenCheck.NoRecord, empty.checkScreen(portrait))

        val p = plan(1 to 1)
        assertEquals(ScreenCheck.Match, p.checkScreen(portrait))

        val rotated = p.checkScreen(landscape)
        assertTrue(rotated is ScreenCheck.Mismatch && rotated.rotated)
        assertTrue((rotated as ScreenCheck.Mismatch).message().contains("横竖屏不一致"))

        val other = p.checkScreen(ScreenSize(720, 1280))
        assertTrue(other is ScreenCheck.Mismatch && !other.rotated)
        assertTrue((other as ScreenCheck.Mismatch).message().contains("1080×2400"))
    }

    @Test
    fun `正方形屏幕不会被判为旋转`() {
        assertFalse(ScreenSize(1000, 1000).isRotationOf(ScreenSize(1000, 1000)))
    }

    @Test
    fun `校准只改尺寸不改坐标，空方案不记录`() {
        val p = plan(100 to 200)
        val calibrated = p.recalibrate(landscape)
        assertEquals(landscape, calibrated.screen)
        assertEquals(100, calibrated.steps.single().x)
        assertNull(Plan(name = "a").recalibrate(landscape).screen)
    }

    @Test
    fun `超出屏幕范围的步骤`() {
        val p = plan(10 to 10, 1080 to 5, 5 to 2400, 1079 to 2399)
        assertEquals(listOf("s1", "s2"), p.outOfBoundsSteps(portrait).map { it.id })
    }

    @Test
    fun `重命名会去空白并拒绝空名称`() {
        val p = Plan(name = "旧名")
        assertEquals("新名", p.rename("  新名  ").name)
        assertSame(p, p.rename("   "))
        assertEquals(PlanLimits.MAX_NAME_LENGTH, p.rename("长".repeat(100)).name.length)
    }

    @Test
    fun `新建方案使用默认名且不重名，首个方案自动选中`() {
        var d = AppData()
        d = d.addPlan(null, id = "a")
        assertEquals("方案 1", d.plans[0].name)
        assertEquals("a", d.selectedPlanId)
        d = d.addPlan("  ", id = "b")
        assertEquals("方案 2", d.plans[1].name)
        d = d.addPlan("自定义", id = "c")
        assertEquals("自定义", d.plans[2].name)
        assertEquals("a", d.selectedPlanId)
        d = d.deletePlan("b").addPlan(null, id = "e")
        assertEquals("方案 3", d.plans.last().name)
    }

    @Test
    fun `删除当前选中方案后选中第一个，删光后为空`() {
        var d = AppData().addPlan("A", id = "a").addPlan("B", id = "b").addPlan("C", id = "c").select("b")
        assertEquals("b", d.selectedPlanId)
        d = d.deletePlan("a")
        assertEquals("b", d.selectedPlanId)
        d = d.deletePlan("b")
        assertEquals("c", d.selectedPlanId)
        d = d.deletePlan("c")
        assertNull(d.selectedPlanId)
        assertTrue(d.plans.isEmpty())
    }

    @Test
    fun `选择不存在的方案会被忽略`() {
        val d = AppData().addPlan("A", id = "a")
        assertEquals("a", d.select("zzz").selectedPlanId)
    }

    @Test
    fun `更新方案不能改 id`() {
        val d = AppData().addPlan("A", id = "a").updatePlan("a") { it.copy(id = "x", name = "B") }
        assertEquals("a", d.plans.single().id)
        assertEquals("B", d.plans.single().name)
    }

    @Test
    fun `复制方案会生成新的方案与步骤 id`() {
        val src = plan(1 to 1, 2 to 2).withLoopCount(4).copy(id = "p")
        val d = AppData(plans = listOf(src, Plan(id = "q", name = "其他"))).duplicatePlan("p")
        assertEquals(3, d.plans.size)
        val clone = d.plans[1]
        assertNotEquals(src.id, clone.id)
        assertEquals("测试 副本", clone.name)
        assertEquals(4, clone.loopCount)
        assertEquals(src.screen, clone.screen)
        assertEquals(src.steps.map { it.x to it.y }, clone.steps.map { it.x to it.y })
        assertTrue(clone.steps.map { it.id }.intersect(src.steps.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `倒计时设置被限制在范围内`() {
        assertEquals(0, AppData().withCountdown(-4).countdownSeconds)
        assertEquals(PlanLimits.MAX_COUNTDOWN_SECONDS, AppData().withCountdown(999).countdownSeconds)
        assertEquals(5, AppData().withCountdown(5).countdownSeconds)
    }

    @Test
    fun `悬浮条外观设置被限制在范围内`() {
        assertEquals(PlanLimits.MIN_BAR_SCALE, AppData().withBarScale(1).barScalePercent)
        assertEquals(PlanLimits.MAX_BAR_SCALE, AppData().withBarScale(999).barScalePercent)
        assertEquals(PlanLimits.MIN_BAR_OPACITY, AppData().withBarOpacity(0).barOpacityPercent)
        assertEquals(PlanLimits.MAX_BAR_OPACITY, AppData().withBarOpacity(300).barOpacityPercent)
        assertEquals(0x123456, AppData().withBarColor(0x7F123456).barColor)
        val dirty = AppData(barScalePercent = 0, barOpacityPercent = 500, barColor = -1).sanitized()
        assertEquals(PlanLimits.MIN_BAR_SCALE, dirty.barScalePercent)
        assertEquals(PlanLimits.MAX_BAR_OPACITY, dirty.barOpacityPercent)
        assertEquals(0xFFFFFF, dirty.barColor)
    }

    @Test
    fun `sanitized 修复异常数据`() {
        val dirty = AppData(
            plans = listOf(
                Plan(
                    id = "a", name = "   ",
                    steps = listOf(
                        Step(id = "s", x = -1, y = 2, delayMs = -5),
                        Step(id = "s", x = 3, y = 4, holdMs = 999_999_999),
                    ),
                    loopCount = -2, screen = ScreenSize(0, 0),
                ),
                Plan(id = "a", name = "重复 id"),
                Plan(id = "b", name = "空方案", screen = landscape),
            ),
            selectedPlanId = "ghost",
            countdownSeconds = 500,
        ).sanitized()
        assertEquals(2, dirty.plans.size)
        val a = dirty.plans[0]
        assertEquals("未命名方案", a.name)
        assertEquals(2, a.steps.map { it.id }.toSet().size)
        assertEquals(0, a.steps[0].x)
        assertEquals(0L, a.steps[0].delayMs)
        assertEquals(PlanLimits.MAX_HOLD_MS, a.steps[1].holdMs)
        assertEquals(0, a.loopCount)
        assertNull(a.screen)
        assertNull(dirty.plans[1].screen)
        assertEquals("a", dirty.selectedPlanId)
        assertEquals(PlanLimits.MAX_COUNTDOWN_SECONDS, dirty.countdownSeconds)
    }
}
