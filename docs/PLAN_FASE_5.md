# Plan de la fase 5 — relleno dinámico

> Escrito el 2026-08-31 sobre `0.10.5-etiquetado-enganchado` (versionCode 75), **leyendo el
> código**, no el ROADMAP. Tanda de planificación: no toca ni una línea de Kotlin.
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
| **5·0** | Quitar el `putIfAbsent` incondicional del responsable (2.4). Que se aplique sólo cuando el esquema lo pida (`ValueOrigin.AJUSTES`). | Bajo | `missingFields` deja de mentir con un PDF que no sea de Orange. |
| **5·1** | `FillStep` recibe las secciones como parámetro en vez de tenerlas escritas. Se le siguen pasando las de `CANON`. | Bajo | **La app se comporta idéntica.** Eso es todo lo que hay que comprobar. |
| **5·2** | Validación, hermano del CP, fecha y tipo de identificación pasan a colgar de `canonical` (2.5, 2.6). Fuente todavía `CANON`, que ya tiene su mapeo canónico. | Medio | Orange sigue validando exactamente igual: mismos mensajes, mismos campos en rojo. |
| **5·3** | **La clave.** `fieldValues`/`fieldStates`/`fieldOrigins`/`fieldCandidates`/`UndoEntry` pasan a indexarse por nombre real; `fieldMapping` desaparece de la salida (2.3). Migración de `PersistedWizardState` v1→v2 y de `ContractProfile.campos` del historial y los perfiles exportados. | **Alto** | Sesión guardada con la versión anterior que se restaura sin perder nada; perfil del historial que se aplica bien. **Tanda sola, nada más dentro.** |
| **5·4** | El `FormSchema` del PDF subido alimenta las secciones de verdad, con caída a `CANON` si no hay esquema. | Medio | Un PDF de Aire muestra SUS campos. Aquí ya no hay migración: la clave es la correcta desde 5·3. |
| **5·5** | Tablas con filas dinámicas, catálogo **local** (lleva comisiones: no sale del dispositivo), `cuota total = cantidad × cuota unitaria`. | Alto | Funcionalidad nueva, no refactor. Se planifica al llegar. |

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

## 4. Lo que hay que decidir antes de la 5·3

No son cosas que se puedan resolver leyendo el código; hacen falta decisiones.

1. **Qué pasa con los perfiles del historial ya guardados.** Están indexados por clave canónica de
   Orange. Migrarlos exige saber a qué PDF pertenecían, y `ContractProfile` guarda `fingerprint`,
   así que **se puede**, pero sólo para los que casen con un esquema conocido. Para el resto:
   ¿se dejan como legado de sólo lectura, o se descartan?
2. **Si `fieldMapping` desaparece de la salida, qué pasa con `MappingEditor`**, el editor de mapeo
   del flujo legado Orange/CANON. Con esquemas ya no hace falta, pero borrarlo rompe el camino
   «subo un PDF propio parecido al de Orange» que hoy funciona.
3. **Los datos compartidos del expediente.** `Expediente` y `CanonicalKeys` (0.9.9) existen para
   que un dato extraído una vez sirva a los cuatro PDFs de Aire. Si en 5·3 la clave de los valores
   pasa a ser el nombre real, hay dos niveles: valor canónico del expediente y valor por campo de
   cada formulario. Conviene decidir en cuál vive la verdad **antes** de escribir la migración, no
   después.

---

## 5. Lo que este plan NO resuelve

- **La calidad de las etiquetas de la 0.10.5.** No bloquea la fase 5 (la estructura del esquema
  está verificada contra los cuatro PDFs de Aire y las etiquetas son cosméticas para la lógica de
  relleno), pero conviene haberlo probado en el móvil antes de la 5·4, que es cuando esas
  etiquetas empiezan a ser lo que se ve en pantalla.
- **La fase 6 (`/Sig`)**: 4 campos de firma en el contrato de Aire, 2 en portabilidad. Independiente.
- **La tarea `label_fields` del proxy**: fuera de este repo.
