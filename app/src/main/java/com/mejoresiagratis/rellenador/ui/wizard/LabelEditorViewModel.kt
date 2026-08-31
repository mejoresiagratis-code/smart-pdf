package com.mejoresiagratis.rellenador.ui.wizard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.TemplateFingerprint
import com.mejoresiagratis.rellenador.data.pdf.AcroFormFiller
import com.mejoresiagratis.rellenador.data.pdf.FormSchemaBuilder
import com.mejoresiagratis.rellenador.data.pdf.PdfFieldInspector
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Cablea un PDF elegido por el usuario con [LabelEditor] — la pieza que faltaba tras la v0.10.3:
 * `PdfFieldInspector` → `FormSchemaBuilder` (o el esquema ya guardado, por huella) → editor →
 * `PrefsRepository.saveSchema()`.
 *
 * Deliberadamente **su propio ViewModel**, no `WizardViewModel`: sigue sin enganchar al
 * asistente, tal como pedía la fase 4. `FieldLabeler` (etiquetado por visión) queda FUERA de
 * esta tanda a propósito — sin él el esquema sale con el nombre real del AcroForm como etiqueta
 * provisional (igual que hoy), que ya es corregible a mano en el editor; la llamada a visión es
 * la siguiente tanda, no un requisito para que esto sea útil.
 */
@HiltViewModel
class LabelEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inspector: PdfFieldInspector,
    private val schemaBuilder: FormSchemaBuilder,
    private val filler: AcroFormFiller,
    private val prefs: PrefsRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val schema: FormSchema? = null,
        val fileName: String? = null,
        val reused: Boolean = false,   // true si el esquema venía ya guardado/migrado
        val saved: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** El usuario ha elegido un PDF (picker SAF): inspecciona, calcula huella y busca o construye. */
    fun pickPdf(uri: Uri) {
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val fields = resolver.openInputStream(uri)!!.use { inspector.inspect(it) }
                    val pageCount = resolver.openInputStream(uri)!!.use { inspector.pageCount(it) }
                    val fieldNames = resolver.openInputStream(uri)!!.use { filler.listFields(it) }
                    // Dos salidas tempranas, las dos con mensaje propio en vez de dejar seguir:
                    //  · pageCount 0 = PDFBox no ha podido abrirlo (`inspect`/`pageCount` traga
                    //    la excepción y devuelven vacío/0, así que aquí no llega ninguna).
                    //  · sin campos = no es un PDF rellenable. Sin esto se construiría un
                    //    esquema de cero secciones y el editor saldría en blanco, con un botón
                    //    "Confirmar etiquetas" que no confirma nada. Y peor: la huella sería
                    //    `"N|"`, idéntica para CUALQUIER PDF de N páginas sin campos, así que se
                    //    guardarían unos encima de otros.
                    if (pageCount == 0) error("No se ha podido abrir el PDF. ¿Está protegido con contraseña?")
                    // Se comprueban las DOS listas: `listFields` da los campos del AcroForm y
                    // `inspect` los widgets colocados en una página. Un campo sin widget en
                    // ninguna página (pasa en PDFs mal generados) contaría en la primera y no en
                    // la segunda, y el editor volvería a salir vacío.
                    if (fieldNames.isEmpty() || fields.isEmpty()) {
                        error(
                            "Este PDF no tiene campos rellenables (AcroForm): es una imagen o un " +
                                "documento plano. Solo se pueden etiquetar formularios rellenables."
                        )
                    }

                    val fingerprint = TemplateFingerprint.of(pageCount, fieldNames)

                    val name = displayName(uri)

                    val existing = prefs.findOrMigrateSchema(fingerprint, pageCount)
                    val schema = existing
                        ?: schemaBuilder.build(
                            fields = fields,
                            fingerprint = fingerprint,
                            pageCount = pageCount,
                            // El nombre del fichero como título de partida: es lo único que
                            // identifica el formulario para quien lo sube ("SEPA_Aire.pdf" dice
                            // mucho más que "Formulario"), y es editable después.
                            title = name ?: "Formulario",
                        )
                    Triple(schema, existing != null, name)
                }
            }
            _state.value = result.fold(
                onSuccess = { (schema, reused, name) ->
                    UiState(schema = schema, fileName = name, reused = reused)
                },
                onFailure = { UiState(error = it.message ?: "No se pudo leer el PDF.") },
            )
        }
    }

    /**
     * Nombre visible del fichero elegido. Con SAF, el `lastPathSegment` del URI es un id opaco
     * (`document:17077`), no el nombre — hay que consultar `OpenableColumns.DISPLAY_NAME`, igual
     * que hacen `DocumentStore` y `WizardViewModel`.
     */
    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** El usuario ha corregido algo en el editor; se guarda solo en memoria hasta [save]. */
    fun onSchemaChange(schema: FormSchema) {
        _state.value = _state.value.copy(schema = schema, saved = false)
    }

    /** Persiste el esquema (con las correcciones `LabelSource.USUARIO`) por su huella. */
    fun save() {
        val schema = _state.value.schema ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { prefs.saveSchema(schema) } }
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun reset() {
        _state.value = UiState()
    }
}
