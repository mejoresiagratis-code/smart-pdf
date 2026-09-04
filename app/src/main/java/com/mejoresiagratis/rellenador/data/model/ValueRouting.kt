package com.mejoresiagratis.rellenador.data.model

/**
 * Reparto de los valores del asistente entre los **dos mapas** que espera
 * `AcroFormFiller.generate()`.
 *
 * Tanda 5·4d. Existe porque el relleno de un botón del AcroForm no es el de un campo de texto:
 * `values` se aplica con `field.setValue(String)` y `checkboxes` con `applyButtonValue()`, que es
 * el único que sabe de `check()`/`unCheck()` y de **estados de activación reales**. Hasta la
 * 5·4c, todo lo que el usuario escribía en la pantalla de relleno se guardaba con
 * `setFieldValue()` y viajaba por el mapa de texto; en cuanto la interfaz pinte una casilla como
 * casilla, ese valor iría por el camino equivocado.
 *
 * Con Aire eso no es teórico: sus estados son `/Sí`, `/0`..`/5`, `/Opción1` — no hay convención
 * (ver v0.9.7). Un `setValue("On")` sobre un radio de Aire no falla: escribe un estado que no
 * existe y **no se ve hasta abrir el PDF generado**. De ahí que el reparto se haga aquí, contra
 * el `onState` que el esquema ya leyó del `/AP /N` del propio PDF, y no en la interfaz.
 *
 * Es una función pura sobre `FormSchema` a propósito: no toca Android ni pdfbox, así que se
 * typecheckea en local y sus comprobaciones son ejecutables (regla de `CONTINUIDAD.md` §6).
 */
data class RoutedValues(
    /** Va a `AcroFormFiller.generate(values = …)`. */
    val text: Map<String, String>,
    /** Va a `AcroFormFiller.generate(checkboxes = …)`. */
    val buttons: Map<String, String>,
    /**
     * Campos `/Sig` que se descartan: la app estampa una imagen de firma, no firma
     * criptográficamente, así que escribir texto dentro de un hueco de firma nunca es correcto.
     * Se devuelven en vez de tragárselos para que quien llame pueda registrarlo.
     */
    val skippedSignatures: List<String>,
)

/**
 * Reparte [values] (clave = **nombre real** del campo, tal como lo fijó la 5·3) según el
 * [FieldKind] que declare [schema].
 *
 * Sin esquema devuelve todo por el mapa de texto, que es exactamente el comportamiento anterior
 * a esta tanda: un PDF sin esquema activo no puede clasificar nada y no se debe adivinar.
 *
 * Una clave que no esté en el esquema también sale por texto. Eso mantiene intactos los caminos
 * que inyectan claves fijas (el responsable comercial de `AcroFormFiller`, las casillas de
 * cabecera de `altaCheckboxes()`) y el contrato de Orange, cuyo esquema es `BUILTIN` y declara
 * todos sus campos como `TEXT`.
 */
fun routeFieldValues(values: Map<String, String>, schema: FormSchema?): RoutedValues {
    if (schema == null) return RoutedValues(values, emptyMap(), emptyList())

    // Un grupo de radio son varias entradas con el MISMO `name` (es un solo campo del AcroForm)
    // que se distinguen por `onState`, así que la unidad de decisión es el nombre, no el campo.
    val byName = schema.allFields().groupBy { it.name }

    val text = LinkedHashMap<String, String>()
    val buttons = LinkedHashMap<String, String>()
    val skipped = mutableListOf<String>()

    for ((name, value) in values) {
        val group = byName[name]
        if (group.isNullOrEmpty()) {
            text[name] = value
            continue
        }
        val kinds = group.map { it.kind }.toSet()
        when {
            // Basta una entrada `/Sig` para descartar el nombre: un campo de firma no es
            // rellenable por ninguna de las dos vías.
            FieldKind.SIGNATURE in kinds -> skipped += name
            kinds == setOf(FieldKind.TEXT) -> text[name] = value
            else -> buttons[name] = buttonStateFor(group, value)
        }
    }
    return RoutedValues(text, buttons, skipped)
}

/**
 * Tanda 5·4k — deja pasar sólo las entradas de un mapa de botones **fijo** cuyo destino sea de
 * verdad un botón según [schema].
 *
 * Existe por un fallo que se vio impreso en el PDF del QA de Aire: «NIF: Off» en el hueco del
 * NIF del representante. La cadena era ésta y no daba ningún error en ningún punto:
 *
 *  1. `ContractFields.CHECKBOX_NIF` vale literalmente `"NIF"` — es el nombre de la casilla de
 *     tipo de identificación del contrato de **Orange**.
 *  2. El contrato de empresas de **Aire** tiene un campo de TEXTO llamado, también
 *     literalmente, `NIF` (el del representante, página 1). Comprobado con `pypdf` sobre
 *     `Contrato_empresas.pdf`: es el único nombre que colisiona de los tres.
 *  3. Con un cliente con CIF, `ContractFields.checkboxStateFor("CIF")` devuelve
 *     `{"CIF": "On", "NIF": "Off", "undefined": "Off"}`, y `FieldKeys.reindex` deja `NIF`
 *     intacto porque no hay canónica que traducir.
 *  4. Ese mapa se suma a `checkboxes` DESPUÉS de [routeFieldValues], así que se salta el
 *     reparto por `FieldKind` entero.
 *  5. `AcroFormFiller.applyButtonValue` cae en su rama `else -> field.setValue(requested)`
 *     porque el campo no es `PDCheckBox` ni `PDButton`, y escribe la cadena `Off` dentro de un
 *     campo de texto.
 *
 * Un nombre que el esquema **no** conozca se deja pasar: es el comportamiento de siempre y
 * cubre a Orange, cuyo esquema `BUILTIN` sí declara las tres como [FieldKind.CHECKBOX] y por
 * tanto pasa igual. Sólo se descarta lo que el esquema afirma que es texto o firma.
 */
fun onlyButtons(fixed: Map<String, String>, schema: FormSchema?): Map<String, String> {
    if (schema == null || fixed.isEmpty()) return fixed
    val byName = schema.allFields().groupBy { it.name }
    return fixed.filter { (name, _) ->
        val group = byName[name] ?: return@filter true
        val kinds = group.map { it.kind }.toSet()
        FieldKind.SIGNATURE !in kinds && kinds != setOf(FieldKind.TEXT)
    }
}

/**
 * Traduce lo que guardó la interfaz al **estado real** del PDF para ese botón.
 *
 * El orden importa: primero se busca una coincidencia con un `onState` de verdad, porque es el
 * único dato que salió del PDF; luego con la etiqueta visible de la opción, que es lo que la
 * interfaz tiene a mano para un selector; y sólo si el nombre tiene una única entrada (casilla
 * suelta) se interpreta «marcada» y se usa su estado.
 *
 * No se normaliza nada más allá de quitar la `/` inicial y los espacios de los extremos: los
 * estados de Aire incluyen `0`, así que tratar `"0"` como «apagado» sería justo el error que
 * esta función existe para evitar. Apagado es cadena vacía o literalmente `Off`.
 */
internal fun buttonStateFor(group: List<FormField>, stored: String): String {
    val bare = stored.trim().removePrefix("/")
    if (bare.isEmpty() || bare.equals(ContractFields.CHECKBOX_OFF, ignoreCase = true)) {
        return ContractFields.CHECKBOX_OFF
    }
    group.firstOrNull { it.onState != null && it.onState.equals(bare, ignoreCase = true) }
        ?.let { return it.onState!! }
    group.firstOrNull { it.optionLabel != null && it.optionLabel.equals(bare, ignoreCase = true) }
        ?.let { return it.onState ?: ContractFields.CHECKBOX_ON }
    if (group.size == 1) return group[0].onState ?: ContractFields.CHECKBOX_ON
    // Grupo con varias opciones y un valor que no reconoce ninguna: se pasa tal cual y decide
    // `applyButtonValue()`, que compara contra los `onValues` del campo vivo. Preferible a
    // inventar un estado, y queda visible en el PDF generado.
    return bare
}
