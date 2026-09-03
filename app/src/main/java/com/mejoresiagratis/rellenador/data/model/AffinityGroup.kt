package com.mejoresiagratis.rellenador.data.model

import java.text.Normalizer

/**
 * Tanda 5·4i — detecta, dado un campo que ya tiene valor, otros huecos **candidatos** a llevar
 * el mismo dato y que siguen vacíos. Es lo que alimenta la lista con casilla de Relleno («este
 * dato aparece en otros N campos, ¿cuáles comparten valor?»).
 *
 * Deliberadamente solo **propone** — nunca decide ni escribe nada. Marcar una casilla es lo que
 * de verdad engancha el campo (vía `SchemaEditing.setCanonical`, que a partir de esta tanda
 * permite que dos campos compartan canónica) o copia el valor una vez; los que el usuario no
 * marque se quedan vacíos para teclearlos a mano.
 *
 * ### Por qué NO por similitud de nombre ni de `name`
 *
 * El propio contrato de Orange tiene pares con el mismo `name` base y significado DISTINTO:
 * `Dirección`/`Dirección_2` son la fiscal y la de instalación; `CP`/`CP_2` igual. Si se
 * detectaran afines por parecido de `name` o de etiqueta APROXIMADO, estos saldrían como
 * candidatos y serían casi siempre un error del usuario esperando a pasar. La única señal fiable
 * es la etiqueta IMPRESA (lo único que el usuario ve del PDF): si dos huecos la tienen
 * **idéntica** tras normalizar, sí son candidatos razonables — y aun así se ofrecen, no se
 * aplican.
 *
 * ### Por qué se respeta `thirdParty`
 *
 * Un campo del titular donante (portabilidad) o de un tercero no debe salir como afín de un
 * campo del titular principal aunque el rótulo coincida ("Nombre y apellidos" aparece en ambos
 * bloques) — es precisamente el caso que el análisis de Aire señaló como peligroso.
 */
object AffinityGroup {

    /**
     * Campos [FieldKind.TEXT] de [schema], distintos de [filled], vacíos según [emptyFieldNames],
     * y candidatos a compartir el valor de [filled]: misma canónica ya asignada (si la tiene) o
     * misma etiqueta impresa tras normalizar.
     */
    fun candidatesFor(
        schema: FormSchema,
        filled: FormField,
        emptyFieldNames: Set<String>,
    ): List<FormField> {
        if (filled.kind != FieldKind.TEXT) return emptyList()
        val normalizedLabel = normalize(filled.label)

        return schema.allFields()
            .filter { candidate ->
                candidate.kind == FieldKind.TEXT &&
                    candidate.name != filled.name &&
                    candidate.name in emptyFieldNames &&
                    candidate.thirdParty == filled.thirdParty &&
                    isCandidate(filled, candidate, normalizedLabel)
            }
            .distinctBy { it.name }
    }

    private fun isCandidate(filled: FormField, candidate: FormField, normalizedFilledLabel: String): Boolean {
        if (filled.canonical != null && candidate.canonical == filled.canonical) return true
        if (normalizedFilledLabel.isBlank()) return false
        return normalize(candidate.label) == normalizedFilledLabel
    }

    /** Minúsculas, sin acentos ni dobles espacios — para comparar rótulos impresos. */
    private fun normalize(label: String): String {
        val withoutAccents = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}
