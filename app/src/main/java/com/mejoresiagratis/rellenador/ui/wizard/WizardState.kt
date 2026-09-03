package com.mejoresiagratis.rellenador.ui.wizard

import com.mejoresiagratis.rellenador.data.model.EngineIssue

import android.net.Uri
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.FieldProposal
import com.mejoresiagratis.rellenador.data.model.Paquete
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import java.io.File

/** Los 5 pasos del flujo, fieles a la app web. */
/**
 * Pasos del asistente. **4 pasos desde v0.8.0**: la antigua "Revisión IA" (índice 2)
 * desapareció y su función se fundió en RELLENO — el formulario llega prerrellenado por
 * la IA, con los conflictos marcados en el propio campo.
 *
 * ⚠️ El índice se PERSISTE en `PersistedWizardState.step`. Al eliminar REVISION todos los
 * índices posteriores se desplazan, así que las sesiones guardadas con el esquema antiguo
 * necesitan migración — ver `PersistedWizardState.SCHEMA_VERSION` y `migrateStepIndex()`.
 */
enum class Step(val index: Int, val title: String) {
    CONTRATO(0, "Contrato"),
    DOCUMENTOS(1, "Documentación"),
    RELLENO(2, "Relleno"),
    FIRMA(3, "Firma");
}

/**
 * Estado de un campo del contrato en el paso de Relleno. Sustituye a la separación
 * "bloques vs campos sueltos" que hacía la antigua pantalla de Revisión IA.
 */
@kotlinx.serialization.Serializable
enum class FieldState {
    /** Vacío: ni la IA lo propuso ni el usuario lo escribió. */
    EMPTY,

    /** Rellenado automáticamente por la IA con garantías suficientes (ver `AutoFillPolicy`). */
    AI,

    /** Varios documentos proponen valores distintos: el usuario debe elegir. Bloquea el avance. */
    CONFLICT,

    /**
     * Hay propuesta, pero su procedencia es dudosa (documento cuyo titular no casa con el
     * resto del lote, o dato que suele pertenecer a un tercero: arrendador, gestoría,
     * banco). NO se autorrellena; el usuario decide. Bloquea el avance.
     */
    WARN,

    /** Escrito o confirmado por el usuario. Nunca lo pisa la IA. */
    USER,
}

/**
 * Procedencia de un valor: de qué DOCUMENTO salió y qué MOTORES lo respaldan.
 * El documento es lo que permite detectar el "documento intruso"; el motor solo mide
 * consenso técnico y por sí solo no garantiza que el dato sea del cliente correcto.
 */
@kotlinx.serialization.Serializable
data class FieldOrigin(
    val document: String,               // p. ej. "Alta en RETA", "Certificado censal"
    val engines: Set<String> = emptySet(),
    val note: String = "",
    val risky: Boolean = false,         // dato que típicamente es de un tercero
)

/** Una alternativa elegible para un campo (las variantes en conflicto). */
@kotlinx.serialization.Serializable
data class FieldCandidate(
    val value: String,
    val origin: FieldOrigin,
    /** Campos que se rellenan junto a este al elegirlo (CP/Población/Provincia de una dirección). */
    val linked: Map<String, String> = emptyMap(),
)

/** Origen del contrato base. */
enum class ContractSource { DEFAULT, USER }

data class WizardUiState(
    val step: Step = Step.CONTRATO,
    val contractSource: ContractSource? = null,
    val userContractUri: Uri? = null,
    /**
     * Nombre visible del PDF aportado (0.10.12). Con SAF el `lastPathSegment` del URI es un id
     * opaco (`document:27726`), que es lo que se estaba enseñando en la tarjeta del paso 1: no
     * dice nada y encima cambia entre aperturas del mismo fichero.
     */
    val userContractName: String? = null,
    // Mapeo de plantilla (cuando el PDF es del usuario)
    val userFieldNames: List<String> = emptyList(),      // nombres reales del PDF del usuario
    val fieldMapping: Map<String, String> = emptyMap(),  // canónica -> real
    val needsMapping: Boolean = false,
    val templateFingerprint: String = "",
    /**
     * Esquema del PDF activo — la fuente de las secciones del paso de Relleno desde la tanda 5·4.
     *
     * Con el contrato de Orange se reconoce por nombres de campo característicos y se resuelve al
     * `BuiltinSchemas.orangeDistribution()` de siempre; con un PDF ajeno se construye con
     * `PdfFieldInspector` + `FormSchemaBuilder` y se persiste por huella en `schemas_v1` para
     * reutilizarlo la próxima vez. Sin esquema activo (sesión restaurada de una versión antigua,
     * o restauración con el URI de origen caducado), `FillStep` cae a `canonFillSections()` con
     * `FieldKeys.IDENTITY` — es la red de seguridad para Orange y sólo para él.
     *
     * No se persiste: el esquema vive en `schemas_v1` bajo la huella, y se recupera al restaurar
     * la sesión leyendo esa huella. Duplicar la carga aquí obligaría a versionar
     * `PersistedWizardState` otra vez para nada.
     */
    val activeSchema: com.mejoresiagratis.rellenador.data.model.FormSchema? = null,
    val responsableComercial: String = com.mejoresiagratis.rellenador.data.model.ContractFields.RESPONSABLE_VALUE,
    val proxyBaseUrlOverride: String = "",

    val docUris: List<Uri> = emptyList(),
    val availableProviders: List<AiProvider> = emptyList(),   // los que tienen clave en servidor (GET)
    val enabledProviders: Set<AiProvider> = emptySet(),
    /** Solo motores que procesan en la UE. Desactiva y bloquea el resto (v0.9.1). */
    val euOnly: Boolean = false,
    /** El usuario marcó «no volver a preguntar» en el aviso previo al análisis. */
    val consentRemembered: Boolean = false,
    /** El aviso está en pantalla, esperando decisión. No se persiste. */
    val showConsent: Boolean = false,

    val busy: Boolean = false,
    val busyMsg: String = "",
    val error: String? = null,
    val engineErrors: List<String> = emptyList(),   // mensaje crudo por motor
    /**
     * Fallos por motor ya clasificados (v0.9.0). Al fundir Revisión IA en Relleno
     * (v0.8.0) se borró el único panel que mostraba `engineErrors`, y los fallos de
     * motor pasaron a ser invisibles: si Gemini agotaba cuota, la extracción salía con
     * menos datos y el usuario no sabía por qué.
     */
    val engineIssues: List<EngineIssue> = emptyList(),

    // Tanda 2 — progreso en vivo de la extracción multi-motor (MotorLoadingIndicator)
    val activeProvider: AiProvider? = null,
    val finishedProviders: Set<AiProvider> = emptySet(),
    // Mezcla 2+3 — progreso real documento × motor para el pop-up de carga con barra
    // animada ("Documento 3/6 · zeb1.pdf"). progressTotal=0 mientras no hay extracción.
    val activeDocLabel: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,

    // Resultado de la extracción
    val proposals: List<FieldProposal> = emptyList(),
    val packages: List<Paquete> = emptyList(),
    val tipoIdentificacion: String? = null,
    val enginesOk: Set<String> = emptySet(),

    // Valores finales confirmados por el usuario (campo canónico -> valor)
    val fieldValues: Map<String, String> = emptyMap(),

    // ── Relleno unificado (v0.8.0) ────────────────────────────────────────────
    /** Estado por campo. Ausente = [FieldState.EMPTY]. */
    val fieldStates: Map<String, FieldState> = emptyMap(),
    /** De dónde salió el valor actual de cada campo (documento + motores). */
    val fieldOrigins: Map<String, FieldOrigin> = emptyMap(),
    /** Alternativas elegibles por campo (conflictos y valores dudosos). */
    val fieldCandidates: Map<String, List<FieldCandidate>> = emptyMap(),
    /**
     * Pila de deshacer. NO se persiste: deshacer es de la sesión en curso.
     * Cada entrada guarda el valor/estado/origen previos de los campos que cambió.
     */
    val undoStack: List<UndoEntry> = emptyList(),

    // Firma
    val signature: SignatureData? = null,
    val stamps: List<SignatureStamp> = emptyList(),
    val locatingSignature: Boolean = false,
    val inkColor: Int = android.graphics.Color.rgb(20, 30, 90),   // azul oscuro por defecto
    val sigBackground: com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background =
        com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.TRANSPARENT,
    val savedSignatures: List<String> = emptyList(),
    // Detección de huecos de firma (Tanda B)
    val signPages: List<Int> = emptyList(),          // índices 0-based detectados/ajustados
    val signAnchors: Map<Int, Float> = emptyMap(),   // página -> yr del rótulo
    val totalPages: Int = 0,
    // Cierto mientras se analiza el contrato elegido/subido en el Paso 1 (páginas,
    // huecos de firma) — controla el estado de carga del resumen "Estructura detectada".
    val detectingStructure: Boolean = false,

    // Previsualización (Tanda C)
    val previewReady: Boolean = false,

    // PDF final generado
    val outputFile: File? = null,
    val outputReady: Boolean = false
) {
    val canAdvanceFromContrato get() = contractSource != null
    val canAdvanceFromDocs get() = docUris.isNotEmpty() && enabledProviders.isNotEmpty()
}

/**
 * Una acción deshacible del paso de Relleno. Guarda el estado ANTERIOR de los campos
 * tocados, de modo que deshacer sea exacto (incluye el caso "el campo no existía":
 * `value = null` ⇒ al deshacer se elimina la clave).
 */
data class UndoEntry(
    val label: String,
    val previousValues: Map<String, String?>,
    val previousStates: Map<String, FieldState?>,
    val previousOrigins: Map<String, FieldOrigin?>,
)

/** Campos que bloquean el avance a Firma mientras sigan sin decidir. */
fun WizardUiState.pendingDecisions(): List<String> =
    fieldStates.filterValues { it == FieldState.CONFLICT || it == FieldState.WARN }.keys.toList()
