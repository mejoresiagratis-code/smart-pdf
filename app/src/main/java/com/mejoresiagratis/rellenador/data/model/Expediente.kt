package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.Serializable

/**
 * **Expediente**: la unidad de trabajo persistida.
 *
 * Un alta en Aire no es un formulario, es un conjunto: la propia documentación de Aire exige
 * CIF + SEPA firmado + CONTRATO firmado + DNI del representante + cuenta bancaria, más los
 * anexos que apliquen (portabilidad, conectividad). Y los cuatro PDFs piden el mismo núcleo de
 * datos de cliente con nombres de campo distintos.
 *
 * Fase 2, tanda 3 de 3. **La interfaz sigue trabajando sobre un solo formulario**: [documents]
 * arranca con exactamente un elemento y nada visible cambia. Se modela así ya para no pagar
 * dos migraciones —primero a `FormSchema` y luego a expediente— sobre datos de trabajo reales.
 * El razonamiento completo está en `ROADMAP.md`.
 */
@Serializable
data class Expediente(
    val id: String,
    /** Fecha ISO de creación. */
    val creado: String = "",
    val titulo: String = "",

    /**
     * Datos compartidos por todos los formularios del expediente, indexados por
     * [CanonicalKeys]. Se extraen **una vez** y cada documento los proyecta a sus campos
     * reales a través de `FormField.canonical`.
     */
    val compartidos: Map<String, String> = emptyMap(),

    /** Formularios del expediente. Hoy siempre uno. */
    val documents: List<ExpedienteDocument> = emptyList(),

    val version: Int = VERSION,
) {
    /** El documento sobre el que trabaja hoy la interfaz, o null si el expediente está vacío. */
    fun primary(): ExpedienteDocument? = documents.firstOrNull()

    companion object {
        const val VERSION = 1
    }
}

/**
 * Un formulario dentro del expediente: qué esquema usa y qué se ha escrito en él.
 *
 * No guarda el PDF. Guarda [fingerprint], que es con lo que se reencuentra el esquema al
 * volver a cargar el documento.
 */
@Serializable
data class ExpedienteDocument(
    val schemaId: String,
    val fingerprint: String,
    val titulo: String = "",

    /** Valor por **nombre real** de campo del AcroForm. Es lo que se escribe en el PDF. */
    val valores: Map<String, String> = emptyMap(),

    /** Casillas por nombre real. El estado de activación real lo resuelve `AcroFormFiller`. */
    val casillas: Map<String, String> = emptyMap(),
)

/**
 * Convierte lo que ya está guardado al modelo de esquema, **sin destruir nada**.
 *
 * Lo persistido hasta ahora es `templates_v1`: un `Map<huella, Map<canónica, real>>`, donde
 * «canónica» son las claves de `ContractFields.CANON` —nombres de campo del contrato de
 * Orange— y «real» el nombre del campo en el PDF que subió el usuario.
 *
 * La migración es **perezosa y aditiva**, no un cambio masivo de golpe: `templates_v1` se
 * queda intacto, y cuando se pide el esquema de una huella que aún no lo tiene, se deriva del
 * mapeo antiguo y se guarda en la clave nueva. Consecuencias buscadas:
 *
 * - No hay un momento único en el que todo se reescriba y pueda romperse a la vez.
 * - Volver atrás es quitar la clave nueva; el dato original sigue donde estaba.
 * - Una plantilla que nunca se vuelva a abrir simplemente no se migra, y no pasa nada.
 *
 * Es deliberadamente lo contrario de lo que se hizo en la 0.8.0 con el índice de paso, que sí
 * fue una migración de golpe y dio problemas.
 */
object SchemaMigration {

    /**
     * Deriva un [FormSchema] de un mapeo antiguo `canónica -> real`.
     *
     * El esquema resultante es `LEARNED`: describe el PDF **del usuario**, no el de Orange.
     * De `CANON` se hereda lo único que sigue siendo válido —la etiqueta legible y la clave
     * canónica transversal—; el nombre del campo es el real del PDF de destino.
     *
     * Los campos del PDF que el mapeo antiguo no cubría no aparecen: esa información nunca se
     * guardó. Se completan al inspeccionar el documento, que es justo lo que hará el
     * constructor de esquemas cuando llegue.
     */
    fun fromLegacyMapping(
        fingerprint: String,
        mapping: Map<String, String>,
        pageCount: Int = 0,
        titulo: String = "Plantilla guardada",
    ): FormSchema {
        val fields = mapping.entries
            .filter { it.value.isNotBlank() }
            .mapIndexed { i, (canonKey, realName) ->
                FormField(
                    name = realName,
                    label = ContractFields.labelFor(canonKey),
                    kind = FieldKind.TEXT,
                    origin = if (canonKey in ContractFields.DATE_KEYS) {
                        ValueOrigin.FIRMA
                    } else {
                        ValueOrigin.DOCUMENTO
                    },
                    canonical = BuiltinSchemas.canonicalFor(canonKey),
                    order = i,
                    // El mapeo antiguo lo confirmó el usuario en el editor, así que su
                    // etiqueta manda sobre cualquier reetiquetado posterior automático.
                    labelSource = LabelSource.USUARIO,
                )
            }

        return FormSchema(
            id = "learned:$fingerprint",
            title = titulo,
            source = SchemaSource.LEARNED,
            fingerprint = fingerprint,
            pageCount = pageCount,
            sections = listOf(
                FormSection(
                    id = "migrado",
                    title = "Campos mapeados",
                    kind = SectionKind.SIMPLE,
                    fields = fields,
                )
            ),
        )
    }
}
