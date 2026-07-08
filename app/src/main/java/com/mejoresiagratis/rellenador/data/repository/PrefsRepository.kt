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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rellenador")

/** Una entrada del histórico: un PDF final ya generado (relleno + firmado). */
@Serializable
data class HistoryEntry(
    val id: String,
    val label: String,       // razón social / nombre comercial, o "Contrato sin nombre"
    val filePath: String,     // ruta absoluta en filesDir/output
    val createdAt: Long
)

/**
 * Replaces the browser localStorage: enabled providers, contract/signature
 * history pointers, and the commercial profile. API keys stay in the proxy;
 * if you ever go proxy-less, encrypt them with EncryptedSharedPreferences.
 */
@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val enabledKey = stringSetPreferencesKey("enabled_providers")
    private val responsableKey = stringPreferencesKey("perfil_responsable")
    private val historyKey = stringPreferencesKey("contract_history_json")

    // ---- Motores IA activos ----

    val enabledProviders: Flow<List<AiProvider>> =
        context.dataStore.data.map { prefs ->
            (prefs[enabledKey] ?: setOf("claude", "gemini", "groq", "mistral", "eurouter"))
                .mapNotNull { AiProvider.fromId(it) }
        }

    suspend fun setEnabled(providers: List<AiProvider>) {
        context.dataStore.edit { it[enabledKey] = providers.map { p -> p.id }.toSet() }
    }

    // ---- Perfil comercial ----

    /** Nombre del Responsable Comercial autorrellenado. Vacío = usar ContractFields.RESPONSABLE_VALUE. */
    val responsableComercial: Flow<String> =
        context.dataStore.data.map { it[responsableKey] ?: "" }

    suspend fun setResponsableComercial(nombre: String) {
        context.dataStore.edit { prefs ->
            if (nombre.isBlank()) prefs.remove(responsableKey) else prefs[responsableKey] = nombre.trim()
        }
    }

    // ---- Histórico de contratos generados (Tanda F) ----

    val history: Flow<List<HistoryEntry>> =
        context.dataStore.data.map { prefs ->
            prefs[historyKey]?.let { raw ->
                runCatching { json.decodeFromString<List<HistoryEntry>>(raw) }.getOrNull()
            } ?: emptyList()
        }

    suspend fun addHistoryEntry(entry: HistoryEntry) {
        val updated = history.first() + entry
        context.dataStore.edit { it[historyKey] = json.encodeToString(updated) }
    }

    suspend fun deleteHistoryEntry(id: String) {
        val updated = history.first().filterNot { it.id == id }
        context.dataStore.edit { it[historyKey] = json.encodeToString(updated) }
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
