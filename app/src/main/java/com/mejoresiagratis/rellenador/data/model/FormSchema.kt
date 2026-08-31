package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.Serializable

/**
 * Modelo de **esquema de formulario**: la descripción de qué campos tiene un PDF rellenable,
 * cómo se agrupan y de dónde sale el valor de cada uno.
 *
 * Sustituye a la premisa de `ContractFields.CANON`, que era una lista fija de 22 campos del
 * contrato de distribución de Orange. Con `CANON`, subir cualquier otro PDF detectaba bien sus
 * campos pero seguía mostrando los 22 de siempre.
 *
 * Fase 2, tanda 2 de 3 (ver `ROADMAP.md`). Esta tanda añade **sólo estructuras nuevas**: nada
 * de esto se persiste ni se usa todavía en el asistente. La persistencia, la migración de los
 * `Map<canónica,real>` ya guardados y el contenedor de expediente van en la tanda 3, aislados
 * por ser la parte de riesgo alto.
 *
 * El diseño sale de analizar los cuatro formularios reales de Aire (481, 202, 141 y 19 campos);
 * el detalle de cada patrón está en `docs/ANALISIS_FORMULARIOS_AIRE.md`.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Vocabulario canónico transversal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Claves canónicas **compartidas por todo el expediente**.
 *
 * Los cuatro formularios de Aire piden el mismo núcleo de datos de cliente con nombres de campo
 * distintos: la razón social es `Nombre o razón social` en el contrato, `NOMBRE DEL DEUDOR` en
 * el SEPA y `Titular` en portabilidad. Enganchando cada campo a una de estas claves, el dato se
 * extrae **una vez** y sirve para todos.
 *
 * Ojo: `CANON` de `ContractFields` NO es esto. `CANON` son los nombres reales de los campos de
 * un PDF concreto (con sus dobles espacios y sus sufijos `_2`); esto es vocabulario de negocio,
 * independiente de cualquier documento.
 */
object CanonicalKeys {
    const val RAZON_SOCIAL = "razon_social"
    const val NOMBRE_COMERCIAL = "nombre_comercial"
    const val IDENTIFICACION = "identificacion"          // CIF/NIF/NIE de la entidad
    const val TIPO_IDENTIFICACION = "tipo_identificacion"
    const val ACTIVIDAD = "actividad"

    const val REPRESENTANTE_NOMBRE = "representante_nombre"
    const val REPRESENTANTE_NIF = "representante_nif"
    const val REPRESENTANTE_MOVIL = "representante_movil"
    const val REPRESENTANTE_EMAIL = "representante_email"

    const val DIRECCION = "direccion"
    const val CP = "cp"
    const val POBLACION = "poblacion"
    const val PROVINCIA = "provincia"

    /** Segundo bloque de dirección (comercio/PdV en Orange, instalación en Aire). */
    const val DIRECCION_2 = "direccion_2"
    const val CP_2 = "cp_2"
    const val POBLACION_2 = "poblacion_2"
    const val PROVINCIA_2 = "provincia_2"

    const val TELEFONO = "telefono"
    const val EMAIL_COMERCIAL = "email_comercial"
    const val EMAIL_FACTURACION = "email_facturacion"
    const val IBAN = "iban"
    const val BIC = "bic"

    const val FECHA_DIA = "fecha_dia"
    const val FECHA_MES = "fecha_mes"
    const val FECHA_ANIO = "fecha_anio"
}

// ─────────────────────────────────────────────────────────────────────────────
// Origen del valor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * De dónde sale el valor de un campo. Determina si la IA debe proponerlo, si se autorrellena
 * solo, o si hay que dejarlo en manos del usuario.
 *
 * Se llama `ValueOrigin` y no `FieldOrigin` porque **ese nombre ya está cogido**:
 * `ui.wizard.FieldOrigin` es un `data class` con otro significado — de qué documento concreto y
 * qué motores salió el valor que hay ahora mismo en un campo. `FieldResolver` y
 * `AutoFillPolicy` viven en este paquete y lo importan, así que declarar aquí otro
 * `FieldOrigin` volvía ambigua la referencia (fue lo que tumbó el build de la v0.9.8).
 * Son dos conceptos distintos: aquél es un hecho de esta ejecución, éste es una propiedad de
 * diseño del formulario.
 */
@Serializable
enum class ValueOrigin {
    /** Se extrae de la documentación aportada por el cliente. Es el caso normal. */
    DOCUMENTO,

    /**
     * Constante del distribuidor, configurada una vez en Ajustes: nombre, teléfono, email y
     * código de distribuidor, más el nombre y DNI del comercial al pie de los formularios.
     * Sucesor de `ContractFields.RESPONSABLE_KEY`, que hacía esto mismo para un único campo.
     */
    AJUSTES,

    /**
     * Sale de la plataforma (TEKI) **después** de dar de alta al cliente: `FECHA DE ALTA EN
     * TEKI`, `CÓDIGO DE CLIENTE EN TEKI`. No está en ningún DNI ni certificado bancario, así
     * que la IA no puede proponerlo.
     *
     * Importante: un campo así **no debe bloquear el avance** como si fuera un conflicto por
     * decidir. Vacío es un estado legítimo hasta que exista el alta.
     */
    PLATAFORMA,

    /**
     * Fila de tabla de tarifa: servicio, permanencia, penalización, cantidad, cuotas. Sale del
     * catálogo o del Cotizador, nunca de la documentación del cliente. La IA no debe tocarlo.
     */
    CATALOGO,

    /** Se calcula: `cuota total = cantidad × cuota unitaria`, totales de columna… */
    CALCULADO,

    /** Lo resuelve el paso de Firma (fecha del contrato, hueco o campo de firma). */
    FIRMA,
}

// ─────────────────────────────────────────────────────────────────────────────
// Campos
// ─────────────────────────────────────────────────────────────────────────────

/** Tipo de widget, tal como viene del AcroForm. */
@Serializable
enum class FieldKind {
    TEXT,

    /** Casilla suelta. Su estado de activación real va en [FormField.onState]. */
    CHECKBOX,

    /**
     * Una opción de un grupo excluyente. Varias opciones comparten [FormField.name] (es el
     * mismo campo del AcroForm) y se distinguen por [FormField.onState].
     */
    RADIO,

    /** Campo `/Sig` del AcroForm. Hoy la app sólo sabe estampar imagen; ver fase 6. */
    SIGNATURE,
}

/** De dónde salió la etiqueta legible del campo. Útil para saber de qué fiarse. */
@Serializable
enum class LabelSource {
    /** El nombre del AcroForm ya era legible (`Nombre o razón social`, `NIF/CIF/NIE`…). */
    NOMBRE_REAL,

    /** Etiquetado por visión, porque el nombre no decía nada (`Campo de texto 116`). */
    VISION,

    /** Corregida a mano por el usuario en el editor de mapeo (fase 4). Manda sobre las otras. */
    USUARIO,
}

/**
 * Un campo del formulario.
 *
 * [name] es el nombre **exacto** del AcroForm, con sus dobles espacios y rarezas incluidas: es
 * la única forma de escribir en el PDF. Nunca se normaliza ni se "arregla".
 */
@Serializable
data class FormField(
    /** Nombre real y exacto del campo en el AcroForm. */
    val name: String,

    /** Etiqueta legible que ve el usuario. */
    val label: String,

    val kind: FieldKind = FieldKind.TEXT,
    val origin: ValueOrigin = ValueOrigin.DOCUMENTO,

    /** Clave de [CanonicalKeys] a la que engancha, si comparte dato con otros formularios. */
    val canonical: String? = null,

    /** Página (0-based) y posición en orden de lectura, de `PdfFieldInspector`. */
    val page: Int = 0,
    val order: Int = 0,

    /**
     * Estado de activación **real** del PDF para CHECKBOX y RADIO: `Sí`, `Opción1`, `0`…
     * Se lee de `/AP /N`; no existe ninguna convención (ver v0.9.7). Nulo para TEXT.
     */
    val onState: String? = null,

    /** Texto de la opción, cuando [kind] es RADIO (`PAGO ÚNICO`, `901`, `PORTABILIDAD`…). */
    val optionLabel: String? = null,

    /**
     * Identificador de grupo cuando un **único valor lógico está troceado en varias casillas**
     * de un carácter — el SWIFT/BIC del SEPA son 11 campos (`Text18`…`Text29`). Todos los
     * trozos comparten `combGroup` y se ordenan por [combIndex]. Nulo si no aplica.
     */
    val combGroup: String? = null,
    val combIndex: Int = 0,

    val labelSource: LabelSource = LabelSource.NOMBRE_REAL,

    /** `true` si el campo pertenece a un tercero (titular donante, dirección de instalación). */
    val thirdParty: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Secciones
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
enum class SectionKind {
    /** Bloque corriente de campos sueltos. */
    SIMPLE,

    /** Rejilla de filas y columnas (tarifas, líneas a portar). Ver [FormSection.columns]. */
    TABLE,

    /**
     * Varios bloques idénticos apilados: `Dirección de instalación 1..4` en Conectividad, los
     * bloques de hijos del Modelo 145. No es tabla (los campos no comparten x), pero se repite.
     */
    REPEATED_BLOCK,
}

/**
 * Una columna de tabla.
 *
 * [x] es la coordenada que **define** la columna. Se detecta por geometría y no por el nombre,
 * porque en una misma fila conviven nombres con sentido y nombres autogenerados: la fila 1 de
 * la tabla de Telefonía Fija del contrato es
 * `Campo de texto 116 | Campo de texto 128 | Campo de texto 140 | TF cantidad 01 | …`.
 */
@Serializable
data class TableColumn(
    val id: String,
    val label: String,
    val x: Float,
    val kind: FieldKind = FieldKind.TEXT,
    val origin: ValueOrigin = ValueOrigin.CATALOGO,
    val labelSource: LabelSource = LabelSource.NOMBRE_REAL,
)

/**
 * Una fila de tabla: para cada columna, el campo real que le corresponde.
 *
 * Las celdas se asignan **por posición**, que es lo único fiable. Vale igual para los
 * checkboxes de fila aunque estén dibujados en un recuadro aparte: en Portabilidad, los 100
 * checkboxes de «Provisión» se llaman `Check Box4.0`, `Check Box4.4.5.10.5`… pero el prefijo
 * (`Check Box4`…`7`) da la columna y la `y` da la fila, verificado sobre las 25 filas.
 */
@Serializable
data class TableRow(
    val index: Int,
    /** columnId → campo. Una columna puede faltar en una fila concreta. */
    val cells: Map<String, FormField> = emptyMap(),
    /** `true` en la fila de totales (`TF cantidad TOTAL`), que se calcula y no se teclea. */
    val isTotal: Boolean = false,
)

@Serializable
data class FormSection(
    val id: String,
    val title: String,
    val kind: SectionKind = SectionKind.SIMPLE,

    /** Campos, cuando [kind] es SIMPLE. */
    val fields: List<FormField> = emptyList(),

    /** Columnas y filas, cuando [kind] es TABLE. */
    val columns: List<TableColumn> = emptyList(),
    val rows: List<TableRow> = emptyList(),

    /** Bloques repetidos, cuando [kind] es REPEATED_BLOCK. */
    val blocks: List<List<FormField>> = emptyList(),
) {
    /** Todos los campos de la sección, sea cual sea su forma. */
    fun allFields(): List<FormField> = when (kind) {
        SectionKind.SIMPLE -> fields
        SectionKind.TABLE -> rows.flatMap { it.cells.values }
        SectionKind.REPEATED_BLOCK -> blocks.flatten()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Esquema
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
enum class SchemaSource {
    /** Viene de serie con la app. Hoy sólo el contrato de distribución de Orange. */
    BUILTIN,

    /** Aprendido de un PDF subido: inspección + etiquetado + correcciones del usuario. */
    LEARNED,
}

/**
 * El esquema completo de un formulario. Se identifica por [fingerprint], que es la misma huella
 * que ya usa `TemplateFingerprint` (nº de páginas + nombres de campo normalizados): así, subir
 * dos veces el mismo PDF reutiliza el esquema ya etiquetado en vez de volver a preguntar.
 */
@Serializable
data class FormSchema(
    val id: String,
    val title: String,
    val source: SchemaSource,
    val fingerprint: String,
    val pageCount: Int,
    val sections: List<FormSection> = emptyList(),

    /**
     * Versión del formato. La migración de datos ya persistidos va en la tanda 3; se declara
     * aquí para que el primer dato que se guarde ya lleve versión y no haya que adivinarlo
     * después (la lección de la 0.8.0 con el índice de paso).
     */
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    fun allFields(): List<FormField> = sections.flatMap { it.allFields() }

    /** Campos enganchados a una clave canónica: los que se comparten en el expediente. */
    fun canonicalFields(): Map<String, List<FormField>> =
        allFields().filter { it.canonical != null }.groupBy { it.canonical!! }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Esquema de serie: contrato de distribución de Orange
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Esquemas que vienen con la app.
 *
 * El contrato de distribución de Orange/MASORANGE deja de ser «el formulario de la app» y pasa
 * a ser **un esquema más**. No se ofrece ya en la interfaz (v0.9.6), pero si alguien lo sube
 * como PDF propio se reconoce por huella y se rellena exactamente igual que siempre.
 *
 * Se deriva de `ContractFields.CANON` en vez de reescribirlo a mano, a propósito: esos nombres
 * de campo son frágiles (dobles espacios, sufijos `_2`, la casilla literalmente llamada
 * `undefined`) y están verificados contra el AcroForm real. Duplicarlos sería pedir que se
 * desincronicen.
 */
object BuiltinSchemas {

    const val ORANGE_DISTRIBUTION_ID = "builtin:orange-distribucion-pdv"

    /** Clave canónica transversal para cada campo de `CANON`, cuando la comparte. */
    private val CANON_TO_CANONICAL: Map<String, String> = mapOf(
        "Nombre  Razón Social" to CanonicalKeys.RAZON_SOCIAL,
        "Nombre Comercial" to CanonicalKeys.NOMBRE_COMERCIAL,
        "NIE" to CanonicalKeys.IDENTIFICACION,
        "Nombre representante" to CanonicalKeys.REPRESENTANTE_NOMBRE,
        "NIF representante" to CanonicalKeys.REPRESENTANTE_NIF,
        "Dirección" to CanonicalKeys.DIRECCION,
        "CP" to CanonicalKeys.CP,
        "Población" to CanonicalKeys.POBLACION,
        "Provincia" to CanonicalKeys.PROVINCIA,
        "Dirección_2" to CanonicalKeys.DIRECCION_2,
        "CP_2" to CanonicalKeys.CP_2,
        "Población_2" to CanonicalKeys.POBLACION_2,
        "Provincia_2" to CanonicalKeys.PROVINCIA_2,
        "Teléfono" to CanonicalKeys.TELEFONO,
        "Email Comercial" to CanonicalKeys.EMAIL_COMERCIAL,
        "Email  Facturación" to CanonicalKeys.EMAIL_FACTURACION,
        "Datos bancarios del DISTRIBUIDOR" to CanonicalKeys.IBAN,
        "Actividad principal del negocio" to CanonicalKeys.ACTIVIDAD,
        "Fecha" to CanonicalKeys.FECHA_DIA,
        "de" to CanonicalKeys.FECHA_MES,
        "año" to CanonicalKeys.FECHA_ANIO,
    )

    /**
     * Clave canónica transversal para una clave de `CANON`, o null si ese campo no se comparte
     * con otros formularios. Lo usa `SchemaMigration` al derivar esquemas de mapeos antiguos.
     */
    fun canonicalFor(canonKey: String): String? = CANON_TO_CANONICAL[canonKey]

    /**
     * Construye el esquema del contrato de Orange a partir de `CANON`.
     *
     * @param fingerprint huella real del PDF; se calcula al cargarlo, no se puede fijar aquí.
     */
    fun orangeDistribution(fingerprint: String = ""): FormSchema {
        val datos = ContractFields.CANON.mapIndexed { i, canon ->
            FormField(
                name = canon.key,
                label = canon.label,
                kind = FieldKind.TEXT,
                origin = when (canon.key) {
                    in ContractFields.DATE_KEYS -> ValueOrigin.FIRMA
                    else -> ValueOrigin.DOCUMENTO
                },
                canonical = CANON_TO_CANONICAL[canon.key],
                order = i,
            )
        }

        // El responsable comercial es constante del distribuidor, no dato del cliente: es el
        // caso que dio origen a ValueOrigin.AJUSTES.
        val responsable = FormField(
            name = ContractFields.RESPONSABLE_KEY,
            label = "Responsable comercial",
            kind = FieldKind.TEXT,
            origin = ValueOrigin.AJUSTES,
            order = datos.size,
        )

        // Las tres casillas de tipo de identificación. `onState` queda a null a propósito: el
        // estado real se resuelve contra el documento al rellenar (v0.9.7), no se declara aquí.
        // La tercera se llama literalmente "undefined" en el AcroForm original — no es un
        // error de transcripción, es el nombre real y hay que usarlo tal cual.
        val casillas = listOf(
            ContractFields.CHECKBOX_CIF to "CIF",
            ContractFields.CHECKBOX_NIF to "NIF",
            ContractFields.CHECKBOX_NIE to "NIE",
        ).mapIndexed { i, (name, label) ->
            FormField(
                name = name,
                label = "Tipo de identificación · $label",
                kind = FieldKind.CHECKBOX,
                origin = ValueOrigin.DOCUMENTO,
                canonical = CanonicalKeys.TIPO_IDENTIFICACION,
                order = datos.size + 1 + i,
            )
        }

        return FormSchema(
            id = ORANGE_DISTRIBUTION_ID,
            title = "Contrato de distribución PdV (Orange/MASORANGE)",
            source = SchemaSource.BUILTIN,
            fingerprint = fingerprint,
            pageCount = 54,
            sections = listOf(
                FormSection(
                    id = "datos",
                    title = "Datos del distribuidor",
                    kind = SectionKind.SIMPLE,
                    fields = datos + responsable + casillas,
                )
            ),
        )
    }
}
