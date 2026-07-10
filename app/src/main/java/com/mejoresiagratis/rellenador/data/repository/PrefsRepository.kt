package com.mejoresiagratis.rellenador.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ContractProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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


    // --- Tanda F: plantillas por huella, historial de contratos, perfiles ---
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val templatesKey = stringPreferencesKey("templates_v1")     // fingerprint -> mapping JSON
    private val historyIndexKey = stringSetPreferencesKey("history_ids_v1")
    private fun historyEntryKey(id: String) = stringPreferencesKey("history_$id")

    /** Guarda el mapeo (canónica->real) asociado a la huella de una plantilla. */
    suspend fun saveTemplate(fingerprint: String, fieldMapping: Map<String, String>) {
        context.dataStore.edit { p ->
            val all = p[templatesKey]?.let {
                runCatching { json.decodeFromString<Map<String, Map<String, String>>>(it) }.getOrNull()
            }.orEmpty().toMutableMap()
            all[fingerprint] = fieldMapping
            p[templatesKey] = json.encodeToString(all)
        }
    }

    /** Busca un mapeo guardado para esta huella, o null si no existe (plantilla nueva). */
    suspend fun findTemplate(fingerprint: String): Map<String, String>? {
        val raw = context.dataStore.data.map { it[templatesKey] }.first() ?: return null
        val all = runCatching { json.decodeFromString<Map<String, Map<String, String>>>(raw) }.getOrNull()
        return all?.get(fingerprint)
    }

    /** Guarda el contrato actual (campos confirmados) en el historial. */
    suspend fun saveToHistory(profile: ContractProfile): String {
        val id = "h_" + System.currentTimeMillis().toString(36)
        context.dataStore.edit { p ->
            p[historyIndexKey] = (p[historyIndexKey] ?: emptySet()) + id
            p[historyEntryKey(id)] = json.encodeToString(profile)
        }
        return id
    }

    suspend fun listHistory(): List<Pair<String, ContractProfile>> {
        val ids = context.dataStore.data.map { it[historyIndexKey] ?: emptySet() }.first()
        return ids.mapNotNull { id ->
            val raw = context.dataStore.data.map { it[historyEntryKey(id)] }.first() ?: return@mapNotNull null
            val profile = runCatching { json.decodeFromString<ContractProfile>(raw) }.getOrNull() ?: return@mapNotNull null
            id to profile
        }.sortedByDescending { it.second.guardado }
    }

    suspend fun deleteFromHistory(id: String) {
        context.dataStore.edit { p ->
            p[historyIndexKey] = (p[historyIndexKey] ?: emptySet()) - id
            p.remove(historyEntryKey(id))
        }
    }

    /** Serializa el perfil actual a JSON (para exportar a fichero). */
    fun exportProfileJson(profile: ContractProfile): String = json.encodeToString(profile)

    /** Parsea un JSON de perfil importado; valida que sea de esta app. */
    fun importProfileJson(raw: String): ContractProfile? {
        val p = runCatching { json.decodeFromString<ContractProfile>(raw) }.getOrNull() ?: return null
        return if (p.tipo == "perfil-rellenador-pdv") p else null
    }


    // --- Ajustes: perfil comercial (Responsable) + URL del proxy ---
    private val responsableKey = stringPreferencesKey("responsable_comercial")
    private val proxyUrlKey = stringPreferencesKey("proxy_base_url_override")

    /** Nombre del responsable comercial que se autorrellena en el contrato. */
    val responsableComercial: Flow<String> =
        context.dataStore.data.map { it[responsableKey] ?: DEFAULT_RESPONSABLE }

    suspend fun setResponsableComercial(value: String) {
        context.dataStore.edit { it[responsableKey] = value }
    }

    /** URL del proxy si el usuario la sobrescribe; vacío = usar la de compilación. */
    val proxyBaseUrlOverride: Flow<String> =
        context.dataStore.data.map { it[proxyUrlKey] ?: "" }

    suspend fun setProxyBaseUrlOverride(url: String) {
        context.dataStore.edit { it[proxyUrlKey] = url.trim() }
    }

    companion object {
        const val DEFAULT_RESPONSABLE = "PABLO SALVADOR POVEDA"
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
