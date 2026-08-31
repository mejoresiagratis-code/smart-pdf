package com.mejoresiagratis.rellenador.data.validation

import com.mejoresiagratis.rellenador.data.model.BuiltinSchemas
import com.mejoresiagratis.rellenador.data.model.CanonicalKeys

/**
 * Valida el valor de un campo canónico según su tipo, replicando validateField()
 * de la web. Devuelve null si no aplica validación o si es válido; mensaje si no.
 *
 * Tanda 5·2 — antes decidía por el NOMBRE del campo normalizado (`base(fieldName)`): funcionaba
 * con Orange porque se escribió para sus nombres, pero con los de Aire (`NOMBRE DEL DEUDOR`,
 * `NIF/CIF/NIE`, `CÓDIGO POSTAL`...) ninguna comparación casa, `validate()` devuelve null y la
 * app deja de comprobar dígitos de control, IBAN mod-97 y CP↔provincia **sin decir nada**
 * (docs/PLAN_FASE_5.md, hallazgo 2.5). Ahora decide primero por la clave CANÓNICA del campo
 * (`BuiltinSchemas.canonicalFor`), que no depende de cómo se llame el campo en el AcroForm.
 *
 * La fuente del mapeo sigue siendo `CANON` (5·3/5·4 la sustituirán por el `FormSchema` real
 * del PDF subido) — por eso `base(fieldName)` se conserva como RESPALDO: sigue haciendo falta
 * para los campos de `CANON` sin canónica propia (checkboxes de tipo de identificación,
 * Responsable Comercial) y para cualquier campo que llegue sin pasar por un esquema.
 */
object FieldValidator {

    data class Result(val ok: Boolean, val message: String? = null)

    /** Heurística de nombre; sólo se consulta si el campo no tiene clave canónica (ver cabecera). */
    private fun base(fieldName: String): String {
        val m = Regex("^(.*?)[_\\s](\\d+)$").find(fieldName)
        return FieldNormalizer.norm(m?.groupValues?.get(1) ?: fieldName)
    }

    /**
     * @param tipoId tipo de identificación elegido (CIF/NIF/NIE) para validar el campo de identificación.
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

        // Canónica primero; heurística de nombre sólo si el campo no tiene (ver cabecera).
        val canonical = BuiltinSchemas.canonicalFor(fieldName)
        val b by lazy { base(fieldName) }

        return when {
            canonical == CanonicalKeys.IDENTIFICACION || (canonical == null && b == "nie") ->
                when (tipoId?.uppercase()?.replace(".", "")) {
                    "CIF" -> if (SpanishValidators.validCIF(v)) Result(true)
                             else Result(false, "CIF no válido (dígito de control)")
                    "NIF" -> if (SpanishValidators.validDNI(v)) Result(true)
                             else Result(false, "NIF/DNI no válido (letra de control)")
                    "NIE" -> if (SpanishValidators.validNIE(v)) Result(true)
                             else Result(false, "NIE no válido (letra de control)")
                    else -> if (SpanishValidators.validIdAny(v)) Result(true)
                            else Result(false, "Documento no válido (CIF/NIF/NIE)")
                }
            canonical == CanonicalKeys.REPRESENTANTE_NIF || (canonical == null && b == "nifrepresentante") ->
                if (SpanishValidators.validDNI(v) || SpanishValidators.validNIE(v)) Result(true)
                else Result(false, "NIF del representante no válido")
            canonical == CanonicalKeys.IBAN ||
                (canonical == null && (b.contains("datosbancarios") || b.contains("iban"))) ->
                if (SpanishValidators.validIBAN(v)) Result(true)
                else Result(false, "IBAN no válido (mod-97)")
            canonical == CanonicalKeys.CP || canonical == CanonicalKeys.CP_2 ||
                (canonical == null && b == "cp") -> {
                val msg = FieldNormalizer.cpProvinciaMsg(v, provinciaSibling)
                if (msg != null) Result(false, msg) else Result(true)
            }
            canonical == CanonicalKeys.TELEFONO || (canonical == null && b == "telefono") ->
                if (SpanishValidators.validPhone(v)) Result(true)
                else Result(false, "Teléfono: 9 dígitos (6/7/8/9)")
            canonical == CanonicalKeys.EMAIL_COMERCIAL || canonical == CanonicalKeys.EMAIL_FACTURACION ||
                (canonical == null && b.startsWith("email")) ->
                if (SpanishValidators.validEmail(v)) Result(true)
                else Result(false, "Email no válido")
            canonical == CanonicalKeys.FECHA_DIA || (canonical == null && b == "fecha") ->
                if (SpanishValidators.validDay(v)) Result(true)
                else Result(false, "Día entre 1 y 31")
            else -> null
        }
    }
}
