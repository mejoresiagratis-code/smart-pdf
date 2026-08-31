# Análisis de los formularios de Aire Networks

> **Para qué sirve este documento.** Es la referencia contra la que se diseñan las fases 2–6
> del roadmap multi-formulario. Sustituye a «lo que analizamos en aquella sesión»: todo lo de
> aquí está verificado leyendo los PDFs reales con `pypdf`, replicando el mismo algoritmo que
> usa `PdfFieldInspector`. Lo que **no** está verificado va marcado como tal.
>
> Fecha del análisis: **2026-08-31** · versión de la app en ese momento: `0.9.6` · seis PDFs
> analizados.

---

## 1. Los documentos

Cuatro son formularios rellenables. Dos son contexto de negocio (sin AcroForm).

| Documento | Págs | Widgets | Campos únicos | Papel |
|---|---|---|---|---|
| `Contrato_empresas.pdf` | 3 | 488 | 481 | Contrato principal |
| `Portabilidad_Fija.pdf` | 1 | 202 | 202 | Anexo de portabilidad |
| `Conectividad.pdf` | 1 | 141 | 141 | Anexo de Aire Connect |
| `SEPA.pdf` | 1 | 20 | 19 | Mandato de domiciliación |
| `Onboarding TEKI` | 58 | 0 | — | Contexto: cómo funciona la plataforma |
| `Tarifario_Distribuidores` | 1 | 0 | — | Contexto: catálogo y comisiones |

Que «widgets» y «campos únicos» no coincidan no es un error: un campo puede tener varios
widgets (un radio con N opciones, o un mismo dato repetido en varias páginas).

---

## 2. El flujo real de un alta

Reconstruido del deck de onboarding de TEKI. Importa porque define **de dónde sale cada dato**,
que es lo que la app necesita saber para no pedir lo imposible.

1. **Cotizador (TEKI)** — se monta la oferta eligiendo servicios del portfolio. TEKI calcula
   la cuota mensual y genera el PDF de oferta.
2. **Alta del cliente (TEKI)** — se crea el cliente en el CRM. Requiere factura base con los
   datos bancarios, *«los mismos que en el documento SEPA firmado»*. De aquí salen el
   **código de cliente** y la **fecha de alta**.
3. **Formularios en papel** — contrato + SEPA + los anexos que apliquen, rellenados y
   firmados. **Esto es lo que hace la app.**
4. **Proceso de contratación (TEKI)** — se lanza con la documentación ya firmada.

**Telefonía móvil queda fuera**: según el deck, esos datos se rellenan directamente en TEKI y
el contrato llega firmado por email. Un formulario menos que soportar.

### Documentación exigida para cliente nuevo

Está fijada por Aire, así que la app puede comprobar si falta algo en vez de limitarse a
detectar intrusos:

| Casuística | Documentación |
|---|---|
| Persona jurídica | CIF · SEPA firmado · CONTRATO firmado · DNI del representante legal · móvil y email del representante · cuenta bancaria para domiciliación · **escritura si es asociación** |
| Persona física | CIF · SEPA firmado · CONTRATO firmado · DNI del representante legal · móvil y email del representante · cuenta bancaria |

Esto confirma que **un alta es un conjunto de formularios, no un formulario suelto** — la
base de la decisión de arquitectura de expediente (ver `ROADMAP.md`).

---

## 3. Taxonomía de patrones

Lo que el modelo de esquema de la fase 2 tiene que ser capaz de representar. Cada patrón está
verificado contra al menos un PDF real.

### 3.1 Campos transversales (mismo dato en varios formularios)

Los cuatro formularios piden el mismo núcleo de datos de cliente con nombres distintos:

| Dato | Contrato | SEPA | Portabilidad | Conectividad |
|---|---|---|---|---|
| Razón social | `Nombre o razón social` | `NOMBRE DEL DEUDOR` | `Titular` | `Titular` |
| Identificación | `NIF/CIF/NIE` | — | `con CIF` | `con C.I.F` |
| Domicilio | `Domicilio` | `DIRECCION DEL DEUDOR` | `Domicilio`¹ | `Domicilio`¹ |
| CP / Localidad / Provincia | `CP`,`Localidad`,`Provincia` | `CODIGO POSTAL`² | ídem¹ | ídem¹ |
| IBAN | — | `IBAN` | — | — |

¹ En el bloque de cambio de titular / dirección de instalación, que puede ser un tercero.
² El SEPA agrupa CP, población y provincia en **un solo campo**.

**Consecuencia para la fase 2:** `canonical` no debe ser «enlace al CANON del contrato Orange»
sino **el canónico transversal del expediente**. Es lo que permite extraer una vez y rellenar
los cuatro.

**Ojo con los terceros.** No todo lo que parece del cliente lo es — es el mismo problema que
ya costó reglas en el prompt de la app anterior:
- `CAMBIO TITULAR` (Portabilidad) y `CAPTURA DE FIBRA CON CAMBIO DE TITULARIDAD` (Contrato) son
  datos **del titular donante**, no del cliente.
- `Dirección de instalación 1..4` (Conectividad) puede ser otra razón social distinta.
- El bloque «A CUMPLIMENTAR POR EL ACREEDOR» del SEPA es **Aire**, y viene ya impreso.

### 3.2 Datos constantes del distribuidor (eres tú)

`NOMBRE`, `TFNO.`, `EMAIL DISTRIBUIDOR`, `CÓDIGO DE DISTRIBUIDOR` en la cabecera del contrato,
y `NOMBRE Y DNI` del comercial al pie de los cuatro formularios.

Es el sucesor directo del autorrelleno de `RESPONSABLE_KEY` del contrato Orange: se configura
**una vez en Ajustes** y aplica a todos los formularios de todos los expedientes.

### 3.3 Datos que no salen de ninguna documentación

`FECHA DE ALTA EN TEKI` y `CÓDIGO DE CLIENTE EN TEKI` salen de la plataforma **después** de dar
de alta al cliente. No están en ningún DNI, censal ni certificado bancario.

**Necesitan un origen propio** (`PLATAFORMA`): ni se autorrellenan, ni deben bloquear el avance
como si fueran un conflicto por decidir. Sin esto, la app pedirá decidir sobre campos que
todavía no existen.

### 3.4 Tablas: la estructura está en la geometría, nunca en el nombre

Los cuatro PDFs nombran las filas de tabla de tres formas distintas, **y las tres conviven en
una misma fila**:

| Forma | Ejemplo | Dónde |
|---|---|---|
| Sufijo `RowN` | `Línea o rangoRow1..Row25` | Portabilidad |
| Sufijo numérico `NN` + fila `TOTAL` | `TF cantidad 01..12`, `TF cantidad TOTAL` | Contrato |
| **Sin patrón alguno** | `Campo de texto 116, 117, 118…` | Contrato, Conectividad |

La fila 1 de la tabla de Telefonía Fija del contrato es, literalmente:

```
Campo de texto 116 | Campo de texto 128 | Campo de texto 140 |
TF cantidad 01 | TF cuotalta 01 | TF cuounitaria 01 | TF cuotatotal 01
```

Los tres primeros son «Servicio contratado», «Permanencia» y «Penalización». El nombre no dice
nada; lo único que revela que son la misma columna es que comparten **x**, y que son la misma
fila que comparten **y**.

**Regla:** las tablas se detectan por geometría (x constante = columna, y constante = fila). El
nombre sólo sirve para etiquetar.

### 3.5 Checkboxes ligados a filas, aunque estén en otro recuadro

El caso más claro es Portabilidad. Los 100 checkboxes de la columna «Provisión» se llaman
`Check Box4.0`, `Check Box4.4.5.10.5`… — jerarquía basura de Acrobat, sin ninguna pista de la
fila. Pero verificado:

| Prefijo | Widgets | x (constante) | Columna real |
|---|---|---|---|
| `Check Box4` | 25 | 412,2 | C.SIP |
| `Check Box5` | 25 | 443,6 | TRUNK |
| `Check Box6` | 25 | 484,9 | C. VIRTUAL |
| `Check Box7` | 25 | 543,5 | FAX TO MAIL |

**El prefijo del nombre da la columna; la y da la fila.** Aunque visualmente vivan en un
recuadro «Provisión» separado de la tabla, pertenecen a la fila. Se resuelve sin IA.

### 3.6 Estados de exportación arbitrarios ⚠️

**Esto rompe la app tal cual está hoy.** `ContractFields.checkboxStateFor()` asume `/On`–`/Off`.
Lo real:

| PDF | Estados encontrados |
|---|---|
| Portabilidad | `/Sí` |
| Contrato | `/Sí` y `/0`,`/1`,`/2`,`/3`,`/4`,`/5` |
| SEPA | `/Opción1`, `/Opción2` |

Hay que leerlos de `/AP /N` del widget, no asumirlos.

### 3.7 Grupos de radio (un campo, varios widgets, opciones excluyentes)

- SEPA: `PAGO RECURRENTE` es **un solo campo** con dos widgets (`/Opción1`, `/Opción2`). Ojo:
  el nombre del campo es el de la primera opción, así que **etiquetarlo por el nombre es
  engañoso** — la segunda opción es «PAGO ÚNICO».
- Contrato: `Botón de opción 10` tiene 6 widgets a la misma y (742,1) con estados `/0`..`/5`
  = la fila de RED INTELIGENTE (NÚMERO NUEVO · 902 · 901 · 900 · otros · PORTABILIDAD).

### 3.8 Un valor lógico troceado en varias casillas

El SWIFT/BIC del SEPA son **11 campos de un carácter** (`Text18`…`Text29`, sin `Text21`). Sin
agruparlos, la IA escribiría el BIC entero en la primera casilla.

Este es además el caso que destapó el bug de orden de lectura de la v0.9.4 (ver `CHANGELOG`).

### 3.9 Bloques repetidos que no son tabla

`Conectividad.pdf` tiene «Dirección de instalación 1..4»: cuatro grupos idénticos de 8 campos,
apilados en vertical. No es una tabla (no comparten x), pero sí es repetición. El Modelo 145
tenía lo mismo con los bloques de hijos.

### 3.10 Campos de firma del AcroForm (`/Sig`)

Tipo que la app **no maneja hoy**: `AcroFormFiller` y `SignaturePageDetector` sólo saben
estampar una imagen en coordenadas.

| PDF | Campos `/Sig` |
|---|---|
| Contrato | `Signature4` (pág. 2), `Signature1`, `Signature2`, `Signature3` (pág. 3) |
| Portabilidad | `Signature6`, `Signature7` (pág. 1) |
| Conectividad, SEPA | ninguno |

El deck insiste: *«Muy importante: poner fecha en el contrato y la firma (electrónica)»*.
Relevante para la fase 6.

---

## 4. El catálogo de producto (tarifario)

El tarifario es un catálogo con precios: categoría · servicio · descripción · cuota unitaria ·
cantidad · cuota total · % de comisión.

**Regla aritmética verificada en 21 filas:** `cuota total = cantidad × cuota unitaria`
(1 €×3 = 3,00 € · 3,50 €×3 = 10,50 € · 150 €×3 = 450 € · 160 €×1 = 160 €…). En el contrato de
ejemplo del deck también cuadra por fila (3 × 7 € = 21 €).

**Oportunidad no prevista en el roadmap:** si la app carga el catálogo, el paso de Relleno
puede ofrecer un selector de servicio por fila que autorrellene descripción y cuota unitaria y
calcule el total. Convierte la parte más tediosa (teclear 12 filas de tarifa en el móvil) en
elegir de una lista.

> ⚠️ **El tarifario contiene los escalados de comisión del distribuidor** (4/8/12/15 %,
> objetivos cuatrimestrales). Es información comercial propia, no del cliente. Si el catálogo
> entra en la app debe quedarse **local**: no tiene ningún motivo para viajar al proxy ni a
> ningún motor de IA.

---

## 5. Quién rellena qué

| Origen | Qué | Cómo llega |
|---|---|---|
| **Documentación del cliente** | Razón social, CIF/NIF/NIE, domicilio, CP, localidad, provincia, representante, IBAN | Extracción multi-IA (lo que la app ya hace) |
| **Ajustes (constante)** | Bloque distribuidor: nombre, teléfono, email, código; nombre y DNI del comercial | Autorrelleno |
| **Catálogo / Cotizador** | Filas de tabla: servicio, permanencia, penalización, cantidad, cuotas | Selección manual, no IA |
| **Calculado** | Cuota total por fila; totales de columna | Aritmética |
| **TEKI (`PLATAFORMA`)** | Fecha de alta y código de cliente en TEKI | Manual, después del alta |
| **Firma** | Fecha del contrato y firma | Paso de Firma |

**La IA no debe tocar las tablas de tarifa.** No salen de la documentación del cliente.

---

## 6. Defectos encontrados en los PDFs de Aire

No son fallos de la app; están en los ficheros originales.

### 6.1 `Conectividad.pdf` — filas 07 y 08 superpuestas

Los widgets de la fila 08 están exactamente encima de los de la 07 (y = 271,02; x idénticas en
las 7 columnas). La tabla tiene 10 filas nombradas pero **9 posiciones físicas**:

```
PEN 01  y=184,89     PEN 06  y=257,49
PEN 02  y=200,54     PEN 07  y=271,02  ← misma coordenada
PEN 03  y=214,07     PEN 08  y=271,02  ← misma coordenada
PEN 04  y=229,02     PEN 09  y=285,59
PEN 05  y=243,25     PEN 10  y=300,15
```

Afecta también a `Campo de texto 199/200`, `209/2010`, `CAN 07/08`, `ALTA 07/08`, `UNI 07/08`,
`TOTAL 07/08`. Si se rellenan las dos, una tapa a la otra en el papel.

**Pendiente de decidir con Pablo:** ¿la app avisa y usa sólo una, o se corrige el PDF con quien
mantiene los formularios en Aire?

### 6.2 Nombres heredados de otra empresa

En `Portabilidad_Fija.pdf`, un campo se llama
`solicita la PORTABILIDAD a favor de LEAST COST ROUTING TELECOM SL`. Sin impacto funcional,
pero confirma que **el nombre técnico no es fuente fiable de etiqueta**.

---

## 7. Qué queda por verificar

Marcado explícitamente para que nadie lo dé por bueno sin comprobarlo:

1. **Los totales de columna.** La regla por fila está confirmada; el sumatorio de la fila TOTAL
   no cuadra leyendo la captura de baja resolución del deck. Hay que comprobarlo contra un
   contrato real ya rellenado antes de programar la suma.
2. **Qué anexos exige cada modalidad.** Las casillas ALTA NUEVA / MODIFICACIÓN / PORTABILIDAD
   de la cabecera parecen decidir qué anexos entran en el expediente, pero es una inferencia,
   no algo que diga el deck.
3. **Si `Conectividad.pdf` se usa siempre o sólo en conectividades masivas.** El contrato dice
   *«en caso de solicitar conectividades masivas usar anexo de conectividad»*.

---

## 8. Impacto en las fases del roadmap

| Fase | Qué cambia respecto al plan original |
|---|---|
| **2 · Esquema** | `canonical` pasa a ser el canónico transversal del expediente. Añadir origen `PLATAFORMA`. Leer estados de checkbox de `/AP /N` en vez de asumir `/On`–`/Off`. Unidad persistida ya como expediente (lista de 1). |
| **3 · Etiquetado IA** | Confirmado imprescindible: hay bloques enteros con nombres autogenerados. Pero conviene **aprovechar el nombre real cuando ya es legible** (`Nombre o razón social`, `NIF/CIF/NIE`…) antes de gastar una llamada de visión sobre 481 campos. |
| **4 · Editor** | Debe permitir editar la etiqueta de un grupo de radio completo, no sólo del campo (el caso `PAGO RECURRENTE` / `PAGO ÚNICO`). |
| **5 · Relleno** | Tablas con filas dinámicas + selector de catálogo + cálculo de totales. Los checkboxes de fila se resuelven por geometría. |
| **6 · Firma** | Hay que mirar los campos `/Sig`, no sólo estampar imagen en coordenadas. |
| **7 · Biblioteca** | Sin cambios. |
