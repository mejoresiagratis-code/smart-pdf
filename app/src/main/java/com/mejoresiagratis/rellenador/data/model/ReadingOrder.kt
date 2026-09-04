package com.mejoresiagratis.rellenador.data.model

/**
 * Tanda 5·4k — **el** orden de lectura de una página, en un solo sitio.
 *
 * Antes de esta tanda había dos implementaciones distintas de la misma idea y la segunda era la
 * versión rota de la primera:
 *
 *  - `PdfFieldInspector.orderByReadingRows` agrupa por el hueco vertical respecto al **ancla**
 *    de la fila (el campo más alto), con 6 pt de tolerancia. Su propio comentario explica por
 *    qué no se trocea el eje Y en tramos fijos, con un caso real medido: en el SEPA de Aire, la
 *    fila de 11 casillas del BIC estaba a y≈539 salvo dos campos a y=540,0, y como
 *    `539/6 = 89` pero `540/6 = 90`, esas dos casillas se iban al final de la fila siguiente.
 *  - `VisionLabelPass.collectTargets` (0.10.26) ordenó por `(y / 12f).toInt()`, que es
 *    **exactamente el troceado en tramos fijos** contra el que avisaba el comentario anterior,
 *    con el doble de tolerancia. Reintrodujo un fallo ya diagnosticado y corregido a diez
 *    ficheros de distancia.
 *
 * De ahí que el criterio viva aquí, en Kotlin puro sin Android ni pdfbox: se typecheckea en
 * local, sus comprobaciones son ejecutables y no hay una segunda copia que se pueda desviar.
 */
object ReadingOrder {

    /**
     * Tolerancia vertical por defecto, en puntos. Dos elementos cuya `y` difiere en menos que
     * esto pertenecen a la misma fila impresa. 6 pt es el valor con el que
     * `PdfFieldInspector` lleva funcionando desde la 5·4b; una línea de texto de estos
     * formularios mide ~12 pt, así que 6 separa filas contiguas sin partir una fila propia.
     */
    const val ROW_TOLERANCE_PT = 6f

    /**
     * Agrupa [items] en filas impresas, de arriba abajo, y ordena cada fila de izquierda a
     * derecha.
     *
     * Un elemento entra en la fila en curso si su [y] está a menos de [tolerancePt] del **ancla**
     * de esa fila (el primer elemento, el más alto). Se compara contra el ancla y no contra el
     * elemento anterior a propósito: con una escalera de elementos separados por saltos pequeños,
     * comparar contra el anterior acabaría fundiendo media página en una sola fila.
     */
    fun <T> rows(
        items: List<T>,
        tolerancePt: Float = ROW_TOLERANCE_PT,
        y: (T) -> Float,
        x: (T) -> Float,
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        val byTop = items.sortedBy { y(it) }
        val out = mutableListOf<List<T>>()
        var row = mutableListOf<T>()
        var anchorY = 0f
        for (item in byTop) {
            if (row.isEmpty()) {
                anchorY = y(item)
                row = mutableListOf(item)
            } else if (y(item) - anchorY <= tolerancePt) {
                row += item
            } else {
                out += row.sortedBy { x(it) }
                anchorY = y(item)
                row = mutableListOf(item)
            }
        }
        if (row.isNotEmpty()) out += row.sortedBy { x(it) }
        return out
    }

    /** [rows] aplanado: los elementos en orden de lectura, sin la estructura de filas. */
    fun <T> sorted(
        items: List<T>,
        tolerancePt: Float = ROW_TOLERANCE_PT,
        y: (T) -> Float,
        x: (T) -> Float,
    ): List<T> = rows(items, tolerancePt, y, x).flatten()
}
