package ro.unitbv.wheresmybus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserManager(private val context: Context) {

    companion object{
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PASSWORD = stringPreferencesKey("user_password")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_CITY = stringPreferencesKey("user_city")
    }

    suspend fun saveUserData(email: String, passwd: String, name: String, city: String){
        context.dataStore.edit{ prefs ->
            prefs[USER_EMAIL] = email
            prefs[USER_PASSWORD] = passwd
            prefs[USER_NAME] = name
            prefs[USER_CITY] = city
        }
    }

    val userEmailFlow: Flow<String?> = context.dataStore.data.map{ it[USER_EMAIL] }
    val userPasswordFlow: Flow<String?> = context.dataStore.data.map{ it[USER_PASSWORD] }
}