package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.CanonicalKeys
import com.mejoresiagratis.rellenador.data.remote.CanonicalMapper
import com.mejoresiagratis.rellenador.data.remote.CanonicalProposal
import com.mejoresiagratis.rellenador.data.model.ProxyProviders
import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import com.mejoresiagratis.rellenador.data.model.ProxyResponse
import com.mejoresiagratis.rellenador.data.remote.ProxyApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tanda 5·4g — el filtro de [CanonicalMapper.sanitize].
 *
 * Es la parte que no se puede saltar: un motor puede devolver claves que no existen, campos que
 * no se le preguntaron, o la misma canónica para dos huecos. Un enganche equivocado no da ningún
 * error — sale impreso en el contrato y nadie lo ve.
 */
class CanonicalMapperSanitizeTest {

    /**
     * `sanitize` es puro y no llama a la red, pero `CanonicalMapper` necesita un `ProxyApi` para
     * construirse. Este doble falla si alguien lo usa, que es lo que se quiere: si un cambio
     * futuro mete una llamada dentro de `sanitize`, la prueba lo delata en vez de tragárselo.
     */
    private class ExplodingProxyApi : ProxyApi {
        override suspend fun providers(): ProxyProviders =
            throw AssertionError("sanitize() no debe llamar al proxy")

        override suspend fun call(request: ProxyRequest): ProxyResponse =
            throw AssertionError("sanitize() no debe llamar al proxy")
    }

    private val mapper = CanonicalMapper(api = ExplodingProxyApi())

    private fun sanitize(
        raw: Map<String, String>,
        preguntados: List<String> = listOf("A", "B"),
        ocupadas: Set<String> = emptySet(),
    ) = mapper.sanitize(CanonicalProposal(raw), preguntados, ocupadas)

    @Test
    fun `una propuesta valida pasa tal cual`() {
        assertEquals(
            mapOf("A" to CanonicalKeys.CP),
            sanitize(mapOf("A" to CanonicalKeys.CP)),
        )
    }

    @Test
    fun `una clave inventada se descarta`() {
        assertTrue(sanitize(mapOf("A" to "codigo_postal_del_cliente")).isEmpty())
    }

    @Test
    fun `un campo que no se pregunto se descarta`() {
        assertTrue(sanitize(mapOf("Z" to CanonicalKeys.CP)).isEmpty())
    }

    @Test
    fun `en un duplicado gana el primero`() {
        val out = sanitize(mapOf("A" to CanonicalKeys.CP, "B" to CanonicalKeys.CP))
        assertEquals(mapOf("A" to CanonicalKeys.CP), out)
    }

    @Test
    fun `una canonica ya asignada en el esquema no se vuelve a proponer`() {
        val out = sanitize(
            mapOf("A" to CanonicalKeys.CP),
            ocupadas = setOf(CanonicalKeys.CP),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `los espacios sobrantes no invalidan la clave`() {
        assertEquals(
            mapOf("A" to CanonicalKeys.IBAN),
            sanitize(mapOf("A" to "  ${CanonicalKeys.IBAN} ")),
        )
    }

    @Test
    fun `una respuesta vacia da un mapa vacio`() {
        assertTrue(sanitize(emptyMap()).isEmpty())
    }
}
