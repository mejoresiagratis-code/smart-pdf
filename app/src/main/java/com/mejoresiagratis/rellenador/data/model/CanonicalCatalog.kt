package com.mejoresiagratis.rellenador.data.model

/**
 * Catálogo de las claves canónicas transversales ([CanonicalKeys]) para poder **ofrecerlas** en la
 * interfaz, y una propuesta automática a partir de la etiqueta ya conocida. Tanda 5·4f.
 *
 * Hasta aquí `CanonicalKeys` era una lista de constantes que sólo se usaba desde código: nadie
 * podía enumerarlas para pintar un selector, ni traducirlas a algo legible. El editor de etiquetas
 * necesita las dos cosas.
 *
 * La propuesta ([proposeFor]) es deliberadamente **local y conservadora**, no una llamada a la IA:
 * la etiqueta ya la leyó la visión en la fase 3, así que aquí sólo hace falta reconocer texto en
 * español. Local significa gratis, instantáneo y verificable con pruebas ejecutables — y sobre
 * todo, sin volver a mandar nada del PDF a ninguna parte.
 *
 * Es una **propuesta**: se ofrece marcada en el selector y el usuario la confirma o la cambia. No
 * se aplica sola, porque una canónica equivocada es peor que ninguna — mete el dato del cliente en
 * el hueco de otro y el PDF sale mal sin que salte nada.
 */
object CanonicalCatalog {

    /** Entrada del catálogo: la clave, su nombre legible y a qué bloque pertenece. */
    data class Entry(
        val key: String,
        val label: String,
        /** Agrupación para el selector; no tiene efecto en el dato. */
        val group: String,
    )

    val ALL: List<Entry> = listOf(
        Entry(CanonicalKeys.RAZON_SOCIAL, "Nombre / Razón social", "Empresa"),
        Entry(CanonicalKeys.NOMBRE_COMERCIAL, "Nombre comercial", "Empresa"),
        Entry(CanonicalKeys.IDENTIFICACION, "CIF / NIF / NIE de la empresa", "Empresa"),
        Entry(CanonicalKeys.TIPO_IDENTIFICACION, "Tipo de identificación", "Empresa"),
        Entry(CanonicalKeys.ACTIVIDAD, "Actividad", "Empresa"),

        Entry(CanonicalKeys.REPRESENTANTE_NOMBRE, "Nombre del representante", "Representante"),
        Entry(CanonicalKeys.REPRESENTANTE_NIF, "NIF del representante", "Representante"),
        Entry(CanonicalKeys.REPRESENTANTE_MOVIL, "Móvil del representante", "Representante"),
        Entry(CanonicalKeys.REPRESENTANTE_EMAIL, "Email del representante", "Representante"),

        Entry(CanonicalKeys.DIRECCION, "Dirección", "Domicilio fiscal"),
        Entry(CanonicalKeys.CP, "Código postal", "Domicilio fiscal"),
        Entry(CanonicalKeys.POBLACION, "Población", "Domicilio fiscal"),
        Entry(CanonicalKeys.PROVINCIA, "Provincia", "Domicilio fiscal"),

        Entry(CanonicalKeys.DIRECCION_2, "Dirección (instalación)", "Domicilio de instalación"),
        Entry(CanonicalKeys.CP_2, "Código postal (instalación)", "Domicilio de instalación"),
        Entry(CanonicalKeys.POBLACION_2, "Población (instalación)", "Domicilio de instalación"),
        Entry(CanonicalKeys.PROVINCIA_2, "Provincia (instalación)", "Domicilio de instalación"),

        Entry(CanonicalKeys.TELEFONO, "Teléfono", "Contacto"),
        Entry(CanonicalKeys.EMAIL_COMERCIAL, "Email comercial", "Contacto"),
        Entry(CanonicalKeys.EMAIL_FACTURACION, "Email de facturación", "Contacto"),

        Entry(CanonicalKeys.IBAN, "IBAN", "Banco"),
        Entry(CanonicalKeys.BIC, "BIC / SWIFT", "Banco"),

        Entry(CanonicalKeys.FECHA_DIA, "Fecha — día", "Fecha"),
        Entry(CanonicalKeys.FECHA_MES, "Fecha — mes", "Fecha"),
        Entry(CanonicalKeys.FECHA_ANIO, "Fecha — año", "Fecha"),
    )

    private val BY_KEY: Map<String, Entry> = ALL.associateBy { it.key }

    /** Nombre legible de una canónica, o la clave tal cual si no está en el catálogo. */
    fun labelFor(key: String): String = BY_KEY[key]?.label ?: key

    /**
     * Normaliza para comparar: minúsculas, sin tildes, sin signos y con los espacios colapsados.
     *
     * Sin quitar tildes no casaría ni «dirección», que es justo el caso más común en un contrato
     * español. Los dos puntos finales también sobran: la visión devuelve el rótulo impreso tal
     * cual, y en un formulario eso es casi siempre «CP:».
     */
    internal fun normalize(text: String): String {
        val lower = text.lowercase().trim()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            val plain = when (ch) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                'ç' -> 'c'
                else -> ch
            }
            when {
                plain.isLetterOrDigit() -> sb.append(plain)
                plain == ' ' || plain == '/' || plain == '-' || plain == '_' -> sb.append(' ')
                // Los demás signos (:, ., ª, paréntesis…) se descartan.
            }
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Canónica que mejor encaja con una etiqueta, o `null` si no hay nada razonable.
     *
     * El orden de las reglas importa y va **de lo más específico a lo más general**: «NIF del
     * representante» tiene que caer en [CanonicalKeys.REPRESENTANTE_NIF] y no en
     * [CanonicalKeys.IDENTIFICACION] sólo porque contenga «nif». Por eso todo lo que menciona al
     * representante se resuelve antes que nada.
     *
     * Lo mismo con el domicilio de instalación: un contrato tiene dos direcciones y confundirlas
     * es un error que no se ve hasta que llega el instalador a la dirección equivocada.
     *
     * Ante la duda, `null`. Preferir no proponer a proponer mal.
     */
    fun proposeFor(label: String): String? {
        val t = normalize(label)
        if (t.isEmpty()) return null

        val representante = listOf("representante", "apoderado", "firmante", "titular")
            .any { t.contains(it) }
        // «instalación», «suministro», «servicio», «entrega»: la segunda dirección del contrato.
        val instalacion = listOf("instalacion", "suministro", "entrega", "servicio")
            .any { t.contains(it) }

        return when {
            representante && (t.contains("nif") || t.contains("dni") || t.contains("nie")) ->
                CanonicalKeys.REPRESENTANTE_NIF
            representante && (t.contains("movil") || t.contains("telefono")) ->
                CanonicalKeys.REPRESENTANTE_MOVIL
            representante && (t.contains("email") || t.contains("correo") || t.contains("mail")) ->
                CanonicalKeys.REPRESENTANTE_EMAIL
            representante && (t.contains("nombre") || t.contains("apellido")) ->
                CanonicalKeys.REPRESENTANTE_NOMBRE

            t.contains("iban") || t.contains("cuenta") || t.contains("cc c") ->
                CanonicalKeys.IBAN
            t.contains("bic") || t.contains("swift") -> CanonicalKeys.BIC

            t == "cp" || t.contains("codigo postal") || t.startsWith("cp ") ->
                if (instalacion) CanonicalKeys.CP_2 else CanonicalKeys.CP
            t.contains("provincia") ->
                if (instalacion) CanonicalKeys.PROVINCIA_2 else CanonicalKeys.PROVINCIA
            t.contains("poblacion") || t.contains("localidad") || t.contains("municipio") ->
                if (instalacion) CanonicalKeys.POBLACION_2 else CanonicalKeys.POBLACION
            t.contains("direccion") || t.contains("domicilio") || t.contains("calle") ->
                if (instalacion) CanonicalKeys.DIRECCION_2 else CanonicalKeys.DIRECCION

            t.contains("email facturacion") || t.contains("correo facturacion") ->
                CanonicalKeys.EMAIL_FACTURACION
            t.contains("email") || t.contains("correo") || t.contains("mail") ->
                CanonicalKeys.EMAIL_COMERCIAL
            t.contains("telefono") || t.contains("movil") || t.contains("tlf") ->
                CanonicalKeys.TELEFONO

            t.contains("razon social") -> CanonicalKeys.RAZON_SOCIAL
            t.contains("nombre comercial") -> CanonicalKeys.NOMBRE_COMERCIAL
            t.contains("actividad") || t.contains("cnae") -> CanonicalKeys.ACTIVIDAD
            t.contains("cif") || t.contains("nif") || t.contains("nie") || t.contains("dni") ->
                CanonicalKeys.IDENTIFICACION
            // «Nombre» a secas, ya descartado el representante, es la razón social de la empresa.
            t == "nombre" || t.startsWith("nombre ") -> CanonicalKeys.RAZON_SOCIAL

            else -> null
        }
    }
}
