package com.autoclick.macro.model

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
data class ScreenSize(val width: Int, val height: Int) {
    val isLandscape: Boolean get() = width > height

    fun sameAs(other: ScreenSize): Boolean = width == other.width && height == other.height

    fun isRotationOf(other: ScreenSize): Boolean = width == other.height && height == other.width && width != height

    fun describe(): String = "${width}×${height}"
}

@Serializable
data class Step(
    val id: String = newId(),
    val x: Int,
    val y: Int,
    /** 本步点击完成后的等待时间（毫秒）。 */
    val delayMs: Long = PlanLimits.DEFAULT_DELAY_MS,
    /** 长按时长（毫秒）；0 表示普通点击。 */
    val holdMs: Long = 0,
)

@Serializable
data class Plan(
    val id: String = newId(),
    val name: String,
    val steps: List<Step> = emptyList(),
    /** 循环次数，至少为 1；[PlanLimits.LOOP_INFINITE] 表示无限循环直到手动停止。 */
    val loopCount: Int = 1,
    /** 记录坐标时的屏幕尺寸（绝对像素）；没有任何步骤时为 null。 */
    val screen: ScreenSize? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val isInfinite: Boolean get() = loopCount == PlanLimits.LOOP_INFINITE
}

@Serializable
data class AppData(
    val version: Int = 1,
    val plans: List<Plan> = emptyList(),
    val selectedPlanId: String? = null,
    val countdownSeconds: Int = PlanLimits.DEFAULT_COUNTDOWN_SECONDS,
    val riskAccepted: Boolean = false,
    /** 悬浮条大小（百分比）。 */
    val barScalePercent: Int = PlanLimits.DEFAULT_BAR_SCALE,
    /** 悬浮条不透明度（百分比）。 */
    val barOpacityPercent: Int = PlanLimits.DEFAULT_BAR_OPACITY,
    /** 悬浮按钮待机（开始）时的底色，0xRRGGBB（不含透明度）。 */
    val barColor: Int = PlanLimits.DEFAULT_BAR_COLOR,
) {
    val selectedPlan: Plan? get() = plans.firstOrNull { it.id == selectedPlanId }
}

object PlanLimits {
    const val DEFAULT_DELAY_MS = 500L
    const val MAX_DELAY_MS = 600_000L
    const val MAX_HOLD_MS = 60_000L
    const val TAP_DURATION_MS = 50L
    const val LOOP_INFINITE = 0
    const val MAX_LOOP_COUNT = 9_999
    const val MAX_NAME_LENGTH = 40
    const val DEFAULT_COUNTDOWN_SECONDS = 3
    const val MAX_COUNTDOWN_SECONDS = 30
    const val MIN_BAR_SCALE = 50
    const val MAX_BAR_SCALE = 150
    const val DEFAULT_BAR_SCALE = 80
    const val MIN_BAR_OPACITY = 20
    const val MAX_BAR_OPACITY = 100
    const val DEFAULT_BAR_OPACITY = 90
    const val DEFAULT_BAR_COLOR = 0x2E9E5B
}
