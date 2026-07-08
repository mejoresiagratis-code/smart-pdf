package com.mejoresiagratis.rellenador.ui.settings

import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AjustesUiState(
    val responsable: String = "",
    val allProviders: List<AiProvider> = AiProvider.entries,
    val enabledProviders: Set<AiProvider> = emptySet(),
    val justSaved: Boolean = false
) {
    val responsablePlaceholder: String get() = ContractFields.RESPONSABLE_VALUE
}

@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val prefs: PrefsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AjustesUiState())
    val ui: StateFlow<AjustesUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.responsableComercial, prefs.enabledProviders) { responsable, enabled ->
                AjustesUiState(responsable = responsable, enabledProviders = enabled.toSet())
            }.collect { loaded ->
                // Preservar el "Guardado ✓" transitorio al recargar por cambios persistidos.
                _ui.value = loaded.copy(justSaved = _ui.value.justSaved)
            }
        }
    }

    fun setResponsable(v: String) {
        _ui.value = _ui.value.copy(responsable = v, justSaved = false)
    }

    fun toggleProvider(p: AiProvider) {
        val cur = _ui.value.enabledProviders.toMutableSet()
        if (!cur.add(p)) cur.remove(p)
        _ui.value = _ui.value.copy(enabledProviders = cur, justSaved = false)
    }

    /** Guarda perfil y motores activos de golpe. */
    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            prefs.setResponsableComercial(s.responsable)
            prefs.setEnabled(s.enabledProviders.toList())
            _ui.value = _ui.value.copy(justSaved = true)
        }
    }
}
