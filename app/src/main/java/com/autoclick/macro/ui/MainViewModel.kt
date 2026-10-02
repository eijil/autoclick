package com.autoclick.macro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.PlanLimits
import com.autoclick.macro.model.ScreenSize
import com.autoclick.macro.model.Step
import com.autoclick.macro.model.addPlan
import com.autoclick.macro.model.addStep
import com.autoclick.macro.model.deletePlan
import com.autoclick.macro.model.duplicatePlan
import com.autoclick.macro.model.moveStep
import com.autoclick.macro.model.newId
import com.autoclick.macro.model.recalibrate
import com.autoclick.macro.model.removeStep
import com.autoclick.macro.model.rename
import com.autoclick.macro.model.select
import com.autoclick.macro.model.updatePlan
import com.autoclick.macro.model.updateStep
import com.autoclick.macro.model.withBarColor
import com.autoclick.macro.model.withBarOpacity
import com.autoclick.macro.model.withBarScale
import com.autoclick.macro.model.withCountdown
import com.autoclick.macro.model.withLoopCount
import com.autoclick.macro.planStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.planStore

    /** 首次读取完成前为 null。 */
    val data: StateFlow<AppData?> = store.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun edit(transform: (AppData) -> AppData) {
        viewModelScope.launch { store.update(transform) }
    }

    private fun now() = System.currentTimeMillis()

    fun acceptRisk() = edit { it.copy(riskAccepted = true) }

    fun setCountdown(seconds: Int) = edit { it.withCountdown(seconds) }

    fun setBarScale(percent: Int) = edit { it.withBarScale(percent) }

    fun setBarOpacity(percent: Int) = edit { it.withBarOpacity(percent) }

    fun setBarColor(rgb: Int) = edit { it.withBarColor(rgb) }

    fun createPlan(name: String?): String {
        val id = newId()
        edit { it.addPlan(name, now(), id) }
        return id
    }

    fun renamePlan(planId: String, name: String) = edit { app -> app.updatePlan(planId) { it.rename(name, now()) } }

    fun deletePlan(planId: String) = edit { it.deletePlan(planId) }

    fun duplicatePlan(planId: String) = edit { it.duplicatePlan(planId, now()) }

    fun selectPlan(planId: String) = edit { it.select(planId) }

    fun setLoopCount(planId: String, count: Int) =
        edit { app -> app.updatePlan(planId) { it.withLoopCount(count, now()) } }

    fun addManualStep(planId: String, step: Step, screen: ScreenSize) =
        edit { app -> app.updatePlan(planId) { it.addStep(step, screen, now()) } }

    fun updateStep(planId: String, stepId: String, x: Int, y: Int, delayMs: Long, holdMs: Long) =
        edit { app ->
            app.updatePlan(planId) { plan ->
                plan.updateStep(stepId, now()) { it.copy(x = x, y = y, delayMs = delayMs, holdMs = holdMs) }
            }
        }

    fun removeStep(planId: String, stepId: String) =
        edit { app -> app.updatePlan(planId) { it.removeStep(stepId, now()) } }

    fun moveStep(planId: String, stepId: String, offset: Int) =
        edit { app -> app.updatePlan(planId) { it.moveStep(stepId, offset, now()) } }

    fun recalibrate(planId: String, screen: ScreenSize) =
        edit { app -> app.updatePlan(planId) { it.recalibrate(screen, now()) } }

    companion object {
        const val DEFAULT_DELAY = PlanLimits.DEFAULT_DELAY_MS
    }
}
