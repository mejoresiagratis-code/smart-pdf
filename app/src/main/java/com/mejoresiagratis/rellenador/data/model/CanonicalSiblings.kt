package com.mejoresiagratis.rellenador.data.model

/**
 * Tanda 5·4i — reparto de valor entre campos que comparten canónica.
 *
 * Hasta esta tanda una [CanonicalKeys] solo podía vivir en un [FormField] a la vez
 * (`SchemaEditing.setCanonical` se la quitaba al anterior en cuanto se asignaba a uno nuevo).
 * Ahora puede compartirse entre varios — porque el usuario los enganchó a mano, o porque
 * confirmó un afín que propuso [AffinityGroup] en Relleno — y esta clase es la que hace que
 * ESCRIBIR en uno de ellos también rellene a los demás.
 *
 * Deliberadamente **no** decide qué campos comparten canónica (eso lo decide el usuario, vía
 * `SchemaEditing.setCanonical`), ni sugiere afines nuevos (eso es [AffinityGroup]): solo reparte
 * un valor ya puesto entre los que YA la comparten.
 */
object CanonicalSiblings {

    /**
     * Amplía [delta] (lo que se está a punto de escribir en `fieldValues`) con los campos
     * hermanos de cada entrada: mismo [FormField.canonical], en el mismo [schema].
     *
     * Un hermano solo recibe el valor si sigue **vacío** en [currentValues] — si ya tiene algo
     * escrito (aunque sea un valor antiguo distinto) no se toca, por si es un dato legítimamente
     * distinto que el usuario puso a mano; queda en pie el aviso de que hay huecos con canónica
     * repetida, pero forzarlo sin avisar sería peor que dejarlo como está.
     *
     * @param schema esquema activo; `null` (sin esquema, o `BUILTIN` sin canónicas repetidas)
     *   devuelve [delta] sin tocar.
     */
    fun expand(
        schema: FormSchema?,
        currentValues: Map<String, String>,
        delta: Map<String, String>,
    ): Map<String, String> {
        if (schema == null) return delta

        val byCanonical: Map<String, List<String>> = schema.allFields()
            .filter { it.canonical != null }
            .groupBy { it.canonical!! }
            .mapValues { (_, fields) -> fields.map { it.name }.distinct() }
        // Atajo: si ninguna canónica se repite (el caso de Orange, y el de cualquier PDF ajeno
        // antes de que el usuario comparta una a mano), no hay nada que repartir.
        if (byCanonical.values.none { it.size > 1 }) return delta

        val canonicalOfName: Map<String, String> = schema.allFields()
            .mapNotNull { field -> field.canonical?.let { field.name to it } }
            .toMap()

        val expanded = delta.toMutableMap()
        delta.forEach { (name, value) ->
            if (value.isBlank()) return@forEach
            val canonical = canonicalOfName[name] ?: return@forEach
            byCanonical[canonical].orEmpty().forEach { sibling ->
                if (sibling != name &&
                    sibling !in expanded &&
                    currentValues[sibling].isNullOrBlank()
                ) {
                    expanded[sibling] = value
                }
            }
        }
        return expanded
    }
}
