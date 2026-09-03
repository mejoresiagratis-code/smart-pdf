package com.mejoresiagratis.rellenador.ui.wizard

import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.FieldKeys

/**
 * Una sección del paso de Relleno: un título y las claves de campo que agrupa.
 *
 * Fase 5, tanda 1 (ver `docs/PLAN_FASE_5.md`). Hasta ahora esto era un `private data class
 * Section` con una constante `SECTIONS` **escrita a mano dentro de `FillStep`**, con los nombres
 * del AcroForm de Orange literales —dobles espacios incluidos— y sin ningún punto de extensión.
 *
 * Sacarlo aquí es lo único que hace esta tanda: `FillStep` pasa a **recibir** las secciones en vez
 * de conocerlas. El comportamiento no cambia en absoluto, porque quien lo llama le sigue pasando
 * las de `CANON` ([canonFillSections]). Es la costura por la que entrará el `FormSchema` del PDF
 * subido en la tanda 5·4, y se hace sola y verificable —«la app se comporta idéntica»— en vez de
 * mezclada con el cambio que sí se nota.
 *
 * `FillSection` y no `Section`: el nombre corto era privado y al hacerlo público chocaría con
 * `FormSection` del modelo y con las secciones de otros pasos. En este proyecto una colisión de
 * nombres entre paquetes ya tumbó un build (v0.9.8.1), así que se elige distinto a propósito.
 */
data class FillSection(
    val title: String,
    /**
     * **Nombres reales** de los campos del PDF que se está rellenando, en el orden en que se
     * pintan. Desde la tanda 5·3 son nombres reales y no claves de `CANON`: es la misma clave con
     * la que se indexan `fieldValues` y compañía, así que la pantalla busca los valores donde de
     * verdad están. Con el contrato de Orange coinciden, porque allí la clave de `CANON` YA es el
     * nombre real del campo.
     */
    val keys: List<String>,
    /** Muestra el atajo «copiar de la dirección fiscal». Sólo la sección de comercio/PdV. */
    val showCopyFiscal: Boolean = false,
)

/**
 * Las secciones del contrato de distribución de Orange, agrupando `ContractFields.CANON`.
 *
 * Se mantiene tal cual estaba (mismos títulos, mismas claves, mismo orden): esta tanda mueve
 * código, no lo cambia. Las tres claves de la fecha (`Fecha`/`de`/`año`) **no** están aquí porque
 * `FillStep` las pinta aparte, como una fila compacta día/mes/año en vez de tres campos apilados.
 * Generalizar ese caso especial es la tanda 5·2.
 */
fun canonFillSections(keys: FieldKeys = FieldKeys.IDENTITY): List<FillSection> = listOf(
    FillSection(
        "Empresa / Identificación",
        listOf(
            "Nombre  Razón Social", "Nombre Comercial", "NIE",
            "Nombre representante", "NIF representante",
            // Añadido tras auditoría contra el AcroForm real y la web (existía en el PDF y
            // en el prompt de IA, pero no estaba conectado en Android).
            "Actividad principal del negocio",
        ).map(keys::real),
    ),
    FillSection("Dirección fiscal",
        listOf("Dirección", "CP", "Población", "Provincia").map(keys::real)),
    FillSection(
        "Dirección comercio / PdV",
        listOf("Dirección_2", "CP_2", "Población_2", "Provincia_2").map(keys::real),
        showCopyFiscal = true,
    ),
    FillSection("Contacto",
        listOf("Teléfono", "Email Comercial", "Email  Facturación").map(keys::real)),
    FillSection("Datos bancarios",
        listOf("Datos bancarios del DISTRIBUIDOR").map(keys::real)),
)

/**
 * Campos que cuenta la barra de progreso: los de las secciones más los de la fecha.
 *
 * Antes el denominador era `ContractFields.CANON.size` fijo. Con las secciones parametrizadas eso
 * ya no valdría: si un día se le pasan otras secciones, el progreso mediría contra una lista que
 * no es la que hay en pantalla. Con `CANON` el resultado es **idéntico** — 18 claves en las
 * secciones + 3 de fecha = 21 = `CANON.size` (verificado) —, así que esta tanda no cambia lo que
 * se ve, sólo deja de depender de una constante ajena a lo que se pinta.
 *
 * Tanda 5·4 — si las secciones ya contienen las claves de fecha (porque vienen del `FormSchema`,
 * y el esquema declara la fecha como sección propia), no se suman otra vez. Se detecta con
 * intersección, no con un flag: si al menos una clave de `DATE_KEYS` está entre las claves ya
 * contadas, se asume que la fecha ya vive en secciones. En Orange, `canonFillSections()` no
 * incluye las fechas (van fuera del bucle en `FillStep`) y el resultado sigue siendo 21.
 */
fun countedKeys(
    sections: List<FillSection>,
    keys: FieldKeys = FieldKeys.IDENTITY,
): List<String> {
    val fromSections = sections.flatMap { it.keys }
    val dateKeys = ContractFields.DATE_KEYS.map(keys::real)
    val alreadyIncluded = fromSections.toSet().let { s -> dateKeys.any { it in s } }
    return if (alreadyIncluded) fromSections else fromSections + dateKeys
}

/**
 * Traduce un [com.mejoresiagratis.rellenador.data.model.FormSchema] en secciones para `FillStep`.
 *
 * Tanda 5·4 — reemplaza a `canonFillSections()` cuando el asistente tiene un esquema del PDF
 * cargado. Cada `FormSection` del esquema (SIMPLE, TABLE o REPEATED_BLOCK) se aplana en una
 * `FillSection` con sus nombres reales de campo en el orden en que están, y las tablas se pintan
 * como bloques cuyas etiquetas incluyen «Fila N · Columna» — sin filas dinámicas, que son la
 * tanda 5·5. `showCopyFiscal` sólo se marca en la sección cuyos campos declaran `DIRECCION_2` (o
 * el `_2` en Orange), que es donde el atajo tiene sentido.
 *
 * Los campos con `ValueOrigin.CATALOGO`/`CALCULADO`/`FIRMA` se dejan en las secciones tal cual:
 * la política de qué es editable la decide `FillStep` con `ValueOrigin`, aquí sólo estructuramos.
 */
fun fillSectionsFrom(
    schema: com.mejoresiagratis.rellenador.data.model.FormSchema,
): List<FillSection> {
    val out = mutableListOf<FillSection>()
    // Tanda 5·4h — un nombre visto en una sección anterior NO se vuelve a pintar. Es el mismo
    // campo del AcroForm y comparte valor, así que dos filas editarían lo mismo; y además la
    // clave repetida rompía `rememberSaveable(key)` y la lista perezosa (ver `FillStep`).
    val vistos = mutableSetOf<String>()
    for (section in schema.sections) {
        // Tanda 5·4d (2ª mitad) — dos correcciones respecto a `.map { it.name }` a secas:
        //
        //  · `distinct()`: un grupo de radio son VARIAS entradas con el mismo `name` (es un solo
        //    campo del AcroForm, ver `ValueRouting.kt`). Sin esto, un grupo de 6 opciones pintaba
        //    6 filas idénticas, todas escribiendo sobre la misma clave.
        //  · los `/Sig` se descartan: la firma se estampa como imagen en el paso 4 y
        //    `routeFieldValues()` ya se niega a escribirlos por ninguna de las dos vías, así que
        //    ofrecer un hueco que no va a ninguna parte sólo puede confundir.
        val names = section.allFields()
            .filter { it.kind != com.mejoresiagratis.rellenador.data.model.FieldKind.SIGNATURE }
            .map { it.name }
            .distinct()
            .filter { vistos.add(it) }
        if (names.isEmpty()) continue
        val hasDireccion2 = section.allFields().any {
            it.canonical == com.mejoresiagratis.rellenador.data.model.CanonicalKeys.DIRECCION_2
        }
        out += FillSection(
            title = section.title,
            keys = names,
            showCopyFiscal = hasDireccion2,
        )
    }
    return out
}
