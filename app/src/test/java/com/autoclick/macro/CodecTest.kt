package com.autoclick.macro

import com.autoclick.macro.data.Codec
import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.ScreenSize
import com.autoclick.macro.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {
    private val sample = AppData(
        plans = listOf(
            Plan(
                id = "p1", name = "卖A买B",
                steps = listOf(
                    Step(id = "s1", x = 540, y = 1200, delayMs = 800),
                    Step(id = "s2", x = 100, y = 200, delayMs = 300, holdMs = 1500),
                ),
                loopCount = 3, screen = ScreenSize(1080, 2400), createdAt = 1L, updatedAt = 2L,
            ),
            Plan(id = "p2", name = "空 \"引号\" 与 emoji", loopCount = 0),
        ),
        selectedPlanId = "p1",
        countdownSeconds = 5,
        riskAccepted = true,
    )

    @Test
    fun `应用数据往返序列化一致`() {
        val text = Codec.encode(sample)
        assertEquals(sample, Codec.decode(text))
    }

    @Test
    fun `编码包含默认值，字段名稳定`() {
        val text = Codec.encode(AppData(plans = listOf(Plan(id = "x", name = "n"))))
        assertTrue(text.contains("\"loopCount\":1"))
        assertTrue(text.contains("\"countdownSeconds\":3"))
        assertTrue(text.contains("\"riskAccepted\":false"))
    }

    @Test
    fun `未知字段与缺失字段都能解析`() {
        val text = """{"plans":[{"id":"a","name":"n","future":42,"steps":[{"x":1,"y":2}]}],"extra":true}"""
        val data = Codec.decode(text)!!
        val step = data.plans.single().steps.single()
        assertEquals(1, step.x)
        assertEquals(500L, step.delayMs)
        assertEquals(0L, step.holdMs)
        assertTrue(step.id.isNotEmpty())
        assertEquals(1, data.plans.single().loopCount)
        assertEquals(3, data.countdownSeconds)
    }

    @Test
    fun `损坏或空内容返回 null`() {
        assertNull(Codec.decode(null))
        assertNull(Codec.decode(""))
        assertNull(Codec.decode("{not json"))
        assertNull(Codec.decode("""{"plans":"oops"}"""))
    }

    @Test
    fun `解码会清洗非法数值`() {
        val text = """{"plans":[{"id":"a","name":"n","loopCount":-9,"steps":[{"id":"s","x":-3,"y":4,"delayMs":-1}]}],"countdownSeconds":-1}"""
        val data = Codec.decode(text)!!
        assertEquals(0, data.plans.single().loopCount)
        assertEquals(0, data.plans.single().steps.single().x)
        assertEquals(0L, data.plans.single().steps.single().delayMs)
        assertEquals(0, data.countdownSeconds)
    }

    @Test
    fun `单个方案可单独导出导入`() {
        val plan = sample.plans.first()
        assertEquals(plan, Codec.decodePlan(Codec.encodePlan(plan)))
        assertNull(Codec.decodePlan("garbage"))
        assertNotNull(Codec.decodePlan("""{"id":"z","name":"ok"}"""))
    }
}
