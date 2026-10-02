package com.autoclick.macro.data

import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.sanitized
import kotlinx.serialization.json.Json

object Codec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    /** 解析失败（损坏的数据）返回 null。 */
    fun decode(text: String?): AppData? {
        if (text.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(AppData.serializer(), text).sanitized() }.getOrNull()
    }

    fun encodePlan(plan: Plan): String = json.encodeToString(Plan.serializer(), plan)

    fun decodePlan(text: String): Plan? =
        runCatching { json.decodeFromString(Plan.serializer(), text) }.getOrNull()
            ?.let { AppData(plans = listOf(it)).sanitized().plans.firstOrNull() }
}
