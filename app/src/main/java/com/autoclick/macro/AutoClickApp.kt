package com.autoclick.macro

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.autoclick.macro.data.PlanStore

private val Context.planDataStore by preferencesDataStore(name = "autoclick")

class AutoClickApp : Application() {
    val store: PlanStore by lazy { PlanStore(planDataStore) }
}

val Context.planStore: PlanStore get() = (applicationContext as AutoClickApp).store
