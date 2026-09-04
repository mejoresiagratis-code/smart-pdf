package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.ReadingOrder
import org.junit.Assert.*
import org.junit.Test

/**
 * Tanda 5·4k — el orden de lectura, ahora en un solo sitio.
 *
 * El caso que da sentido a la clase es el primero: con troceado en tramos fijos
 * (`(y / 6f).toInt()`, que es lo que hacía `VisionLabelPass` en la 0.10.26 con tolerancia 12)
 * una fila impresa se parte en cuanto el corte del tramo cae entre dos de sus campos. Los
 * números son los medidos en el SEPA de Aire, en la fila de casillas del BIC.
 */
class ReadingOrderTest {

    private data class P(val name: String, val x: Float, val y: Float)

    private fun order(vararg items: P): List<String> =
        ReadingOrder.sorted(items.toList(), y = { it.y }, x = { it.x }).map { it.name }

    @Test
    fun `una fila no se parte porque el corte del tramo caiga en medio`() {
        // 539 y 540 caen en tramos distintos con `(y/6).toInt()` (89 vs 90) pese a estar a
        // 1,1 pt: es exactamente el caso del BIC del SEPA.
        val got = order(
            P("a", x = 10f, y = 538.9f),
            P("b", x = 20f, y = 540.0f),
            P("c", x = 30f, y = 539.2f),
            P("d", x = 40f, y = 540.0f),
        )
        assertEquals(listOf("a", "b", "c", "d"), got)
    }

    @Test
    fun `filas distintas no se funden aunque compartan tramo`() {
        val got = order(
            P("fila2-izq", x = 10f, y = 111f),
            P("fila1-der", x = 90f, y = 100f),
            P("fila1-izq", x = 10f, y = 100f),
        )
        assertEquals(listOf("fila1-izq", "fila1-der", "fila2-izq"), got)
    }

    @Test
    fun `la fila CP Localidad Provincia sale de izquierda a derecha`() {
        // Coordenadas reales de la fila del contrato de empresas de Aire.
        val got = order(
            P("Provincia", x = 404f, y = 224.4f),
            P("CP", x = 60f, y = 224.0f),
            P("Localidad", x = 180f, y = 224.9f),
        )
        assertEquals(listOf("CP", "Localidad", "Provincia"), got)
    }

    @Test
    fun `una escalera de saltos pequenos no funde media pagina en una fila`() {
        // Cada uno está a 5 pt del anterior, pero se compara contra el ANCLA de la fila,
        // así que a partir del tercero se abre fila nueva.
        val got = ReadingOrder.rows(
            listOf(P("a", 10f, 100f), P("b", 20f, 105f), P("c", 30f, 110f), P("d", 40f, 115f)),
            y = { it.y },
            x = { it.x },
        )
        assertTrue("no puede quedar todo en una sola fila", got.size > 1)
        assertEquals(4, got.sumOf { it.size })
    }

    @Test
    fun `lista vacia devuelve lista vacia`() {
        assertEquals(emptyList<List<P>>(), ReadingOrder.rows(emptyList<P>(), y = { it.y }, x = { it.x }))
    }
}
