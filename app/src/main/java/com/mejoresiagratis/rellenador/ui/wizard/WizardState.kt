package com.mejoresiagratis.rellenador.ui.wizard

import android.net.Uri
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.FieldProposal
import com.mejoresiagratis.rellenador.data.model.Paquete
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import java.io.File

/** Los 5 pasos del flujo, fieles a la app web. */
enum class Step(val index: Int, val title: String) {
    CONTRATO(0, "Contrato"),
    DOCUMENTOS(1, "Documentación"),
    REVISION(2, "Revisión IA"),
    RELLENO(3, "Relleno"),
    FIRMA(4, "Firma");
}

/** Origen del contrato base. */
enum class ContractSource { DEFAULT, USER }

data class WizardUiState(
    val step: Step = Step.CONTRATO,
    val contractSource: ContractSource? = null,
    val userContractUri: Uri? = null,

    val docUris: List<Uri> = emptyList(),
    val availableProviders: List<AiProvider> = emptyList(),   // los que tienen clave en servidor (GET)
    val enabledProviders: Set<AiProvider> = emptySet(),

    val busy: Boolean = false,
    val busyMsg: String = "",
    val error: String? = null,

    // Resultado de la extracción
    val proposals: List<FieldProposal> = emptyList(),
    val packages: List<Paquete> = emptyList(),
    val tipoIdentificacion: String? = null,
    val enginesOk: Set<String> = emptySet(),

    // Valores finales confirmados por el usuario (campo canónico -> valor)
    val fieldValues: Map<String, String> = emptyMap(),

    // Firma
    val signature: SignatureData? = null,
    val stamps: List<SignatureStamp> = emptyList(),
    val locatingSignature: Boolean = false,

    // PDF final generado
    val outputFile: File? = null,
    val outputReady: Boolean = false
) {
    val canAdvanceFromContrato get() = contractSource != null
    val canAdvanceFromDocs get() = docUris.isNotEmpty() && enabledProviders.isNotEmpty()
}
