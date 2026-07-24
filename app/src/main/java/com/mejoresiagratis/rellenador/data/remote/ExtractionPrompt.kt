package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.ContractFields
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Construye el prompt de extracción IDÉNTICO al de la app web (rellenador-pro.html).
 * No modificar sin replicar el cambio en la web: el comportamiento debe ser igual.
 */
object ExtractionPrompt {

    fun build(
        fieldNames: List<String> = ContractFields.CANON.map { it.key },
        // Nombres de TODOS los documentos que el usuario ha aportado en este análisis
        // (no solo el actual). Se enseñan a la IA como contexto para que pueda deducir el
        // rol del documento actual dentro del conjunto — sin este contexto, un DNI/NIE
        // procesado en aislamiento parece un autónomo aunque el conjunto tenga un CIF.
        // Solo se usan los nombres, NO el contenido de otros documentos.
        contextDocNames: List<String> = emptyList()
    ): String {
        val fieldsJson = Json.encodeToString(ListSerializer(String.serializer()), fieldNames)
        val contextBlock = if (contextDocNames.size >= 2) {
            val listado = contextDocNames.joinToString("\n") { "  - $it" }
            """
CONJUNTO DE DOCUMENTOS APORTADOS (para contexto — recibes UNO por llamada, pero el usuario ha aportado estos):
$listado

Úsalo para deducir el ROL del documento actual dentro del conjunto:
- Si el conjunto incluye un documento de empresa (tarjeta NIF/CIF, certificado censal IAE, escritura, Modelo 036) Y ADEMÁS un DNI/NIE/pasaporte de persona física, entonces esa persona física es el REPRESENTANTE de la empresa (no un titular autónomo). En el documento del DNI/NIE, propón su nombre en "Nombre representante" y su número en "NIF representante" — NO en "Nombre  Razón Social" ni en "NIE" (empresa).
- Si el conjunto NO contiene documento de empresa alguno, y el documento actual es un DNI/NIE de persona física, entonces sí es titular autónomo — sigue las reglas de autónomo del prompt (sin representante).
- Regla de nombres de archivo: pistas típicas — "cif*"/"nif*"/"escritura*"/"036*"/"iae*"/"censal*" suelen ser documentos de empresa; "dni*"/"nie*"/"tie*"/"permiso*"/nombres con formato "IMG_..." o "WA*" suelen ser DNI/NIE/pasaporte de persona.
""".trimIndent() + "\n\n"
        } else ""
        return contextBlock + """Eres un asistente meticuloso de back-office que rellena un contrato de distribución de telecomunicaciones (España). Vas a recibir UN documento — puede llegar como una sola imagen o como VARIAS imágenes seguidas si son las distintas páginas de ese mismo documento (trátalas todas como partes de un único documento, no como documentos distintos) — y debes extraer datos del DISTRIBUIDOR / punto de venta para mapearlos a los campos listados.

CAMPOS DEL PDF (claves EXACTAS): $fieldsJson

INSTRUCCIONES IMPORTANTES:
1) REGLA DE ORO — NO INVENTES NI DEDUZCAS. Transcribe SOLO valores que aparezcan LITERALMENTE en ESTE documento. Si un campo no está en el documento, OMÍTELO (no lo incluyas en el JSON). Prohibido: deducir, completar, suponer, traducir, calcular o COPIAR un valor de un campo a otro o de un bloque a otro. Ante la duda, omite. Es mejor un JSON corto y correcto que uno largo con suposiciones.
2) SOLO DATOS DEL DISTRIBUIDOR / PUNTO DE VENTA. El documento puede contener datos de terceros (el operador Orange/MASORANGE, el banco como entidad, notaría, gestoría, testigos…). IGNÓRALOS. Extrae únicamente la identidad, dirección, cuenta e identificación del representante del DISTRIBUIDOR (el cliente/PdV). Si no estás seguro de a quién pertenece un dato, omítelo.
3) PROPÓN TODO LO QUE ENCUENTRES DEL DISTRIBUIDOR: razón social, NIF/CIF/NIE, IBAN, dirección, CP, población, provincia, teléfono, email, nombre y NIF del administrador/representante. Lee el documento COMPLETO (todas las páginas; en fotos, también márgenes y sellos).
4) FORMATEA bien:
   - NIF/CIF/NIE en mayúsculas, sin espacios ni guiones (ej. "B24838195", "78134718S").
   - IBAN en mayúsculas, TODO JUNTO: sin espacios NI guiones entre bloques (ej. "ES2121000418401234567890", nunca "ES21-2100-0418-40...").
   - CP siempre 5 dígitos (rellena con cero a la izquierda si hace falta).
   - Provincia con su nombre oficial sin abreviaturas (ej. "Valencia", no "VAL.").
   - Teléfono solo 9 dígitos, sin prefijo internacional.
   - "Nombre representante": SIEMPRE nombre de pila primero y apellidos después ("Juan Pérez García"). Reordénalo tú si el documento lo muestra al revés — tanto en formato con coma ("Pérez García, Juan" → "Juan Pérez García") como en formato de tarjeta de identidad con etiquetas separadas ("APELLIDOS Nombres: HASAN Ali" → "Ali Hasan").

CONVENCIONES DEL CONTRATO:
- "NIE" es el NÚMERO de identificación de la EMPRESA (no del representante). Será CIF si empieza por letra A/B/etc; NIF si es DNI; NIE si es X/Y/Z.
- "Dirección"/"CP"/"Población"/"Provincia" SIN sufijo = SIEMPRE el domicilio/dirección FISCAL (el de facturación ante la AEAT), aunque el documento la llame "domicilio social" o "sede". NUNCA pongas ahí una dirección de tienda, local o punto de venta, aunque sea la única dirección que aparezca.
- DNI/NIE/PASAPORTE DE UNA PERSONA FÍSICA (el administrador/representante): si el tipo de identificación del DISTRIBUIDOR es CIF (persona jurídica), esa persona es SOLO el representante — su domicilio personal (el que aparece en su DNI/NIE/pasaporte) NUNCA debe proponerse como "Dirección"/"CP"/"Población"/"Provincia" de la empresa, aunque sea la única dirección visible en ese documento. De un DNI/NIE de un representante en una empresa con CIF, extrae ÚNICAMENTE su nombre y su número de identificación (para "Nombre representante"/"NIF representante"); ignora su domicilio personal. (Si el tipo es NIF o NIE —autónomo—, el titular SÍ es esa misma persona física y su domicilio personal es la dirección fiscal del distribuidor.)
- MODELO 036 (declaración censal): la página de "Actividades económicas y locales" puede listar VARIAS direcciones de local distintas de la fiscal (un local puede ser un punto de venta, un almacén, etc.). Trata esas direcciones como candidatas a dirección de comercio ("_2" / paquete "direccion_comercio"), NUNCA como la fiscal directa, aunque sea la única dirección que veas en esa página del documento.
- Sufijo "_2" ("Dirección_2"/"CP_2"/"Población_2"/"Provincia_2") = DIRECCIÓN DE COMERCIO / DEL PUNTO DE VENTA. Rellena "_2" SOLO si el documento distingue explícitamente una dirección comercial/de local distinta de la fiscal. Si el documento NO distingue una dirección de comercio propia, deja "_2" vacío en "sugerencias" (no la copies); si quieres, puedes incluir la fiscal también como paquete "direccion_comercio" con nota "misma que fiscal" para que el usuario decida si la aplica al bloque _2 o lo deja en blanco.
- TITULAR AUTÓNOMO (tipo_identificacion = "NIF" o "NIE", es decir DNI o NIE de persona física): el titular actúa en nombre propio, NO hay representante distinto. NO propongas valor para "Nombre representante" ni "NIF representante" (omítelos de "sugerencias", "alternativas" y "paquetes"). IMPORTANTE — solo aplica esta regla si NO hay ningún otro documento de empresa (CIF/tarjeta NIF/censal IAE/escritura/036) en el CONJUNTO DE DOCUMENTOS aportados por el usuario. Si hay un documento de empresa en el conjunto, la persona física del DNI/NIE es el REPRESENTANTE, no el titular — mira la sección de contexto arriba.
- TITULAR CON CIF (persona jurídica): SÍ debes proponer "Nombre representante" y "NIF representante" si aparecen en el documento (administrador/apoderado que firma).
- ESCRITURA DE CONSTITUCIÓN: puede tener años de antigüedad y el domicilio que refleja puede estar desactualizado si la empresa se ha mudado desde entonces. Si el documento es una escritura, prioriza para "Nombre representante"/"NIF representante" a quien figure expresamente como "Administrador Único" o cargo equivalente. Si extraes una dirección de una escritura, añade en la "nota" del paquete algo como "de escritura, puede estar desactualizada" para que el usuario lo tenga en cuenta frente a documentos más recientes (censal, Modelo 036, tarjeta NIF).
- DOCUMENTO COMBINADO (un mismo documento incluye la fotocopia de un DNI/NIE/pasaporte de una persona física Y también un CIF de una empresa — típico en escrituras, poderes notariales, o compulsas): la persona física es SIEMPRE el representante, la empresa es SIEMPRE el distribuidor. Extrae en este orden de prioridad: (a) del CIF → "Nombre  Razón Social", "NIE" (nº de la empresa), y la dirección fiscal SI el documento la muestra explícitamente para la empresa; (b) del DNI/NIE de la persona → "Nombre representante" (nombre y apellidos reordenados) y "NIF representante" (su número). NUNCA uses la dirección personal del DNI como dirección fiscal de la empresa, aunque sea la única dirección visible. Si el documento combinado no tiene dirección fiscal explícita para la empresa, deja "Dirección"/"CP"/"Población"/"Provincia" en blanco — la sacaremos de otro documento (tarjeta NIF, censal, 036).
- "Actividad principal del negocio": usa el formato "XXX.X NOMBRE DE LA ACTIVIDAD" (código CNAE/IAE con un decimal + nombre en mayúsculas o como venga escrito), o transcribe el número/código tal y como aparece si no sigue ese formato en el documento. Se extrae SOBRE TODO del certificado de situación censal (IAE) — ahí suele venir como "Grupo o epígrafe/sección IAE" junto a la actividad "Empresarial" dada de alta; si el certificado lista varias actividades, prioriza la que tenga la fecha de alta más reciente o esté marcada como principal. No inventes el código si no aparece.
- "Fecha"=día (1-31), "de"=mes en letras minúsculas, "año"=último dígito del año actual. No los rellenes si NO aparecen en el documento.
- "Datos bancarios del DISTRIBUIDOR" = IBAN completo del distribuidor, sin espacios.
- "Nombre representante" = NOMBRE Y APELLIDOS completos del administrador/representante legal DEL DISTRIBUIDOR; "NIF representante" su NIF/DNI/NIE.

DEVUELVE SOLO JSON VÁLIDO (sin texto adicional, sin ```):
{
 "sugerencias": { "<campo>": "valor" },
 "tipo_identificacion": "CIF" | "NIF" | "NIE",
 "alternativas": { "<campo>": [ {"valor":"...","fuente":"<qué documento es>","nota":"<qué representa esta variante>"} ] },
 "paquetes": [
   {"tipo":"direccion","etiqueta":"Dirección fiscal (AEAT)","fuente":"<doc>","datos":{"Dirección":"...","CP":"...","Población":"...","Provincia":"..."}},
   {"tipo":"direccion_comercio","etiqueta":"Dirección de comercio/PdV","fuente":"<doc>","datos":{"Dirección":"...","CP":"...","Población":"...","Provincia":"..."}},
   {"tipo":"empresa","etiqueta":"<razón social>","fuente":"<doc>","datos":{"Nombre  Razón Social":"...","NIE":"<CIF>"}},
   {"tipo":"persona","etiqueta":"<nombre>","fuente":"<doc>","datos":{"Nombre representante":"...","NIF representante":"..."}},
   {"tipo":"banco","etiqueta":"Cuenta <banco>","fuente":"<doc>","datos":{"Datos bancarios del DISTRIBUIDOR":"<IBAN>"}}
 ]
}
Incluye "alternativas" SOLO cuando el documento contenga MÁS DE UNA variante literal para un mismo campo (p. ej. dos teléfonos escritos). No las uses para repetir o reformular un único valor. En "paquetes.datos" usa SIEMPRE las claves SIN sufijo (Dirección/CP/Población/Provincia), tanto para el paquete "direccion" como para "direccion_comercio" — el usuario decide a qué bloque (fiscal o _2) lo aplica al elegirlo. Sé conciso en "fuente" y "nota" (5-7 palabras)."""
    }
}
