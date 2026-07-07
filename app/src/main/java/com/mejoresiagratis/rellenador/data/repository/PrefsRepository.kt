package com.mejoresiagratis.rellenador.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mejoresiagratis.rellenador.data.model.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    // --- Firmas guardadas (Tanda E) ---
    private val sigListKey = stringSetPreferencesKey("saved_signatures")
    private fun sigDataKey(name: String) = stringPreferencesKey("sig_data_$name")
    private fun sigArKey(name: String) = stringPreferencesKey("sig_ar_$name")

    suspend fun saveSignature(name: String, b64: String, aspectRatio: Float) {
        context.dataStore.edit { p ->
            p[sigListKey] = (p[sigListKey] ?: emptySet()) + name
            p[sigDataKey(name)] = b64
            p[sigArKey(name)] = aspectRatio.toString()
        }
    }
    suspend fun listSignatures(): List<String> =
        context.dataStore.data.map { it[sigListKey]?.toList() ?: emptyList() }.first()

    /** Devuelve (base64, aspectRatio) o null. */
    suspend fun getSignature(name: String): Pair<String, Float>? =
        context.dataStore.data.map { p ->
            val b64 = p[sigDataKey(name)] ?: return@map null
            val ar = p[sigArKey(name)]?.toFloatOrNull() ?: 0.4f
            b64 to ar
        }.first()

}
