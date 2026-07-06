package com.mejoresiagratis.rellenador.data.validation

import java.text.Normalizer

/**
 * Normalización de valores por campo (normVal de la web) + coherencia CP/provincia.
 */
object FieldNormalizer {

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    /** base del nombre canónico sin sufijo _2 (Dirección_2 -> Dirección). */
    private fun baseOf(fieldName: String): String {
        val m = Regex("^(.*?)[_\\s](\\d+)$").find(fieldName)
        return m?.groupValues?.get(1) ?: fieldName
    }

    /**
     * Normaliza un valor según el campo destino (fiel a normVal de la web):
     * IBAN/NIF/CP en formato, y "Apellidos, Nombre" -> "Nombre Apellidos".
     */
    fun normVal(fieldName: String, raw: String?): String {
        var v = (raw ?: "").trim()
        if (v.isEmpty()) return v
        val b = norm(baseOf(fieldName))
        return when {
            b.contains("datosbancarios") || b.contains("iban") ->
                v.uppercase().replace(Regex("[\\s.\\-–—]"), "")
            b == "nie" || b == "nifrepresentante" ->
                v.uppercase().replace(Regex("[\\s.\\-]"), "")
            b == "cp" -> {
                val d = v.replace(Regex("\\D"), "")
                if (d.isNotEmpty() && d.length < 5) d.padStart(5, '0') else v
            }
            b == "nombrerepresentante" && v.contains(",") -> {
                val parts = v.split(",").map { it.trim() }
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty())
                    "${parts[1]} ${parts[0]}" else v
            }
            else -> v
        }
    }

    // Prefijos de CP -> nombres de provincia (fiel a PROV de la web).
    private val PROV = mapOf(
        "01" to "alava araba","02" to "albacete","03" to "alicante alacant","04" to "almeria","05" to "avila",
        "06" to "badajoz","07" to "baleares balears illes","08" to "barcelona","09" to "burgos","10" to "caceres",
        "11" to "cadiz","12" to "castellon castello","13" to "ciudad real","14" to "cordoba","15" to "coruna",
        "16" to "cuenca","17" to "girona gerona","18" to "granada","19" to "guadalajara","20" to "gipuzkoa guipuzcoa",
        "21" to "huelva","22" to "huesca","23" to "jaen","24" to "leon","25" to "lleida lerida","26" to "rioja",
        "27" to "lugo","28" to "madrid","29" to "malaga","30" to "murcia","31" to "navarra","32" to "ourense orense",
        "33" to "asturias oviedo","34" to "palencia","35" to "palmas","36" to "pontevedra","37" to "salamanca",
        "38" to "tenerife","39" to "cantabria santander","40" to "segovia","41" to "sevilla","42" to "soria",
        "43" to "tarragona","44" to "teruel","45" to "toledo","46" to "valencia","47" to "valladolid",
        "48" to "bizkaia vizcaya","49" to "zamora","50" to "zaragoza","51" to "ceuta","52" to "melilla"
    )

    /** Devuelve mensaje de error si CP no es válido o no cuadra con la provincia; null si ok. */
    fun cpProvinciaMsg(cp: String?, provincia: String?): String? {
        val c = SpanishValidators.clean(cp)
        if (!Regex("^\\d{5}$").matches(c)) return "El C.P. debe tener 5 dígitos"
        val pre = c.substring(0, 2)
        val names = PROV[pre] ?: return "Prefijo de C.P. inexistente ($pre)"
        if (!provincia.isNullOrBlank()) {
            val p = norm(provincia)
            if (p.isNotEmpty() && !names.split(" ").any { p.contains(it) || it.contains(p) })
                return "El C.P. $c es de ${names.split(" ")[0]} y la provincia es «$provincia»"
        }
        return null
    }
}
