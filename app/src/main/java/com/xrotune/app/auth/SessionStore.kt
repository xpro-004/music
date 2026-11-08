package com.xrotune.app.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xrotune_session")

/** Stores non-secret display metadata and guest preference. Firebase owns authentication tokens. */
class SessionStore(private val context: Context) {
    private object Keys {
        val id = stringPreferencesKey("account_id")
        val name = stringPreferencesKey("account_name")
        val email = stringPreferencesKey("account_email")
        val photo = stringPreferencesKey("account_photo")
        val guest = booleanPreferencesKey("guest_mode")
        // Remove the token persisted by older app versions; Firebase owns auth credentials.
        val legacyGoogleToken = stringPreferencesKey("account_token")
    }

    val isGuest: Flow<Boolean> = context.dataStore.data.map { it[Keys.guest] ?: false }

    val account: Flow<UserAccount?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.id] ?: return@map null
        UserAccount(id, prefs[Keys.name], prefs[Keys.email], prefs[Keys.photo])
    }

    suspend fun save(account: UserAccount) {
        context.dataStore.edit { prefs ->
            prefs[Keys.id] = account.id
            putOrRemove(prefs, Keys.name, account.displayName)
            putOrRemove(prefs, Keys.email, account.email)
            putOrRemove(prefs, Keys.photo, account.photoUrl)
            prefs[Keys.guest] = false
        }
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.guest] = enabled
            if (enabled) clearAccount(prefs)
        }
    }

    suspend fun clearAccount() { context.dataStore.edit(::clearAccount) }
    suspend fun clear() { context.dataStore.edit { it.clear() } }

    private fun clearAccount(prefs: MutablePreferences) {
        prefs.remove(Keys.id)
        prefs.remove(Keys.name)
        prefs.remove(Keys.email)
        prefs.remove(Keys.photo)
        prefs.remove(Keys.legacyGoogleToken)
    }

    private fun putOrRemove(prefs: MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value
    }
}
