package com.mejoresiagratis.rellenador.ui.wizard

import com.mejoresiagratis.rellenador.data.model.ContractFields

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
    /** Claves de campo, en el orden en que se pintan. */
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
fun canonFillSections(): List<FillSection> = listOf(
    FillSection(
        "Empresa / Identificación",
        listOf(
            "Nombre  Razón Social", "Nombre Comercial", "NIE",
            "Nombre representante", "NIF representante",
            // Añadido tras auditoría contra el AcroForm real y la web (existía en el PDF y
            // en el prompt de IA, pero no estaba conectado en Android).
            "Actividad principal del negocio",
        ),
    ),
    FillSection("Dirección fiscal", listOf("Dirección", "CP", "Población", "Provincia")),
    FillSection(
        "Dirección comercio / PdV",
        listOf("Dirección_2", "CP_2", "Población_2", "Provincia_2"),
        showCopyFiscal = true,
    ),
    FillSection("Contacto", listOf("Teléfono", "Email Comercial", "Email  Facturación")),
    FillSection("Datos bancarios", listOf("Datos bancarios del DISTRIBUIDOR")),
)

/**
 * Campos que cuenta la barra de progreso: los de las secciones más los de la fecha.
 *
 * Antes el denominador era `ContractFields.CANON.size` fijo. Con las secciones parametrizadas eso
 * ya no valdría: si un día se le pasan otras secciones, el progreso mediría contra una lista que
 * no es la que hay en pantalla. Con `CANON` el resultado es **idéntico** — 18 claves en las
 * secciones + 3 de fecha = 21 = `CANON.size` (verificado) —, así que esta tanda no cambia lo que
 * se ve, sólo deja de depender de una constante ajena a lo que se pinta.
 */
fun countedKeys(sections: List<FillSection>): List<String> =
    sections.flatMap { it.keys } + ContractFields.DATE_KEYS
