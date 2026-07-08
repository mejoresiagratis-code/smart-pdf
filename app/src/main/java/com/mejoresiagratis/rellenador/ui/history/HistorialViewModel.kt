package com.mejoresiagratis.rellenador.ui.history

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mejoresiagratis.rellenador.data.pdf.PdfExporter
import com.mejoresiagratis.rellenador.data.repository.HistoryEntry
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HistorialViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    private val exporter: PdfExporter
) : ViewModel() {

    val entries: StateFlow<List<HistoryEntry>> = prefs.history
        .map { list -> list.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** El fichero pudo borrarse fuera de la app (limpieza de caché, etc.). */
    fun fileExists(entry: HistoryEntry): Boolean = File(entry.filePath).exists()

    fun shareIntentFor(entry: HistoryEntry): Intent = exporter.shareIntent(File(entry.filePath))

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch {
            runCatching { File(entry.filePath).delete() }
            prefs.deleteHistoryEntry(entry.id)
        }
    }
}
