package com.mejoresiagratis.rellenador.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ExtractedField
import com.mejoresiagratis.rellenador.data.remote.MultiAiExtractor
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val busy: Boolean = false,
    val enabledProviders: List<AiProvider> = emptyList(),
    val fields: Map<String, ExtractedField> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractor: MultiAiExtractor,
    private val prefs: PrefsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.enabledProviders.collect { providers ->
                _state.value = _state.value.copy(enabledProviders = providers)
            }
        }
    }

    fun runExtraction(pdfText: String, prompt: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            runCatching {
                extractor.extract(pdfText, prompt, _state.value.enabledProviders)
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, fields = it)
            }.onFailure {
                _state.value = _state.value.copy(busy = false, error = it.message)
            }
        }
    }
}
