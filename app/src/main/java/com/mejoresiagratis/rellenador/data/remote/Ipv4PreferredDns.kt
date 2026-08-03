package com.mejoresiagratis.rellenador.data.remote

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Resolutor DNS que prioriza direcciones IPv4 sobre IPv6.
 *
 * Diagnosticado con `datingtrck.com` (dominio real del proxy en producción): no tiene
 * registro AAAA (IPv6), confirmado consultando `https://dns.google/resolve?...&type=AAAA`
 * — la respuesta solo trae el SOA en "Authority", sin ningún "Answer". En redes móviles
 * IPv6-only (habitual en 5G, con NAT64), el sistema operativo sintetiza una dirección
 * IPv6 falsa para poder alcanzar servidores que solo tienen IPv4 — pero esa síntesis
 * puede fallar de forma más frágil en una conexión de socket cruda (como hace OkHttp)
 * que en un navegador, que tiene mecanismos de repliegue mucho más robustos (Happy
 * Eyeballs). Confirmado en la práctica: la misma URL, mismo dispositivo, misma red 5G,
 * funcionaba en Chrome pero fallaba con "Unable to resolve host" dentro de la app.
 *
 * Este resolutor evita depender de que esa síntesis NAT64 funcione: pide al sistema
 * TODAS las direcciones (`Dns.SYSTEM.lookup`) y se queda solo con las IPv4 si hay
 * alguna. Si por lo que sea no hay ninguna IPv4 disponible, no rompe nada — cae a lo
 * que haya devuelto el sistema (nunca deja la lista vacía).
 */
class Ipv4PreferredDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val onlyIpv4 = all.filterIsInstance<Inet4Address>()
        return onlyIpv4.ifEmpty { all }
    }
}
