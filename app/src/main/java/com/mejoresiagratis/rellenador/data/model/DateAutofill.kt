package com.mejoresiagratis.rellenador.data.model

import java.util.Calendar

/**
 * Autorrelleno de fecha actual, fiel a autoFillDates() de la web:
 * Fecha = día, de = mes en letras (español), año = ÚLTIMO dígito del año.
 * Solo rellena los campos de fecha que estén vacíos.
 */
object DateAutofill {
    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    /** Devuelve los valores de fecha a fusionar (solo para claves de fecha vacías). */
    fun values(current: Map<String, String>): Map<String, String> {
        val cal = Calendar.getInstance()
        val dia = cal.get(Calendar.DAY_OF_MONTH).toString()
        val mes = MESES[cal.get(Calendar.MONTH)]
        val anio = cal.get(Calendar.YEAR).toString().takeLast(1)

        val out = LinkedHashMap<String, String>()
        // Los tres campos de fecha canónicos.
        if (current["Fecha"].isNullOrBlank()) out["Fecha"] = dia
        if (current["de"].isNullOrBlank()) out["de"] = mes
        if (current["año"].isNullOrBlank()) out["año"] = anio
        return out
    }
}
