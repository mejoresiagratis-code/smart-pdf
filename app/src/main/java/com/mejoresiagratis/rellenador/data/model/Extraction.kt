package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.Serializable

/** Respuesta JSON que la IA devuelve dentro de proxy.text. Fiel a la app web. */
@Serializable
data class AiExtraction(
    val sugerencias: Map<String, String> = emptyMap(),
    val tipo_identificacion: String? = null,           // "CIF" | "NIF" | "NIE"
    val alternativas: Map<String, List<AltValue>> = emptyMap(),
    val paquetes: List<Paquete> = emptyList()
)

@Serializable
data class AltValue(
    val valor: String,
    val fuente: String = "",
    val nota: String = ""
)

@Serializable
data class Paquete(
    val tipo: String,                 // direccion | direccion_comercio | empresa | persona | banco
    val etiqueta: String = "",
    val fuente: String = "",
    val datos: Map<String, String> = emptyMap()
)

/**
 * Una propuesta agregada por campo, con todos los valores candidatos y qué
 * motores los propusieron. Alimenta el toast de confirmación por campo.
 */
data class FieldProposal(
    val fieldKey: String,
    val candidates: List<Candidate> = emptyList()
)

data class Candidate(
    val value: String,
    val sources: Set<String> = emptySet(),   // motores que lo propusieron (Claude, Gemini…)
    val note: String = ""
)

/** Campo canónico del contrato con su etiqueta legible.
 *  Verificado contra el AcroForm real (contrato-base.pdf, 54 págs, vía pypdf get_fields():
 *  26 campos en total — 20 de aquí + CIF/NIF/undefined (checkboxes, gestionados aparte
 *  vía checkboxStateFor) + Responsable Comercial MASORANGE (autorrelleno, vía RESPONSABLE_KEY)). */
data class CanonField(val key: String, val label: String)

object ContractFields {
    /** Claves canónicas EXACTAS — los dobles espacios importan. */
    val CANON: List<CanonField> = listOf(
        CanonField("Nombre  Razón Social", "Razón social"),
        CanonField("Nombre Comercial", "Nombre comercial"),
        CanonField("NIE", "CIF/NIF/NIE de la empresa"),
        CanonField("Nombre representante", "Nombre del representante"),
        CanonField("NIF representante", "NIF del representante"),
        CanonField("Dirección", "Dirección (fiscal)"),
        CanonField("CP", "Código postal"),
        CanonField("Población", "Población"),
        CanonField("Provincia", "Provincia"),
        CanonField("Dirección_2", "Dirección comercio/PdV"),
        CanonField("CP_2", "CP comercio"),
        CanonField("Población_2", "Población comercio"),
        CanonField("Provincia_2", "Provincia comercio"),
        CanonField("Teléfono", "Teléfono"),
        CanonField("Email Comercial", "Email comercial"),
        CanonField("Email  Facturación", "Email facturación"),
        CanonField("Datos bancarios del DISTRIBUIDOR", "IBAN"),
        // Añadidos tras auditoría contra el AcroForm real (contrato-base.pdf):
        // existían en el PDF y en el prompt de IA de ambas apps, pero no estaban
        // conectados aquí — migración incompleta de la web a Android.
        CanonField("Actividad principal del negocio", "Actividad principal (IAE)"),
        CanonField(
            "Profesión puestos de trabajo datos no económicos de nómina historial del trabajador",
            "Profesión / puesto de trabajo"
        ),
        CanonField("Fecha", "Fecha · día"),
        CanonField("de", "Fecha · mes"),
        CanonField("año", "Fecha · año"),
    )

    /** Valor fijo autorrellenado (regla de la app web). */
    const val RESPONSABLE_KEY = "Responsable Comercial MASORANGE"
    /** Valor por defecto; el real lo edita el usuario en Ajustes (PrefsRepository). */
    const val RESPONSABLE_VALUE = "PABLO SALVADOR POVEDA"

    // Checkboxes del tipo de identificación (valores reales del AcroForm).
    const val CHECKBOX_NIF = "NIF"
    const val CHECKBOX_CIF = "CIF"
    // El contrato real SÍ tiene una tercera casilla para NIE, pero quedó sin nombre
    // propio al exportar el AcroForm original — su campo real se llama literalmente
    // "undefined" (verificado con pypdf get_fields() contra contrato-base.pdf; la app
    // web ya lo detecta así: cbs.find(f=>norm(f.name)==="undefined")). No es un nombre
    // que podamos cambiar aquí: para marcar esa casilla en el PDF real hay que usar
    // exactamente ese valor, tal como CHECKBOX_CIF/CHECKBOX_NIF usan los suyos.
    const val CHECKBOX_NIE = "undefined"
    const val CHECKBOX_ON = "/On"
    const val CHECKBOX_OFF = "/Off"

    /**
     * Devuelve el estado de las 3 casillas de tipo de identificación (CIF/NIF/NIE)
     * según el tipo detectado por la IA o corregido a mano por el usuario.
     * Antes NIE no marcaba ninguna casilla (premisa incorrecta: "el contrato solo
     * tiene casillas NIF y CIF") — el contrato real sí tiene una tercera casilla
     * para NIE, solo que mal nombrada en el propio PDF.
     */
    fun checkboxStateFor(tipoIdentificacion: String?): Map<String, String> = when (tipoIdentificacion?.uppercase()) {
        "CIF" -> mapOf(CHECKBOX_CIF to CHECKBOX_ON, CHECKBOX_NIF to CHECKBOX_OFF, CHECKBOX_NIE to CHECKBOX_OFF)
        "NIF" -> mapOf(CHECKBOX_NIF to CHECKBOX_ON, CHECKBOX_CIF to CHECKBOX_OFF, CHECKBOX_NIE to CHECKBOX_OFF)
        "NIE" -> mapOf(CHECKBOX_NIE to CHECKBOX_ON, CHECKBOX_CIF to CHECKBOX_OFF, CHECKBOX_NIF to CHECKBOX_OFF)
        else -> emptyMap()   // tipo desconocido: no marcar ninguna (dejar manual)
    }

    fun labelFor(key: String): String =
        CANON.firstOrNull { it.key == key }?.label ?: key
}

/**
 * Aplica un paquete a los campos, replicando applyPaquete() de la web.
 * Las claves del paquete vienen SIN sufijo (Dirección/CP/Población/Provincia).
 * `targetBlock2 = true` las envía al bloque de comercio (_2); false al fiscal.
 * Devuelve el mapa de (claveCanónica -> valor) a fusionar en fieldValues.
 */
object PackageApplier {
    /** Claves de dirección que admiten el sufijo _2. */
    private val ADDRESS_KEYS = setOf("Dirección", "CP", "Población", "Provincia")

    fun apply(paquete: Paquete, targetBlock2: Boolean): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in paquete.datos) {
            if (value.isBlank()) continue
            val targetKey = if (targetBlock2 && key in ADDRESS_KEYS) "${key}_2" else key
            out[targetKey] = value
        }
        return out
    }

    /** ¿Este paquete puede ir al bloque _2? (solo direcciones). */
    fun canTargetBlock2(paquete: Paquete): Boolean =
        paquete.tipo == "direccion" || paquete.tipo == "direccion_comercio"
}
