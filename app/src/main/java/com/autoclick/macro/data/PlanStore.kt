package com.autoclick.macro.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.autoclick.macro.model.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 以 JSON 形式把全部方案与设置保存在 DataStore 中。 */
class PlanStore(private val dataStore: DataStore<Preferences>) {

    val data: Flow<AppData> = dataStore.data
        .map { Codec.decode(it[KEY_DATA]) ?: AppData() }
        .distinctUntilChanged()

    suspend fun update(transform: (AppData) -> AppData) {
        dataStore.edit { prefs ->
            val raw = prefs[KEY_DATA]
            val current = Codec.decode(raw)
            if (current == null && !raw.isNullOrBlank()) {
                prefs[KEY_CORRUPT_BACKUP] = raw
            }
            prefs[KEY_DATA] = Codec.encode(transform(current ?: AppData()))
        }
    }

    private companion object {
        val KEY_DATA = stringPreferencesKey("app_data_json")
        val KEY_CORRUPT_BACKUP = stringPreferencesKey("app_data_json_corrupt_backup")
    }
}
