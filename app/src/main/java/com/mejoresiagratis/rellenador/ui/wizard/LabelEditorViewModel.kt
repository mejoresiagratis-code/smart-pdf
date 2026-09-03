package com.mejoresiagratis.rellenador.ui.wizard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.SchemaEditing
import com.mejoresiagratis.rellenador.data.model.TemplateFingerprint
import com.mejoresiagratis.rellenador.data.pdf.AcroFormFiller
import com.mejoresiagratis.rellenador.data.pdf.FormSchemaBuilder
import com.mejoresiagratis.rellenador.data.pdf.LayoutTextExtractor
import com.mejoresiagratis.rellenador.data.pdf.PdfFieldInspector
import com.mejoresiagratis.rellenador.data.remote.CanonicalMapper
import com.mejoresiagratis.rellenador.data.remote.ProxyApi
import com.mejoresiagratis.rellenador.data.remote.VisionLabelPass
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private val layoutText: LayoutTextExtractor,
    private val filler: AcroFormFiller,
    private val prefs: PrefsRepository,
    private val api: ProxyApi,
    private val visionPass: VisionLabelPass,
    private val canonicalMapper: CanonicalMapper,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val schema: FormSchema? = null,
        val fileName: String? = null,
        val reused: Boolean = false,   // true si el esquema venía ya guardado/migrado
        val saved: Boolean = false,
        val error: String? = null,

        /** Etiquetado por visión en curso, con su progreso por páginas. */
        val labelling: Boolean = false,
        val labelProgress: VisionLabelPass.Progress? = null,
        /** Resultado del último etiquetado, para contarlo en pantalla. */
        val labelNotice: String? = null,

        /** Tanda 5·4g — propuesta de canónicas por IA en curso, y su resultado. */
        val mapping: Boolean = false,
        val mapNotice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * El PDF elegido. Se guarda porque el etiquetado por visión ocurre **después**, cuando el
     * usuario lo pide, y necesita volver a leer el documento para renderizar sus páginas.
     */
    private var pickedUri: Uri? = null

    /**
     * Carga [uri] sólo si no está ya cargado (0.10.12).
     *
     * Lo usa el paso 1 del asistente, que siembra este ViewModel con el contrato ya elegido en vez
     * de pedir el fichero otra vez. Sin la guarda, cada recomposición que reevaluara el
     * `LaunchedEffect` volvería a leer el PDF y tiraría las correcciones a medias.
     */
    fun ensureLoaded(uri: Uri) {
        if (pickedUri == uri && (_state.value.schema != null || _state.value.loading)) return
        pickPdf(uri)
    }

    /** El usuario ha elegido un PDF (picker SAF): inspecciona, calcula huella y busca o construye. */
    fun pickPdf(uri: Uri) {
        pickedUri = uri
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

                    // Texto del PDF con posición: es lo que permite a `FormSchemaBuilder`
                    // titular las secciones como el papel (DATOS DEL CLIENTE, AIRE CONNECT…),
                    // detectar la casilla que activa cada banda y etiquetar los campos sueltos
                    // por geometría antes de gastar una llamada de visión. Tanda 5·4b.
                    val words = resolver.openInputStream(uri)!!.use { layoutText.extract(it) }

                    fun buildFresh(): FormSchema = schemaBuilder.build(
                        fields = fields,
                        fingerprint = fingerprint,
                        pageCount = pageCount,
                        // El nombre del fichero como título de partida: es lo único que
                        // identifica el formulario para quien lo sube ("SEPA_Aire.pdf" dice
                        // mucho más que "Formulario"), y es editable después.
                        title = name ?: "Formulario",
                        layoutWords = words,
                    )

                    val existing = prefs.findOrMigrateSchema(fingerprint, pageCount)
                    // Regeneración perezosa y NO destructiva: un esquema guardado por una
                    // versión anterior del constructor (p.ej. el que deja el paso 1, que aún no
                    // pasa `layoutWords` y produce secciones «Página 1») se reconstruye aquí,
                    // que es el camino que sí sabe hacerlo mejor. `isStaleBuild()` sólo dice que
                    // sí cuando NADIE ha editado etiquetas a mano, así que nunca se pisa trabajo
                    // del usuario — misma regla que la migración v1→v2 de la 5·3.
                    val stale = existing?.isStaleBuild() == true
                    val schema = if (existing == null || stale) buildFresh() else existing
                    Triple(schema, existing != null && !stale, name)
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

    /**
     * Etiquetado por visión, **a petición del usuario** y no automático al abrir el PDF.
     *
     * Es una llamada de red por página con huecos y cuesta dinero y segundos, así que se dispara
     * con un botón: si el PDF ya trae nombres legibles (`Nombre o razón social`), no hace falta.
     *
     * Lo que se manda es la **plantilla en blanco**, no documentación del cliente. Es una
     * diferencia que importa para el aviso de privacidad de la v0.9.1: aquí no viajan datos
     * personales, sólo el formulario impreso. Aun así se respeta «solo motores europeos».
     */
    fun labelWithVision() {
        val schema = _state.value.schema ?: return
        val uri = pickedUri ?: return
        if (_state.value.labelling) return

        _state.value = _state.value.copy(labelling = true, labelNotice = null, error = null)
        viewModelScope.launch {
            val outcome = runCatching {
                val available = visionProviders()
                if (available.isEmpty()) error(
                    "No hay ningún motor con visión disponible. Revisa los motores en Ajustes."
                )

                // `PdfRenderer` no acepta un Uri de SAF: necesita un descriptor sobre un fichero
                // real. Se copia a la caché y se borra al terminar, pase lo que pase.
                val tmp = withContext(Dispatchers.IO) { copyToCache(uri) }
                try {
                    visionPass.run(
                        schema = schema,
                        file = tmp,
                        available = available,
                        onProgress = { p -> _state.value = _state.value.copy(labelProgress = p) },
                    )
                } finally {
                    withContext(Dispatchers.IO) { runCatching { tmp.delete() } }
                }
            }

            _state.value = outcome.fold(
                onSuccess = { r ->
                    _state.value.copy(
                        schema = r.schema,
                        labelling = false,
                        labelProgress = null,
                        saved = false,
                        labelNotice = if (r.labelled == 0) {
                            "Ningún motor devolvió etiquetas utilizables. Las de abajo siguen " +
                                "siendo los nombres del PDF; puedes corregirlas a mano."
                        } else {
                            "${r.labelled} etiqueta(s) propuestas a partir de ${r.pages} " +
                                "página(s). Revísalas: lo que corrijas manda sobre la IA."
                        },
                    )
                },
                onFailure = {
                    _state.value.copy(
                        labelling = false,
                        labelProgress = null,
                        error = it.message ?: "No se pudo etiquetar.",
                    )
                },
            )
        }
    }

    /**
     * Tanda 5·4g — pide a la IA que **proponga** el enganche de cada campo con un dato
     * transversal, y lo aplica al esquema con [SchemaEditing.setCanonical].
     *
     * Se aplica en bloque a propósito, en vez de ir campo a campo: son cientos de huecos y
     * confirmarlos de uno en uno no lo haría nadie. La red de seguridad es que el filtro de
     * [CanonicalMapper.sanitize] tira las claves inventadas, y en el editor cada enganche queda
     * visible en su chip y se puede cambiar o quitar. Como toda edición manual, lo que el
     * usuario corrija después manda sobre esto.
     *
     * Tanda 5·4i — la IA puede proponer la MISMA canónica para varios campos (el mismo dato
     * repetido en el PDF) y aquí se aplican todas: `SchemaEditing.setCanonical` ya no le quita
     * la canónica al campo anterior, así que el `fold` deja los dos enganchados en vez de que el
     * segundo borre al primero.
     *
     * No manda ningún valor: sólo nombres de campo y los rótulos impresos de la plantilla en
     * blanco, igual que el etiquetado por visión.
     */
    fun proposeCanonicals() {
        val schema = _state.value.schema ?: return
        if (_state.value.mapping) return

        _state.value = _state.value.copy(mapping = true, mapNotice = null, error = null)
        viewModelScope.launch {
            val outcome = runCatching {
                val available = visionProviders()
                if (available.isEmpty()) error(
                    "No hay ningún motor disponible. Revisa los motores en Ajustes."
                )
                canonicalMapper.propose(schema, available)
            }

            _state.value = outcome.fold(
                onSuccess = { propuestas ->
                    val actualizado = propuestas.entries.fold(schema) { acc, (name, canonical) ->
                        SchemaEditing.setCanonical(acc, name, canonical)
                    }
                    _state.value.copy(
                        schema = actualizado,
                        mapping = false,
                        saved = false,
                        mapNotice = if (propuestas.isEmpty()) {
                            "Ningún motor propuso enganches utilizables. Puedes asignarlos a " +
                                "mano con el selector de cada campo."
                        } else {
                            "${propuestas.size} enganche(s) propuestos. Revísalos en el chip de " +
                                "cada campo: lo que corrijas manda sobre la IA."
                        },
                    )
                },
                onFailure = {
                    _state.value.copy(
                        mapping = false,
                        error = it.message ?: "No se pudieron proponer enganches.",
                    )
                },
            )
        }
    }

    /**
     * Motores con visión que se pueden usar ahora: los que el servidor declara con clave,
     * cruzados con la selección del usuario en Ajustes y con «solo motores europeos».
     *
     * Se pregunta al proxy en vez de fiarse de lo guardado porque una clave puede haber caducado
     * en el servidor desde la última vez. Si el proxy no contesta, se cae a lo guardado antes de
     * rendirse — mejor intentarlo con la última lista conocida que no ofrecer la función.
     */
    private suspend fun visionProviders(): List<AiProvider> {
        val fromServer = runCatching { api.providers() }.getOrNull()
            ?.providers.orEmpty()
            .filterValues { it }.keys
            .mapNotNull { AiProvider.fromId(it) }
        val enabled = runCatching { prefs.enabledProviders.first() }.getOrNull().orEmpty()
        val euOnly = runCatching { prefs.euOnly.first() }.getOrNull() ?: false

        val base = fromServer.ifEmpty { enabled }
        return base
            .filter { enabled.isEmpty() || it in enabled }
            .filter { !euOnly || it.eu }
    }

    private fun copyToCache(uri: Uri): File {
        val dst = File(context.cacheDir, "etiquetado-${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            dst.outputStream().use { input.copyTo(it) }
        }
        return dst
    }

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
        pickedUri = null
        _state.value = UiState()
    }
}
