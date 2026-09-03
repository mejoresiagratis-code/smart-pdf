package com.mejoresiagratis.rellenador.data.repository

import android.util.Base64
import com.mejoresiagratis.rellenador.data.model.Paquete
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import com.mejoresiagratis.rellenador.ui.wizard.ContractSource
import com.mejoresiagratis.rellenador.ui.wizard.FieldCandidate
import com.mejoresiagratis.rellenador.ui.wizard.FieldOrigin
import com.mejoresiagratis.rellenador.ui.wizard.FieldState
import com.mejoresiagratis.rellenador.ui.wizard.Step
import com.mejoresiagratis.rellenador.ui.wizard.WizardUiState
import kotlinx.serialization.Serializable

/**
 * DTO plano y `@Serializable` con TODO lo que merece la pena persistir del estado del
 * wizard entre sesiones. Se aísla del `WizardUiState` original (que tiene tipos no
 * serializables como `Uri`, `SignatureData` con ByteArray, y campos efímeros como
 * `busy` que no interesan al restaurar).
 *
 * Qué NO se persiste, y por qué:
 *  - `busy`, `busyMsg`, `error`, `engineErrors`, `activeProvider`, `finishedProviders`,
 *    `activeDocLabel`, `progressCurrent`, `progressTotal`, `locatingSignature`,
 *    `previewReady`, `outputFile`, `outputReady` — son transitorios. Al restaurar la app
 *    no está "ocupada" ni "generando".
 *  - `availableProviders`, `enabledProviders`, `savedSignatures`, `responsableComercial`,
 *    `proxyBaseUrlOverride` — ya se cargan por su cuenta desde PrefsRepository al arrancar
 *    el ViewModel; no hay que duplicarlos aquí.
 *  - `proposals` — se recalculan de `packages` + `fieldValues` implícitos. Persistir los
 *    dos primeros es suficiente para retomar la Revisión IA en el mismo punto.
 *
 * URIs: se persisten como String. AVISO: si el proceso murió, los permisos de lectura
 * al URI original pueden haberse perdido si el usuario no los concedió como persistables.
 * Al restaurar comprobamos accesibilidad y avisamos si algún URI ya no es válido. La
 * copia local a almacenamiento privado se pospone a la Fase 2 (ver ROADMAP.md).
 */
@Serializable
data class PersistedWizardState(
    /**
     * Versión del esquema. Ausente (0) = sesiones guardadas ANTES de v0.8.0, cuando el
     * asistente tenía 5 pasos e incluía "Revisión IA" en el índice 2. Al eliminarlo, los
     * índices se desplazaron y hay que migrarlos — ver [migrateStepIndex].
     */
    val schemaVersion: Int = 0,
    val step: Int = 0,
    val contractSource: String? = null,        // "DEFAULT" | "USER" | null
    val userContractUri: String? = null,
    /**
     * Nombre visible del PDF aportado (0.10.12). Campo nuevo con valor por defecto, así que una
     * sesión guardada por una versión anterior se deserializa sin tocar `SCHEMA_VERSION`: sale
     * nulo y la tarjeta del paso 1 cae al comportamiento de antes hasta que se vuelva a elegir.
     */
    val userContractName: String? = null,
    val userFieldNames: List<String> = emptyList(),
    val fieldMapping: Map<String, String> = emptyMap(),
    val needsMapping: Boolean = false,
    val templateFingerprint: String = "",

    val docUris: List<String> = emptyList(),

    val packages: List<Paquete> = emptyList(),
    val tipoIdentificacion: String? = null,
    val enginesOk: Set<String> = emptySet(),

    val fieldValues: Map<String, String> = emptyMap(),

    /**
     * Estado de revisión por campo (v0.8.1). Sin esto, al restaurar una sesión los
     * valores volvían pero los conflictos dejaban de estar marcados y **el bloqueo del
     * avance a Firma desaparecía**: se podía firmar con un conflicto sin resolver.
     */
    val fieldStates: Map<String, FieldState> = emptyMap(),
    val fieldOrigins: Map<String, FieldOrigin> = emptyMap(),
    val fieldCandidates: Map<String, List<FieldCandidate>> = emptyMap(),

    // Firma (bytes en base64 estándar)
    val signaturePngBase64: String? = null,
    val signatureAspectRatio: Float = 0.4f,
    val stamps: List<PersistedStamp> = emptyList(),
    val inkColor: Int = android.graphics.Color.rgb(20, 30, 90),
    val sigBackgroundName: String = "TRANSPARENT",  // TRANSPARENT | WHITE
    val signPages: List<Int> = emptyList(),
    val signAnchors: Map<String, Float> = emptyMap(),   // Int keys serializan como String
    val totalPages: Int = 0
) {
    companion object {
        /** Estado vacío — usado como reset "empezar de nuevo". */
        val EMPTY = PersistedWizardState()
    }
}

@Serializable
data class PersistedStamp(
    val pageIndex: Int,
    val xRel: Float,
    val yRel: Float,
    val widthRel: Float = 0.28f,
    val heightRel: Float = 0.114f
)

/** Convierte el estado en memoria al DTO plano para guardar. */
fun WizardUiState.toPersisted(): PersistedWizardState {
    val sig = signature
    return PersistedWizardState(
        schemaVersion = SCHEMA_VERSION,
        step = step.index,
        fieldStates = fieldStates,
        fieldOrigins = fieldOrigins,
        fieldCandidates = fieldCandidates,
        contractSource = contractSource?.name,
        userContractUri = userContractUri?.toString(),
        userContractName = userContractName,
        userFieldNames = userFieldNames,
        fieldMapping = fieldMapping,
        needsMapping = needsMapping,
        templateFingerprint = templateFingerprint,
        docUris = docUris.map { it.toString() },
        packages = packages,
        tipoIdentificacion = tipoIdentificacion,
        enginesOk = enginesOk,
        fieldValues = fieldValues,
        signaturePngBase64 = sig?.let { Base64.encodeToString(it.pngBytes, Base64.NO_WRAP) },
        signatureAspectRatio = sig?.aspectRatio ?: 0.4f,
        stamps = stamps.map { PersistedStamp(it.pageIndex, it.xRel, it.yRel, it.widthRel, it.heightRel) },
        inkColor = inkColor,
        sigBackgroundName = sigBackground.name,
        signPages = signPages,
        signAnchors = signAnchors.mapKeys { it.key.toString() },
        totalPages = totalPages
    )
}

/** Aplica el DTO restaurado sobre un WizardUiState base (que ya trae los campos que
 *  el ViewModel recarga por su cuenta: providers, responsable, saved signatures…). */
fun PersistedWizardState.applyTo(base: WizardUiState): WizardUiState {
    val bg = runCatching {
        com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.valueOf(sigBackgroundName)
    }.getOrDefault(com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.TRANSPARENT)
    val sig = signaturePngBase64?.let {
        SignatureData(
            pngBytes = Base64.decode(it, Base64.NO_WRAP),
            aspectRatio = signatureAspectRatio
        )
    }
    // Tanda 5·3 — migración v1 -> v2 de la CLAVE de los mapas de campo.
    //
    // Desde la 0.9.3 la extracción ya devolvía nombres reales para un PDF propio, así que en una
    // sesión guardada la mayoría de las claves YA son nombres reales; lo que había mezclado eran
    // las que la app inyectaba con el nombre de Orange (responsable, las tres de fecha, la copia
    // de dirección fiscal). Reindexar con el `fieldMapping` que este mismo DTO ya guardaba las
    // pone todas en nombre real, y es **idempotente** sobre las que ya lo estaban, porque
    // `FieldKeys.real()` deja intacta cualquier clave que el mapeo no cubra.
    //
    // Por eso una sesión de Orange (mapeo vacío) se restaura EXACTAMENTE igual que antes.
    val keys = com.mejoresiagratis.rellenador.data.model.FieldKeys(fieldMapping)
    val needsKeyMigration = schemaVersion < SCHEMA_VERSION
    fun <T> reindexed(m: Map<String, T>): Map<String, T> =
        if (needsKeyMigration) keys.reindex(m) else m

    return base.copy(
        step = Step.entries.getOrNull(migrateStepIndex(step, schemaVersion)) ?: Step.CONTRATO,
        fieldStates = reindexed(fieldStates),
        fieldOrigins = reindexed(fieldOrigins),
        fieldCandidates = reindexed(fieldCandidates),
        // La pila de deshacer NO se persiste a propósito: deshacer es de la sesión en curso.
        undoStack = emptyList(),
        contractSource = contractSource?.let { runCatching { ContractSource.valueOf(it) }.getOrNull() },
        userContractUri = userContractUri?.let { android.net.Uri.parse(it) },
        userContractName = userContractName,
        userFieldNames = userFieldNames,
        fieldMapping = fieldMapping,
        needsMapping = needsMapping,
        templateFingerprint = templateFingerprint,
        docUris = docUris.mapNotNull { runCatching { android.net.Uri.parse(it) }.getOrNull() },
        packages = packages,
        tipoIdentificacion = tipoIdentificacion,
        enginesOk = enginesOk,
        fieldValues = reindexed(fieldValues),
        signature = sig,
        stamps = stamps.map { SignatureStamp(it.pageIndex, it.xRel, it.yRel, it.widthRel, it.heightRel) },
        inkColor = inkColor,
        sigBackground = bg,
        signPages = signPages,
        signAnchors = signAnchors.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
        totalPages = totalPages
    )
}

/**
 * Versión actual del esquema de sesión persistida.
 *
 * - 1 — v0.8.0: asistente de 4 pasos (ver [migrateStepIndex]).
 * - 2 — tanda 5·3: los mapas de campo se indexan por **nombre real** del campo del AcroForm en
 *   vez de por clave de `CANON` (ver `applyTo`).
 */
const val SCHEMA_VERSION = 2

/**
 * Traduce el índice de paso guardado al esquema actual.
 *
 * Esquema 0 (5 pasos, hasta v0.7.10):
 *   0 CONTRATO · 1 DOCUMENTOS · 2 REVISION · 3 RELLENO · 4 FIRMA
 * Esquema 1 (4 pasos, desde v0.8.0):
 *   0 CONTRATO · 1 DOCUMENTOS · 2 RELLENO · 3 FIRMA
 *
 * Sin esta migración, una sesión guardada en RELLENO (3) reabriría en FIRMA, y una
 * guardada en FIRMA (4) caería al índice inexistente 4 → CONTRATO, perdiendo el trabajo.
 * REVISION (2) se traduce a RELLENO, que es donde vive ahora esa función.
 */
fun migrateStepIndex(saved: Int, schemaVersion: Int): Int =
    // Ojo: el umbral es 1 y NO `SCHEMA_VERSION`. Al subir la versión a 2 en la tanda 5·3, un
    // `>= SCHEMA_VERSION` habría vuelto a traducir índices de sesiones guardadas con el esquema 1
    // —que ya tenían el índice correcto de 4 pasos— y RELLENO se habría reabierto en DOCUMENTOS.
    // Cada migración mira su propio umbral, no el global.
    if (schemaVersion >= 1) saved
    else when (saved) {
        0 -> 0   // CONTRATO   → CONTRATO
        1 -> 1   // DOCUMENTOS → DOCUMENTOS
        2 -> 2   // REVISION   → RELLENO (absorbe la revisión)
        3 -> 2   // RELLENO    → RELLENO
        4 -> 3   // FIRMA      → FIRMA
        else -> 0
    }
