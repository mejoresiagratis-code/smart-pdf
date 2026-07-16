package com.mejoresiagratis.rellenador.data.repository

import android.util.Base64
import com.mejoresiagratis.rellenador.data.model.Paquete
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import com.mejoresiagratis.rellenador.ui.wizard.ContractSource
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
    val step: Int = 0,
    val contractSource: String? = null,        // "DEFAULT" | "USER" | null
    val userContractUri: String? = null,
    val userFieldNames: List<String> = emptyList(),
    val fieldMapping: Map<String, String> = emptyMap(),
    val needsMapping: Boolean = false,
    val templateFingerprint: String = "",

    val docUris: List<String> = emptyList(),

    val packages: List<Paquete> = emptyList(),
    val tipoIdentificacion: String? = null,
    val enginesOk: Set<String> = emptySet(),

    val fieldValues: Map<String, String> = emptyMap(),

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
        step = step.index,
        contractSource = contractSource?.name,
        userContractUri = userContractUri?.toString(),
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
    return base.copy(
        step = Step.entries.getOrNull(step) ?: Step.CONTRATO,
        contractSource = contractSource?.let { runCatching { ContractSource.valueOf(it) }.getOrNull() },
        userContractUri = userContractUri?.let { android.net.Uri.parse(it) },
        userFieldNames = userFieldNames,
        fieldMapping = fieldMapping,
        needsMapping = needsMapping,
        templateFingerprint = templateFingerprint,
        docUris = docUris.mapNotNull { runCatching { android.net.Uri.parse(it) }.getOrNull() },
        packages = packages,
        tipoIdentificacion = tipoIdentificacion,
        enginesOk = enginesOk,
        fieldValues = fieldValues,
        signature = sig,
        stamps = stamps.map { SignatureStamp(it.pageIndex, it.xRel, it.yRel, it.widthRel, it.heightRel) },
        inkColor = inkColor,
        sigBackground = bg,
        signPages = signPages,
        signAnchors = signAnchors.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
        totalPages = totalPages
    )
}
