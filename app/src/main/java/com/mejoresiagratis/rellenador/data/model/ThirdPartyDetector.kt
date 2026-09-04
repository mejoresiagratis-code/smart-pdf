package com.mejoresiagratis.rellenador.data.model

import java.text.Normalizer

/**
 * Tanda 5·4j — marca [FormField.thirdParty] en los campos que pertenecen a **otra persona o
 * empresa**, no al titular del contrato.
 *
 * ### Por qué hace falta
 *
 * `FormField.thirdParty` existía desde la 5·4b y `AffinityGroup` lo respeta desde la 5·4i, pero
 * **nadie lo ponía nunca a `true`**: `FormSchemaBuilder` no lo deduce y el etiquetado por visión
 * tampoco. El resultado, visto en el QA del contrato de Aire con datos reales, es que el dato del
 * cliente acabó impreso en huecos que son de un tercero:
 *
 * - Página 2, «CAPTURA DE FIBRA CON CAMBIO DE TITULARIDAD»: el CIF, el domicilio, la localidad y
 *   la provincia del **titular donante** salieron con los datos de MOFIZOL.
 * - Página 3, «CAMBIO TITULAR» (datos del titular de la línea a portar, «en caso de ser diferente
 *   del cliente»): razón social, CIF, domicilio, CP, localidad y provincia, todos con los datos
 *   del cliente.
 *
 * Ninguno de los dos da error: sale impreso y nadie lo ve, que es exactamente el fallo contra el
 * que avisaba el análisis de Aire. Un contrato así declara que el donante de la línea es el propio
 * cliente, que es justo lo contrario de lo que el bloque quiere decir.
 *
 * ### Cómo se decide
 *
 * Por el **título de la sección**, no por el rótulo del campo: dentro del bloque de cambio de
 * titular los rótulos son idénticos a los del titular («Domicilio», «CP», «Localidad»), y es la
 * cabecera del bloque la única que dice de quién son. Se marca la sección entera.
 *
 * Es deliberadamente conservador: marcar de más deja un campo sin autorrellenar (molesto, visible,
 * se teclea a mano), marcar de menos mete el dato de otro en el contrato (invisible). Ante la
 * duda, marcar.
 */
object ThirdPartyDetector {

    /**
     * Fragmentos que, en el título de una sección, indican que sus campos son de un tercero.
     * Se comparan ya normalizados (minúsculas, sin acentos).
     */
    private val THIRD_PARTY_HINTS = listOf(
        "titular donante",
        "operador donante",
        "cambio titular",
        "cambio de titular",
        "cambio de titularidad",
        "titular de la linea",
        "titular linea",
        "firma titular",
        "datos titular",
    )

    /** Marca [FormField.thirdParty] en todas las secciones cuyo título delata a un tercero. */
    fun mark(schema: FormSchema): FormSchema = schema.copy(
        sections = schema.sections.map { section ->
            if (!isThirdParty(section.title)) section
            else section.copy(
                fields = section.fields.map { it.copy(thirdParty = true) },
                rows = section.rows.map { row ->
                    row.copy(cells = row.cells.mapValues { (_, f) -> f.copy(thirdParty = true) })
                },
                blocks = section.blocks.map { block -> block.map { it.copy(thirdParty = true) } },
            )
        }
    )

    /** `true` si el título indica que la sección recoge datos de alguien que no es el titular. */
    fun isThirdParty(sectionTitle: String): Boolean {
        val t = normalize(sectionTitle)
        if (t.isBlank()) return false
        return THIRD_PARTY_HINTS.any { hint -> t.contains(hint) }
    }

    private fun normalize(text: String): String {
        val withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}
