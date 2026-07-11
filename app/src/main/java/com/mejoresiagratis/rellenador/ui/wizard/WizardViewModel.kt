package com.mejoresiagratis.rellenador.ui.wizard

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.DateAutofill
import com.mejoresiagratis.rellenador.data.model.PackageApplier
import com.mejoresiagratis.rellenador.data.model.Paquete
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import com.mejoresiagratis.rellenador.data.pdf.DocumentLoader
import com.mejoresiagratis.rellenador.data.pdf.PdfExporter
import com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor
import com.mejoresiagratis.rellenador.data.pdf.TemplateMapper
import com.mejoresiagratis.rellenador.data.pdf.AcroFormFiller
import com.mejoresiagratis.rellenador.data.pdf.SignaturePageDetector
import com.mejoresiagratis.rellenador.data.pdf.PdfPageRenderer
import com.mejoresiagratis.rellenador.data.model.ContractProfile
import com.mejoresiagratis.rellenador.data.model.TemplateFingerprint
import com.mejoresiagratis.rellenador.data.remote.SignatureLocator
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.mejoresiagratis.rellenador.data.remote.MultiAiExtractor
import com.mejoresiagratis.rellenador.data.remote.ProxyApi
import com.mejoresiagratis.rellenador.data.repository.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    private var previewRenderer: PdfPageRenderer? = null
    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init {
        probeProviders()
        loadPersistedSettings()
    }

    private fun loadPersistedSettings() {
        viewModelScope.launch {
            val name = runCatching { prefs.responsableComercial.first() }
                .getOrDefault(ContractFields.RESPONSABLE_VALUE)
            _state.value = _state.value.copy(responsableComercial = name)
        }
        viewModelScope.launch {
            val url = runCatching { prefs.proxyBaseUrlOverride.first() }.getOrDefault("")
            _state.value = _state.value.copy(proxyBaseUrlOverride = url)
        }
    }

    /** GET al proxy: qué motores tienen clave en servidor. */
    private fun probeProviders() {
        viewModelScope.launch {
            val resp = runCatching { api.providers() }.getOrNull()
            val available = resp?.providers.orEmpty()
                .filterValues { it }.keys
                .mapNotNull { AiProvider.fromId(it) }
            // Si el usuario ya eligió motores en Ajustes, respetar esa selección
            // (solo los que además sigan disponibles en el servidor).
            val persisted = runCatching { prefs.enabledProviders.first() }.getOrNull()
            val enabled = if (!persisted.isNullOrEmpty()) persisted.filter { it in available }.toSet()
                          else available.toSet()
            _state.value = _state.value.copy(
                availableProviders = available,
                enabledProviders = enabled.ifEmpty { available.toSet() }
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
            val fp = TemplateFingerprint.of(fields.size, fields)  // huella provisional (páginas se ajustan tras detectar)
            val saved = runCatching { prefs.findTemplate(fp) }.getOrNull()
            _state.value = _state.value.copy(
                userFieldNames = fields,
                fieldMapping = saved ?: mapping,
                needsMapping = saved == null,   // si ya había plantilla guardada, no hace falta revisar
                templateFingerprint = fp
            )
            detectSignaturePages()
        }
    }

    /** Persiste el mapeo actual bajo la huella de la plantilla, para reaplicarlo la próxima vez. */
    fun rememberTemplateMapping() {
        val s = _state.value
        if (s.templateFingerprint.isBlank() || s.fieldMapping.isEmpty()) return
        viewModelScope.launch { prefs.saveTemplate(s.templateFingerprint, s.fieldMapping) }
    }

    // ---- Perfiles e historial (Tanda F) ----
    private fun buildProfile(label: String): ContractProfile = ContractProfile(
        label = label,
        guardado = java.time.Instant.now().toString(),
        fingerprint = _state.value.templateFingerprint,
        campos = _state.value.fieldValues.filterValues { it.isNotBlank() },
        fieldMapping = _state.value.fieldMapping
    )

    fun exportProfileJson(label: String): String = prefs.exportProfileJson(buildProfile(label))

    fun saveCurrentToHistory(label: String) {
        viewModelScope.launch { prefs.saveToHistory(buildProfile(label)) }
    }

    fun loadHistoryList(onLoaded: (List<Pair<String, ContractProfile>>) -> Unit) {
        viewModelScope.launch { onLoaded(prefs.listHistory()) }
    }

    fun deleteHistoryEntry(id: String) {
        viewModelScope.launch { prefs.deleteFromHistory(id) }
    }

    /** Aplica un perfil (de historial o importado) sobre los campos actuales. */
    fun applyProfile(profile: ContractProfile) {
        _state.value = _state.value.copy(
            fieldValues = _state.value.fieldValues + profile.campos
        )
    }

    fun importProfileFromJson(raw: String): Boolean {
        val profile = prefs.importProfileJson(raw) ?: return false
        applyProfile(profile)
        return true
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
        viewModelScope.launch { prefs.setEnabled(cur.toList()) }
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
            prefill[ContractFields.RESPONSABLE_KEY] = _state.value.responsableComercial
            // Autofill de fechas (Tanda D): rellena Fecha/de/año actuales si están vacías.
            prefill.putAll(DateAutofill.values(prefill))

            _state.value = _state.value.copy(
                busy = false, step = Step.REVISION,
                proposals = result.proposals, packages = result.packages,
                tipoIdentificacion = result.tipoIdentificacion, enginesOk = result.enginesOk,
                fieldValues = prefill,
                error = result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                engineErrors = result.errors
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

    /**
     * Coordenadas calibradas contra `contrato-relleno-a1.pdf` (contrato real ya firmado).
     * Medidas con pdfplumber: para cada página se localizó el rótulo "EL DISTRIBUIDOR" y
     * la imagen de firma inmediatamente asociada a él (más cercana en Y, alineada en X),
     * y se convirtió su posición a CENTRO relativo (xRel/yRel esperan centro, no esquina —
     * ver AcroFormFiller). Esto reemplaza el valor fijo anterior (0.30/0.82 para todas)
     * que colocaba la firma a un lado en vez de justo debajo y centrada al rótulo.
     *
     * Nota importante: el rótulo "EL DISTRIBUIDOR" NO está siempre a la izquierda de la
     * página. En las páginas 30 y 33 el bloque del distribuidor está a la DERECHA (Xfera
     * a la izquierda, distribuidor a la derecha); en 24, 45 y 54 está a la izquierda.
     *
     * heightRel (0.114 ≈ 90/792) es la caja MÁXIMA disponible en el hueco real del
     * contrato — la firma se escala para caber dentro de widthRel×heightRel sin
     * deformarse (letterbox), en vez de forzar su altura a partir del aspect ratio
     * de la imagen de origen (eso causaba el recorte/ampliación en exceso del PDF final).
     *
     * Índice = página 0-based. Valores: (xRel centro, yRel centro, widthRel, heightRel).
     */
    private data class Calib(val x: Float, val y: Float, val w: Float, val h: Float)
    private val calibratedStamps: Map<Int, Calib> = mapOf(
        23 to Calib(0.275f, 0.463f, 0.256f, 0.114f),   // Página 24 — izquierda
        29 to Calib(0.722f, 0.261f, 0.256f, 0.114f),   // Página 30 — derecha
        32 to Calib(0.220f, 0.940f, 0.256f, 0.114f),   // Página 33 — izquierda, muy abajo
        44 to Calib(0.222f, 0.853f, 0.256f, 0.114f),   // Página 45 — izquierda
        53 to Calib(0.183f, 0.886f, 0.256f, 0.114f)    // Página 54 — izquierda
    )

    /** Devuelve el stamp para una página: usa calibración si existe, si no cae al ancla detectada. */
    private fun stampFor(pageIdx: Int, anchors: Map<Int, Float>): SignatureStamp {
        calibratedStamps[pageIdx]?.let { c ->
            return SignatureStamp(pageIndex = pageIdx, xRel = c.x, yRel = c.y, widthRel = c.w, heightRel = c.h)
        }
        val yr = anchors[pageIdx]?.let { (it + 0.06f).coerceAtMost(0.95f) } ?: 0.82f
        return SignatureStamp(pageIndex = pageIdx, xRel = 0.30f, yRel = yr, widthRel = 0.28f, heightRel = 0.114f)
    }

    /** Detecta las páginas de firma del contrato activo (Tanda B). */
    fun detectSignaturePages() {
        viewModelScope.launch {
            val det = withContext(Dispatchers.IO) {
                runCatching { openContract().use { pageDetector.detect(it) } }.getOrNull()
            } ?: return@launch
            // Colocación por defecto en cada página detectada (calibrada si existe).
            val stamps = det.signPages.map { pageIdx -> stampFor(pageIdx, det.anchors) }
            _state.value = _state.value.copy(
                signPages = det.signPages,
                signAnchors = det.anchors,
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
     *  usando la calibración real por página si existe (fiel a bulkStamps de la web). */
    fun stampAllPages() {
        val sig = _state.value.signature ?: return
        val stamps = _state.value.signPages.map { pageIdx -> stampFor(pageIdx, _state.value.signAnchors) }
        _state.value = _state.value.copy(stamps = stamps)
    }

    /** Estampado en UNA página concreta (modo una a una). */
    fun stampOnePage(pageIdx: Int) {
        val sig = _state.value.signature ?: return
        val others = _state.value.stamps.filterNot { it.pageIndex == pageIdx }
        _state.value = _state.value.copy(
            stamps = others + stampFor(pageIdx, _state.value.signAnchors)
        )
    }

    // ---- Paso 5: firma ----
    /** Firma dibujada en el lienzo: bitmap -> PNG transparente. */
    /** Bitmap "crudo" (antes de tintar) de la última firma preparada, para poder
     *  reprocesar en vivo al cambiar color/fondo sin volver a llamar a la IA de
     *  localización. isPhoto indica qué pipeline usar al reprocesar. */
    private var rawSignatureBitmap: Bitmap? = null
    private var rawSignatureIsPhoto: Boolean = false

    fun setDrawnSignature(bmp: Bitmap) {
        rawSignatureBitmap = bmp
        rawSignatureIsPhoto = false
        // Trazo dibujado: tintar (color elegido) sobre transparente con Otsu.
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val thr = sigProcessor.otsuThreshold(px)
        val processed = sigProcessor.processInk(bmp, thr, _state.value.inkColor, _state.value.sigBackground)
            ?: bmp
        val data = sigProcessor.toSignatureData(processed)
        // Colocación por defecto en la página de firma del distribuidor (pág. 24).
        _state.value = _state.value.copy(signature = data)
        // Colocar en todas las páginas de firma detectadas (o la 24 por defecto).
        if (_state.value.signPages.isNotEmpty()) stampAllPages()
        else _state.value = _state.value.copy(stamps = listOf(defaultStamp()))
    }

    /** Firma extraída de una foto: localiza con IA, recorta y limpia. */
    /** Guarda la última foto elegida en "Extraer de foto", para poder recortarla de
     *  nuevo sin tener que volver a seleccionarla. */
    private var lastPickedPhoto: Bitmap? = null

    fun rememberPickedPhoto(bmp: Bitmap) { lastPickedPhoto = bmp }
    fun lastPickedPhotoOrNull(): Bitmap? = lastPickedPhoto

    /**
     * Recorte MANUAL confirmado por el usuario: NO pasa por la IA de localización —
     * el usuario ya decidió la región exacta arrastrando el dedo. Se ejecuta
     * directamente el pipeline de tinta (aplanar + Otsu + recorte a bounding-box)
     * sobre esa región, igual que cuando el locator sí acierta.
     */
    fun useManualSignatureCrop(cropped: Bitmap) {
        val processed = sigProcessor.fromPhoto(cropped, _state.value.inkColor, _state.value.sigBackground)
            ?: cropped
        rawSignatureBitmap = cropped
        rawSignatureIsPhoto = true
        val data = sigProcessor.toSignatureData(processed)
        _state.value = _state.value.copy(signature = data, error = null)
        if (_state.value.signPages.isNotEmpty()) stampAllPages()
        else _state.value = _state.value.copy(stamps = listOf(defaultStamp()))
    }

    fun extractSignatureFromPhoto(bmp: Bitmap) {
        lastPickedPhoto = bmp
        viewModelScope.launch {
            _state.value = _state.value.copy(locatingSignature = true, error = null)
            // Redimensionar antes de mandar a locate_signature (mismo motivo que en
            // DocumentLoader: fotos a resolución completa provocan 400/500 en las IAs).
            val forLocate = withContext(Dispatchers.Default) {
                val longSide = maxOf(bmp.width, bmp.height)
                if (longSide > 2000) {
                    val scale = 2000f / longSide
                    bmp.scale((bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1))
                } else bmp
            }
            val b64 = withContext(Dispatchers.IO) {
                val jpg = ByteArrayOutputStream().also { forLocate.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                Base64.encodeToString(jpg, Base64.NO_WRAP)
            }
            val box = runCatching {
                locator.locate(b64, _state.value.availableProviders)
            }.getOrNull()
            // Fallback razonable (0.2.0): si ningún motor localiza la firma, NO se aplica
            // processInk a la foto ENTERA (eso sacaba resultados basura: cualquier sombra
            // o arruga se colaba como "trazo"). Se ofrece la foto original tal cual y se
            // avisa al usuario, en vez de forzar un procesado ciego. Tampoco se guarda como
            // "bitmap crudo reprocesable": cambiar color/fondo no tendría sentido sobre una
            // foto sin recortar ni tintar.
            val data: com.mejoresiagratis.rellenador.data.model.SignatureData
            if (box != null) {
                val cropped = sigProcessor.crop(bmp, box)
                // El recorte a bounding-box SIGUE activo aunque el locator ya recorte:
                // el riesgo de "desviarse a una esquina" (0.2.0) solo aplica a fotos
                // COMPLETAS sin recortar (ese caso ya no se procesa en absoluto, ver
                // fallback abajo). Sobre una región ya acotada por el locator, recortar
                // de nuevo al trazo real es seguro y necesario: sin esto, una caja del
                // locator floja (con margen vacío) produce un lienzo grande casi en
                // blanco que luego se amplía/recorta mal al encajarlo en el hueco del
                // contrato (letterbox).
                val processed = sigProcessor.fromPhoto(
                    cropped, _state.value.inkColor, _state.value.sigBackground
                ) ?: cropped
                rawSignatureBitmap = cropped
                rawSignatureIsPhoto = true
                data = sigProcessor.toSignatureData(processed)
            } else {
                // Sin caja fiable del locator: en vez de dejar la foto cruda sin
                // procesar (que no se ve como una firma limpia), se aplica el MISMO
                // pipeline completo (aplanado + Otsu + recorte a bounding-box) a la
                // foto entera. Funciona muy bien si la foto ya es solo la firma
                // aislada sobre fondo claro (caso frecuente), y es un mejor punto de
                // partida que la foto sin tocar incluso si hay más contenido alrededor.
                val processedWhole = sigProcessor.fromPhoto(bmp, _state.value.inkColor, _state.value.sigBackground)
                if (processedWhole != null) {
                    rawSignatureBitmap = bmp
                    rawSignatureIsPhoto = true
                    data = sigProcessor.toSignatureData(processedWhole)
                } else {
                    // Ni siquiera se detectó tinta en toda la foto: ofrecer tal cual.
                    rawSignatureBitmap = null
                    data = sigProcessor.toSignatureData(bmp)
                }
            }
            _state.value = _state.value.copy(
                locatingSignature = false, signature = data,
                error = if (box == null) "No se localizó automáticamente el área exacta; se procesó la imagen completa." else null
            )
            if (_state.value.signPages.isNotEmpty()) stampAllPages()
            else _state.value = _state.value.copy(stamps = listOf(defaultStamp()))
        }
    }

    private fun defaultStamp() = SignatureStamp(
        pageIndex = 23,       // página 24 (bloque EL DISTRIBUIDOR)
        // Coordenadas calibradas contra contrato-relleno-a1.pdf (centro real)
        xRel = 0.275f, yRel = 0.463f, widthRel = 0.256f, heightRel = 0.114f
    )

    /** Ajuste manual de la colocación (posición/tamaño relativos). Mantiene heightRel
     *  proporcional al ancho para que el slider de "Tamaño" siga escalando la caja
     *  completa (no solo el ancho) y no vuelva a producir deformación. */
    fun updateStamp(xRel: Float, yRel: Float, widthRel: Float) {
        val prevStamp = _state.value.stamps.firstOrNull()
        // Mantiene la proporción ancho/alto de la caja calibrada al reescalar con el slider.
        val ratio = if (prevStamp != null && prevStamp.widthRel > 0f)
            prevStamp.heightRel / prevStamp.widthRel else 0.114f / 0.256f
        _state.value = _state.value.copy(
            stamps = listOf(SignatureStamp(23, xRel, yRel, widthRel, widthRel * ratio))
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

    /** Genera el PDF de preview y abre el renderer bajo demanda. */
    fun buildPreview() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(busy = true, busyMsg = "Preparando previsualización…", error = null)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = exporter.generatePreview(
                        userContractUri = s.userContractUri,
                        values = s.fieldValues,
                        signature = s.signature,
                        stamps = s.stamps,
                        checkboxes = com.mejoresiagratis.rellenador.data.model.ContractFields
                            .checkboxStateFor(s.tipoIdentificacion),
                        fieldMapping = if (s.contractSource == ContractSource.USER) s.fieldMapping else emptyMap()
                    )
                    previewRenderer?.close()
                    PdfPageRenderer(file)
                }
            }.getOrElse {
                _state.value = _state.value.copy(busy = false, error = "No se pudo previsualizar: ${it.message}")
                return@launch
            }
            previewRenderer = result
            _state.value = _state.value.copy(busy = false, totalPages = result.pageCount, previewReady = true)
        }
    }

    fun renderer(): PdfPageRenderer? = previewRenderer

    /** Mueve la firma de una página a una posición relativa (por arrastre/toque). */
    fun moveStamp(pageIdx: Int, xRel: Float, yRel: Float) {
        val cur = _state.value.stamps.firstOrNull { it.pageIndex == pageIdx }
        val width = cur?.widthRel ?: 0.256f
        val height = cur?.heightRel ?: 0.114f
        val others = _state.value.stamps.filterNot { it.pageIndex == pageIdx }
        _state.value = _state.value.copy(
            stamps = others + com.mejoresiagratis.rellenador.data.model.SignatureStamp(
                pageIdx, xRel.coerceIn(0f, 1f), yRel.coerceIn(0f, 1f), width, height
            )
        )
    }

    /** Cambia el color de la tinta de la firma (re-procesa si hay firma cruda). */
    /** Cambia el color de la tinta y reprocesa en vivo desde el bitmap crudo (sin
     *  volver a llamar a la IA de localización). */
    fun setInkColor(color: Int) {
        _state.value = _state.value.copy(inkColor = color)
        reprocessSignatureFromRaw()
    }
    fun setSigBackground(bg: com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background) {
        _state.value = _state.value.copy(sigBackground = bg)
        reprocessSignatureFromRaw()
    }

    private fun reprocessSignatureFromRaw() {
        val raw = rawSignatureBitmap ?: return
        val processed = if (rawSignatureIsPhoto) {
            sigProcessor.fromPhoto(raw, _state.value.inkColor, _state.value.sigBackground) ?: raw
        } else {
            val px = IntArray(raw.width * raw.height)
            raw.getPixels(px, 0, raw.width, 0, 0, raw.width, raw.height)
            val thr = sigProcessor.otsuThreshold(px)
            sigProcessor.processInk(raw, thr, _state.value.inkColor, _state.value.sigBackground) ?: raw
        }
        _state.value = _state.value.copy(signature = sigProcessor.toSignatureData(processed))
    }

    /** Guarda la firma actual para reutilizarla (persistida en PrefsRepository). */
    fun saveCurrentSignature(name: String) {
        val sig = _state.value.signature ?: return
        viewModelScope.launch {
            val b64 = android.util.Base64.encodeToString(sig.pngBytes, android.util.Base64.NO_WRAP)
            prefs.saveSignature(name, b64, sig.aspectRatio)
            refreshSavedSignatures()
        }
    }
    fun refreshSavedSignatures() {
        viewModelScope.launch {
            _state.value = _state.value.copy(savedSignatures = prefs.listSignatures())
        }
    }
    fun useSavedSignature(name: String) {
        viewModelScope.launch {
            val saved = prefs.getSignature(name) ?: return@launch
            val bytes = android.util.Base64.decode(saved.first, android.util.Base64.NO_WRAP)
            _state.value = _state.value.copy(
                signature = com.mejoresiagratis.rellenador.data.model.SignatureData(bytes, saved.second)
            )
            if (_state.value.signPages.isNotEmpty()) stampAllPages()
            else _state.value = _state.value.copy(stamps = listOf(defaultStamp()))
        }
    }

    // ---- Ajustes (perfil comercial, URL proxy) ----
    fun setResponsableComercial(name: String) {
        _state.value = _state.value.copy(responsableComercial = name)
        viewModelScope.launch { prefs.setResponsableComercial(name) }
    }

    fun setProxyBaseUrlOverride(url: String) {
        _state.value = _state.value.copy(proxyBaseUrlOverride = url)
        viewModelScope.launch { prefs.setProxyBaseUrlOverride(url) }
    }

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

    override fun onCleared() {
        previewRenderer?.close(); previewRenderer = null
        super.onCleared()
    }
}
