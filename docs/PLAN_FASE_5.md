# Plan de la fase 5 — relleno dinámico

> Escrito el 2026-08-31 sobre `0.10.5-etiquetado-enganchado` (versionCode 75), **leyendo el
> código**, no el ROADMAP.
>
> **Estado: 5·0 y 5·1 en `0.10.6`, 5·2 en `0.10.7`, 5·2b en `0.10.8` (las tres verdes) y **5·3 en
> `0.10.9`**. La siguiente es la 5·4. Las decisiones de la 5·3 están en §4.**
> Las referencias `fichero:línea` de la sección 2 son de antes del refactor: `FillStep.kt:47` ya no
> tiene las secciones, están en `FillSections.kt`. El resto sigue en pie.
>
> Objetivo de la fase: que `FillStep` se dibuje a partir del `FormSchema` del PDF subido en vez
> de las 6 secciones fijas de `CANON`, y que un formulario de Aire se rellene de punta a punta.

---

## 1. Por qué hay que partirla

La fase 2 se partió en tres tandas y salió bien. La 5 es más grande que la 2:

| Fichero | Líneas |
|---|---|
| `ui/wizard/WizardViewModel.kt` | 1126 |
| `ui/wizard/FillStep.kt` | 670 |
| `ui/wizard/WizardState.kt` | 187 |

Y no es sólo tamaño. **La fase 5 esconde una migración de datos de trabajo reales**, que es el
riesgo que la 0.8.0 ya cobró una vez con el índice de paso. Eso tiene que ir en una tanda sola.

---

## 2. Lo que dice el código (hallazgos, con sitio)

### 2.1 Las secciones están escritas a mano, con nombres de Orange dentro

`FillStep.kt:47` — `private val SECTIONS = listOf(Section("Empresa / Identificación", listOf(
"Nombre  Razón Social", ...)))`. Seis secciones con los nombres literales del AcroForm de Orange,
dobles espacios incluidos. No hay ningún punto de extensión: es una constante de fichero.

### 2.2 Todo el estado del relleno está indexado por clave canónica de `CANON`

`WizardState.kt:131-139` — `fieldValues`, `fieldStates`, `fieldOrigins`, `fieldCandidates`, más
`UndoEntry` (`:178`). Y **se persiste**: `PersistedWizardState.kt:59,66` con `SCHEMA_VERSION = 1`.
También `ContractProfile.campos` (perfiles del historial y los JSON exportados) usa esas claves.

Con esquema dinámico la clave tiene que ser el **nombre real del AcroForm**. Ahí está la
migración: sesión guardada, historial y perfiles exportados.

### 2.3 ✅ Buena noticia: la salida al PDF ya es compatible

`AcroFormFiller.kt:70` — `fun realName(canonical) = fieldMapping[canonical] ?: canonical`. Es
decir, **si los valores vinieran ya indexados por nombre real y `fieldMapping` fuera vacío, el
relleno funciona sin tocar nada**. No hay que construir una capa de traducción: hay que dejar de
necesitarla. Eso quita bastante riesgo del plan original.

### 2.4 ⚠️ El responsable de Orange se inyecta a la fuerza, y hoy miente

`AcroFormFiller.kt:68` — `effective.putIfAbsent(ContractFields.RESPONSABLE_KEY,
ContractFields.RESPONSABLE_VALUE)`, **sin condición ninguna**. Y además `WizardViewModel.kt:495`
ya lo pre-rellena por su cuenta, así que está inyectado dos veces.

Consecuencia con cualquier PDF que no sea el de Orange: ese campo no existe en el AcroForm,
`form.getField()` devuelve null y **acaba siempre en `missingFields`**. O sea que hoy, al rellenar
un PDF de Aire, la app ya informa de un campo que falta y que no debería haber pedido nunca. Es
cosmético, pero es ruido justo en lo que hay que observar durante toda la fase 5.

### 2.5 ⚠️ La validación se apagará EN SILENCIO al llegar Aire

`FieldValidator.kt:11` — `base(fieldName)` normaliza el **nombre del campo** y luego decide por
heurística: `b == "nie"`, `b == "cp"`, `b.contains("iban")`. Funciona con los nombres de Orange
porque se escribió para ellos.

Con los nombres de Aire (`NOMBRE DEL DEUDOR`, `NIF/CIF/NIE`, `CÓDIGO POSTAL`, `Titular`) esas
comparaciones no casan. El resultado no es un error: `validate()` devuelve `null`, que significa
«nada que validar», y la app deja de comprobar dígitos de control, IBAN mod-97 y CP↔provincia
**sin decir nada**. Misma clase de fallo que el desajuste de identificadores de la 0.10.5: no
casa, no aplica, no avisa.

La pieza correcta ya existe: `FormField.canonical` (`FormSchema.kt`, y el mapeo de Orange en
`BuiltinSchemas.CANON_TO_CANONICAL`). La validación tiene que colgar de la clave canónica, no del
nombre.

### 2.6 Tres acoplamientos menores a `CANON`, todos en `FillStep`

- `FillStep.kt:96` — el hermano para validar el CP se calcula con
  `if (key.endsWith("_2")) "Provincia_2" else "Provincia"`: convención de nombres de Orange.
- `FillStep.kt:43` — `FECHA_KEYS = setOf("Fecha", "de", "año")`, las tres claves que se pintan
  como una fila compacta día/mes/año.
- `tipoIdentificacion` es un `String?` global del estado (`WizardState.kt:127`) atado al grupo de
  tres casillas CIF/NIF/NIE de Orange. Con `FieldKind.RADIO` en el esquema (0.10.2) esto
  generaliza a «un grupo de opción cualquiera», pero hoy es un caso especial.

### 2.7 Lo que ya es agnóstico y no hay que tocar

`pendingDecisions()` (`WizardState.kt:186`) trabaja sobre las claves de `fieldStates` sin
interpretarlas. `AutoFillPolicy` y `FieldResolver` operan sobre propuestas, no sobre `SECTIONS`.

---

## 3. El plan

Seis tandas. El orden no es por tamaño: es para que **la migración de datos ocurra mientras el
comportamiento visible sigue siendo el de Orange**, que es la única forma de tener una referencia
contra la que comparar. Si primero se cambia lo que se dibuja y luego la clave, se migra a ciegas
— que es exactamente lo que dolió en la 0.8.0.

| Tanda | Alcance | Riesgo | Cómo se verifica |
|---|---|---|---|
| ~~**5·0**~~ ✅ | Quitar el `putIfAbsent` incondicional del responsable (2.4). *Hecho en `0.10.6`: se inyecta sólo si la plantilla tiene el campo. Colgarlo de `ValueOrigin.AJUSTES` se deja para la 5·4, cuando haya esquema de verdad en el relleno.* | Bajo | `missingFields` deja de mentir con un PDF que no sea de Orange. |
| ~~**5·1**~~ ✅ | `FillStep` recibe las secciones como parámetro. *Hecho en `0.10.6`: `FillSections.kt` con `FillSection` + `canonFillSections()`; `WizardScreen` se las pasa. El denominador del progreso se deriva de las secciones y da el mismo 21.* | Bajo | **La app se comporta idéntica.** Verificado con una prueba contra la lista original transcrita literal. |
| ~~**5·2**~~ ✅ | Validación, hermano del CP, fecha y tipo de identificación pasan a colgar de `canonical` (2.5, 2.6). *Hecha en `0.10.7`.* | Medio | Orange sigue validando exactamente igual: mismos mensajes, mismos campos en rojo. |
| ~~**5·2b**~~ ✅ | Tanda que no estaba en el plan original, abierta al ejecutar la 5·2: los mismos acoplamientos por nombre quedaban en otros cinco sitios (`normVal`, `DateAutofill`, `copyFiscalToComercio`, `keyboardFor`, `coverageKeys`). *Hecho en `0.10.8`.* Va antes de la 5·3 porque manipulan claves de campo: si la 5·3 les cambia la clave por debajo estando aún en literales, se rompen a la vez que la migración y no se sabe cuál fue. | Bajo | Cuatro de cinco son cero-cambio en Orange. `normVal` cambia en 3 campos (bug de espacios, ver `CONTINUIDAD.md` §5). |
| ~~**5·3**~~ ✅ | **La clave.** *Hecha en `0.10.9`.* ⚠️ Al ejecutarla se vio que el diagnóstico de este plan era incompleto: desde la 0.9.3 el prompt ya pide los nombres REALES del PDF cargado (`WizardViewModel:440` manda `userFieldNames`), así que la IA ya devolvía nombres reales y `fieldValues` **ya estaba** indexado por nombre real con un PDF propio. Lo que había no era una clave equivocada sino **contaminación** en cinco puntos que inyectaban claves de Orange (responsable, tres fechas, copia fiscal). Consecuencias: (a) la migración de datos era mucho menor de lo que se temía, y (b) había un **fallo vivo**: `FillStep` leía por clave de Orange lo que estaba guardado por nombre real, así que con un PDF propio el Relleno mostraba vacíos los campos extraídos. | `fieldValues`/`fieldStates`/`fieldOrigins`/`fieldCandidates`/`UndoEntry` pasan a indexarse por nombre real; `fieldMapping` desaparece de la salida (2.3). Migración de `PersistedWizardState` v1→v2 y de `ContractProfile.campos` del historial y los perfiles exportados. | **Alto** | Sesión guardada con la versión anterior que se restaura sin perder nada; perfil del historial que se aplica bien. **Tanda sola, nada más dentro.** |
| **5·4** | El `FormSchema` del PDF subido alimenta las secciones de verdad, con caída a `CANON` si no hay esquema. **Al terminar, en vez de los 21 campos de Orange aparecen TODOS los del PDF subido, agrupados en secciones y en el orden del PDF** — y eso vale para las dos pantallas, mapeo y relleno (ver §6). | Medio | Un PDF de Aire muestra SUS campos, por secciones. Aquí ya no hay migración: la clave es la correcta desde 5·3. |
| **5·5** | Tablas con filas dinámicas, catálogo **local** (lleva comisiones: no sale del dispositivo), `cuota total = cantidad × cuota unitaria`. | Alto | Funcionalidad nueva, no refactor. Se planifica al llegar. **No bloquea el alta**: el alta de Aire sólo usa páginas 1 y 3, que no tienen tabla (ver §6). |

### Por qué 5·3 antes de 5·4

Es la decisión de este plan. En 5·3 la pantalla sigue mostrando los campos de Orange, así que
cualquier cosa que se rompa en la migración se ve comparando con la versión anterior. En 5·4 la
pantalla cambia, y entonces «esto no aparece» puede ser la migración o el esquema, sin manera de
saberlo. Se separan las dos preguntas.

### Por qué 5·0 y 5·2 antes de todo lo demás

Las dos arreglan cosas que **hoy ya están mal** y que, si no se arreglan primero, contaminan la
observación del resto: 5·0 mete un campo falso en `missingFields`, y 5·2 hace que la validación se
apague sin avisar en el momento en que 5·4 traiga los primeros campos de Aire. Ninguna de las dos
cambia comportamiento en Orange, así que son baratas de verificar.

---

## 4. Decisiones para la 5·3 — TOMADAS (2026-08-31, por Pablo)

Eran tres. Ya no bloquean.

1. **Perfiles del historial ya guardados → migración PEREZOSA**, al leerlos, como se hizo con
   `schemas_v1`. `history_*` no se reescribe en bloque.

   *Y resulta más fácil de lo que este plan asumía*: no hay que «saber a qué PDF pertenecían»,
   porque `ContractProfile` **ya guarda su propio `fieldMapping`** además de `campos` y
   `fingerprint` (`ContractProfile.kt:17`). Cada perfil lleva dentro su tabla de traducción:
   - perfil de Orange (`fieldMapping` vacío) → la clave canónica **ya es** el nombre real:
     migración identidad;
   - perfil de un PDF propio → `campos[canónica]` se reindexa con `fieldMapping[canónica] ?: canónica`.

   No queda ningún perfil huérfano. Hará falta una marca de versión en `ContractProfile` para no
   re-migrar lo ya migrado.

2. **`MappingEditor` no se toca en la 5·3.** Sólo se usa con `needsMapping = true`, y el mapeo
   sigue haciendo falta para saber dónde va cada dato extraído. *Rectificación (2026-09-03):
   `MappingEditor` **se conserva** también en la 5·4* — así lo fija el `CONTINUIDAD.md` §4 y el
   roadmap HTML: sigue sirviendo para enlazar campos con su `canonical` cuando el PDF es un
   contrato conocido. Lo que sí cambia en 5·4 es la comprobación de tipo compatible del §6.5.

3. **La verdad del dato vive en el VALOR POR NOMBRE REAL de campo.** `Expediente.compartidos`
   (canónicas) se queda como está —existe, sin usar— y pasa a ser la capa de **propagación** entre
   formularios en la fase de expediente, no la de verdad. Razones:
   - el almacén por nombre real es **obligatorio** (los 400+ campos de Aire sin canónica: tarifas,
     TEKI, casillas, firmas); el canónico es opcional. La verdad va en lo obligatorio;
   - hoy `Expediente.documents` siempre tiene un elemento y no está enganchado al asistente, así
     que el problema de propagación no existe todavía: decidir ahora su regla de conflicto sería
     especular sobre un caso que no se puede probar;
   - **es reversible en un sentido y no en el otro**: de valores por nombre real se puede derivar
     `compartidos` vía `FormField.canonical` y luego invertir el flujo; empezando por la canónica
     ya tendrías dos almacenes y desmontarlo costaría otra migración.

   Señal que lo confirma: en Orange, `Dirección` y `Dirección_2` tienen canónicas **distintas**
   (`direccion`/`direccion_2`) porque son dos datos, no uno compartido. Las canónicas ya se están
   usando como identificador de campo lógico más que de dato único.

---

## 5. Lo que este plan NO resuelve

- **La calidad de las etiquetas de la 0.10.5.** No bloquea la fase 5 (la estructura del esquema
  está verificada contra los cuatro PDFs de Aire y las etiquetas son cosméticas para la lógica de
  relleno), pero conviene haberlo probado en el móvil antes de la 5·4, que es cuando esas
  etiquetas empiezan a ser lo que se ve en pantalla.
- **La fase 6 (`/Sig`)**: 4 campos de firma en el contrato de Aire, 2 en portabilidad. Independiente.
- **La tarea `label_fields` del proxy**: fuera de este repo.

---

## 6. Requisitos acordados para la 5·4 (2026-09-02, por Pablo)

Salen de tres capturas: la pantalla de mapeo actual con el contrato de Aire cargado, y las páginas
1 y 3 de ese contrato.

### 6.1 Secciones en el orden del PDF, en las DOS pantallas

Hoy el mapeo pregunta por los 21 destinos de Orange en una lista plana («Razón social», «Nombre
comercial», «CIF/NIF/NIE de la empresa»…), y el relleno hace lo mismo con sus 6 secciones fijas.
Al cerrar la 5·4:

- **Mapeo** — muestra los campos **del PDF subido**, agrupados por sección y **en el orden en que
  aparecen en el PDF**, no en el orden de `CANON`. Es el orden que `PdfFieldInspector` ya calcula
  (página → fila → columna) y que `FormSchemaBuilder` ya agrupa; sólo hay que dibujarlo.
- **Relleno** — la misma agrupación y el mismo orden. El objetivo declarado es **visual**: que el
  usuario sepa en qué parte del formulario está mientras rellena. Con 481 campos, una lista plana
  es inservible.

Ambas pantallas leen del mismo `FormSchema`, así que la agrupación se define una vez.

### 6.2 Alcance del alta: sólo páginas 1 y 3

Un alta de Aire con **sólo el contrato** rellena únicamente lo que hay en esas dos páginas:

- **Página 1** — cabecera (nombre, teléfono, email y código del DISTRIBUIDOR; `FECHA DE ALTA EN
  TEKI` y `CÓDIGO DE CLIENTE EN TEKI`, ambos `ValueOrigin.PLATAFORMA`) y el bloque DATOS DEL
  CLIENTE: nombre o razón social, NIF/CIF/NIE, domicilio, teléfono, CP, localidad, provincia, fax,
  nombre y NIF del representante, móvil y email del representante, contacto de administración, TIF
  y email de administración.
- **Página 3** — la fecha (`Madrid, dd / mm / aa`, tres campos) y los bloques de firma: CLIENTE
  (firma y sello, nombre y DNI) y COMERCIAL/DISTRIBUIDOR (firma, nombre y DNI). El bloque de AIRE
  NETWORKS viene ya firmado y sellado en el PDF: **no se toca**.

La página 2 (productos y servicios, con sus tablas) queda fuera del alta, y por eso la 5·5 no
bloquea. Cuando entre, entra por ahí.

### 6.3 Casillas de la cabecera: marcar ALTA y **desmarcar** las otras dos

La cabecera tiene tres casillas de CLIENTE: `ALTA NUEVA`, `MODIFICACIÓN`, `PORTABILIDAD`
(`Casilla de verificación 56`/`57`/`58` en el AcroForm). Comprobado sobre
`Contrato_empresas.pdf` con `pypdf` al implementar la 5·4: **las tres vienen del PDF marcadas
de fábrica** con `/V = /Sí`. La redacción anterior de esta sección — «se marca ALTA NUEVA y nada
más» — dejaba caso abierto qué hacer con las otras dos. La respuesta correcta es **desmarcarlas
explícitamente**: la 5·4 emite `ALTA NUEVA=On`, `MODIFICACIÓN=Off`, `PORTABILIDAD=Off`. Si no,
un alta salta con dos casillas marcadas y no vale como alta.

Los otros casos (modificación, portabilidad con o sin cambio de titularidad, servicios,
centralita) llegarán cuando se aporten los PDFs correspondientes; de momento **no se implementa
esa lógica**, sólo el alta.

### 6.4 Más adelante, no ahora

Si el usuario aporta en el paso de Documentación un PDF de servicios, centralita o portabilidad, la
IA debería **reconocer los campos ya rellenos de ese PDF** y trasladarlos. Queda anotado y fuera de
la 5·4.

### 6.5 Un fallo que se ve en la captura del mapeo

El auto-mapeo por similitud asignó **`Fecha · mes` → `Casilla de verificación 56`**: un checkbox
como destino de un campo de texto. Y encima esa casilla es la de ALTA NUEVA (ver §6.3), así que
el mapeo estaba escribiendo un mes dentro del alta. También dejó `Fecha · día` sin asignar
mientras la página 3 tiene tres campos de fecha claros.

Se arregla en el mapeo actual —conservado por decisión del `CONTINUIDAD.md` §4 y del roadmap
HTML— aplicando la regla obvia: **el destino tiene que ser de un `FieldKind` compatible con el
origen**. Un texto nunca puede mapear a una casilla. Implementado en `MappingEditor` filtrando
la lista de opciones por el `FieldKind` esperado de cada canónica, leído de `activeSchema`. Con
esquema activo, ese fallo desaparece; sin él (sesión previa a la 5·4) el editor se comporta
como antes, no se pierde información.

Segunda regla, salida al inspeccionar el propio PDF con `pypdf`: **el flag `isRadio` del
AcroForm miente**. En el contrato de Aire trece campos vienen con ese flag, pero doce son de
un solo widget con un único estado — o sea casillas sueltas con el flag mal puesto. El único
radio de verdad es `Botón de opción 10`, la fila de RED INTELIGENTE (6 widgets, estados
`/0`..`/5`). `FormSchemaBuilder` promociona los doce falsos a `CHECKBOX` mirando el grupo
completo (número de widgets y de estados distintos). Sin esa promoción, la comprobación de
tipo compatible de arriba rechazaría doce asignaciones legítimas.
