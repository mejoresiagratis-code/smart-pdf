package com.mejoresiagratis.rellenador.ui.wizard

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.PackageApplier
import com.mejoresiagratis.rellenador.data.model.Paquete
import android.graphics.Bitmap
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import com.mejoresiagratis.rellenador.data.pdf.DocumentLoader
import com.mejoresiagratis.rellenador.data.pdf.PdfExporter
import com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor
import com.mejoresiagratis.rellenador.data.pdf.TemplateMapper
import com.mejoresiagratis.rellenador.data.pdf.AcroFormFiller
import com.mejoresiagratis.rellenador.data.pdf.SignaturePageDetector
import com.mejoresiagratis.rellenador.data.remote.SignatureLocator
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.mejoresiagratis.rellenador.data.remote.MultiAiExtractor
import com.mejoresiagratis.rellenador.data.remote.ProxyApi
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: MultiAiExtractor,
    private val loader: DocumentLoader,
    private val api: ProxyApi,
    private val prefs: PrefsRepository,
    private val locator: SignatureLocator,
    private val sigProcessor: SignatureProcessor,
    private val exporter: PdfExporter,
    private val templateMapper: TemplateMapper,
    private val filler: AcroFormFiller,
    private val pageDetector: SignaturePageDetector
) : ViewModel() {

    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init { probeProviders() }

    /** GET al proxy: qué motores tienen clave en servidor. */
    private fun probeProviders() {
        viewModelScope.launch {
            val resp = runCatching { api.providers() }.getOrNull()
            val available = resp?.providers.orEmpty()
                .filterValues { it }.keys
                .mapNotNull { AiProvider.fromId(it) }
            _state.value = _state.value.copy(
                availableProviders = available,
                enabledProviders = available.toSet()   // por defecto todos los disponibles
            )
        }
    }

    // ---- Paso 1: contrato ----
    fun chooseDefaultContract() {
        _state.value = _state.value.copy(contractSource = ContractSource.DEFAULT, userContractUri = null)
        detectSignaturePages()
    }
    fun chooseUserContract(uri: Uri) {
        _state.value = _state.value.copy(contractSource = ContractSource.USER, userContractUri = uri)
        // Leer los nombres reales de campo del PDF del usuario y auto-mapear.
        viewModelScope.launch {
            val fields = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { filler.listFields(it) }
                }.getOrElse { emptyList() }
            }
            if (fields.isEmpty()) {
                _state.value = _state.value.copy(userFieldNames = emptyList(), needsMapping = false)
                return@launch
            }
            val suggestions = templateMapper.suggest(fields)
            val mapping = suggestions.mapNotNull { sug ->
                sug.canonicalKey?.let { it to sug.realField }
            }.toMap()
            _state.value = _state.value.copy(
                userFieldNames = fields,
                fieldMapping = mapping,
                needsMapping = true
            )
            detectSignaturePages()
        }
    }

    /** Ajuste manual de una asignación canónica -> real en el editor de mapeo. */
    fun setMapping(canonicalKey: String, realField: String?) {
        val m = _state.value.fieldMapping.toMutableMap()
        if (realField == null) m.remove(canonicalKey) else m[canonicalKey] = realField
        _state.value = _state.value.copy(fieldMapping = m)
    }

    // ---- Paso 2: documentación ----
    fun addDocuments(uris: List<Uri>) {
        _state.value = _state.value.copy(docUris = (_state.value.docUris + uris).distinct())
    }
    fun removeDocument(uri: Uri) {
        _state.value = _state.value.copy(docUris = _state.value.docUris - uri)
    }
    fun toggleProvider(p: AiProvider) {
        val cur = _state.value.enabledProviders.toMutableSet()
        if (!cur.add(p)) cur.remove(p)
        _state.value = _state.value.copy(enabledProviders = cur)
    }

    /** Lanza la extracción multi-motor y avanza a Revisión. */
    fun runExtraction() {
        val s = _state.value
        if (!s.canAdvanceFromDocs) return
        viewModelScope.launch {
            _state.value = s.copy(busy = true, busyMsg = "Preparando documentos…", error = null)
            val payloads = withContext(Dispatchers.IO) {
                s.docUris.flatMap { runCatching { loader.load(it) }.getOrElse { emptyList() } }
            }
            if (payloads.isEmpty()) {
                _state.value = _state.value.copy(busy = false, error = "No se pudieron leer los documentos.")
                return@launch
            }
            _state.value = _state.value.copy(busyMsg = "Analizando con IA…")
            val result = runCatching {
                extractor.extract(payloads, s.enabledProviders.toList())
            }.getOrElse {
                _state.value = _state.value.copy(busy = false, error = it.message); return@launch
            }
            // Prerelleno inicial: valor de mayor consenso por campo.
            val prefill = result.proposals.associate { fp ->
                fp.fieldKey to (fp.candidates.firstOrNull()?.value ?: "")
            }.toMutableMap()
            // Regla fija de la web.
            prefill[ContractFields.RESPONSABLE_KEY] = ContractFields.RESPONSABLE_VALUE

            _state.value = _state.value.copy(
                busy = false, step = Step.REVISION,
                proposals = result.proposals, packages = result.packages,
                tipoIdentificacion = result.tipoIdentificacion, enginesOk = result.enginesOk,
                fieldValues = prefill,
                error = result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            )
        }
    }

    // ---- Paso 3: revisión / relleno ----
    fun setFieldValue(key: String, value: String) {
        _state.value = _state.value.copy(fieldValues = _state.value.fieldValues + (key to value))
    }
    fun clearField(key: String) {
        _state.value = _state.value.copy(fieldValues = _state.value.fieldValues - key)
    }

    /** Aplica un paquete en bloque (dirección/empresa/persona/banco).
     *  Para direcciones, targetBlock2=true lo manda al bloque de comercio (_2). */
    fun applyPackage(paquete: Paquete, targetBlock2: Boolean = false) {
        val delta = PackageApplier.apply(paquete, targetBlock2)
        _state.value = _state.value.copy(fieldValues = _state.value.fieldValues + delta)
    }


    /** Abre el contrato activo (por defecto o del usuario) para leerlo. */
    private fun openContract(): java.io.InputStream =
        _state.value.userContractUri?.let { context.contentResolver.openInputStream(it)!! }
            ?: context.assets.open("contrato-base.pdf")

    /** Detecta las páginas de firma del contrato activo (Tanda B). */
    fun detectSignaturePages() {
        viewModelScope.launch {
            val det = withContext(Dispatchers.IO) {
                runCatching { openContract().use { pageDetector.detect(it) } }.getOrNull()
            } ?: return@launch
            // Colocación por defecto en cada página detectada, anclada bajo el rótulo.
            val stamps = det.signPages.map { pageIdx ->
                val yr = det.signAnchors[pageIdx]?.let { (it + 0.06f).coerceAtMost(0.95f) } ?: 0.82f
                SignatureStamp(pageIndex = pageIdx, xRel = 0.30f, yRel = yr, widthRel = 0.28f)
            }
            _state.value = _state.value.copy(
                signPages = det.signPages,
                signAnchors = det.signAnchors,
                stamps = if (_state.value.signature != null) stamps else _state.value.stamps
            )
        }
    }

    /** Añade una página de firma manualmente (1-based desde la UI). */
    fun addSignPage(pageOneBased: Int) {
        val idx = pageOneBased - 1
        if (idx < 0) return
        if (idx in _state.value.signPages) return
        _state.value = _state.value.copy(signPages = (_state.value.signPages + idx).sorted())
    }

    /** Quita una página de firma. */
    fun removeSignPage(pageIdx: Int) {
        _state.value = _state.value.copy(
            signPages = _state.value.signPages - pageIdx,
            stamps = _state.value.stamps.filterNot { it.pageIndex == pageIdx }
        )
    }

    /** Estampado MASIVO: coloca la firma en TODAS las páginas de firma a la vez,
     *  anclando bajo el rótulo detectado en cada una (fiel a bulkStamps de la web). */
    fun stampAllPages() {
        val sig = _state.value.signature ?: return
        val stamps = _state.value.signPages.map { pageIdx ->
            val yr = _state.value.signAnchors[pageIdx]?.let { (it + 0.06f).coerceAtMost(0.95f) } ?: 0.82f
            SignatureStamp(pageIndex = pageIdx, xRel = 0.30f, yRel = yr, widthRel = 0.28f)
        }
        _state.value = _state.value.copy(stamps = stamps)
    }

    /** Estampado en UNA página concreta (modo una a una). */
    fun stampOnePage(pageIdx: Int) {
        val sig = _state.value.signature ?: return
        val yr = _state.value.signAnchors[pageIdx]?.let { (it + 0.06f).coerceAtMost(0.95f) } ?: 0.82f
        val others = _state.value.stamps.filterNot { it.pageIndex == pageIdx }
        _state.value = _state.value.copy(
            stamps = others + SignatureStamp(pageIdx, 0.30f, yr, 0.28f)
        )
    }

    // ---- Paso 5: firma ----
    /** Firma dibujada en el lienzo: bitmap -> PNG transparente. */
    fun setDrawnSignature(bmp: Bitmap) {
        val stroke = sigProcessor.toTransparentStroke(bmp)
        val data = sigProcessor.toSignatureData(stroke)
        // Colocación por defecto en la página de firma del distribuidor (pág. 24).
        _state.value = _state.value.copy(signature = data)
        // Colocar en todas las páginas de firma detectadas (o la 24 por defecto).
        if (_state.value.signPages.isNotEmpty()) stampAllPages()
        else _state.value = _state.value.copy(stamps = listOf(defaultStamp()))
    }

    /** Firma extraída de una foto: localiza con IA, recorta y limpia. */
    fun extractSignatureFromPhoto(bmp: Bitmap) {
        viewModelScope.launch {
            _state.value = _state.value.copy(locatingSignature = true, error = null)
            val b64 = withContext(Dispatchers.IO) {
                val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                Base64.encodeToString(jpg, Base64.NO_WRAP)
            }
            val box = runCatching {
                locator.locate(b64, _state.value.availableProviders)
            }.getOrNull()
            val cropped = if (box != null) sigProcessor.crop(bmp, box) else bmp
            val stroke = sigProcessor.toTransparentStroke(cropped)
            val data = sigProcessor.toSignatureData(stroke)
            _state.value = _state.value.copy(
                locatingSignature = false, signature = data,
                error = if (box == null) "No se localizó la firma automáticamente; usa la imagen completa." else null
            )
            if (_state.value.signPages.isNotEmpty()) stampAllPages()
            else _state.value = _state.value.copy(stamps = listOf(defaultStamp()))
        }
    }

    private fun defaultStamp() = SignatureStamp(
        pageIndex = 23,       // página 24 (bloque EL DISTRIBUIDOR)
        xRel = 0.30f, yRel = 0.82f, widthRel = 0.28f
    )

    /** Ajuste manual de la colocación (posición/tamaño relativos). */
    fun updateStamp(xRel: Float, yRel: Float, widthRel: Float) {
        _state.value = _state.value.copy(
            stamps = listOf(SignatureStamp(23, xRel, yRel, widthRel))
        )
    }

    fun clearSignature() {
        _state.value = _state.value.copy(signature = null, stamps = emptyList())
    }

    /** Genera el PDF final (relleno + firmado) a fichero interno. */
    fun generatePdf(onReady: (java.io.File) -> Unit = {}) {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(busy = true, busyMsg = "Generando PDF final…", error = null)
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    exporter.generateToFile(
                        userContractUri = s.userContractUri,
                        values = s.fieldValues,
                        signature = s.signature,
                        stamps = s.stamps,
                        checkboxes = com.mejoresiagratis.rellenador.data.model.ContractFields
                            .checkboxStateFor(s.tipoIdentificacion),
                        fieldMapping = if (s.contractSource == ContractSource.USER) s.fieldMapping else emptyMap()
                    )
                }
            }.getOrElse {
                _state.value = _state.value.copy(busy = false, error = "No se pudo generar el PDF: ${it.message}")
                return@launch
            }
            _state.value = _state.value.copy(busy = false, outputFile = file, outputReady = true)
            onReady(file)
        }
    }

    fun shareIntentFor(file: java.io.File) = exporter.shareIntent(file)
    fun uriFor(file: java.io.File) = exporter.uriFor(file)

    // ---- Navegación ----
    fun goTo(step: Step) { _state.value = _state.value.copy(step = step) }
    fun next() {
        val cur = _state.value.step
        val nextIdx = (cur.index + 1).coerceAtMost(Step.entries.last().index)
        _state.value = _state.value.copy(step = Step.entries[nextIdx])
    }
    fun back() {
        val cur = _state.value.step
        val prevIdx = (cur.index - 1).coerceAtLeast(0)
        _state.value = _state.value.copy(step = Step.entries[prevIdx])
    }
    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
