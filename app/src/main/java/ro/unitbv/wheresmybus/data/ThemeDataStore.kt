package ro.unitbv.wheresmybus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")

val Context.settingsDataStore by preferencesDataStore(name = "theme_prefs")
val DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")