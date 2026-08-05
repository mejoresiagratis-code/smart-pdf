package com.mejoresiagratis.rellenador.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolutor DNS que prioriza IPv4 y, si el sistema no consigue resolver, cae a
 * DNS-over-HTTPS (DoH).
 *
 * ── Por qué IPv4 primero ──
 * `datingtrck.com` (proxy en producción) no tiene registro AAAA (IPv6). En redes móviles
 * IPv6-only (5G con NAT64) el sistema sintetiza una IPv6 falsa para alcanzar servidores
 * solo-IPv4, y esa síntesis falla más fácilmente en un socket crudo (OkHttp) que en un
 * navegador (Happy Eyeballs). Confirmado: misma URL, mismo móvil, misma red — Chrome sí,
 * la app no.
 *
 * ── Por qué el fallback DoH (ago 2026) ──
 * Tras el cambio de servidor, la app dio "Unable to resolve host datingtrck.com: No
 * address associated with hostname" ESTANDO EN WIFI, mientras el dominio resolvía
 * perfectamente desde fuera (registro A presente). Causa: caché DNS NEGATIVA en el
 * dispositivo o en el router/ISP, cacheada mientras la zona propagaba. Y el TTL de caché
 * negativa del SOA de este dominio es 86400 → una respuesta negativa puede quedarse
 * pegada hasta 24 HORAS. Sin fallback, la app queda inservible todo ese tiempo aunque el
 * servidor esté perfecto.
 *
 * Con DoH la app resuelve por su cuenta, saltándose el resolutor de la red. Los endpoints
 * se piden por IP LITERAL a propósito: si tuvieran hostname harían falta DNS para
 * resolverlos — justo lo que está roto (círculo vicioso). Los certificados de Cloudflare y
 * Google incluyen esas IPs en el SAN, así que la validación TLS es correcta y NO se
 * desactiva ninguna verificación.
 *
 * Orden: sistema (rápido, normal) → DoH (solo si el sistema falla). En operación normal el
 * DoH nunca se usa; es una red de seguridad.
 */
class Ipv4PreferredDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        // 1) Camino normal: el resolutor del sistema.
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull()
        if (!system.isNullOrEmpty()) return preferIpv4(system)

        // 2) El sistema no pudo (caché negativa, resolutor caído, NAT64 roto…) → DoH.
        val viaDoh = resolveViaDoh(hostname)
        if (viaDoh.isNotEmpty()) return viaDoh

        throw UnknownHostException(
            "No se pudo resolver \"$hostname\" ni por el sistema ni por DNS-over-HTTPS. " +
            "Revisa la conexión (probar datos móviles, o desactivar/activar el WiFi)."
        )
    }

    private fun preferIpv4(all: List<InetAddress>): List<InetAddress> =
        all.filterIsInstance<Inet4Address>().ifEmpty { all }

    // ── DoH ──────────────────────────────────────────────────────────────────────

    @Serializable private data class DohAnswer(val type: Int = 0, val data: String = "")
    @Serializable private data class DohResponse(val Status: Int = -1, val Answer: List<DohAnswer> = emptyList())

    private class Cached(val addrs: List<InetAddress>, val atMs: Long)

    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        cache[hostname]?.let { c ->
            if (System.currentTimeMillis() - c.atMs < CACHE_TTL_MS) return c.addrs
            cache.remove(hostname)
        }
        for (url in DOH_ENDPOINTS) {
            val addrs = runCatching { queryDoh(url, hostname) }.getOrNull().orEmpty()
            if (addrs.isNotEmpty()) {
                cache[hostname] = Cached(addrs, System.currentTimeMillis())
                return addrs
            }
        }
        return emptyList()
    }

    private fun queryDoh(endpoint: String, hostname: String): List<InetAddress> {
        val req = Request.Builder()
            .url("$endpoint?name=$hostname&type=A")
            .header("accept", "application/dns-json")
            .build()
        dohClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val parsed = json.decodeFromString<DohResponse>(body)
            if (parsed.Status != 0) return emptyList()
            // type 1 = registro A (IPv4). Se ignora todo lo demás (CNAME, SOA…).
            return parsed.Answer
                .filter { it.type == 1 && it.data.isNotBlank() }
                .mapNotNull { a ->
                    runCatching { InetAddress.getByAddress(hostname, parseIpv4(a.data)) }.getOrNull()
                }
        }
    }

    /** "107.6.184.117" → ByteArray(4). Se construye a mano para NO volver a pedir DNS. */
    private fun parseIpv4(ip: String): ByteArray {
        val parts = ip.split('.')
        require(parts.size == 4) { "IPv4 inválida: $ip" }
        return ByteArray(4) { i ->
            val n = parts[i].toInt()
            require(n in 0..255) { "Octeto fuera de rango en $ip" }
            n.toByte()
        }
    }

    private companion object {
        /** IPs literales a propósito: resolverlas NO requiere DNS (ver KDoc). */
        val DOH_ENDPOINTS = listOf(
            "https://1.1.1.1/dns-query",   // Cloudflare
            "https://8.8.8.8/resolve",     // Google
        )
        const val CACHE_TTL_MS = 5 * 60 * 1000L

        val cache = ConcurrentHashMap<String, Cached>()
        val json = Json { ignoreUnknownKeys = true }

        /**
         * Cliente propio y mínimo para el DoH. Usa Dns.SYSTEM (no este resolutor) para no
         * entrar en recursión, aunque con IPs literales no llega a hacer ninguna consulta.
         * Timeouts cortos: es un camino de rescate, no debe colgar la extracción.
         */
        val dohClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}
