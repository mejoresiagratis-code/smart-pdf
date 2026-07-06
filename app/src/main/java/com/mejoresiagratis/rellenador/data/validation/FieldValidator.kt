package com.mejoresiagratis.rellenador.data.validation

/**
 * Valida el valor de un campo canónico según su tipo, replicando validateField()
 * de la web. Devuelve null si no aplica validación o si es válido; mensaje si no.
 */
object FieldValidator {

    data class Result(val ok: Boolean, val message: String? = null)

    private fun base(fieldName: String): String {
        val m = Regex("^(.*?)[_\\s](\\d+)$").find(fieldName)
        return FieldNormalizer.norm(m?.groupValues?.get(1) ?: fieldName)
    }

    /**
     * @param tipoId tipo de identificación elegido (CIF/NIF/NIE) para validar el campo NIE.
     * @param provinciaSibling valor de la provincia del mismo bloque (para CP).
     */
    fun validate(
        fieldName: String,
        value: String?,
        tipoId: String? = null,
        provinciaSibling: String? = null
    ): Result? {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return null
        val b = base(fieldName)
        return when {
            b == "nie" -> when (tipoId?.uppercase()?.replace(".", "")) {
                "CIF" -> if (SpanishValidators.validCIF(v)) Result(true)
                         else Result(false, "CIF no válido (dígito de control)")
                "NIF" -> if (SpanishValidators.validDNI(v)) Result(true)
                         else Result(false, "NIF/DNI no válido (letra de control)")
                "NIE" -> if (SpanishValidators.validNIE(v)) Result(true)
                         else Result(false, "NIE no válido (letra de control)")
                else -> if (SpanishValidators.validIdAny(v)) Result(true)
                        else Result(false, "Documento no válido (CIF/NIF/NIE)")
            }
            b == "nifrepresentante" ->
                if (SpanishValidators.validDNI(v) || SpanishValidators.validNIE(v)) Result(true)
                else Result(false, "NIF del representante no válido")
            b.contains("datosbancarios") || b.contains("iban") ->
                if (SpanishValidators.validIBAN(v)) Result(true)
                else Result(false, "IBAN no válido (mod-97)")
            b == "cp" -> {
                val msg = FieldNormalizer.cpProvinciaMsg(v, provinciaSibling)
                if (msg != null) Result(false, msg) else Result(true)
            }
            b == "telefono" ->
                if (SpanishValidators.validPhone(v)) Result(true)
                else Result(false, "Teléfono: 9 dígitos (6/7/8/9)")
            b.startsWith("email") ->
                if (SpanishValidators.validEmail(v)) Result(true)
                else Result(false, "Email no válido")
            b == "fecha" ->
                if (SpanishValidators.validDay(v)) Result(true)
                else Result(false, "Día entre 1 y 31")
            else -> null
        }
    }
}
