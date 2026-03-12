package com.calendersharing.test.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val MY_EVENT_COLOR = stringPreferencesKey("my_event_color")
        private val SHARED_EVENT_COLOR = stringPreferencesKey("shared_event_color")

        const val DEFAULT_MY_COLOR = 0xFF4285F4L     // Google Blue
        const val DEFAULT_SHARED_COLOR = 0xFFEA4335L  // Google Red
    }

    val myEventColor: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[MY_EVENT_COLOR]?.toLongOrNull() ?: DEFAULT_MY_COLOR
    }

    val sharedEventColor: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[SHARED_EVENT_COLOR]?.toLongOrNull() ?: DEFAULT_SHARED_COLOR
    }

    suspend fun setMyEventColor(color: Long) {
        context.dataStore.edit { it[MY_EVENT_COLOR] = color.toString() }
    }

    suspend fun setSharedEventColor(color: Long) {
        context.dataStore.edit { it[SHARED_EVENT_COLOR] = color.toString() }
    }
}
