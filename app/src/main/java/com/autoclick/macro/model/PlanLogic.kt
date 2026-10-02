package com.autoclick.macro.model

/** 屏幕尺寸校验结果。 */
sealed interface ScreenCheck {
    /** 方案还没有记录尺寸（没有步骤）。 */
    data object NoRecord : ScreenCheck

    data object Match : ScreenCheck

    data class Mismatch(val recorded: ScreenSize, val current: ScreenSize, val rotated: Boolean) : ScreenCheck {
        fun message(): String = if (rotated) {
            "当前屏幕为 ${current.describe()}，方案记录于 ${recorded.describe()}（横竖屏不一致）。请旋转屏幕后再使用。"
        } else {
            "当前屏幕尺寸为 ${current.describe()}，与方案记录的 ${recorded.describe()} 不一致，点击位置可能不准确。"
        }
    }
}

fun Plan.checkScreen(current: ScreenSize): ScreenCheck {
    val recorded = screen ?: return ScreenCheck.NoRecord
    if (recorded.sameAs(current)) return ScreenCheck.Match
    return ScreenCheck.Mismatch(recorded, current, recorded.isRotationOf(current))
}

fun Step.normalized(): Step = copy(
    x = x.coerceAtLeast(0),
    y = y.coerceAtLeast(0),
    delayMs = delayMs.coerceIn(0, PlanLimits.MAX_DELAY_MS),
    holdMs = holdMs.coerceIn(0, PlanLimits.MAX_HOLD_MS),
)

/** 超出给定屏幕范围的步骤。 */
fun Plan.outOfBoundsSteps(size: ScreenSize): List<Step> = steps.filter { it.x >= size.width || it.y >= size.height }

/** 末尾追加一个步骤；首个步骤会同时记录屏幕尺寸。 */
fun Plan.addStep(step: Step, current: ScreenSize, now: Long = 0): Plan = copy(
    steps = steps + step.normalized(),
    screen = screen ?: current,
    updatedAt = now,
)

fun Plan.updateStep(stepId: String, now: Long = 0, transform: (Step) -> Step): Plan {
    if (steps.none { it.id == stepId }) return this
    return copy(
        steps = steps.map { if (it.id == stepId) transform(it).copy(id = it.id).normalized() else it },
        updatedAt = now,
    )
}

/** 删除步骤；删光后清除屏幕尺寸记录，下次取点重新记录。 */
fun Plan.removeStep(stepId: String, now: Long = 0): Plan {
    if (steps.none { it.id == stepId }) return this
    val remaining = steps.filterNot { it.id == stepId }
    return copy(steps = remaining, screen = if (remaining.isEmpty()) null else screen, updatedAt = now)
}

/** 把步骤移动 [offset] 个位置（负数向前），越界时停在边界。 */
fun Plan.moveStep(stepId: String, offset: Int, now: Long = 0): Plan {
    val from = steps.indexOfFirst { it.id == stepId }
    if (from < 0) return this
    val to = (from + offset).coerceIn(0, steps.lastIndex)
    if (to == from) return this
    val mutable = steps.toMutableList()
    mutable.add(to, mutable.removeAt(from))
    return copy(steps = mutable, updatedAt = now)
}

fun Plan.withLoopCount(count: Int, now: Long = 0): Plan =
    copy(loopCount = count.coerceIn(0, PlanLimits.MAX_LOOP_COUNT), updatedAt = now)

/** 名称去空白并截断；空白名称返回 null。 */
fun cleanPlanName(raw: String): String? = raw.trim().take(PlanLimits.MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }

fun Plan.rename(raw: String, now: Long = 0): Plan {
    val cleaned = cleanPlanName(raw) ?: return this
    return copy(name = cleaned, updatedAt = now)
}

/** 校准：把记录的屏幕尺寸改为当前尺寸（用户确认坐标仍然适用时使用）。 */
fun Plan.recalibrate(current: ScreenSize, now: Long = 0): Plan =
    if (steps.isEmpty()) this else copy(screen = current, updatedAt = now)

/** 单轮耗时：每步 点击(或长按)时长 + 延迟，最后一步的延迟也计入。 */
fun Plan.singleLoopDurationMs(): Long =
    steps.sumOf { maxOf(it.holdMs, PlanLimits.TAP_DURATION_MS) + it.delayMs }

/** 总耗时估算；无限循环返回 null。 */
fun Plan.estimatedDurationMs(): Long? = if (isInfinite) null else singleLoopDurationMs() * loopCount

fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms} 毫秒"
    val seconds = ms / 1000.0
    if (seconds < 60) return "%.1f 秒".format(seconds)
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60} 分 ${totalSeconds % 60} 秒"
}

fun Plan.loopLabel(): String = if (isInfinite) "无限循环" else "循环 $loopCount 次"

// ---- 方案列表操作 ----

fun AppData.addPlan(rawName: String?, now: Long = 0, id: String = newId()): AppData {
    val name = rawName?.let(::cleanPlanName) ?: defaultPlanName()
    val plan = Plan(id = id, name = name, createdAt = now, updatedAt = now)
    return copy(plans = plans + plan, selectedPlanId = selectedPlanId ?: plan.id)
}

fun AppData.defaultPlanName(): String {
    var n = plans.size + 1
    while (plans.any { it.name == "方案 $n" }) n++
    return "方案 $n"
}

fun AppData.updatePlan(planId: String, transform: (Plan) -> Plan): AppData {
    if (plans.none { it.id == planId }) return this
    return copy(plans = plans.map { if (it.id == planId) transform(it).copy(id = it.id) else it })
}

fun AppData.deletePlan(planId: String): AppData {
    val remaining = plans.filterNot { it.id == planId }
    val selected = if (selectedPlanId == planId || remaining.none { it.id == selectedPlanId }) {
        remaining.firstOrNull()?.id
    } else {
        selectedPlanId
    }
    return copy(plans = remaining, selectedPlanId = selected)
}

fun AppData.duplicatePlan(planId: String, now: Long = 0): AppData {
    val source = plans.firstOrNull { it.id == planId } ?: return this
    val copyName = "${source.name} 副本".take(PlanLimits.MAX_NAME_LENGTH)
    val clone = source.copy(
        id = newId(),
        name = copyName,
        steps = source.steps.map { it.copy(id = newId()) },
        createdAt = now,
        updatedAt = now,
    )
    val index = plans.indexOfFirst { it.id == planId }
    return copy(plans = plans.toMutableList().apply { add(index + 1, clone) })
}

fun AppData.select(planId: String?): AppData =
    if (planId == null || plans.any { it.id == planId }) copy(selectedPlanId = planId) else this

fun AppData.withCountdown(seconds: Int): AppData =
    copy(countdownSeconds = seconds.coerceIn(0, PlanLimits.MAX_COUNTDOWN_SECONDS))

fun AppData.withBarScale(percent: Int): AppData =
    copy(barScalePercent = percent.coerceIn(PlanLimits.MIN_BAR_SCALE, PlanLimits.MAX_BAR_SCALE))

fun AppData.withBarOpacity(percent: Int): AppData =
    copy(barOpacityPercent = percent.coerceIn(PlanLimits.MIN_BAR_OPACITY, PlanLimits.MAX_BAR_OPACITY))

fun AppData.withBarColor(rgb: Int): AppData = copy(barColor = rgb and 0xFFFFFF)

/** 反序列化后的清洗：数值截断、去重 ID、修正选中方案。 */
fun AppData.sanitized(): AppData {
    val seenPlanIds = HashSet<String>()
    val cleanPlans = plans.mapNotNull { plan ->
        if (!seenPlanIds.add(plan.id)) return@mapNotNull null
        val seenStepIds = HashSet<String>()
        val cleanSteps = plan.steps.map { step ->
            val s = if (seenStepIds.add(step.id)) step else step.copy(id = newId())
            s.normalized()
        }
        val screen = plan.screen?.takeIf { it.width > 0 && it.height > 0 }
        plan.copy(
            name = cleanPlanName(plan.name) ?: "未命名方案",
            steps = cleanSteps,
            loopCount = plan.loopCount.coerceIn(0, PlanLimits.MAX_LOOP_COUNT),
            screen = if (cleanSteps.isEmpty()) null else screen,
        )
    }
    val selected = selectedPlanId?.takeIf { id -> cleanPlans.any { it.id == id } } ?: cleanPlans.firstOrNull()?.id
    return copy(
        plans = cleanPlans,
        selectedPlanId = selected,
        countdownSeconds = countdownSeconds.coerceIn(0, PlanLimits.MAX_COUNTDOWN_SECONDS),
        barScalePercent = barScalePercent.coerceIn(PlanLimits.MIN_BAR_SCALE, PlanLimits.MAX_BAR_SCALE),
        barOpacityPercent = barOpacityPercent.coerceIn(PlanLimits.MIN_BAR_OPACITY, PlanLimits.MAX_BAR_OPACITY),
        barColor = barColor and 0xFFFFFF,
    )
}
