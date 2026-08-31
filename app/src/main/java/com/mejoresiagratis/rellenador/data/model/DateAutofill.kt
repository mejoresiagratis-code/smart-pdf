package com.mejoresiagratis.rellenador.data.model

import java.util.Calendar

/**
 * Autorrelleno de fecha actual, fiel a autoFillDates() de la web:
 * Fecha = día, de = mes en letras (español), año = ÚLTIMO dígito del año.
 * Solo rellena los campos de fecha que estén vacíos.
 *
 * Tanda 5·2b — antes las tres claves eran los literales `"Fecha"`, `"de"` y `"año"`, nombres
 * reales del AcroForm de Orange. Ahora salen de las canónicas de fecha vía
 * `BuiltinSchemas.realKeyFor`, con el literal como red de seguridad si esa vuelta no resuelve
 * (docs/PLAN_FASE_5.md, hallazgo 2.6).
 */
object DateAutofill {
    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    private val KEY_DIA = BuiltinSchemas.realKeyFor(CanonicalKeys.FECHA_DIA) ?: "Fecha"
    private val KEY_MES = BuiltinSchemas.realKeyFor(CanonicalKeys.FECHA_MES) ?: "de"
    private val KEY_ANIO = BuiltinSchemas.realKeyFor(CanonicalKeys.FECHA_ANIO) ?: "año"

    /** Devuelve los valores de fecha a fusionar (solo para claves de fecha vacías). */
    fun values(current: Map<String, String>): Map<String, String> {
        val cal = Calendar.getInstance()
        val dia = cal.get(Calendar.DAY_OF_MONTH).toString()
        val mes = MESES[cal.get(Calendar.MONTH)]
        val anio = cal.get(Calendar.YEAR).toString().takeLast(1)

        val out = LinkedHashMap<String, String>()
        // Los tres campos de fecha, resueltos por canónica.
        if (current[KEY_DIA].isNullOrBlank()) out[KEY_DIA] = dia
        if (current[KEY_MES].isNullOrBlank()) out[KEY_MES] = mes
        if (current[KEY_ANIO].isNullOrBlank()) out[KEY_ANIO] = anio
        return out
    }
}
