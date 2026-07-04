package com.mejoresiagratis.rellenador.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mejoresiagratis.rellenador.data.model.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rellenador")

/**
 * Replaces the browser localStorage: enabled providers, contract/signature
 * history pointers, and the commercial profile. API keys stay in the proxy;
 * if you ever go proxy-less, encrypt them with EncryptedSharedPreferences.
 */
@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val enabledKey = stringSetPreferencesKey("enabled_providers")
    private val profileKey = stringPreferencesKey("commercial_profile")

    val enabledProviders: Flow<List<AiProvider>> =
        context.dataStore.data.map { prefs ->
            (prefs[enabledKey] ?: setOf("claude", "gemini", "groq", "mistral", "eurouter"))
                .mapNotNull { AiProvider.fromId(it) }
        }

    suspend fun setEnabled(providers: List<AiProvider>) {
        context.dataStore.edit { it[enabledKey] = providers.map { p -> p.id }.toSet() }
    }

    val profile: Flow<String> =
        context.dataStore.data.map { it[profileKey] ?: "" }

    suspend fun saveProfile(json: String) {
        context.dataStore.edit { it[profileKey] = json }
    }
}
