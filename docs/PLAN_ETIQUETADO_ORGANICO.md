# Plan de la tanda 5·4b — etiquetado orgánico del mapeo y el relleno

> **Qué es esto.** El plan de la tanda que va **después** de la 5·4 (`0.10.10`) y **antes** de la
> 5·5. La 5·4 consiguió que las secciones salgan del `FormSchema` del PDF subido; esta tanda
> consigue que esas secciones y esos campos **se llamen como en el papel**, que es lo que hace
> que el mapeo se pueda usar con 481 campos delante.
>
> Escrito el 2026-09-03 midiendo sobre `Contrato_empresas.pdf` con `pypdf` y `pdfplumber`, no
> leyendo capturas. Todos los números de aquí son reproducibles.

---

## 1. El problema, con el `FormSchema` real delante

Simulado el algoritmo de `FormSchemaBuilder` (mismas constantes: `ROW_TOLERANCE=6`,
`X_TOLERANCE=3`, `MIN_ROWS_FOR_COLUMN=4`, `MIN_COLS_FOR_ROW=3`, `MIN_ROWS_FOR_TABLE=2`) sobre el
contrato de empresas, salen **ocho secciones**:

```
[TABLE ] Tabla 1    pág 1   13×7
[TABLE ] Tabla 2    pág 1   13×7
[SIMPLE] Página 1   pág 1   35 campos
[TABLE ] Tabla 3    pág 2    9×8
[TABLE ] Tabla 4    pág 2   10×7
[SIMPLE] Página 2   pág 2   34 campos
[TABLE ] Tabla 5    pág 3   10×8
[SIMPLE] Página 3   pág 3   24 campos
```

Dos cosas están mal, y la segunda es un fallo de la 5·4:

**Los títulos no dicen nada.** «Página 1» y «Tabla 1» no le dicen al usuario en qué parte del
formulario está, que es el objetivo declarado del §6.1 de `PLAN_FASE_5.md`. Y dentro de
«Página 1» conviven la cabecera del DISTRIBUIDOR, los 15 campos de DATOS DEL CLIENTE, la casilla
de sólo-tráfico-nacional y los tres radios del tipo de centralita: cuatro bloques distintos del
papel en una lista plana de 35.

**El orden está mal.** `flushLooseBefore(pageOfNext)` sólo vuelca los sueltos de páginas
*anteriores* a la de la tabla, así que los sueltos de la página en curso salen **siempre detrás
de todas las tablas de esa página**. Resultado: DATOS DEL CLIENTE, que está arriba del todo
(y≈736 pt), aparece en tercera posición, después de las dos tablas que están debajo (y≈562 y
y≈232). Es exactamente lo contrario de «en el orden en que aparecen en el PDF».

---

## 2. Lo que hay en el PDF que hoy no se está mirando

488 widgets, 481 nombres únicos, 3 páginas. Desglose por tipo:

| | pág 1 | pág 2 | pág 3 |
|---|---|---|---|
| `/Tx` | 201 | 157 | 47 |
| `/Btn` | 10 | 11 | 58 |
| `/Sig` | 0 | 1 | 3 |

**252 de 488 widgets (52%) tienen nombre autogenerado** (`Campo de texto 116`,
`Casilla de verificación 56`, `Botón de opción 5`). Confirma el §3.3 y el §6.2 del
`ANALISIS_FORMULARIOS_AIRE.md`: el nombre técnico no es fuente de etiqueta.

### 2.1 Las 12 «casillas disfrazadas de radio» son cabeceras de banda

La 5·4 las promociona a `CHECKBOX` porque tienen un widget y un estado, y hace bien. Pero no es
que el flag esté mal puesto: **son el interruptor de cada bloque del formulario**.

| Campo | Banda que encabeza | Pág |
|---|---|---|
| `Botón de opción 5` | TELEFONÍA FIJA SERVICIOS DE VOZ | 1 |
| `Botón de opción 6` | CENTRALITA VIRTUAL | 1 |
| `Botón de opción 7` | AIRE CONNECT | 2 |
| `Botón de opción 14` | CAPTURA DE FIBRA CON CAMBIO DE TITULARIDAD | 2 |
| `Botón de opción 8` | PRODUCTOS CLOUD | 2 |
| `Botón de opción 9` | RED INTELIGENTE | 2 |
| `Botón de opción 11` | PORTABILIDAD TELEFONÍA FIJA | 3 |
| `Botón de opción 12` | CAMBIO TITULAR | 3 |

`Botón de opción 10` (RED INTELIGENTE, 6 widgets, estados `/0`..`/5`) es el único radio de
verdad, como ya dice el `CHANGELOG` de la 0.10.10.

### 2.2 Tres pulsadores y cuatro firmas entran hoy como texto

`PdfFieldInspector.inspect()` recorre `form.fieldTree` y marca `isCheckbox`/`isRadio`; todo lo
demás cae en el `else -> FieldKind.TEXT` de `FormSchemaBuilder.toField()`. Eso mete en el
esquema, como campos de texto rellenables:

- **3 pulsadores** — `Botón 2` (pág 1), `Botón 3` (pág 2), `Botón 4` (pág 3), los tres con
  `Ff = 65536` (bit 17, *pushbutton*). Son los enlaces «descargar aquí». No tienen valor.
- **4 campos `/Sig`** — `Signature1` (cliente), `Signature2` (comercial), `Signature3` (titular
  de línea en portabilidad), `Signature4` (titular donante de fibra). Ya inventariados en el
  §3.10 del análisis, pero hoy el usuario puede **escribir texto dentro de ellos**.

Siete campos fantasma en el mapeo y en el relleno.

### 2.3 Defectos de origen nuevos (van al §6 del análisis)

- `email conectividad` **está en dos widgets visualmente distintos** de la página 2: el «Email»
  de la dirección de instalación (y≈536,8) y el «Email para recibir los informes de Zentinela»
  (y≈522,8). Mismo nombre de campo: rellenar uno rellena el otro.
- `PDC cuotalta 08` **está dos veces** (y≈181,8 y y≈166,7) y la numeración de la tabla PRODUCTOS
  CLOUD está descuadrada: `PDC cantidad` va 01–06, **08**, 09, 10 (sin 07). Cualquier heurística
  que empareje celdas por el sufijo del nombre se equivoca de fila. La agrupación por geometría
  del builder no se ve afectada, que es justo lo que se quería.
- **Cuatro pares de casillas superpuestas** en la columna CENTRAL VIRTUAL de la tabla de
  portabilidad (pág 3, x = 465,6): `40`/`44`, `41`/`45`, `42`/`46`, `43`/`47`. Mismo defecto que
  las filas 07/08 de `Conectividad.pdf` (§6.1).
- **El orden de los nombres no es el visual**: la fila 5 de esa misma tabla va
  `26, 25, 24, 23, 22` de izquierda a derecha.

---

## 3. La propuesta: tres pasadas, y la IA la última

Hoy `label = f.name` y la única alternativa es `VisionLabelPass` sobre 481 campos. Entre las dos
hay una pasada geométrica que es determinista, gratis y resuelve la mayoría.

Pieza nueva: **`LayoutTextExtractor`** — texto del PDF con posiciones y tamaño de fuente.
`pdfbox-android` lo da con `PDFTextStripper` sobrescribiendo
`writeString(String, List<TextPosition>)`. **Verificar en el fuente real de
`pdfbox-android 2.0.27.0` antes de subir** (regla de `CONTINUIDAD.md` §6: dos builds se rompieron
por métodos inventados de memoria).

### 3.1 Los títulos de sección salen del texto del PDF

Una línea de texto es **ancla de sección** si mide ≥ 8 pt, arranca en el margen izquierdo
(x < 150 pt) y va en mayúsculas, **o** tiene una casilla suelta pegada a su izquierda
(hueco < 25 pt, centros verticales a < 12 pt). Sobre el contrato de empresas eso da 14 anclas y
son las buenas:

```
p1  DATOS DEL CLIENTE
p1  PRODUCTOS Y SERVICIOS CONTRATADOS
p1  TELEFONÍA FIJA SERVICIOS DE VOZ        ← casilla: Botón de opción 5
p1  CENTRALITA VIRTUAL                     ← casilla: Botón de opción 6
p2  AIRE CONNECT                           ← casilla: Botón de opción 7
p2  CAPTURA DE FIBRA CON CAMBIO DE TITULARIDAD
p2  PRODUCTOS CLOUD                        ← casilla: Botón de opción 8
p2  RED INTELIGENTE                        ← casilla: Botón de opción 9
p3  PORTABILIDAD TELEFONÍA FIJA            ← casilla: Botón de opción 11
p3  CAMBIO TITULAR                         ← casilla: Botón de opción 12
p3  Resumen de todos los servicios contratados
```

(Las tres restantes son la etiqueta `DOCUMENTACIÓN` que se repite una vez por página; se filtran
por lista negra junto al pie legal y las cabeceras repetidas de página.)

Consecuencias en el modelo:

- `FormSection.title` deja de ser «Tabla 3» y pasa a ser «AIRE CONNECT».
- `FormSection` gana un **`enablerField: String?`** — la casilla de la banda deja de ser un campo
  suelto perdido y pasa a ser el interruptor de su sección.
- La sección se define por el **intervalo entre dos anclas**, así que las tablas caen dentro de
  su sección en vez de flotar, y **el bug de orden del §1 desaparece de camino**: ya no hay
  «sueltos de la página» que volcar al final.

### 3.2 Etiqueta geométrica antes que IA, para los campos sueltos

La etiqueta es el grupo de palabras a la izquierda del campo dentro de su banda vertical,
**acotado por el borde derecho del widget anterior de la misma fila**. Ese acotado es lo que
evita que `Localidad` se lleve el «CP:» del campo de al lado. Si no hay nada a la izquierda, la
línea de encima que solape en x.

Resultado sobre los 15 campos de DATOS DEL CLIENTE, **sin una sola llamada a la IA** (15 de 15):

```
Nombre o razón social      → NOMBRE O RAZÓN SOCIAL
NIF/CIF/NIE                → NIF/CIF/NIE
Domicilio                  → Domicilio
Telefóno                   → Tfno.
CP / Localidad / Provincia → CP / Localidad / Provincia
Fax                        → Fax
NOmbre representante       → Nombre Representante
NIF                        → NIF
Móvil representante        → Móvil Representante
Email representante        → E-mail Representante
Contacto Administracion    → Contacto Administración
TIF                        → TIF
email administracion       → E-mail Administración
```

Y en la cabecera, donde los nombres son autogenerados y hoy no hay nada que enseñar:
`Campo de texto 18 → NOMBRE`, `19 → TFNO.`, `20 → EMAIL DISTRIBUIDOR`,
`17 → FECHA DE ALTA EN TEKI`, `16 → CÓDIGO DE CLIENTE EN TEKI`.

**Cobertura medida: 67 de los 90 campos sueltos (74%) quedan bien etiquetados con cero IA.**

### 3.3 Las celdas de tabla no se etiquetan una a una

398 de los 488 widgets son celdas. Etiquetarlas por separado con la regla anterior produce
basura (`TF cuotalta 01 → "Permanencia Penalización Cantidad"`). La celda **no tiene etiqueta
propia: hereda la cabecera de su columna**, y la cabecera es el texto que hay encima de la
primera celda — que es justo el `rect` representativo que `tableSection()` ya calcula y que hoy
sólo usa la visión. Siete lecturas resuelven 398 campos, y la etiqueta del campo pasa a ser
«Cuota unitaria · fila 3».

### 3.4 La IA, sólo sobre el residuo

Quedan 23 campos que la geometría no resuelve, y son un conjunto reconocible: los cuatro trozos
de `CÓDIGO DE DISTRIBUIDOR`, el `dia`/`mes`/`año` (cuya «etiqueta» a la izquierda es una barra),
el bloque de firmas de la página 3 (rótulos debajo, no a la izquierda) y los campos contaminados
por el texto rotado del margen lateral. Ahí sí `FieldLabeler`, pero con **23 recortes en vez de
481**, y pudiéndole pasar el título de la sección como contexto en el prompt.

---

## 4. El número que manda en el diseño

**Un alta usa 37 de los 488 widgets del contrato. Un 7,6%.**

- Página 1 — 3 casillas de cabecera + 8 del DISTRIBUIDOR (nombre, tfno., email y las 5 cajas del
  código) + 2 de TEKI + 15 de DATOS DEL CLIENTE = 28.
- Página 3 — 3 de fecha + 2 del cliente (nombre y DNI) + 2 del comercial + 2 firmas = 9.

Todo lo demás cuelga de las ocho bandas del §2.1, que en un alta van desmarcadas. Eso convierte
el `enablerField` de una mejora estética en **el requisito que hace usable la pantalla**: sección
con su activador apagado, sección plegada. El usuario ve 37 campos y ocho cabeceras plegadas, no
481 campos seguidos.

---

## 5. Reglas de higiene que salen de aquí

1. **Excluir los pulsadores del esquema.** Comprobar el bit 17 de `Ff` en `PdfFieldInspector`.
2. **`/Sig` merece su propio `FieldKind.SIGNATURE`**, no `TEXT`. Además deja el terreno hecho
   para la fase 6.
3. **Un valor troceado en N cajas es un campo lógico, no N campos.** `CÓDIGO DE DISTRIBUIDOR`
   son 5 cajas contiguas de 12 pt con una sola etiqueta a la izquierda; es el patrón del §3.8 del
   análisis (el BIC del SEPA). Detectarlo por geometría (misma fila, mismo ancho, huecos < 2 pt,
   una sola etiqueta) y ofrecer un campo que se reparte al escribir.
4. **El texto rotado del margen se excluye del etiquetado.** El pie legal vertical de las páginas
   2 y 3 contamina las etiquetas de los campos cercanos.
5. **Los nombres duplicados se avisan, no se tapan** — igual que las filas superpuestas de
   `Conectividad.pdf`.

---

## 6. Riesgo y verificación

Riesgo **bajo**: toca `FormSchemaBuilder` y añade `LayoutTextExtractor`; **no toca
`WizardViewModel`** ni la clave de los valores (que la fijó la 5·3). Con el contrato de Orange no
cambia nada, porque su esquema es `BUILTIN` y no pasa por el builder.

Se verifica sin dispositivo: sobre los cuatro PDFs de Aire, las anclas detectadas y las etiquetas
de los campos sueltos son una lista fija que se compara en una prueba. El `kotlinc` de las
releases de JetBrains typecheckea todo lo que no dependa de pdfbox.

---

## 7. Orden propuesto

1. **Tanda de normalización y procedencia** (ver `CONTINUIDAD.md` §5, bloque de la documentación
   real): son arreglos pequeños en `AutoFillPolicy`, `FieldNormalizer` y el merge de
   `MultiAiExtractor`, verificables en local con un juego de documentos real. No tocan el
   asistente.
2. **Esta tanda (5·4b)** — es la que hace utilizable la pantalla de 481 campos.
3. **5·5, tablas en el Relleno.** Puede esperar: con el alta en 37 campos, no bloquea nada. Y
   necesita las cabeceras de columna resueltas por la 5·4b para poder decir «Cantidad» y «Cuota
   unitaria» en vez de «Columna 4».
