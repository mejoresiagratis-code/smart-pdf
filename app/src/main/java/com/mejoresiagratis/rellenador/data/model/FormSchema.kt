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
 * Rectángulo de un widget dentro de su página, con **origen arriba-izquierda y en puntos** —
 * exactamente la convención de `PdfFieldInspector.Field`, para no tener que convertir nada al
 * copiarlo.
 *
 * Existe para poder **recortar la región de página** que rodea a un campo. Es lo que necesita
 * `FieldLabeler` para preguntar a la visión «¿cómo se llama esto?» sin mandarle la página
 * completa, y lo que permite señalar en la previsualización dónde cae un campo. Hasta ahora el
 * esquema guardaba `page` pero perdía la posición dentro de ella, así que una vez construido el
 * esquema el recorte era imposible: había que volver a inspeccionar el PDF.
 *
 * Opcional a propósito, y por eso **no** se toca `FormSchema.SCHEMA_VERSION`: los esquemas
 * `BUILTIN` derivados de `CANON` no tienen geometría, y los ya persistidos por la 0.9.9 tampoco.
 * El `Json` de `AppModule` va con `ignoreUnknownKeys = true` y `explicitNulls = false`, así que
 * el campo es compatible en los dos sentidos — un esquema nuevo se lee con código viejo y al
 * revés — y no hay nada que migrar.
 */
@Serializable
data class FieldRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

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
     * Posición dentro de [page], si se conoce (ver [FieldRect]). Nulo en los esquemas `BUILTIN`,
     * que se derivan de una lista de nombres y no de una inspección del PDF.
     */
    val rect: FieldRect? = null,

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

    /**
     * Página y rectángulo **representativos** de la columna, para poder recortarla: no es la
     * unión de todas sus celdas, sino la celda más alta (ver `FormSchemaBuilder.tableSection`).
     * La cabecera de una tabla está justo encima de su primera fila, así que ése es el ancla
     * desde el que buscar el rótulo hacia arriba; la unión de 25 filas sería media página.
     */
    val page: Int = 0,
    val rect: FieldRect? = null,
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
     * Inverso de `canonicalFor`: qué nombre real de `CANON` tiene una clave canónica dada,
     * o null si ninguno la usa. Tanda 5·2 — lo necesita `FillStep` para resolver "el campo
     * Provincia/Fecha que corresponde a este" sin asumir la convención de nombre `_2` de
     * Orange (docs/PLAN_FASE_5.md, hallazgo 2.6). Con más de un campo compartiendo canónica
     * (no ocurre hoy en `CANON`) devolvería cualquiera de ellos; no es el caso de uso.
     */
    fun realKeyFor(canonical: String): String? =
        CANON_TO_CANONICAL.entries.firstOrNull { it.value == canonical }?.key

    /**
     * Canónica de la Provincia que corresponde a la canónica de un CP dado (mismo bloque:
     * fiscal↔fiscal, comercio↔comercio). Sólo tiene sentido para CP/Provincia, por eso vive
     * aquí y no en un mapa genérico "canónica hermana".
     */
    private val CP_A_PROVINCIA_CANONICAL: Map<String, String> = mapOf(
        CanonicalKeys.CP to CanonicalKeys.PROVINCIA,
        CanonicalKeys.CP_2 to CanonicalKeys.PROVINCIA_2,
    )

    /**
     * Nombre real del campo Provincia que acompaña a un CP dado, para la coherencia
     * CP↔provincia de `FieldValidator`. Antes `FillStep` lo adivinaba con
     * `if (key.endsWith("_2")) "Provincia_2" else "Provincia"` — una convención de nombre de
     * Orange que no significa nada para otro AcroForm. Null si `cpFieldName` no tiene
     * canónica de CP o si no hay campo con la canónica de Provincia correspondiente;
     * el llamador decide el respaldo.
     */
    fun provinciaKeyFor(cpFieldName: String): String? {
        val cpCanonical = canonicalFor(cpFieldName) ?: return null
        val provinciaCanonical = CP_A_PROVINCIA_CANONICAL[cpCanonical] ?: return null
        return realKeyFor(provinciaCanonical)
    }

    /**
     * Pares canónicos del bloque fiscal y su equivalente en el bloque de comercio/PdV.
     * Tanda 5·2b — lo usa `copyFiscalToComercio`, que antes llevaba dentro los literales
     * `"Dirección"`/`"CP"`/`"Población"`/`"Provincia"` y construía el destino concatenando
     * `"_2"`: una convención de nombre de Orange que no significa nada en otro AcroForm.
     */
    private val FISCAL_A_COMERCIO_CANONICAL: Map<String, String> = mapOf(
        CanonicalKeys.DIRECCION to CanonicalKeys.DIRECCION_2,
        CanonicalKeys.CP to CanonicalKeys.CP_2,
        CanonicalKeys.POBLACION to CanonicalKeys.POBLACION_2,
        CanonicalKeys.PROVINCIA to CanonicalKeys.PROVINCIA_2,
    )

    /**
     * Pares `(nombre real fiscal, nombre real comercio)` de dirección, resueltos por canónica.
     * Sólo incluye los pares que existan de verdad a los dos lados; si a un formulario le falta
     * el bloque de comercio, ese par simplemente no sale y la copia no lo intenta.
     */
    fun fiscalToComercioKeyPairs(): List<Pair<String, String>> =
        FISCAL_A_COMERCIO_CANONICAL.mapNotNull { (fiscal, comercio) ->
            val from = realKeyFor(fiscal) ?: return@mapNotNull null
            val to = realKeyFor(comercio) ?: return@mapNotNull null
            from to to
        }

    /**
     * Nombres de campo de `CANON` que identifican inequívocamente el contrato de Orange: son
     * literales muy específicos (dobles espacios incluidos) y su coincidencia sobre el conjunto
     * completo del AcroForm de un PDF cualquiera es prácticamente nula. El reconocimiento por
     * huella exigiría una tabla de huellas de referencia; comparar contra nombres de campo es
     * determinista y no arrastra datos que puedan quedar viejos.
     *
     * Tanda 5·4 — se usa desde `WizardViewModel.chooseUserContract` para decidir si el PDF
     * subido es el propio contrato de Orange (caso legado: sigue rellenándose exactamente igual
     * que siempre, con las 6 secciones de abajo) o un formulario ajeno para el que hay que
     * construir un esquema `LEARNED`.
     */
    private val ORANGE_SIGNATURE_NAMES: Set<String> = setOf(
        "Nombre  Razón Social",
        "Datos bancarios del DISTRIBUIDOR",
        "Email  Facturación",
        ContractFields.CHECKBOX_NIE,        // el literal "undefined" del AcroForm original
    )

    /**
     * Nombres de campo que identifican el `Contrato_empresas.pdf` de Aire (contrato principal,
     * 481 campos). Tanda 5·4 — verificado sobre el PDF real con `pypdf`: la cabecera del bloque
     * CLIENTE tiene tres casillas cuyos nombres son `Casilla de verificación 56/57/58`, y las
     * casillas 56/57/58 vienen del PDF **marcadas de fábrica** con `/V = /Sí` en los tres. El
     * alta con sólo el contrato marca `ALTA NUEVA` y **desmarca** las otras dos (`MODIFICACIÓN`,
     * `PORTABILIDAD`), en vez de dejarlas como están: es la corrección al §6.3 del
     * `docs/PLAN_FASE_5.md`, cuya redacción hablaba sólo de marcar `ALTA NUEVA` sin decir nada
     * de desmarcar las otras dos.
     *
     * El literal `airetech.es` aparece en el texto fijo de las páginas 1 y 3 pero **no** como
     * nombre de campo del AcroForm, así que no sirve para identificar. Estas cuatro casillas y
     * los campos `TF cuotalta TOTAL` / `CV cuotalta TOTAL` — que son de tablas y `CATALOGO`,
     * pero de nombre único en el AcroForm de Aire — sí sirven.
     */
    const val AIRE_CONTRATO_EMPRESAS_ID = "builtin:aire-contrato-empresas"
    private val AIRE_CONTRATO_SIGNATURE_NAMES: Set<String> = setOf(
        "Casilla de verificación 56",   // ALTA NUEVA
        "Casilla de verificación 57",   // MODIFICACIÓN
        "Casilla de verificación 58",   // PORTABILIDAD
        "TF cuotalta TOTAL",
    )

    /**
     * Identifica el esquema `BUILTIN` que corresponde al conjunto de nombres de campo dado, o
     * null si no es ninguno conocido. La comprobación es por **intersección**: el PDF tiene que
     * declarar todos los nombres firma del contrato; que declare **más** campos no descarta
     * (Orange trae 22, Aire 481). El orden y las mayúsculas se comparan tal cual, porque los
     * nombres de AcroForm no se normalizan nunca en este proyecto.
     */
    fun recognize(fieldNames: Collection<String>): String? {
        val set = fieldNames.toSet()
        return when {
            set.containsAll(ORANGE_SIGNATURE_NAMES) -> ORANGE_DISTRIBUTION_ID
            set.containsAll(AIRE_CONTRATO_SIGNATURE_NAMES) -> AIRE_CONTRATO_EMPRESAS_ID
            else -> null
        }
    }

    /**
     * Construye el esquema del contrato de Orange a partir de `CANON`.
     *
     * Tanda 5·4 — deja de ser una única sección plana y estrena las **6 secciones** que
     * `FillStep` pintaba desde la 5·1 vía `canonFillSections()`. Esas 6 secciones se conservan
     * literales aquí (mismo orden, mismos títulos, mismas claves) para que la regla de la
     * fase 5 se cumpla al pie de la letra: en Orange, la app se dibuja exactamente igual que
     * antes de esta tanda, porque el esquema **es** lo que se pintaba a mano. Verificado en
     * `docs/roadmap-multiformulario.html` («mismo orden, mismas validaciones, mismo
     * autorrelleno, misma firma. Cualquier diferencia es un fallo, no una mejora»).
     *
     * La fecha, el responsable y las casillas de tipo van cada uno en su sección para que
     * salgan **al final** del formulario, después del bloque de datos — que es como los pintaba
     * `FillStep` antes de la 5·4 (`fechaKeys` fuera del bucle de secciones, tipo de
     * identificación como cabecera). El paso de Relleno tiene que aprender a colocarlos en esos
     * sitios sea cual sea el esquema; con Orange como testigo, colocarlos aquí en secciones
     * explícitas hace que el cambio de fuente sea invisible.
     *
     * @param fingerprint huella real del PDF; se calcula al cargarlo, no se puede fijar aquí.
     */
    fun orangeDistribution(fingerprint: String = ""): FormSchema {

        fun field(name: String, order: Int): FormField = FormField(
            name = name,
            label = ContractFields.labelFor(name),
            kind = FieldKind.TEXT,
            origin = when (name) {
                in ContractFields.DATE_KEYS -> ValueOrigin.FIRMA
                else -> ValueOrigin.DOCUMENTO
            },
            canonical = CANON_TO_CANONICAL[name],
            order = order,
        )

        val secciones = listOf(
            "Empresa / Identificación" to listOf(
                "Nombre  Razón Social", "Nombre Comercial", "NIE",
                "Nombre representante", "NIF representante",
                "Actividad principal del negocio",
            ),
            "Dirección fiscal" to listOf(
                "Dirección", "CP", "Población", "Provincia",
            ),
            "Dirección comercio / PdV" to listOf(
                "Dirección_2", "CP_2", "Población_2", "Provincia_2",
            ),
            "Contacto" to listOf(
                "Teléfono", "Email Comercial", "Email  Facturación",
            ),
            "Datos bancarios" to listOf(
                "Datos bancarios del DISTRIBUIDOR",
            ),
        )

        var order = 0
        val simples = secciones.mapIndexed { i, (title, names) ->
            FormSection(
                id = "orange-$i",
                title = title,
                kind = SectionKind.SIMPLE,
                fields = names.map { field(it, order++) },
            )
        }

        val fecha = FormSection(
            id = "orange-fecha",
            title = "Fecha del contrato",
            kind = SectionKind.SIMPLE,
            fields = ContractFields.DATE_KEYS.map { field(it, order++) },
        )

        // El responsable comercial es constante del distribuidor, no dato del cliente: es el
        // caso que dio origen a ValueOrigin.AJUSTES.
        val responsable = FormSection(
            id = "orange-responsable",
            title = "Responsable comercial",
            kind = SectionKind.SIMPLE,
            fields = listOf(
                FormField(
                    name = ContractFields.RESPONSABLE_KEY,
                    label = "Responsable comercial",
                    kind = FieldKind.TEXT,
                    origin = ValueOrigin.AJUSTES,
                    order = order++,
                )
            ),
        )

        // Las tres casillas de tipo de identificación. `onState` queda a null a propósito: el
        // estado real se resuelve contra el documento al rellenar (v0.9.7), no se declara aquí.
        // La tercera se llama literalmente "undefined" en el AcroForm original — no es un
        // error de transcripción, es el nombre real y hay que usarlo tal cual.
        val casillasTipo = FormSection(
            id = "orange-tipo-id",
            title = "Tipo de identificación",
            kind = SectionKind.SIMPLE,
            fields = listOf(
                ContractFields.CHECKBOX_CIF to "CIF",
                ContractFields.CHECKBOX_NIF to "NIF",
                ContractFields.CHECKBOX_NIE to "NIE",
            ).map { (name, label) ->
                FormField(
                    name = name,
                    label = "Tipo de identificación · $label",
                    kind = FieldKind.CHECKBOX,
                    origin = ValueOrigin.DOCUMENTO,
                    canonical = CanonicalKeys.TIPO_IDENTIFICACION,
                    order = order++,
                )
            },
        )

        return FormSchema(
            id = ORANGE_DISTRIBUTION_ID,
            title = "Contrato de distribución PdV (Orange/MASORANGE)",
            source = SchemaSource.BUILTIN,
            fingerprint = fingerprint,
            pageCount = 54,
            sections = simples + fecha + responsable + casillasTipo,
        )
    }
}
