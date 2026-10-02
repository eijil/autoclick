package com.autoclick.macro

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.autoclick.macro.data.PlanStore
import com.autoclick.macro.model.ScreenSize
import com.autoclick.macro.model.Step
import com.autoclick.macro.model.addPlan
import com.autoclick.macro.model.addStep
import com.autoclick.macro.model.updatePlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlanStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun newDataStore(scope: kotlinx.coroutines.CoroutineScope, file: File = File(tmp.root, "t.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test
    fun `空存储返回默认数据`() = runTest {
        val store = PlanStore(newDataStore(backgroundScope))
        val data = store.data.first()
        assertTrue(data.plans.isEmpty())
        assertEquals(3, data.countdownSeconds)
    }

    @Test
    fun `写入后可读回，重新打开文件依然存在`() = runTest {
        val file = File(tmp.root, "persist.preferences_pb")
        val first = PlanStore(newDataStore(backgroundScope, file))
        first.update { app ->
            app.addPlan("卖A买B", id = "p")
                .updatePlan("p") { it.addStep(Step(x = 5, y = 6), ScreenSize(1080, 2400)) }
        }
        val read = first.data.first()
        assertEquals("卖A买B", read.plans.single().name)

        val inner = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        try {
            val reopened = PlanStore(newDataStore(inner, File(tmp.root, "persist2.preferences_pb")))
            assertTrue(reopened.data.first().plans.isEmpty())
        } finally {
            inner.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun `损坏的数据回退为空并备份原始内容`() = runTest {
        val ds = newDataStore(backgroundScope)
        ds.edit { it[stringPreferencesKey("app_data_json")] = "{broken" }
        val store = PlanStore(ds)
        assertTrue(store.data.first().plans.isEmpty())
        store.update { it.addPlan("新方案", id = "n") }
        assertEquals("新方案", store.data.first().plans.single().name)
        val backup = ds.data.first()[stringPreferencesKey("app_data_json_corrupt_backup")]
        assertEquals("{broken", backup)
    }
}
