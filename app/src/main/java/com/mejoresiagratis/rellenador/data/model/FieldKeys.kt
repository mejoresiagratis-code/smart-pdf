package com.mejoresiagratis.rellenador.data.model

/**
 * Traduce entre las **claves de `CANON`** (los nombres del AcroForm de Orange, que es el
 * vocabulario en el que está escrito casi todo el asistente) y los **nombres reales de campo del
 * PDF que se está rellenando ahora mismo**.
 *
 * Fase 5, tanda 5·3 — «la clave». A partir de esta tanda, la clave de `fieldValues`,
 * `fieldStates`, `fieldOrigins`, `fieldCandidates` y `UndoEntry` es **siempre el nombre real del
 * campo**, que es lo único que se puede escribir en el PDF. Antes era una mezcla, y de ahí venía
 * el fallo que esta tanda arregla (ver más abajo).
 *
 * ### Por qué hacía falta
 *
 * Desde la 0.9.3 el prompt de extracción lleva los campos que el PDF tiene **de verdad**
 * (`WizardViewModel` manda `userFieldNames`), y le pide a la IA «usa EXACTAMENTE esos nombres de
 * campo como claves». O sea que con un PDF propio **la IA ya devolvía nombres reales**, y
 * `fieldValues` ya estaba indexado por nombre real… salvo en cinco sitios que seguían inyectando
 * claves de Orange a la fuerza: el responsable comercial, las tres claves de fecha y la copia de
 * la dirección fiscal.
 *
 * El resultado era una mezcla incoherente, y con ella un fallo visible: `FillStep` pintaba las
 * secciones con claves de `CANON` y buscaba `fieldValues["Nombre  Razón Social"]`, pero el dato
 * estaba guardado bajo el nombre real del PDF del usuario. **Con un PDF propio, el paso de Relleno
 * mostraba vacíos los campos que la IA sí había extraído** — sólo se veían las fechas y el
 * responsable, que eran precisamente los contaminados. El PDF final salía bien por casualidad:
 * `AcroFormFiller.realName()` cae a `?: canonical` y acertaba.
 *
 * ### Cómo se resuelve
 *
 * La fuente de la traducción es hoy `fieldMapping` (`canónica de CANON -> nombre real`), que ya
 * existía para el editor de mapeo. En la tanda 5·4 esa fuente pasará a ser el `FormSchema` del PDF
 * y esta clase será el único sitio que haya que cambiar.
 *
 * **Con el contrato de Orange el mapeo está vacío y todo esto es la identidad**, así que el
 * comportamiento en Orange no cambia en absoluto — que es como se verifica la tanda.
 */
class FieldKeys(
    /** `canónica de CANON -> nombre real`. Vacío para el contrato de Orange. */
    private val mapping: Map<String, String> = emptyMap(),
) {

    /** Índice inverso `nombre real -> canónica de CANON`, construido una vez. */
    private val reverse: Map<String, String> =
        mapping.entries
            .filter { it.value.isNotBlank() }
            .associate { (canon, real) -> real to canon }

    /**
     * Nombre real del campo que corresponde a una clave de `CANON`.
     *
     * Si el mapeo no la cubre devuelve la clave tal cual: es lo correcto para Orange (donde la
     * clave YA es el nombre real) y es también el comportamiento que tenía
     * `AcroFormFiller.realName()`, así que no cambia nada por ese lado.
     */
    fun real(canonKey: String): String = mapping[canonKey] ?: canonKey

    /**
     * Igual que [real], pero devuelve null si el campo **no existe** en el PDF actual.
     *
     * La diferencia importa para los valores que la app inyecta por su cuenta y que no salen de
     * ningún documento: el responsable comercial y la fecha de la firma. Si el formulario no tiene
     * esos campos, escribirlos con la clave de Orange los mandaba derechos a `missingFields`
     * (el mismo fallo que la 5·0 arregló para el responsable, que seguía vivo para las fechas).
     * Con esto, un formulario sin campo de fecha simplemente no recibe fecha.
     *
     * @param knownFields nombres reales de los campos del PDF actual (`userFieldNames`). Vacío
     *   significa «no lo sé» — contrato de Orange o PDF sin inspeccionar — y entonces no se
     *   descarta nada, para no perder valores por falta de información.
     */
    fun realIfPresent(canonKey: String, knownFields: Collection<String>): String? {
        val name = real(canonKey)
        if (knownFields.isEmpty()) return name
        return name.takeIf { it in knownFields }
    }

    /** Clave de `CANON` que corresponde a un nombre real, o null si ese campo no está mapeado. */
    fun canonKeyOf(realName: String): String? =
        reverse[realName] ?: realName.takeIf { name -> CANON_KEYS.contains(name) }

    /**
     * Clave canónica **transversal** ([CanonicalKeys]) de un campo, por su nombre real.
     *
     * Es la que gobierna validación, normalización y teclado desde las tandas 5·2 y 5·2b. Esas
     * piezas la resolvían con `BuiltinSchemas.canonicalFor(nombre)`, que sólo conoce los nombres
     * de Orange; con un nombre real de Aire no casaba y volvían a quedarse mudas. Ahora se resuelve
     * en dos saltos: nombre real -> clave de `CANON` -> canónica transversal.
     */
    fun canonicalOf(realName: String): String? =
        canonKeyOf(realName)?.let { BuiltinSchemas.canonicalFor(it) }

    /** Etiqueta legible de un campo por su nombre real; el propio nombre si no se conoce. */
    fun labelOf(realName: String): String =
        canonKeyOf(realName)?.let { ContractFields.labelFor(it) } ?: realName

    /**
     * Reindexa un mapa que esté en claves de `CANON` para que lo esté en nombres reales.
     *
     * Lo usan las migraciones: una sesión o un perfil guardados antes de esta tanda pueden traer
     * las claves de Orange contaminantes mezcladas con nombres reales. Reindexar es idempotente
     * sobre las que ya son reales, porque [real] deja intacta cualquier clave no mapeada.
     */
    fun <T> reindex(byCanonKey: Map<String, T>): Map<String, T> =
        byCanonKey.entries.associate { (k, v) -> real(k) to v }

    companion object {
        private val CANON_KEYS: Set<String> = ContractFields.CANON.map { it.key }.toSet() +
            setOf(
                ContractFields.RESPONSABLE_KEY,
                ContractFields.CHECKBOX_CIF,
                ContractFields.CHECKBOX_NIF,
                ContractFields.CHECKBOX_NIE,
            )

        /** Sin mapeo: todo es identidad. Es el caso del contrato de Orange. */
        val IDENTITY = FieldKeys()
    }
}
