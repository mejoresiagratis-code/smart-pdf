package com.mejoresiagratis.rellenador.data.validation

/**
 * Validadores con dígito de control, portados VERBATIM de la web de producción.
 * No cambiar los algoritmos sin replicar en la web: deben dar el mismo resultado.
 */
object SpanishValidators {

    private const val DNI_L = "TRWAGMYFPDXBNJZSQVHLCKE"

    fun clean(v: String?): String =
        (v ?: "").uppercase().replace(Regex("[\\s.\\-]"), "")

    /** DNI/NIF: 8 dígitos + letra de control (módulo 23). */
    fun validDNI(value: String?): Boolean {
        val v = clean(value)
        val m = Regex("^(\\d{8})([A-Z])$").find(v) ?: return false
        val num = m.groupValues[1].toInt()
        return DNI_L[num % 23] == m.groupValues[2][0]
    }

    /** NIE: X/Y/Z + 7 dígitos + letra. X=0, Y=1, Z=2. */
    fun validNIE(value: String?): Boolean {
        val v = clean(value)
        val m = Regex("^([XYZ])(\\d{7})([A-Z])$").find(v) ?: return false
        val prefix = when (m.groupValues[1]) { "X" -> "0"; "Y" -> "1"; else -> "2" }
        val num = (prefix + m.groupValues[2]).toInt()
        return DNI_L[num % 23] == m.groupValues[3][0]
    }

    /** CIF: letra tipo + 7 dígitos + control (dígito o letra según tipo). */
    fun validCIF(value: String?): Boolean {
        val v = clean(value)
        val m = Regex("^([ABCDEFGHJNPQRSUVW])(\\d{7})([0-9A-J])$").find(v) ?: return false
        val d = m.groupValues[2]
        var s = 0
        for (i in 0 until 7) {
            var n = d[i].digitToInt()
            if (i % 2 == 0) { n *= 2; n = n / 10 + n % 10 }
            s += n
        }
        val c = (10 - s % 10) % 10
        val type = m.groupValues[1][0]
        val control = m.groupValues[3]
        return when {
            "NPQRSW".contains(type) -> control == "JABCDEFGHI"[c].toString()
            "ABEH".contains(type) -> control == c.toString()
            else -> control == c.toString() || control == "JABCDEFGHI"[c].toString()
        }
    }

    fun validIdAny(v: String?): Boolean = validDNI(v) || validNIE(v) || validCIF(v)

    /** IBAN: formato + módulo 97. Longitud 24 para ES. */
    fun validIBAN(value: String?): Boolean {
        val v = clean(value)
        if (!Regex("^[A-Z]{2}\\d{2}[A-Z0-9]{10,30}$").matches(v)) return false
        if (v.startsWith("ES") && v.length != 24) return false
        val rearranged = (v.substring(4) + v.substring(0, 4))
            .map { ch -> if (ch.isLetter()) (ch.code - 55).toString() else ch.toString() }
            .joinToString("")
        var rem = 0
        for (ch in rearranged) rem = (rem * 10 + (ch - '0')) % 97
        return rem == 1
    }

    fun validPhone(v: String?): Boolean = Regex("^[6789]\\d{8}$").matches(clean(v))

    fun validEmail(v: String?): Boolean =
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches((v ?: "").trim())

    fun validDay(v: String?): Boolean {
        val c = clean(v)
        return Regex("^\\d{1,2}$").matches(c) && c.toInt() in 1..31
    }
}
