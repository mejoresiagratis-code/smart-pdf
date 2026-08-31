# Changelog — Rellenador de Contratos (Android)

Todas las versiones que han llegado a **build verde** en el workflow.
(Las 1–2 entradas más recientes pueden estar pendientes de verificación en Actions;
se consolidan al confirmarse el verde.) Se sigue [Keep a
Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado semántico. El nombre del
artifact / APK del workflow coincide con `versionName` para poder distinguirlos.

---

## [0.9.8.2-nombres-consistentes] — 2026-08-31

Cosmética de entrega. El commit, el run de Actions, el zip del artefacto y el APK de dentro
pasan a llamarse **igual**: `rellenador-<versionName>`.

### Cambiado — el APK ya no se llama `app-debug.apk`
Gradle siempre lo llama así, de modo que al descargar varios había que abrirlos para saber cuál
era cuál. Nuevo paso que lo renombra a `rellenador-<versionName>.apk` antes de subirlo, igual
que el zip que lo contiene.

### Corregido — el título del run salía como un muro de texto
`run-name` usaba `github.event.head_commit.message`, que es el mensaje **entero** (título y
cuerpo). Como los cuerpos eran largos, el título del run salía enorme y cortado.

Se ha quitado `run-name`: sin él, GitHub usa su valor por defecto, que para un `push` es
exactamente la primera línea del commit. Las expresiones de Actions no tienen función para
partir cadenas, así que recortarla desde el YAML no era posible. En `workflow_dispatch` el
título vuelve a ser «Android CI #N»; para saber qué se compiló está el paso «Resumen del
build», que ya imprime versión y versionCode.

### Norma nueva, documentada en la guía de continuidad
Título del commit = `rellenador-<versionName>` y nada más. Cuerpo corto (qué y por qué); el
razonamiento largo vive en este CHANGELOG, que es donde se busca.

---

## [0.9.8.1-arreglo-de-compilacion] — 2026-08-31

**Las v0.9.7 y v0.9.8 no compilaban.** Ambas se subieron sin build verde, saltándose la regla de
«una tanda, una versión, un build verde antes de la siguiente». Esta versión las arregla; no
añade ni cambia funcionalidad.

Lleva número `.1` en vez de `0.9.9` a propósito: la 0.9.9 sigue reservada para la tanda 3 de la
fase 2 (persistencia y migración), que es la que tiene el riesgo de verdad.

### Corregido — `PDRadioButton.getSelectableValues()` no existe (rompía la v0.9.7)
`applyButtonValue()` llamaba a ese método para listar las opciones de un grupo. Verificado
contra el fuente de `pdfbox-android v2.0.27.0`, que es la versión que usa el proyecto:
`PDRadioButton` sólo expone `getSelectedIndex()` y `getSelectedExportValues()`.

- Reescrito usando únicamente API comprobada en esa versión: `PDCheckBox.check()`/`unCheck()`
  y `PDButton.getOnValues()`. Como `PDCheckBox` hereda de `PDButton`, la rama de casilla va
  primero. El comportamiento previsto no cambia.

### Corregido — `FieldOrigin` chocaba con un nombre ya existente (rompía la v0.9.8)
El enum nuevo se declaró como `FieldOrigin` en `data.model`, pero **ese nombre ya estaba
cogido**: `ui.wizard.FieldOrigin` es un `data class` con otro significado (de qué documento y
qué motores salió el valor actual de un campo). `FieldResolver` y `AutoFillPolicy` viven en
`data.model` y lo importan explícitamente, así que la referencia quedaba ambigua.

- Renombrado a **`ValueOrigin`**, que además describe mejor lo que es: una propiedad de diseño
  del formulario, no un hecho de la ejecución en curso. Documentado en el propio fichero para
  que nadie vuelva a caer.

### Por qué se coló
Ninguno de los dos fallos era detectable leyendo el código: uno dependía de la API real de una
dependencia y el otro de un nombre declarado en otro paquete. Aun así, ambos eran verificables
**antes** de subir —el primero consultando el fuente de la librería, el segundo con un `grep` de
colisiones— y no se hizo. Ambas comprobaciones se han incorporado ya al trabajo de esta
corrección.

---

## [0.9.8-modelo-de-esquema] — 2026-08-31

Fase 2, tanda 2 de 3. **Sólo estructuras nuevas**: un fichero añadido, cero ficheros existentes
modificados aparte de la versión y la documentación. Nada de esto se persiste ni se usa todavía
en el asistente — eso es la tanda 3, aislada por ser la de riesgo alto.

### Añadido — `FormSchema.kt`, el modelo de esquema dinámico
Sustituye la premisa de `ContractFields.CANON` (lista fija de 21 campos del contrato de Orange),
que era la causa de que subir cualquier otro PDF detectara bien sus campos pero siguiera
mostrando los 22 de siempre.

- **`CanonicalKeys`** — vocabulario canónico **transversal al expediente** (razón social,
  identificación, domicilio, representante, IBAN, BIC…). Es lo que permite extraer el dato una
  vez y rellenar los cuatro formularios de Aire, que piden lo mismo con nombres distintos
  (`Nombre o razón social` / `NOMBRE DEL DEUDOR` / `Titular`). Ojo: **no es lo mismo que
  `CANON`** — `CANON` son nombres reales de campos de un PDF concreto; esto es vocabulario de
  negocio, independiente de cualquier documento.
- **`FieldOrigin`** — de dónde sale cada valor: `DOCUMENTO`, `AJUSTES` (constantes del
  distribuidor), `PLATAFORMA` (lo que sale de TEKI después del alta y la IA no puede proponer),
  `CATALOGO` (filas de tarifa, que la IA no debe tocar), `CALCULADO`, `FIRMA`.
- **`FormField`** — con `onState` para el estado de activación real (v0.9.7), `optionLabel`
  para los grupos de opción, y `combGroup`/`combIndex` para el caso de **un valor lógico
  troceado en varias casillas** (el BIC del SEPA son 11 campos de un carácter).
- **`FormSection`** con `SectionKind`: `SIMPLE`, `TABLE` y `REPEATED_BLOCK` (los cuatro
  bloques idénticos de «Dirección de instalación» de Conectividad, que se repiten pero no son
  tabla porque no comparten x).
- **`TableColumn` / `TableRow`** — la columna se define por su **x**, no por el nombre. Es
  obligatorio: en una misma fila de la tabla de Telefonía Fija conviven `TF cantidad 01` y
  `Campo de texto 116`. Las celdas se asignan por posición, que también es lo que resuelve los
  checkboxes de fila de Portabilidad (nombres tipo `Check Box4.4.5.10.5`, donde el prefijo da
  la columna y la `y` la fila).
- **`FormSchema`** — identificado por la misma huella que ya usa `TemplateFingerprint`, para
  que subir dos veces el mismo PDF reutilice el esquema ya etiquetado. Lleva `schemaVersion`
  desde el primer día, para no tener que adivinarlo después (la lección de la 0.8.0).
- **`BuiltinSchemas.orangeDistribution()`** — el contrato de Orange deja de ser «el formulario
  de la app» y pasa a ser **un esquema más**. Se **deriva** de `ContractFields.CANON` en vez de
  reescribirlo: esos nombres son frágiles (dobles espacios, sufijos `_2`, la casilla llamada
  literalmente `undefined`) y están verificados contra el AcroForm real; duplicarlos sería
  pedir que se desincronicen.

### Verificado
Las 21 claves de `CANON` tienen su clave canónica y ninguna clave del mapeo se ha inventado
(comprobado por script contra el fichero, no a ojo). El responsable comercial queda como
`FieldOrigin.AJUSTES` — es el caso que dio origen a ese valor — y las tres casillas de tipo de
identificación dejan `onState` a nulo a propósito: el estado real se resuelve contra el
documento al rellenar (v0.9.7), no se declara en el esquema.

### Lo que NO entra aquí (y por qué)
El constructor que convierte la salida de `PdfFieldInspector` en un `FormSchema` —con detección
de tablas por geometría— **no está**. Necesita decisiones que aún dependen de la tanda 3
(cómo se persiste) y de la fase 3 (cómo se etiqueta lo que no tiene nombre útil). Meterlo aquí
habría convertido una tanda sin riesgo en una con bastante.

---

## [0.9.7-estado-real-de-casillas] — 2026-08-31

Primera de las tres tandas en que se ha partido la fase 2 (ver `ROADMAP.md`). Es la única que
arregla algo **roto hoy**, y no depende del modelo de esquema, así que va primero y sola.

### Corregido — las casillas se marcaban asumiendo `/On`, que casi nunca existe
`ContractFields.CHECKBOX_ON` valía literalmente `"/On"`, dando por hecho que toda casilla de
todo PDF se activa con ese estado. No hay tal convención: el nombre del estado de activación lo
elige quien generó el documento. Verificado leyendo `/AP /N` de los formularios reales de Aire:

| PDF | Estados de activación reales |
|---|---|
| Portabilidad Fija | `Sí` |
| Contrato empresas | `Sí`, y `0`…`5` en los grupos de opción |
| SEPA | `Opción1`, `Opción2` |

En ninguno existe `On`. Con los PDFs de Aire **no se marcaría ni una sola casilla**.

- Nuevo `AcroFormFiller.applyButtonValue()`: resuelve el estado contra el propio documento.
  Casillas con `check()`/`unCheck()` (lo resuelve PDFBox); grupos de opción buscando el valor
  pedido entre `selectableValues`, tolerando la barra inicial (`/Sí` ↔ `Sí`).
- `CHECKBOX_ON`/`CHECKBOX_OFF` pasan a ser **valores lógicos** (`"On"`/`"Off"`): expresan la
  intención, y quien traduce al estado real del PDF es el filler. `checkboxStateFor()` no
  cambia de forma, así que sus dos llamadas en `WizardViewModel` siguen igual.

### Corregido — el fallo al marcar una casilla era invisible
El bucle de casillas hacía `runCatching { field.setValue(value) }` **sin `onFailure`**. Y
`PDButton.setValue()` valida el valor contra los estados declarados y lanza si no encaja, así
que cualquier casilla que no se marcara desaparecía sin dejar rastro: ni en `missingFields`, ni
en pantalla. Es la misma lección de la v0.9.0 (los fallos tienen que verse), en otro sitio.

- Los fallos de casilla se acumulan ahora en `FillResult.missingFields`, como ya hacían los
  campos de texto.

### ⚠️ Pendiente de verificar en el móvil
Estos dos arreglos se cruzan de una forma que conviene mirar con un contrato real: si el
antiguo `"/On"` tampoco encajaba en el AcroForm del **contrato de Orange**, las casillas
CIF/NIF/NIE llevarían sin marcarse desde siempre y nadie se habría enterado, precisamente por
el fallo silencioso. Esta tanda haría que empezaran a marcarse. **No es una regresión** — sería
un fallo antiguo saliendo a la luz — pero hay que confirmar contra un contrato firmado de
verdad antes de darlo por bueno.

---

## [0.9.6-orden-de-lectura-y-contrato-oculto] — 2026-08-31

Tanda de saneamiento **antes** de construir la fase 2 encima. Los esquemas de la fase 2 se
persisten por huella: un orden de campos erróneo no se quedaría en pantalla, se guardaría en
DataStore y arreglarlo después ya no sería un fix sino una migración de datos.

### Corregido — el orden de lectura del `PdfFieldInspector` partía filas (bug de la v0.9.4)
`PdfFieldInspector` agrupaba las filas con `(y / 6).toInt()`, es decir **troceando el eje Y en
tramos fijos**. Dos campos separados por décimas de punto caen en tramos distintos si el corte
del tramo pasa justo entre ellos, y la fila se parte — precisamente lo que la tolerancia de
6 pt debía evitar.

**Detectado contra un PDF real** (`SEPA.pdf` de Aire), en la fila de 11 casillas del SWIFT/BIC:
todo el grupo a y≈539,x salvo dos campos a y=540,0. Como `539/6 = 89` pero `540/6 = 90`, esas
dos casillas se iban al final de la fila siguiente:

```
antes  → Text18, Text19, Text22, Text23 … Text28 … luego Text20, Text29
después→ Text18, Text19, Text20, Text22, Text23 … Text28, Text29
```

…con un span vertical real de **1,1 pt** entre todas ellas. La verificación original contra el
Modelo 145 no lo detectó por casualidad, no porque el algoritmo fuera correcto.

- Nuevo `orderByReadingRows()`: agrupa por el **hueco** respecto al ancla de la fila (el campo
  más alto), que es el criterio que se pretendía desde el principio. Se compara contra el
  ancla y no contra el campo anterior para que una escalera de saltos pequeños no funda media
  página en una sola fila.

**Verificado contra los cuatro formularios reales de Aire:**

| PDF | Widgets | Posiciones que cambian |
|---|---|---|
| SEPA | 20 | 8 (la fila del BIC, ahora correcta) |
| Conectividad | 141 | 3 (una fila de dirección de instalación) |
| Portabilidad Fija | 202 | 8 |
| Contrato empresas | 488 | **0** |

Que el contrato de 488 campos no cambie ni una posición es la señal de que el arreglo corrige
el caso patológico sin alterar el comportamiento general.

### Cambiado — la tarjeta del contrato Orange/MASORANGE deja de mostrarse
Ya no se trabaja con ese operador, así que no tiene sentido ofrecerlo como opción de partida.

- Nueva bandera `SHOW_LEGACY_DEFAULT_CONTRACT = false` en `ContractStep.kt`. Ponerla a `true`
  devuelve la tarjeta.
- **No se ha borrado nada.** El camino `ContractSource.DEFAULT` sigue completo:
  `chooseDefaultContract()`, el asset `contrato-base.pdf`, `ContractFields.CANON`,
  `RESPONSABLE_KEY`, `checkboxStateFor()` y la calibración de firma de las páginas
  24/30/33/45/54. Consecuencias buscadas:
  - Ese contrato **se sigue reconociendo y rellenando exactamente igual si se sube como PDF
    propio**, que era el requisito.
  - Una sesión persistida que estuviera en `DEFAULT` se restaura sin romperse (el bloque
    «Estructura detectada» sigue leyendo `isDefault`).
- Sin auto-selección que quitar: `contractSource` ya arrancaba en `null`.

---

## [0.9.5-paleta-aire-y-desacoplo-orange] — 2026-08-31

Pablo ya no trabaja con Orange/MASORANGE; la empresa nueva es **Aire Networks**
(airetech.es), con varios PDFs rellenables propios (el primero, `Contrato_empresas.pdf`,
compartido y analizado: 481 campos AcroForm reales — 403 texto, 74 checkbox/radio y
**4 campos `/Sig` de firma digital**, ver nota técnica más abajo). Esta tanda es
puramente de desacoplo y branding — **cero cambios de comportamiento** en el contrato
Orange, que debe seguir reconociéndose exactamente igual si se sube.

### Cambiado — paleta de marca: de Orange a Aire
- `Theme.kt`: los 22 roles de color (antes derivados del naranja `#FF7900` de Orange)
  pasan a derivarse de la identidad de Aire. Los tonos **no son inventados**: están
  muestreados por píxel del propio `Contrato_empresas.pdf` —
  `#9F0BFF` (violeta del logo y cabeceras de tabla) como `primary`,
  `#00095A` (azul marino de la banda superior) como `secondary`,
  `#ECD0FF` (fondo lila de las filas de tabla) como `primaryContainer`.
  Terciario añadido (índigo `#5B4FE0`) como contrapunto de color, sin tocar la
  estructura de roles ni sumar dependencias. Superficies: tinte frío neutro en vez del
  cálido anterior (coherente con dejar de ser "naranja").
- Símbolos renombrados (`BrandOrange`→`BrandVioleta`, etc.); ninguno se usaba fuera de
  `Theme.kt`, así que no hay más ficheros que tocar por esto.

### Cambiado — el contrato Orange deja de presentarse como "por defecto"
- `ContractStep.kt`: **"Aportar mi PDF" pasa a ser la primera opción** (antes iba
  segunda); el contrato de distribución pasa a llamarse explícitamente
  **"Contrato Orange/MASORANGE (heredado)"** en vez de "Contrato por defecto". Sigue
  siendo una opción completamente funcional — mismo `chooseDefaultContract()`, mismo
  PDF de assets, mismo `RESPONSABLE_KEY`/`CANON` — solo cambia cómo se presenta: ya no
  se sugiere como la opción natural.
- **No había auto-selección que quitar**: `contractSource` ya arrancaba en `null`
  (verificado en `WizardUiState`); el usuario siempre ha tenido que elegir a propósito.
  Lo que sí sesgaba hacia Orange era el orden y el rótulo "por defecto" — ya corregido.

### Cambiado — copy genérico donde antes asumía Orange/MASORANGE
- `WizardScreen.kt` / `AjustesScreen.kt`: el texto de "Perfil comercial" ya no da por
  hecho que el campo se llama "Responsable Comercial MASORANGE" — ahora describe el
  autorrelleno en genérico y menciona MASORANGE solo como ejemplo entre paréntesis.
- `ExtractionPrompt.kt`: "el operador Orange/MASORANGE" → "el operador de
  telecomunicaciones", en la instrucción de ignorar datos de terceros. ⚠️ Pendiente de
  replicar en `rellenador-pro.html` para mantener la paridad del prompt (mismo tipo de
  pendiente que `tipo_documento` en v0.7.9).

### Invariantes confirmadas SIN TOCAR (el contrato Orange debe seguir funcionando)
- `ContractFields.CANON` (22 campos, dobles espacios, sufijo `_2`) — intacto.
- `RESPONSABLE_KEY = "Responsable Comercial MASORANGE"` — es el nombre LITERAL del
  campo en el AcroForm de ese PDF, no branding de la app. Tocarlo rompería el
  autorrelleno del contrato real.
- `AcroFormFiller`, calibración de firma (págs. 24/30/33/45/54) — sin cambios.

### Nota técnica — hallazgos de `Contrato_empresas.pdf` (Aire) para las próximas fases
Confirma exactamente el síntoma que motivó el roadmap multi-formulario, a mayor escala
que el Modelo 145:
- **481 campos AcroForm** (403 texto + 74 checkbox/radio + **4 `/Sig`**). Los `/Sig` son
  un tipo de widget que la app **no maneja hoy** — `AcroFormFiller`/`SignaturePageDetector`
  solo conocen huecos de firma por estampado de imagen en coordenadas, no campos de
  firma real del AcroForm. Aparecen en portabilidad (2), captura de fibra (1) y cierre
  del contrato (1) — a tener en cuenta en la fase 6 (firma por esquema).
- Muchos campos usan nombres autogenerados sin significado
  (`Campo de texto 116`, `Casilla de verificación 27`…), en bloques repetidos (12 filas
  de servicio × varias tablas). Nombre técnico inútil para etiquetar → confirma que el
  etiquetado por VISIÓN (fase 3) es imprescindible, no solo para el 145.
- Al mismo tiempo, un bloque de campos SÍ tiene nombres legibles y ya en español
  (`Nombre o razón social`, `Domicilio`, `NIF/CIF/NIE`, `Móvil representante`…): la
  huella + etiquetado deberían aprovechar el nombre real cuando exista antes de gastar
  una llamada de visión, en vez de tratar los 481 por igual.

### Verificado
Compilación limpia (sin referencias colgantes a los símbolos de color antiguos,
verificado por grep). Sin cambios en `data/model`, `data/pdf` ni `data/validation`:
el comportamiento de extracción/relleno/firma del contrato Orange es idéntico al de
la v0.9.4.

---

## [0.9.4-inspector-de-campos] — 2026-08-31

Fase 1 del roadmap multi-formulario (`roadmap-multiformulario.html`). Base invisible:
no toca ni la interfaz ni el modelo de datos, así que entra sola y con riesgo cero —
todo lo que viene después (esquema dinámico, etiquetado por IA, editor) la necesita.

### Añadido — `PdfFieldInspector`
Lee los widgets del AcroForm **en el orden en que se rellenan**: por página, y dentro de
cada página de arriba abajo y de izquierda a derecha (no el orden interno del PDF, que no
tiene por qué parecerse al visual).

- Devuelve `Field(name, page, x, y, width, height, isCheckbox)` por widget, con las
  coordenadas convertidas a origen **arriba-izquierda** en puntos (el eje Y de PDF crece
  hacia arriba; aquí se invierte).
- Orden: página → fila (tolerancia de 6 pt) → columna. La tolerancia evita que dos campos
  de la misma fila se desordenen por un par de puntos de diferencia vertical — pasa
  constantemente en los formularios de la AEAT, donde las casillas no están perfectamente
  alineadas.
- **VERIFICADO contra `Modelo_145_rellenable.pdf`** (60 campos): el orden resultante
  coincide con el del formulario impreso, sección por sección.
- Sin dependencias nuevas: usa `pdfbox-android` (`tom-roush`), ya presente en el proyecto.

### Sin cambios de comportamiento
Es una utilidad pura, sin llamadas desde ningún ViewModel ni pantalla todavía. El
contrato de distribución (assets) sigue funcionando exactamente igual.

---

## [0.9.3-campos-reales-del-pdf] — 2026-08-07

Tercera tanda del plan web→app. La de más valor técnico: elimina de raíz que se «olviden»
campos.

### Cambiado — el prompt lleva los campos que el PDF tiene DE VERDAD
Hasta ahora la app mandaba siempre `ContractFields.CANON`, una lista **fija y transcrita a
mano**. Si el PDF tenía un campo que no estaba en esa lista, la IA ni siquiera sabía que
existía — así se «olvidaron» campos en su día. La web nunca tuvo ese problema porque
construye la lista leyendo el PDF cargado.

- `MultiAiExtractor.extract()` acepta `fieldNames`, y el ViewModel le pasa
  `state.userFieldNames` cuando el usuario ha cargado su propio contrato.
- Con el contrato de assets no cambia nada: el valor por defecto sigue siendo `CANON`,
  así que el comportamiento del caso habitual es idéntico al de antes.
- `ExtractionPrompt.build()` ya aceptaba `fieldNames`; solo faltaba que alguien le pasara
  algo distinto del valor por defecto.

### Añadido — guía de campos cuando el contrato usa nombres propios
Si el PDF no es el de MASORANGE, sus campos se llaman de otra forma («RAZON_SOCIAL» en vez
de «Nombre  Razón Social»). Sin una guía, la IA devuelve claves que no existen en ese PDF
y no se rellena nada.

- Nuevo parámetro `fieldMapping` (canónica → nombre real). Genera el bloque
  **GUÍA DE CAMPOS**, insertado entre «CAMPOS DEL PDF» e «INSTRUCCIONES», exactamente
  donde lo pone la web.
- **Copiado literalmente de `tplHint` de `rellenador-pro.html` (línea 1459)**, incluidas
  las comillas angulares y la frase final. Al ser el mismo texto, **la paridad del prompt
  se mantiene**: esta tanda no obliga a tocar la web.
- El mapeo ya existía en el estado (`fieldMapping`, del editor de plantillas): no hizo
  falta lógica nueva, solo llevarlo hasta el prompt.

### Verificado
Simulado el prompt en los dos escenarios: con el contrato de assets sale idéntico al
actual (sin bloque de guía); con un PDF de nombres propios aparecen los campos reales y su
traducción.

---

## [0.9.2-fix-mime-file-uri] — 2026-08-07

### Corregido — «No se pudieron leer los documentos» (regresión de la v0.8.7)
Desde la v0.8.7 el análisis fallaba **siempre**: al pulsar «Analizar con IA» saltaba
«No se pudieron leer los documentos». En la v0.8.6 funcionaba.

**Causa:** `DocumentLoader.mimeOf()` resolvía el tipo con
`ContentResolver.getType(uri)`, que **solo funciona con `content://`**. Para un `file://`
devuelve `null`. Y precisamente la v0.8.7 introdujo `DocumentStore`, que copia los
documentos a `filesDir` — desde entonces **todos** los URIs son `file://`.

La cadena completa: `getType()` → `null` → `application/octet-stream` → `load()` no entra
ni por la rama de imagen ni por la de PDF → devuelve lista vacía → `docGroups.isEmpty()`
→ error. Los documentos estaban perfectamente copiados y eran legibles; lo único que
fallaba era decidir de qué tipo eran.

**Solución:** cuando el resolver no sabe (o dice `octet-stream`), se deduce por la
extensión del nombre, que la copia conserva. Verificado con los ficheros reales del caso
(`036.PDF`, `Certificado_cuenta.PDF`, `CIF.PDF`, `DNI_24_DNI_2-24_merged.PDF`, y un `.jpg`):
los cinco vuelven a entrar por su rama. El camino de `content://` no cambia: si el resolver
responde, manda él.

`WizardViewModel.detectDocType()` no estaba afectado — ya miraba también la extensión del
nombre además del MIME.

---

## [0.9.1-consentimiento-y-solo-ue] — 2026-08-06

Segunda tanda del plan web→app. Las dos funciones van juntas porque comparten la misma
pregunta: **a dónde van los datos del cliente**.

### Añadido — aviso previo antes de mandar documentos a la IA
Los documentos que se analizan son DNI, NIE, certificados censales y datos bancarios **de
terceros**. Enviarlos a un proveedor de IA es una comunicación de datos personales, y
varios motores procesan fuera de la UE. La app web ya paraba aquí desde su versión F7; la
Android los enviaba directamente — justo al revés de lo esperable, porque la Android es la
que se usa a diario con clientes reales.

- Nuevo `ConsentSheet`: cuántos documentos se van a enviar, advertencia de datos de
  terceros, y **los motores separados en dos bloques** — los que salen de la UE en
  `errorContainer`, los que procesan dentro en `tertiaryContainer`. Ese es el dato que
  cambia la decisión, así que va destacado y no enterrado en un párrafo.
- El botón «Analizar» **está deshabilitado** hasta marcar la casilla de autorización.
- Casilla «no volver a preguntar en este dispositivo», persistida
  (`ai_consent_remembered_v1`).
- `runExtraction()` deja de ser el punto de entrada de la UI: ahora es
  `requestExtraction()`, que abre el aviso salvo que ya esté recordado. Verificado que no
  queda ninguna llamada directa desde la interfaz.
- Si todos los motores activos son europeos, el bloque de advertencia **no aparece**: no
  conviene acostumbrar al usuario a descartar un aviso que casi nunca aplica.

### Añadido — modo «solo motores europeos»
Interruptor en el acordeón de Motores IA, persistido (`eu_only_engines_v1`).

- Va **arriba del listado**: es un filtro que decide qué motores son elegibles, y leerlo
  después de haber elegido sería llegar tarde.
- Al activarlo **apaga de verdad** los motores no europeos, no los deja marcados pero
  inertes: un chip encendido que no participa es una mentira visual.
- Sus chips quedan atenuados y sin respuesta, pero **siguen visibles**, para que se vea que
  existen y por qué no se pueden usar ahora.
- `toggleProvider()` respeta el filtro: con solo-UE activo, un motor de fuera no puede
  encenderse.

### Notas
- Ambas preferencias **sobreviven a «Empezar otro contrato»**: son decisiones del usuario
  sobre sus datos, no estado de un contrato concreto.
- `AiProvider.eu` ya existía; no hizo falta tocar el modelo de motores.
- Sin cambios en el prompt → la paridad con la web no se ve afectada.

---

## [0.9.0-errores-de-motor-visibles] — 2026-08-06

Primera tanda del plan web→app. Arregla una **regresión introducida en la v0.8.0**.

### Corregido — los fallos de motor eran invisibles desde la v0.8.0
`WizardViewModel` seguía calculando `engineErrors` tras cada extracción, pero el único
composable que los mostraba —el panel plegable «Ver motores no disponibles» de
`ReviewStep`— **se borró al fundir Revisión IA dentro de Relleno**. Desde entonces, si
Gemini agotaba cuota o Groq devolvía 429, la extracción salía con menos datos y el usuario
no tenía forma de saber por qué. Peor aún: el hero anuncia «La IA ha rellenado el
contrato», así que el silencio hacía parecer que la IA no había encontrado nada, cuando en
realidad ni había llegado a ejecutarse.

### Añadido — causa legible en vez del error crudo
Los mensajes de los proveedores son inservibles para un comercial en la calle
(`HTTP 429 — Resource has been exhausted (e.g. check quota)`). Nuevo `EngineFailure` que
los traduce a seis causas con su consejo, portando la idea de `shortCause()` de la web:

| Causa | Consejo que se muestra |
|---|---|
| límite o cuota alcanzada | Vuelve a intentarlo en unos minutos o usa otro motor |
| clave no válida o sin permiso | Revisa la clave de ese motor en la config del proxy |
| documento demasiado grande | Ese motor no lo admite; los demás sí lo han analizado |
| problema de red | Comprueba la conexión; en WiFi, prueba con datos móviles |
| respuesta incompleta | Suele arreglarse repitiendo el análisis |
| servicio no disponible | Fallo temporal del proveedor, no de la app |

- `EngineIssue(engine, failure, detail)` conserva el mensaje crudo para diagnosticar.
- La clasificación se hace en `MultiAiExtractor`, donde está el mensaje original, para que
  la UI no tenga que interpretar cadenas de error.
- Verificado contra 10 mensajes reales de producción (incluidos el 429 de Gemini y el
  `Unable to resolve host` del incidente de DNS): 10/10 bien clasificados.

### Añadido — aviso plegable en el hero de Relleno
«N motores no participaron», desplegable con la causa y el consejo de cada uno. Va dentro
del hero, no en un banner de error aparte, porque es contexto de *qué ha hecho la IA* —no
un fallo de la app— y así no compite con los campos que sí requieren decisión.

---

## [0.8.7-documentos-persistentes] — 2026-08-06

### Añadido — los documentos sobreviven a la muerte del proceso (Fase 2 de robustez)
Pendiente marcado como **alta prioridad** en el ROADMAP desde la v0.6.5.

El selector devuelve un `content://` cuyo permiso de lectura es **efímero**: vive mientras
viva el proceso. Si Android mataba la app en segundo plano —algo habitual mientras el
comercial hace fotos o consulta WhatsApp—, al volver el URI restaurado ya no se podía abrir
y **había que volver a añadir todos los documentos**. Hasta ahora solo se avisaba de ello.

- Nuevo **`DocumentStore`**: copia los documentos a `filesDir/docs/` al añadirlos y
  devuelve URIs `file://`, que no dependen del proveedor original.
- `takePersistableUriPermission` se descartó como solución general: solo funciona si quien
  abrió el selector concedió el permiso como persistible, y aquí los documentos llegan por
  rutas muy variadas (WhatsApp, cámara, Descargas…). Copiar los bytes es lo único que no
  depende del proveedor.
- La UI muestra los documentos **de inmediato** y se sustituyen por la copia al terminar,
  para que añadir no dé sensación de espera.
- Las copias llevan prefijo `<millis>_` porque dos documentos pueden llamarse igual (muy
  típico con WhatsApp entre lotes distintos). El prefijo se oculta en la lista y en el
  nombre que viaja a la IA.
- `removeDocument` borra su copia, y `resetSession` limpia la carpeta entera: sin eso las
  copias se acumularían indefinidamente.
- Si una copia falla se conserva el URI original: mejor un documento con permiso efímero
  que perderlo.

---

## [0.8.6-fixes-uso-real] — 2026-08-06

Cuatro fallos detectados usando la app con un alta real.

### Corregido — Documentación no se podía desplazar
Su `Column` tenía `weight(1f)` pero **ningún `verticalScroll`**: con varios documentos y
los dos acordeones abiertos, el contenido desbordaba y quedaba cortado sin forma de bajar.

- Añadido el scroll que faltaba.
- Retirado el `Spacer(Modifier.weight(1f))` del final: dentro de un contenedor con scroll
  la altura es infinita, así que `weight` no reparte nada.
- Eliminado el scroll propio de la lista de documentos (`heightIn(max = 240.dp)`): con el
  contenedor externo ya desplazable, dos scrolls anidados en el mismo eje se pelean por el
  gesto. Ahora la lista fluye dentro del scroll de la pantalla.

### Corregido — la hoja de Ajustes se quedaba a medio desplegar
`ModalBottomSheet` se abre a media altura por defecto y ahí se quedaba, dejando cortados
los motores y el resto de ajustes. Añadido `skipPartiallyExpanded = true` y scroll propio
al contenido, por si en pantallas bajas o con fuente grande sigue sin caber.

### Corregido — "Dejar en blanco" no vaciaba el campo
`dismissField()` cambiaba el estado a `EMPTY` pero **conservaba el valor y la
procedencia**, así que el campo seguía mostrando el dato que el usuario acababa de
rechazar. Ahora borra valor y origen además del estado (y sigue siendo deshacible).

### Corregido — el contrato se fechaba con la fecha de los documentos
La IA extraía la fecha del censal o del 036 (p. ej. **23 de julio de 2026**) y, como el
campo ya llegaba con valor, `DateAutofill` lo respetaba: el contrato salía **fechado en el
pasado**. La fecha del contrato es siempre la de la **firma**.

- Nuevo `ContractFields.DATE_KEYS`; esas claves se descartan del prerelleno antes de
  aplicar `DateAutofill`, que ahora manda siempre.
- `FieldResolver` ignora por completo los candidatos de fecha, para que la fecha de un
  documento no se ofrezca siquiera como alternativa.
- Resultado hoy: **6 · agosto · 6** (día · mes en letras · último dígito del año, la regla
  de la web).

---

## [0.8.5-scroll-firma] — 2026-08-06

### Corregido — el final del paso de Firma quedaba inalcanzable
Con el acordeón "Huecos de firma" desplegado (5 páginas), los controles del final —los
botones de «2 · Estampar la firma»— quedaban tapados y no había forma de llegar a ellos.

El paso **sí tenía scroll**; el problema eran otras dos cosas:

- **El snackbar es un overlay anclado abajo** ("Firmadas 5 páginas") y no había hueco
  reservado para él, así que se comía los últimos ~90 dp de contenido mientras estaba
  visible. Añadido `bottom = 96.dp` al padding del contenido desplazable.
- **La previsualización tenía 560 dp fijos.** En móviles de pantalla corta ocupaba casi
  todo el alto disponible y empujaba el resto de controles fuera de vista. Ahora es
  proporcional a la pantalla (62% del alto), acotada entre 320 y 560 dp.

Verificado que los cuatro pasos del asistente tienen contenedor desplazable.

---

## [0.8.4-firma-sin-marcos] — 2026-08-06

### Corregido — la firma extraída de una foto arrastraba el marco del recuadro
Al extraer la firma de un documento, el recorte incluía **el recuadro impreso donde se
firma** (y, si la había, la raya de pauta). Nada del procesado las eliminaba.

**Por qué `despeckle` no bastaba**: descarta componentes *pequeñas* (motas de papel), y una
línea de marco es de las componentes **más grandes** de la imagen, así que sobrevivía
siempre. Además, al entrar en la bounding box, el recorte final se estiraba hasta el
recuadro y la firma quedaba pequeña y descentrada dentro de un marco.

**Nuevo paso `removeFrameLines()`**, antes del despeckle (así los restos del marco quedan
como fragmentos pequeños que el despeckle limpia después). Una línea impresa es *recta,
fina y larguísima*: cubre casi todo el ancho (o alto) en una sola fila (o columna). Se
borra una fila/columna solo si cumple las tres condiciones:

1. **cobertura ≥ 75%** del ancho/alto,
2. **grosor ≤ 4** filas/columnas consecutivas (más gruesa ya no es una raya impresa), y
3. **está pegada al borde** (franja exterior del 18%, donde vive el recuadro) **o** cruza
   la imagen casi entera (≥ 90%, que es el caso de una raya de pauta interior).

**Calibrado contra una firma real, no a ojo**: en la firma de referencia el trazo vertical
largo cubre el **78%** de la altura. Con el umbral inicial del 55% que probé primero, ese
trazo se habría borrado en una firma fina o de baja resolución. De ahí las condiciones 1 y
3, que lo dejan fuera por posición (está centrado) y por no llegar al 90%.

Verificado en tres escenarios: firma real dentro de recuadro, la misma a 1/4 de resolución
(trazos finos, el caso de riesgo) y raya de pauta bajo la firma. En los tres: **100% del
marco eliminado, 0 píxeles de trazo perdidos**.

---

## [0.8.3-hero-ia] — 2026-08-06

Última pieza visual del diseño aprobado (mockup v2): la cabecera del Relleno pasa de un
contador plano a un **hero** que resume qué ha hecho la IA.

### Añadido — hero de la IA en la cabecera del Relleno
- Tarjeta en `tertiaryContainer` (frío, para no competir con el naranja de las acciones)
  con icono, titular **"La IA ha rellenado el contrato"**, subtítulo con el número de
  documentos y los motores que respondieron, y contador `X/N` grande.
- **Rebote real del contador** al cambiar el número de campos completos: `Animatable` +
  `LaunchedEffect(filledFields)`, con los specs de `motionScheme` **capturados antes** del
  efecto (`motionScheme` es `@Composable` y no puede llamarse dentro de un `LaunchedEffect`
  — error ya cometido en este proyecto y anotado en el ROADMAP).
- Barra de progreso animada con `defaultSpatialSpec()`, redondeada, en tono terciario.
- El botón "Historial" se mueve a su propia fila para no competir con el hero.

### Notas de prudencia
- `gapSize` y `drawStopIndicator` de `LinearProgressIndicator` se descartaron: no se usan
  en ninguna parte del proyecto y no puedo compilar aquí para verificarlos.
- `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` añadido a `FillStep` —la anotación no
  se hereda entre funciones, que fue exactamente la causa del build rojo de la 0.8.2.

---

## [0.8.2-aspecto-calido] — 2026-08-06

Alinea la app con el diseño aprobado (mockup v2): paleta cálida completa, stepper de
barras y campos como cajas rellenas con su procedencia visible.

### Corregido — los contenedores de superficie salían en gris neutro
`Theme.kt` definía `surface` con tinte cálido (`#FFF8F5`) pero **no** los roles
`surfaceContainer*`. Material 3 los derivaba en gris neutro, así que las tarjetas y
secciones se veían frías sobre un fondo cálido — la incoherencia visual que se arrastraba
desde el principio.

- Añadidos `surfaceContainerLowest/Low/…/Highest` en claro y oscuro, continuando la rampa
  tonal del naranja de marca (`#FFFFFF`, `#FFF1EA`, `#FCEBE2`, `#F6E4DA`, `#F0DED4`).
- Afecta a **toda la app**, no solo al Relleno: es la base para propagar el estilo al resto
  de pasos.

### Cambiado — stepper de barras en lugar de círculos numerados
Con 4 pasos, las barras comunican avance de forma continua (cada paso es un tramo que se
llena) y **liberan altura vertical**, que en el formulario de Relleno es lo más escaso. El
tramo activo lleva degradado; la animación usa `motionScheme.defaultSpatialSpec()`.

### Cambiado — los campos se leen como cajas rellenas, con su procedencia
- El estado tiñe el **contenedor del propio campo** (`OutlinedTextFieldDefaults.colors`) en
  vez de pintar un fondo detrás: se acabó la doble superficie.
  `AI` → `tertiaryContainer` · `CONFLICT` → `errorContainer` · `WARN` →
  `surfaceContainerHighest` · vacío → `surfaceContainerLowest`.
- La procedencia pasa de texto suelto a **chip**, con los motores al lado.

### Limpieza
Eliminados 8 imports que quedaron huérfanos al reescribir el stepper y los campos
(`rememberInfiniteTransition`, `infiniteRepeatable`, `RepeatMode`, `LinearOutSlowInEasing`,
`tween`, `BorderStroke`, `scale`, `clip`, `background`, `Color`). Se usa
`defaultSpatialSpec()` —ya probado en este proyecto— en lugar de `slowSpatialSpec()`, que
no tenía uso previo aquí.

---

## [0.8.1-intrusos-y-persistencia] — 2026-08-06

Cierra los dos huecos que dejó la 0.8.0, ambos de seguridad real, y añade el pulido de
feedback que faltaba respecto al diseño aprobado.

### Corregido — la detección de documento intruso estaba escrita pero no conectada
`AutoFillPolicy.flagIntruders()` existía desde la 0.8.0 pero **no la llamaba nadie**: la
protección contra el escenario del documento de otro titular (caso real: el censal de otra
persona colado en el lote con el mismo nombre de archivo) no estaba activa.

- `FieldResolver` deduce ahora el **titular de cada documento** a partir de los paquetes de
  tipo `empresa` (los de tipo `persona` se ignoran a propósito: el NIE del representante de
  una S.L. es legítimamente distinto del CIF, y evaluarlo daría falsos positivos).
- El **titular esperado** es el que afirman más documentos. **Ante empate no se acusa a
  nadie** (conjunto vacío): un aviso falso enseña al usuario a ignorar los avisos.
- Los candidatos de un documento con otro titular se marcan `risky` con la nota "este
  documento parece de otro titular (X)" → nunca se autorrellenan y la nota se ve **en la
  propia alternativa** dentro de la hoja de decisión.
- Verificado contra los tres casos: lote de ZEB IN NISA SL con el censal intruso (lo
  detecta), lote limpio del autónomo (no marca nada) y empate (no acusa).

### Corregido — los estados de revisión no sobrevivían a la muerte del proceso
`fieldStates`, `fieldOrigins` y `fieldCandidates` no se persistían. Al restaurar una sesión
los valores volvían pero **los conflictos dejaban de estar marcados y el bloqueo del avance
desaparecía**: se podía llegar a Firma con un conflicto sin resolver.

- Los tres mapas se añaden a `PersistedWizardState` (`FieldState`, `FieldOrigin` y
  `FieldCandidate` pasan a `@Serializable`).
- `undoStack` **no** se persiste, a propósito: deshacer es de la sesión en curso.

### Añadido — feedback de las decisiones (v0.8.2 del plan, adelantado)
- **Snackbar de confirmación** con acción **DESHACER** tras elegir un candidato o marcar un
  campo como manual. Antes el cambio ocurría en silencio.
- **Badge por sección** ("N por decidir") en la cabecera de cada bloque del formulario, para
  no tener que buscar los campos pendientes bajando por todo el formulario.

---

## [0.8.0-relleno-unificado] — 2026-08-06

### Cambiado — el asistente pasa de 5 a 4 pasos: "Revisión IA" se funde en "Relleno"
La pantalla de Revisión IA obligaba a leer tarjetas abstractas de bloques, aplicarlas a
ciegas y luego bajar al formulario a comprobar el resultado. Ahora la extracción va
**directa al formulario, ya prerrellenado**, con el estado de cada campo visible en el
propio campo.

- **`Step`**: `CONTRATO · DOCUMENTOS · RELLENO · FIRMA` (antes 5, con `REVISION` en el 2).
- **`ReviewStep.kt` eliminado** (`git rm`) junto con su rama en `WizardScreen` y el
  `applyPackage()` del ViewModel, que solo usaba esa pantalla.
- **Migración de sesiones persistidas** (`PersistedWizardState`): el paso se guarda como
  índice, así que al quitar uno todos los posteriores se desplazan. Nuevo campo
  `schemaVersion` + `migrateStepIndex()`: `REVISION(2)→RELLENO(2)`, `RELLENO(3)→RELLENO(2)`,
  `FIRMA(4)→FIRMA(3)`. Sin esto, una sesión guardada en Relleno reabría en **Firma** y una
  guardada en Firma caía a **Contrato**, perdiendo el trabajo.

### Añadido — autorrelleno con control de procedencia (`AutoFillPolicy` + `FieldResolver`)
Nuevos estados por campo (`FieldState`): `AI` (autorrellenado), `CONFLICT` (documentos que
se contradicen), `WARN` (procedencia dudosa), `USER`, `EMPTY`.

- **No se autorrellena por consenso de motores a secas.** `Candidate.sources` son MOTORES,
  no documentos, y en producción solo hay dos activos → el consenso máximo es 2. Un
  documento intruso (caso real: el censal de otra persona con el mismo nombre de archivo)
  lo leen ambos motores y produce consenso 2/2, el máximo, con el dato equivocado.
  `AutoFillPolicy` mira además **de qué documento** sale el valor y si esa fuente es
  legítima para ese campo (un IBAN de un contrato de alquiler es del arrendador; un
  representante del Modelo 036 suele ser la gestoría) → esos casos quedan en `WARN`.
- **Conflictos y dudosos NO se rellenan solos** y **bloquean el avance a Firma**: el botón
  pasa a "Resuelve N campos". Son justo los que provocan los errores caros.
- **`FieldOrigin`**: cada valor recuerda su documento de origen y los motores que lo
  respaldan; se muestra bajo el campo.

### Añadido — decisión y deshacer en el propio formulario
- **`CandidateSheet`** (`ModalBottomSheet`): al tocar un campo en conflicto o dudoso se
  abre la hoja con las alternativas, **cada una con su documento de origen**, sus motores
  y los campos enlazados que rellenará (elegir una dirección rellena CP/Población/Provincia
  en la misma acción, evitando medias direcciones mezcladas).
- **Deshacer** (`undoStack`, máx. 20): toda escritura pasa por `pushUndo`, así que cualquier
  elección o edición es reversible desde el botón de la barra inferior. No se persiste: es
  de la sesión en curso.
- **Aviso de pendientes** en la cabecera del Relleno, con el número de campos por decidir.

### Notas
- `PackageApplier` se conserva (utilidad pura, sin usos activos): `FieldResolver` resuelve
  el destino fiscal/`_2` por sí mismo a partir del `tipo` del paquete.
- Sin cambios en el prompt ni en el JSON de la IA → **la paridad con la web no se ve
  afectada** por esta tanda.

---

## [0.7.10-dns-fallback-doh] — 2026-08-05

### Corregido — "Unable to resolve host datingtrck.com" pese a que el dominio resuelve bien
Tras el cambio de servidor, la app fallaba con `No address associated with hostname`
**estando en WiFi**, mientras el dominio resolvía perfectamente desde fuera (registro A
presente: `107.6.184.117`; sin AAAA, como ya se sabía).

**Diagnóstico**: NO era el filtro IPv4. El error se lanza dentro de `Dns.SYSTEM.lookup()`,
antes de que el filtro llegue a ejecutarse, y el filtro además nunca deja la lista vacía
(`ifEmpty { all }`). La causa real es **caché DNS negativa** en el dispositivo o en el
router/ISP, cacheada mientras la zona propagaba (serial SOA `2026080506`, cambiada ese
mismo día). Agravante: el **TTL de caché negativa del SOA es 86400 → hasta 24 horas** con
la app inservible aunque el servidor esté perfecto.

**Solución**: `Ipv4PreferredDns` gana un fallback a **DNS-over-HTTPS**. Si el resolutor del
sistema falla, la app resuelve por su cuenta saltándose el resolutor de la red.
- Endpoints por **IP literal** a propósito (`https://1.1.1.1/dns-query`,
  `https://8.8.8.8/resolve`): con hostname harían falta DNS para resolverlos — justo lo que
  está roto (círculo vicioso). Los certificados de Cloudflare y Google incluyen esas IPs en
  el SAN, así que **la validación TLS sigue activa; no se desactiva ninguna verificación**.
- Cliente OkHttp propio y mínimo, con `Dns.SYSTEM` (sin recursión) y timeouts cortos
  (5s/5s, call 8s): es un camino de rescate, no debe colgar la extracción.
- Caché en memoria de 5 min para no repetir la consulta DoH en cada conexión.
- IPv4 se construye a mano desde el texto (`InetAddress.getByAddress`) para NO volver a
  pedir DNS al crear la dirección.
- **Sin dependencias nuevas** (no hace falta `okhttp-dnsoverhttps`).

Orden: sistema (rápido, normal) → DoH (solo si el sistema falla). En operación normal el
DoH nunca se usa.

---

## [0.7.9-tipo-documento-por-ia] — 2026-08-05

### Añadido — la IA tipifica fotos y escaneos (opción B, completa la 0.7.8)
La detección local por contenido (0.7.8) solo cubre PDFs con capa de texto. Las fotos de
DNI/NIE (jpg/png) y los PDFs que son solo escaneo-imagen salían como "Documento" porque no
hay texto que leer. Ahora los tipifica la propia IA, que ya los está mirando con visión.

- **Prompt**: nuevo campo `tipo_documento` en el JSON de salida, con **vocabulario cerrado**
  (mismas etiquetas que usa la detección local) para que los motores no devuelvan cada uno
  una variante distinta. Se indica explícitamente que no afecta a los valores extraídos y
  que anverso/reverso de un DNI/NIE comparten etiqueta.
- **Modelo**: `AiExtraction.tipo_documento` (nullable, default null → motores que no lo
  devuelvan siguen funcionando igual).
- **MultiAiExtractor**: nuevo callback `onDocTypeDetected(docLabel, tipo)`, emitido en
  cuanto un motor devuelve el campo. Default no-op (no altera llamadores existentes).
- **WizardViewModel**: `docTypeByName` pasa a mutable. **La detección local tiene
  prioridad** — el tipo de la IA solo rellena huecos ("Documento"), así que un motor que se
  equivoque no pisa un PDF ya tipificado correctamente en local. El sufijo `(parte X/Y)` de
  los archivos troceados se normaliza al nombre base antes de casar en el mapa.

### Notas
- Para fotos, el tipo aparece **al responder el primer motor** (no antes): hasta entonces
  se muestra "Documento". Es inherente a que lo resuelva la IA.
- **Paridad con la app web**: este cambio toca `ExtractionPrompt`. Para mantener la simetría
  hay que replicar el campo `tipo_documento` en el prompt de la versión web.

---

## [0.7.8-tipo-documento-por-contenido] — 2026-08-05

### Añadido — tipo de documento DETECTADO POR CONTENIDO en el diálogo "Analizando con …"
El diálogo mostraba `document:17077` (el ID crudo del proveedor SAF). Ahora muestra el
TIPO del documento leyendo su contenido, no su nombre de archivo.

- **Detección por contenido**: `DocumentLoader.firstPagesText()` extrae el texto de las
  primeras páginas del PDF con PDFBox (`PDFTextStripper`), y `DocumentTypeDetector.fromContent()`
  lo tipifica por frases-firma → "Certificado de situación censal", "Modelo 036",
  "Tarjeta CIF/NIF", "Certificado bancario", "Alta en RETA", "Contrato de alquiler",
  "Escritura de constitución", "NIE / Permiso de residencia", "DNI", "Pasaporte". El nombre
  de archivo es irrelevante: da igual que llegue como `DOC-20260716-WA0015.PDF` (nombre de
  export de WhatsApp) — decide lo que dice dentro. Reglas ordenadas para evitar cruces (el
  036 menciona "tarjeta acreditativa"; contrato y RETA mencionan un IBAN).
- **Nombre real del fichero → IA**: `resolveDisplayName()` (vía `OpenableColumns.DISPLAY_NAME`)
  sigue alimentando `docNames`, que viaja a la IA como contexto (la "Regla de nombres de
  archivo" del prompt). El nombre real NO se muestra en la UI; solo va a la IA.
- **Desacople UI/IA**: la IA recibe el nombre de archivo real; la UI muestra el tipo por
  contenido (mapa `docTypeByName` en `WizardViewModel`, aplicado en `onProviderStart`).

### Notas / límites
- Solo PDFs con capa de texto (todo el papeleo oficial: censal, tarjeta NIF, 036, banco,
  contrato, RETA). **Fotos de DNI/NIE (jpg/png) y PDFs que son solo escaneo-imagen no
  tienen texto** → "Documento" (sin OCR no hay contenido). Tipificar también esos requiere
  que la IA (visión) devuelva el tipo (fase posterior, toca el prompt → replicar en la web).
- Todo defensivo: cualquier fallo de lectura/tipificación → "Documento", nunca peta.

---

## [0.7.7-estructura-detectada-paso1] — 2026-08-04

### Añadido — resumen "Estructura detectada" en el Paso 1 (Contrato)
La app web, al elegir el contrato por defecto o subir uno propio, actualizaba la parte
inferior del primer paso con campos detectados, páginas y huecos de firma, antes de dar
a "Continuar". Android no tenía nada de esto — el contrato por defecto mostraba solo el
texto estático "54 páginas" y el propio no mostraba ningún resumen.

- **`SignaturePageDetector.Detection`**: nuevo campo `totalPages` (ya se calculaba
  internamente como `doc.numberOfPages`, solo faltaba exponerlo).
- **`WizardViewModel.detectSignaturePages()`**: ahora también actualiza
  `state.totalPages`, y expone `state.detectingStructure` (estado de carga) mientras
  analiza el PDF en segundo plano.
- **`ContractStep.kt`**: nueva tarjeta "Estructura detectada" bajo las opciones de
  contrato, con indicador de carga mientras analiza y, al terminar, "N páginas · M
  campos · K huecos de firma". Para el contrato por defecto, M viene de
  `ContractFields.CANON.size`; para uno propio, de `state.userFieldNames.size` (ya se
  leía al elegir el PDF, solo faltaba mostrarlo). Si un PDF propio no tiene ningún
  campo AcroForm, se avisa explícitamente en vez de mostrar "0 campos" sin más contexto.

---

## [0.7.6-banco-tercero-alquiler-sugerencias] — 2026-08-04

### Corregido — motores no mueren para siempre por un fallo pasajero
(Fix pendiente de una entrega anterior, aplicado ahora junto con el resto para no
desincronizar versiones.) `MultiAiExtractor.kt`: solo se marca un motor como muerto
permanente en `HttpException` con código 401/403/404. Todo lo demás (429, 5xx, 413,
excepciones sin código HTTP) se trata como pasajero: se salta ese documento para ese
motor, pero se sigue intentando en los siguientes. Groq recibe solo la primera
página/imagen del grupo (su límite de 8000 tokens/minuto revienta con varias imágenes
juntas — confirmado con HTTP 413 real en producción).

### Corregido — certificado de un CIF de tercero (banco) confundido con "empresa"
Diagnosticado con el certificado de situación censal real de un titular NIE (autónomo):
el certificado de IBAN de CaixaBank contiene el CIF real del banco (A-08663619). La
regla de contexto del prompt no distinguía explícitamente "CIF de la empresa
distribuidora" de "CIF de un tercero que simplemente certifica" — el sistema podía
concluir "hay una empresa en el conjunto" a partir del CIF del BANCO, tratando
erróneamente al titular autónomo como representante de una empresa inexistente y
suprimiendo su propia dirección fiscal.

**Fix** (`ExtractionPrompt.kt`):
- Bloque de contexto y regla "TITULAR AUTÓNOMO" reforzados: un CIF de tercero (banco,
  notaría, gestoría) NUNCA cuenta como "documento de empresa" del distribuidor.
- Nueva regla explícita "CERTIFICADO BANCARIO / IBAN": el banco nunca genera un paquete
  tipo "empresa"; de ese documento solo se extrae el IBAN del titular.

### Añadido — alternativas de dirección de actividad cuando el censal no la tiene
Confirmado con el documento real: algunos certificados de situación censal solo listan
actividades con código y fecha, sin ninguna dirección de local distinta de la fiscal.
Nueva regla "DIRECCIÓN DE ACTIVIDAD/COMERCIO SIN DOCUMENTO PROPIO": si no hay dirección
de actividad explícita, y el conjunto tiene un DNI/NIE con domicilio propio o un
CONTRATO DE ALQUILER (nuevo tipo de documento reconocido) con la dirección del local
arrendado, se proponen como ALTERNATIVAS candidatas para "direccion_comercio" (nunca
automáticas) — el usuario decide si las aplica.

### Añadido — ver más sugerencias al tocar un campo ya rellenado (Paso 4 · Relleno)
`FillStep.kt`: los campos que ya tienen valor (autorrellenados por un paquete aplicado
o el candidato de mayor consenso) ahora muestran un icono desplegable si la IA propuso
más de un candidato para ese campo — al tocarlo, se abre un menú con las alternativas
(valor + motores que la propusieron) sin tener que volver al paso de Revisión IA.

### Roadmap
Documentada la idea de mandar TEXTO extraído del PDF en vez de imagen rasterizada
(cuando el PDF tiene capa de texto real, como los certificados de la AEAT) — la app web
ya lo hace para Groq, Android nunca lo ha aprovechado. Requiere una librería de
extracción de texto nueva; queda como tanda futura en `ROADMAP.md`.

---

## [0.7.5-forzar-ipv4-fix-5g] — 2026-08-03

### Corregido — "Unable to resolve host" en redes 5G, funcionando bien en el navegador
Reportado y diagnosticado con datos reales: la app fallaba con "Unable to resolve host
datingtrck.com" en red 5G, mientras que el navegador del mismo dispositivo, misma red,
conectaba sin problema a la misma URL exacta.

**Causa raíz confirmada**: `datingtrck.com` no tiene registro DNS AAAA (IPv6) — verificado
consultando `https://dns.google/resolve?name=datingtrck.com&type=AAAA`, cuya respuesta
solo trae el SOA en "Authority" sin ningún "Answer". En redes móviles IPv6-only (habitual
en 5G, con NAT64), el sistema sintetiza una dirección IPv6 falsa para alcanzar servidores
que solo tienen IPv4 — esa síntesis puede fallar en una conexión de socket cruda (OkHttp)
aunque el navegador, con mecanismos de repliegue más robustos (Happy Eyeballs), sí
consiga conectar.

**Fix**: `Ipv4PreferredDns.kt` (nuevo) — implementación de `okhttp3.Dns` que pide al
sistema todas las direcciones y se queda solo con las IPv4 si hay alguna, sin depender de
que la síntesis NAT64 del operador funcione bien. Conectado en `AppModule.kt` vía
`.dns(Ipv4PreferredDns())` en el `OkHttpClient`. Si por algún motivo no hubiera ninguna
IPv4 disponible, no rompe nada — cae a lo que devuelva el sistema.

### Nota sobre la causa de fondo
El hosting real es BanaHosting (`europe121.banahosting.com`), gestionado desde el cPanel
de `mejoresiagratis.com`. Si en algún momento se añade un registro AAAA real para
`datingtrck.com` (requiere que BanaHosting dé IP pública IPv6 en el plan contratado), este
fix seguiría funcionando sin cambios — simplemente dejaría de ser necesario, pero no
hace daño mantenerlo como red de seguridad para cualquier otro dominio que en el futuro
tampoco tenga AAAA.

---

## [0.7.4-prompt-formato-nif-irpf-censal] — 2026-08-03

### Auditoría completa de cobertura de campos (sin cambios de código — confirmación)
Comparado `ContractFields.CANON` (21 campos) contra los 26 campos reales del AcroForm
de `contrato-base.pdf` (extraídos con `pypdf`): coinciden exactamente. Los 5 restantes
son 3 checkboxes gestionados aparte, el autorrelleno de Responsable, y el campo
"Profesión..." descartado a petición de Pablo. **No hay ningún campo de texto real sin
cubrir** — el problema de fondo no era de cobertura de `CANON`.

### Corregido — bug real confirmado con documento aportado (Certificado de Situación
### Censal de un autónomo con NIE)
Se generaba un paquete "Representante" a partir del documento de identidad de un titular
autónomo (NIE), en vez de reconocer que esa misma persona ES el titular — violando la
regla ya existente de "titular autónomo sin representante distinto". Causa: la regla de
"domicilio personal ≠ fiscal de empresa" solo mencionaba explícitamente documentos
"DNI/NIE/pasaporte", sin cubrir con la misma claridad un **certificado censal
individual** (que también muestra domicilio y actividad de una persona física).

### Añadido al prompt (`ExtractionPrompt.kt`)
- **Regla generalizada**: la protección de "domicilio personal ≠ fiscal de empresa"
  ahora cubre explícitamente CUALQUIER documento centrado en una persona física
  (censal individual incluido), no solo DNI/NIE/pasaporte.
- **Heurística de formato de NIF/CIF/NIE**: letra inicial de persona jurídica
  (A,B,C,D,E,F,G,H,J,N,P,Q,R,S,U,V,W) = CIF; letra X/Y/Z = NIE; 8 dígitos sin letra
  inicial = DNI/NIF. Señal fiable por sí sola, útil cuando el documento no lo dice
  explícitamente.
- **Heurística IRPF vs Impuesto de Sociedades**: un certificado censal que mencione
  IRPF es SIEMPRE de persona física (una empresa nunca paga IRPF, paga Impuesto de
  Sociedades). Señal cruzada con el formato del NIF/CIF para determinar
  `tipo_identificacion` con más fiabilidad.
- **Desempate de "Actividad principal"** cuando varias actividades comparten la misma
  fecha de alta (sin señal que desempate): usar la primera listada en el documento, en
  su mismo orden.
- Bloque de contexto de conjunto de documentos generalizado igual (censal individual
  reconocible por IRPF + formato de NIF, no solo por el nombre del archivo).

### Aviso pendiente
Este prompt es compartido con la app web (`rellenador-pro.html`) por diseño — el
comentario de cabecera de `ExtractionPrompt.kt` ya avisa de esto. Estos mismos 4
refuerzos deberían aplicarse también allí para no desincronizar el comportamiento entre
ambas apps.

---

## [0.7.3-migracion-datingtrck] — 2026-08-03

### Cambiado — migración de dominio del proxy (mejoresiagratis.com → datingtrck.com)
Auditado el repo entero buscando el dominio antiguo (`mejoresiagratis.com`) — se
distinguió cuidadosamente del paquete Android (`com.mejoresiagratis.rellenador`, que
aparece en la cabecera de cada archivo `.kt` pero NO es una URL y no se toca: cambiar
el `applicationId` rompería continuidad de instalación/actualización en Play Store).
Solo 3 apariciones reales de la URL del dominio en todo el código:

- **`app/build.gradle.kts`**: `PROXY_BASE_URL` (la URL de fábrica compilada en el
  APK) — de `https://mejoresiagratis.com/pdf/` a `https://datingtrck.com/pdf/`.
- **`AjustesScreen.kt`**: texto de ayuda bajo "URL del proxy IA" que describe la URL
  de fábrica — actualizado para no mentir sobre cuál es el valor por defecto real.
- **`network_security_config.xml`**: solo un comentario descriptivo (`<!-- ... -->`),
  sin ninguna regla de dominio restringida — el `base-config
  cleartextTrafficPermitted="false"` ya aplicaba HTTPS-only a cualquier dominio, así
  que este cambio es puramente documental, sin efecto funcional.

### Nota sobre el override manual existente
Quien ya haya guardado `https://datingtrck.com/pdf/` a mano en Ajustes (override local
por dispositivo, vía DataStore) no necesita hacer nada — su override sigue
funcionando igual y ahora además coincide con el nuevo valor de fábrica. Esta
actualización solo afecta a instalaciones NUEVAS del APK que nunca hayan tocado ese
campo.

---

## [0.7.2-preview-antes-scroll-sin-ajuste-pag24] — 2026-07-24

### Corregido — scroll llegaba antes de que la previsualización reflejara la firma
`stampOnePage()`/`stampAllPages()` solo actualizaban la lista de estampas en memoria —
la previsualización real (el PDF renderizado que se ve en pantalla) solo se reconstruye
llamando aparte a `buildPreview()`, que nadie llamaba tras estampar. Los botones "🎯 Una
a una" y "⚡ Todos" hacían scroll a la página estampada INMEDIATAMENTE, antes de que esa
reconstrucción ocurriera — la página podía verse un instante sin la firma, dando la
sensación de que no se había estampado.

- **`WizardViewModel.rebuildPreviewNow()`** (nuevo, `suspend`): misma lógica que
  `buildPreview()` pero awaitable desde la propia corrutina de la UI. `buildPreview()`
  se mantiene igual para el botón "Actualizar previsualización" (fire-and-forget).
- **`SignatureStep.kt`**: ambos botones ahora hacen `vm.rebuildPreviewNow()` y ESPERAN
  a que termine antes de `previewListState.animateScrollToItem(...)` — el scroll llega
  siempre a una página que ya muestra la firma recién estampada.

### Quitado
- **Sección "Ajuste en la página 24 (posición y tamaño)"** con sus 3 sliders
  (Horizontal/Vertical/Tamaño) — retirada de la UI a petición de Pablo. La función
  `WizardViewModel.updateStamp()` se mantiene intacta por si se retoma en el futuro; el
  composable `LabeledSlider` (ya sin ningún uso) se eliminó de `SignatureStep.kt`.

---

## [0.7.1-agrupar-paginas-por-archivo] — 2026-07-24

### Corregido — causa real de la lentitud frente a la versión web
Reportado: la revisión con IA es mucho más lenta en Android que en la web. Auditado el
código real de ambas para confirmar la causa exacta (no por suposición):

- **Android** (hasta v0.7.0): cada página de un PDF se mandaba en una llamada
  independiente por motor. Un PDF de 13 páginas × hasta 3 motores = hasta 39 peticiones
  de red secuenciales para un solo archivo.
- **Web** (`rellenador-pro.html`, línea 1058): cada archivo se guarda como UN único
  "doc" (`S.docs.push({name, mime, b64})`) — el PDF entero, sin partir por páginas — y
  se manda así en cada llamada.

Ambas versiones iteran documento y motor en serie (ninguna paraleliza), así que la
diferencia real no es concurrencia — es el NÚMERO de llamadas por archivo.

### Cambiado
- **`MultiAiExtractor.extract()`**: ahora recibe `docGroups: List<List<DocPayload>>`
  (un grupo = todas las páginas de un mismo archivo) en vez de una lista aplanada de
  páginas sueltas. Cada grupo se manda en UNA sola llamada por motor (varias imágenes
  dentro del mismo `docs` de `ProxyRequest` — el proxy ya soportaba esto desde siempre,
  solo el cliente Android no lo aprovechaba).
- **`WizardViewModel.runExtraction()`**: construye `docGroups`/`docNames` uno por
  archivo, ya no aplana por página.
- **`ExtractionPrompt.kt`**: aclarado que un documento puede llegar como varias
  imágenes seguidas (sus páginas) que hay que tratar como partes de un único
  documento, no como documentos distintos.

### Corregido — límite de páginas del proxy que truncaba en silencio
- **`ai-proxy.php`**: `MAX_DOCS` subido de 12 a 20. Con el límite anterior, agrupar
  las páginas de un archivo de 13+ páginas en una sola llamada habría hecho que el
  proxy truncara el array `docs` en silencio (`array_slice`), perdiendo páginas sin
  ningún aviso.
- **`MultiAiExtractor.MAX_PAGES_PER_CALL = 20`** (nuevo, debe mantenerse sincronizado
  con `MAX_DOCS` del proxy): si un archivo excede este límite (caso raro — contratos o
  escrituras de más de 20 páginas), se trocea en sub-lotes con su propia etiqueta
  "(parte X/Y)" en vez de mandarlas todas juntas o perder páginas.

### Aviso honesto
Un archivo con muchas páginas de alta resolución en una sola llamada aumenta el tamaño
de esa petición (más imágenes en base64 en el mismo cuerpo JSON). Para los casos
habituales (Modelo 036, escrituras, certificados — pocas páginas) no debería notarse.
Si algún día se sube un documento con páginas muy pesadas y muchas de ellas, vigilar
que no se acerque al límite de `MAX_BODY` (20 MB) del proxy.

---

## [0.7.0-retocar-firma-y-reintentar] — 2026-07-24

### Añadido — herramientas reales para aislar mejor el trazo de la firma
Reportado con fotos reales (firma con fondo de patrón de seguridad tipo DNI/carné): el
procesado podía confundir una línea impresa/guía cercana a la firma con parte del trazo
manuscrito. Tres mejoras, ninguna sustituye a las otras — se complementan:

1. **Prompt de `SignatureLocator` reforzado**: ahora instruye explícitamente ignorar
   líneas rectas impresas, líneas de puntos, rayas guía ("firme aquí"), marcas "X"
   pre-impresas, sellos, y el fondo/patrón de seguridad típico de carnés y documentos de
   identidad — aunque estén muy cerca del trazo real o lo toquen.
2. **"🔄 Volver a intentar con IA"** (nuevo botón, solo visible con "Mejorar con IA"
   activado y una foto ya procesada): vuelve a llamar a `SignatureLocator` sobre la
   MISMA foto. Los modelos de visión no son perfectamente deterministas — una segunda
   pasada puede acertar una caja más ajustada sin tener que rehacer la foto.
   `WizardViewModel.retryAiExtraction()`.
3. **"🧹 Retocar firma"** (nuevo, `SignatureEraserDialog.kt`): editor táctil que permite
   borrar a mano (poner transparente) cualquier parte del resultado ya procesado que no
   sea el trazo real — la solución definitiva para cualquier caso que ni la IA ni el
   umbral resuelvan bien, sea cual sea la causa exacta. Arrastra o toca para borrar,
   con control de grosor de pincel, "Deshacer" (hasta 15 pasos) y "Guardar"/"Cancelar".
   Usa el mismo mapeo de coordenadas consciente del letterbox de `ContentScale.Fit` que
   ya se corrigió en `SignatureCropDialog` (ver v0.6.7).

### Decisión de diseño explícita
Tras usar "Retocar firma", los ajustes de color de tinta y fondo (`setInkColor`/
`setSigBackground`) quedan inactivos — `WizardViewModel.applyErasedSignature()` pone
`rawSignatureBitmap = null` a propósito. Si no lo hiciéramos, cambiar el color después
de retocar volvería a reprocesar el bitmap CRUDO original (sin el borrado) y el retoque
se perdería en silencio. Mejor que esos ajustes queden sin efecto tras retocar —no
rompen nada— que perder el trabajo de borrado sin aviso.

---

## [0.6.10-fix-despeckle-corta-puntas] — 2026-07-16

### Corregido — regresión real introducida por el propio despeckle de v0.6.9
Probado con foto real: tras aplicar el filtro de motas de ruido de v0.6.9, la firma
seguía cortándose, ahora por arriba **y** por abajo — un síntoma distinto al original,
que apuntaba a una causa nueva en vez de a la de antes.

**Causa raíz confirmada**: `despeckle()` etiquetaba componentes conexas directamente
sobre la máscara de "es tinta". Una extremidad fina del propio trazo (la parte superior
de una "S", el rabillo final de una "D") puede quedar conectada al resto por apenas 1-2
píxeles debido al antialiasing del umbral — si esa conexión se rompía justo ahí, la
extremidad se convertía en SU PROPIA componente pequeña y el filtro la borraba como si
fuera ruido. Como esto puede pasar en cualquier punta del trazo, cortaba arriba y abajo
por igual.

**Fix**: `despeckle()` ahora DILATA la máscara (radio 2px) antes de etiquetar
componentes — una conexión de 1 píxel se "engorda" lo suficiente para no partirse en
dos. El tamaño que decide si un componente se conserva sigue contando SOLO los píxeles
ORIGINALES de tinta (la dilatación solo decide qué va junto, nunca añade tinta de más
al resultado final). Esto conserva las motas de ruido genuinamente aisladas fuera
(siguen sin conectar con nada) mientras deja de cortar las puntas finas del trazo real.

---

## [0.6.9-firma-margen-despeckle] — 2026-07-16

### Corregido — firma cortada por arriba y con motas de ruido
Reportado con foto real: al recortar una firma con poco margen de papel alrededor
(recorte ajustado), el resultado procesado salía con la parte superior de las letras
cortada y con puntos de ruido dispersos.

**Causa 1 — corte por arriba**: `flattenIllumination()` estima el fondo reduciendo y
ampliando la imagen entera. Con muy poco margen real de papel limpio, esa estimación
queda contaminada por la propia tinta cerca de los bordes, y los trazos más finos
(p.ej. la parte superior de una letra, con menos presión de bolígrafo) acaban justo por
debajo del umbral de Otsu y se pierden.

**Fix**: `SignatureProcessor.padWithWhiteMargin()` (nuevo, privado) añade un margen
blanco sintético (25% del ancho/alto, mínimo 12px) antes de `flattenIllumination()`.
Le da a la estimación de fondo zonas fiables de papel limpio cerca de cada borde. El
recorte final a bounding-box del trazo real (ya existente en `processInk()`) vuelve a
ajustar el resultado, así que el margen añadido no queda en el resultado final — solo
mejora la calibración del paso intermedio.

**Causa 2 — motas de ruido**: no había ningún filtro que distinguiera "textura del papel
o grano de la foto que pasa el umbral" de "trazo real de la firma" — cualquier píxel
oscuro aislado se quedaba en el resultado.

**Fix**: `SignatureProcessor.despeckle()` (nuevo, privado) etiqueta componentes conexas
(8-conectividad, para no partir trazos cursivos en diagonal) sobre la máscara de tinta y
descarta las que tengan menos de 12 píxeles — el trazo real de una firma es, con mucha
diferencia, la componente más grande; una mota de ruido son unos pocos píxeles sueltos.
`processInk()` se reestructuró en dos pasadas (máscara + despeckle, luego tintado) para
poder aplicar el filtro antes de calcular el bounding-box final.

### Aviso honesto
El umbral de 12 píxeles para descartar una componente es un compromiso: en signatures
con acentos o puntos deliberados muy pequeños y desconectados del trazo principal (poco
habitual, pero posible), podría eliminar también esa marca intencional junto con el
ruido. Si esto se observa en la práctica, el valor es fácilmente ajustable
(`despeckle(..., minPixels = N)`).

---

## [0.6.8-foto-completa-sin-relocalizar] — 2026-07-16

### Corregido — "Foto completa" recortaba de más
Reportado con foto real: al subir una firma ya recortada casi sin margen y pulsar
"Foto completa" en el diálogo de recorte, el resultado salía cortado, perdiendo trazos.

**Causa raíz**: el botón "Foto completa" llamaba a `extractSignatureFromPhoto()`, que
**siempre** pasa primero por la IA de localización (`SignatureLocator`) antes de recortar
y procesar. Si la foto ya es un recorte ajustado (poco margen), es habitual que la IA
devuelva una caja menor al 100% aunque el prompt le pida `{x:0,y:0,w:100,h:100}` cuando
toda la imagen es la firma — el modelo no siempre lo sigue al pie de la letra. Esa caja
más pequeña se usaba para recortar OTRA VEZ por encima de la decisión del usuario de
"usar la foto entera", cortando la firma.

**Fix**: nueva función `WizardViewModel.useWholePhotoAsSignature()` — mismo pipeline que
`useManualSignatureCrop()` (aplanado + Otsu + recorte a bounding-box del trazo real) pero
**sin pasar nunca por la IA de localización ni por `sigProcessor.crop()`**. El botón
"Foto completa" ahora es fiel a su nombre: usa la foto tal cual el usuario la ve, sin que
ningún motor decida recortar más por su cuenta.

---

## [0.6.7-fix-recorte-firma-letterbox] — 2026-07-16

### Corregido — bug real de recorte manual de firma deformado
Reportado con capturas: al recortar manualmente una firma con el dedo (pantalla "Recorta
la firma"), el rectángulo se veía perfecto sobre la foto en pantalla, pero la firma
resultante en la previsualización salía deformada/descuadrada.

**Causa raíz confirmada**: `SignatureCropDialog.kt` mostraba la foto con `Image(...)` sin
especificar `contentScale`, que por defecto es `ContentScale.Fit` — la foto se encaja
proporcionalmente dentro del contenedor, con márgenes (letterbox) si la proporción de la
foto no coincide con la del contenedor. Pero el cálculo del recorte al pulsar "Confirmar"
asumía que la foto ocupaba el contenedor entero y estirada (`scaleX = photo.width /
containerSize.width`, `scaleY` análogo) — correcto solo si fuera `ContentScale.FillBounds`.
Con letterbox presente (el caso normal, ya que casi ninguna foto de móvil coincide en
proporción con el recuadro de recorte), la posición arrastrada en pantalla no se traducía
correctamente a píxeles reales de la foto.

**Fix**: se calcula el rectángulo real donde `ContentScale.Fit` dibuja la foto dentro del
contenedor (ancho/alto renderizado + offset de letterbox según qué eje se ajusta), y se
invierte esa transformación exacta al mapear cada punto arrastrado a coordenadas de píxel
real. Los puntos que caen en el margen de letterbox se acotan (`coerceIn`) al borde de la
foto en vez de producir coordenadas fuera de rango.

---

## [0.6.6-ocultar-campos-resueltos] — 2026-07-16

### Cambiado — Revisión IA (Paso 3)
Al aplicar un bloque o elegir un candidato de un campo suelto, la tarjeta de ese campo
seguía apareciendo en la lista "Campos" — duplicando visualmente algo ya resuelto.

- **`ReviewStep.kt`**: los `FieldProposal` cuyo `fieldKey` ya tiene valor en
  `fieldValues` (`!isNullOrBlank()`) se separan de los pendientes y se muestran dentro
  de un `ExpressiveAccordion` "Ya resueltos · N", plegado por defecto.
- No se ocultan del todo (perdería la capacidad de reconsiderar una elección): siguen
  siendo `ProposalCard` completos con sus chips de candidato, solo que agrupados aparte
  y colapsados para no ensuciar la vista principal.
- Si TODOS los campos propuestos quedan resueltos, se muestra un mensaje corto en vez
  de la lista "Campos" vacía.
- Reutiliza el mismo `ExpressiveAccordion` compartido que ya usan Documentación y
  Firma — coherencia visual, cero componente nuevo.

---

## [0.6.5-persistencia-sesion] — 2026-07-15

### Diagnóstico del problema real
`WizardViewModel._state` era un `MutableStateFlow` puramente en memoria, sin
`SavedStateHandle` ni persistencia a disco. `hiltViewModel()` sobrevive rotaciones, pero
**no sobrevive la muerte del proceso** — algo habitual en fabricantes que gestionan
agresivamente la batería en segundo plano (Honor, Xiaomi, Huawei…). Al volver a primer
plano tras la muerte del proceso, Android recrea la Activity desde cero y el ViewModel
arranca en su estado inicial: todo el progreso se pierde.

### Añadido — Persistencia de sesión (Fase 1 del roadmap)
- **`PersistedWizardState`** (nuevo, `data/repository/`): DTO `@Serializable` plano que
  aísla lo que merece persistirse (paso, documentos, extracción, campos rellenados,
  firma, huecos, estampas) de lo transitorio (`busy`, progreso de extracción en vivo,
  previsualización, motores disponibles — estos se recargan por su cuenta). Incluye
  `toPersisted()`/`applyTo()` para convertir en ambas direcciones sin tocar los modelos
  originales (`SignatureData`, `SignatureStamp`, `Paquete` no eran `@Serializable`).
- **`PrefsRepository`**: `saveWizardSession()`, `loadWizardSession()`,
  `clearWizardSession()` — mismo DataStore que ya usa el resto de la app.
- **`WizardViewModel`**: `observeStateForAutosave()` guarda a disco cada cambio relevante
  (con `distinctUntilChanged`); `restoreSessionIfAny()` restaura al arrancar, DESPUÉS de
  cargar providers/responsable para no pisarlos; `resetSession()` borra la sesión y
  vuelve al Paso 1.
- **Aviso de URIs inválidos**: si al restaurar un documento ya no es accesible (el
  proceso murió sin que se hubiera tomado permiso persistente sobre el URI), se filtra
  de la lista y se avisa por snackbar con los nombres afectados, en vez de fallar en
  silencio o mostrar un documento fantasma.
- **"Empezar de nuevo"** en Ajustes (con diálogo de confirmación).
- **"Empezar otro contrato"** en el paso de Firma, tras generar el PDF (con diálogo de
  confirmación) — solo se ofrece cuando ya hay un PDF generado, para no invitar a
  descartar progreso en curso sin querer.

### Aviso honesto — lo que esta fase NO resuelve
Los `Uri` de documentos (PDFs/fotos aportados en el Paso 2) siguen dependiendo de que el
proveedor de contenido (galería, gestor de archivos) mantenga el permiso de lectura tras
la muerte del proceso. Hoy la app no llama a `takePersistableUriPermission()`, así que en
algunos casos el usuario tendrá que volver a añadir los documentos (con aviso claro, no
en silencio). La solución definitiva — copiar los documentos a almacenamiento privado de
la app al añadirlos — queda como **Fase 2**, documentada en `ROADMAP.md`.

### Añadido — ROADMAP.md
Nuevo documento en la raíz del repo con el estado real de versiones completadas y las
tandas pendientes, priorizadas. Sustituye al roadmap informal que vivía solo en el
contexto de las sesiones de Claude.

---

## [0.6.4-firma-navegacion-huecos-feedback] — 2026-07-15

### Corregido — previsualización duplicada en modo Dibujar
La caja gris con la firma azul que aparecía justo debajo del canvas de dibujo (añadida en
v0.6.3) estaba duplicando la información: `SignatureCanvas` ya muestra el trazo interno-
mente con el estilo aplicado, así que la caja extra solo saturaba visualmente sin aportar.
Ahora esa caja solo se muestra en modo **Extraer de foto** (donde no hay canvas y el
usuario sí necesita ver la firma extraída/procesada). El chip "Firma cargada ✓" se
mantiene en ambos modos porque es información útil.

### Añadido — navegación y feedback al estampar
Al pulsar los botones "🎯 Una a una" o "⚡ Todos":
- **Scroll automático a la página estampada** en la previsualización (`animateScrollToItem`)
  — antes había que buscarla a mano bajando por 54 páginas.
- **Snackbar de confirmación** con la acción realizada:
  - "Una a una": `Firma estampada en la pág X`
  - "Todos": `Firmadas N páginas`

### Añadido — navegación entre huecos como en la web
Nueva fila `Ir al hueco: ↑ 3/5 · p.33 ↓` justo encima de la previsualización, visible
cuando hay huecos detectados. Flechas circulares que hacen scroll a cada página con
`animateScrollToItem`. Replica la barra de navegación entre huecos que tiene la app web
sobre el documento en el paso de firma.

### Técnico
- `PdfPreview` acepta ahora un parámetro opcional `listState: LazyListState` para que el
  llamador pueda controlar el scroll desde fuera (sigue funcionando con default
  `rememberLazyListState()` si no se le pasa).
- `SignatureStep` envuelve su Column raíz en un `Box` para poder poner el
  `SnackbarHost` como overlay flotante en la parte inferior (patrón Material estándar,
  sin desplazar el contenido del scroll).

---

## [0.6.3-firma-alineada-web] — 2026-07-14

### Añadido / cambiado — SignatureStep alineado con la app web
Auditoría contra `rellenador-pro.html` (paso 4 de la web) y aplicación de 6 mejoras
estructurales al paso de Firma:

1. **`ExpressiveAccordion` extraído a componente compartido** (`ui/components/`): antes
   estaba definido como `private fun AccordionSection` dentro de `DocumentsStep.kt`.
   Ahora se reutiliza desde SignatureStep sin duplicar código. Firma más flexible:
   `count` es `Int?` (opcional) para poder tener secciones sin contador natural.
2. **"Ajustes de firma" en acordeón plegable** (secondaryContainer, `shapes.medium`),
   con tinta, fondo, firmas guardadas y guardar-firma-actual todo dentro. Plegado por
   defecto — antes estaba siempre visible ocupando espacio.
3. **"Huecos de firma" en acordeón plegable** (tertiaryContainer, `shapes.extraLarge`
   para tensión visual con Ajustes), con dos sub-secciones numeradas:
   `1 · Páginas detectadas` (chips con IconButton × al lado para quitar) y
   `2 · Estampar la firma`.
4. **"Una a una" + "⚡ Todos" en Estampar**: dos botones lado a lado que replican la
   pauta de la web. "Una a una" estampa en la página actual del cursor y avanza al
   siguiente hueco automáticamente (con recycle al llegar al final). "Todos" hace el
   estampado masivo (comportamiento anterior).
5. **Paleta de tintas ampliada a 6** (Negro, Azul bolígrafo, Azul claro, Turquesa,
   Sepia vintage, Tinta violeta) — antes solo 3.
6. **Checkbox "Mejorar con IA (localizar y limpiar)"** en modo Extraer de foto, activo
   por defecto. Si el usuario lo desactiva, se salta la localización IA y se abre el
   recorte manual directamente.
7. **Botón "📷 Hacer foto"** dedicado en modo Extraer (además del selector de foto de
   galería). Requiere permiso `CAMERA` — añadido al Manifest con
   `<uses-feature required="false">` para no restringir la instalación a dispositivos
   con cámara.

### No aplicado (con justificación)
- **Modo "De documento"** (elegir firma de un PDF ya subido en el Paso 2): añade
  complejidad estructural (enlazar `docUris` del Paso 2 con selector+recorte en Paso 5).
  Merece una tanda propia, no un añadido.
- **Slider de "Tamaño global"** de firma: el `stampFor()` actual calibra cada estampa
  con anchors por página; un slider global requeriría refactor mayor de
  `WizardViewModel.stampFor()`. Decisión de Pablo: dejarlo fuera.

### Nota honesta sobre iconos
Los iconos "PhotoCamera", "Bolt", "MyLocation", "Tune" y "EditNote" no se han verificado
directamente contra el catálogo real de `material-icons-extended` en esta versión. Para
minimizar riesgo de compilación (viendo que hemos tenido antes el problema con
`Icons.Outlined.Cpu` que no existía), se han sustituido por:
- Iconos ya usados y confirmados en otros archivos del proyecto (`Settings`, `Description`)
- Emojis Unicode en el texto de los botones (📷, ⚡, 🎯) — coincide además con lo que
  usa la app web y no depende del catálogo de iconos.

---

## [0.6.2-contexto-conjunto-docs-popup-squiggly] — 2026-07-14

### Corregido — extracción CIF + DNI/NIE en documentos separados
Bug real reportado: al subir el CIF de una empresa y el DNI/NIE del administrador en
documentos separados, TODOS los motores clasificaban a la persona física como titular
autónomo (poniendo su nombre en "Razón Social" y su NIE en "NIE empresa"), en vez de
como representante de la empresa. Causa raíz confirmada: cada documento se procesa en
llamada aislada — la IA nunca ve el CIF y el DNI en la misma petición. Sin contexto,
un DNI/NIE aislado parece un autónomo por diseño (la propia regla del prompt lo
clasifica así).

Solución arquitectural:
- **`ExtractionPrompt.build()`** ahora acepta `contextDocNames: List<String>` con los
  nombres de TODOS los documentos aportados por el usuario en este análisis. Solo los
  nombres — no se filtra contenido de otros documentos.
- Cuando se pasan 2+ nombres, se inyecta un bloque nuevo "CONJUNTO DE DOCUMENTOS
  APORTADOS" al principio del prompt con instrucciones explícitas para deducir el ROL
  del documento actual dentro del conjunto: si hay un CIF/tarjeta NIF/censal/036/IAE
  en el conjunto Y también un DNI/NIE de persona, la persona ES el representante.
- **`MultiAiExtractor.extract()`** deduplica `docNames` quitando el sufijo "(pág. N/M)"
  y los pasa como `contextDocNames`. No cambia nada del payload que viaja al proxy —
  los nombres son puramente contexto para el prompt del cliente.
- La regla "TITULAR AUTÓNOMO" del prompt principal se refuerza con el matiz: solo aplica
  si NO hay documento de empresa en el conjunto.

### Cambiado (pop-up de progreso)
- **`MotorLoadingIndicator`**: la cabecera ahora muestra **siempre** el `LoadingIndicator`
  squiggly de M3 Expressive, incluso cuando hay motor activo. Antes se mostraba el logo
  grande del motor arriba y también su logo pequeño en la fila de motores debajo — el
  duplicado hacía sensación de "dos avatares del mismo motor" en el mismo pop-up. La
  actividad se sigue viendo perfectamente en la fila de motores con halo y tick.

---

## [0.6.1-doc-lleno-popup-limpio] — 2026-07-14

### Corregido
- **Dos pop-ups solapados al pulsar "Analizar con IA"**: el `WizardScreen` mostraba un
  overlay genérico global (`busy` → `LoadingIndicator` + `busyMsg`) al mismo tiempo que
  `DocumentsStep` mostraba su Dialog rico. Ahora el overlay genérico se salta cuando el
  paso actual es `Step.DOCUMENTOS` — allí manda el Dialog con progreso doc × motor. En
  los otros pasos (guardar contrato, procesar firma…) el genérico sigue activo, que era
  la decisión de diseño de Pablo.

### Cambiado
- **Blob hero rediseñado en horizontal**: icono a la izquierda, texto de estado en dos
  líneas ("6 documento(s)" + "Toca para añadir más") a la derecha, con `weight(1f)` para
  ocupar el ancho disponible. Antes era vertical y con padding 24dp que dejaba el resto
  de la pantalla comprimido — ahora es más compacto y coherente.
- **Layout del scroll eliminado**: la pantalla ya no scrolea (con la lista de documentos
  dentro de su propio acordeón plegable, el contenido siempre cabe). Se elimina el
  `verticalScroll` del Column principal y se sustituye por distribución con
  `Spacer(Modifier.weight(1f))` — así los elementos se empujan al top y no queda hueco
  vacío entre "Motores IA" y la barra inferior (visto en captura en Honor 400 con 6
  documentos).
- **Lista de documentos con altura máxima y scroll propio** (`heightIn(max = 240.dp)`):
  al añadir muchos documentos ya no empuja a la sección Motores IA fuera de pantalla.

### Refinado (Pop-up de progreso, `MotorLoadingIndicator`)
- **Retirados los círculos redundantes** que quedaban apilados debajo del progreso: el
  `LoadingIndicator` squiggly de 32dp que salía cuando había motor activo (duplicaba
  información con el logo de arriba) y la forma naranja pequeña.
- **Barra de progreso + porcentaje en misma fila** (antes apilados): más compacto y
  legible; la barra sube a 8dp de altura, el porcentaje a `labelLarge` con color primario.
- **Fila de motores más limpia**: espaciado 10dp, sin el LoadingIndicator suelto encima.
- Se retira el `Surface + Column` extra que envolvía el `MotorLoadingIndicator` dentro del
  Dialog — redundantes porque el componente ya envuelve todo en su propio
  `ExpressiveSurface` con padding.

---

## [0.6.0-blob-cta-popup-modal-sin-titulos] — 2026-07-14

### Cambiado
- **Blob hero → CTA principal** (`DocumentsStep.kt`): el blob grande ahora es también el
  botón que abre el selector de documentos, ocupa el ancho completo, texto adaptativo
  ("Toca para añadir documentos" cuando está vacío / "N documento(s) — toca para añadir
  más" cuando hay). Deshabilitado durante `busy` para no cambiar los inputs a mitad del
  análisis.
- **Sección "Documentos" condicional**: solo aparece si hay al menos un documento subido.
  Cuando la lista está vacía, el blob queda solo como CTA claro sin ruido debajo. Al
  aparecer/desaparecer usa `AnimatedVisibility` con motion physics real. Sirve para revisar
  y quitar documentos ya subidos (caso de uso real y frecuente).
- **Pop-up de progreso ahora es modal real** (`Dialog` no descartable): antes el indicador
  vivía embebido en el scroll Y el sistema también mostraba una capa "Analizando con
  IA...", dando efecto visual de dos pop-ups solapados. Ahora solo hay uno, con la barra
  de progreso, el motor activo, y el documento en curso — todo dentro del Dialog. No se
  puede descartar con tap fuera ni con botón "atrás" del sistema (la extracción no debería
  interrumpirse a medias).
- **Títulos "Paso N · ..." retirados en los 5 pasos** del wizard (Contrato, Documentación,
  Revisión IA, Relleno, Firma): el stepper superior ya indica en qué paso estás. Las
  descripciones auxiliares también se eliminan. En FillStep el contador "X de N campos"
  sube a `titleMedium` para ocupar el hueco con información útil.

### Refuerzo del prompt de extracción (`ExtractionPrompt.kt`)
- Nueva regla explícita para **documento combinado**: si un mismo documento incluye
  fotocopia de DNI/NIE/pasaporte de una persona física Y un CIF de una empresa (típico en
  escrituras, poderes, compulsas), la persona es SIEMPRE el representante y la empresa el
  distribuidor. Del CIF: razón social y nº empresa, y dirección fiscal solo si el documento
  la muestra explícitamente para la empresa. Del DNI/NIE: nombre y NIF del representante.
  Nunca usar la dirección personal del DNI como dirección fiscal.

### Incluye también, para no perder cambios pendientes de subir a producción
- **`ai-proxy.php` completo actualizado** en la raíz del ZIP (fuera de `app/`): incluye
  todos los arreglos ya confirmados (modelos Gemini 3.5/3.1 correctos, EUrouter con
  `mistral-small-4`/`mistral-large-2`, `thinkingLevel="low"` con techo 8192 tokens),
  MÁS los tres nuevos ajustes tras revisión de Gemini Pro: `systemInstruction` de
  refuerzo del rol y regla de oro anti-invención, `safetySettings` en `BLOCK_NONE` para
  las 4 categorías (evita falsos positivos en documentos legales), y `responseMimeType:
  "application/json"` (JSON puro sin markdown envolvente). Este archivo se sube por
  FTP/cPanel a `mejoresiagratis.com/pdf/`, no vía git.

---

## [0.5.9-firma-segmented-button] — 2026-07-13

### Cambiado
- **`SignatureStep.kt` (Paso 5 · Firma)**: `TabRow`/`Tab` (marcado deprecated en el log de
  build) → `SingleChoiceSegmentedButtonRow`/`SegmentedButton` M3 para elegir entre
  Dibujar/Extraer de foto — mismo patrón ya usado en `FillStep.kt` para NIF/CIF/NIE. Resto
  de la pantalla (recorte de firma, páginas, previsualización) sin tocar, a propósito:
  pantalla grande, cambio acotado para no arriesgar nada más.

### Quitado
- **`ContractFields.CANON`**: eliminado `"Profesión puestos de trabajo datos no
  económicos de nómina historial del trabajador"` — campo real del AcroForm pero sin uso
  claro en este flujo (confirmado con Pablo). Queda solo `"Actividad principal del
  negocio"` de los dos campos añadidos en la auditoría contra el PDF real. `CANON` pasa de
  22 a 21 campos.

---

## [0.5.8-documentacion-motion-physics-formas] — 2026-07-13

### Añadido — refuerzo Expressive real sobre la Mezcla 2+3, según
[m3.material.io/blog/building-with-m3-expressive](https://m3.material.io/blog/building-with-m3-expressive)
- **Motion physics real** (no tween/easing manual): chevron de los acordeones, expandir/
  contraer, y el "pop" del blob hero ahora usan `MaterialTheme.motionScheme.defaultSpatialSpec()`
  / `fastSpatialSpec()` — el muelle configurado por `MotionScheme.expressive()` en `Theme.kt`,
  no un spring hardcodeado por mi cuenta.
- **"Pop" del blob hero** al añadir/quitar documentos: pequeño rebote de escala (`Animatable`
  + `LaunchedEffect` sobre `docUris.size`) que refuerza el cambio sin depender solo del texto.
- **Formas diferenciadas por sección** (táctica Expressive real: "combinar formas y radios de
  esquina para generar tensión visual", no solo color): sección Documentos con
  `shapes.medium`, sección Motores IA con `shapes.extraLarge` — más redondeada, marca
  contraste entre bloques.
- **Contadores animados** (`AnimatedContent` con slide+fade) en la cabecera de cada acordeón
  y en el texto del blob hero, en vez de saltar el número sin transición.
- Un solo "momento hero" en la pantalla (el blob) — la propia guía Expressive recomienda
  limitar los focos así a 1-2 por pantalla para no diluir el impacto.

---

## [0.5.7-documentacion-blob-acordeon-progreso-vivo] — 2026-07-13

### Añadido — mezcla de mockups 2+3 aplicada al `DocumentsStep.kt` real
- **Blob hero grande** (Propuesta 3) como foco visual, sustituyendo la tarjeta de estado
  más pequeña de la Tanda 2.
- **Secciones "Documentos" y "Motores IA" como acordeones** en bloques tonales (Propuesta
  2, `AccordionSection` reutilizable nueva en `DocumentsStep.kt`): plegadas por defecto en
  cuanto ya hay documentos cargados al entrar en la pantalla (si está vacía, se despliega
  sola para no esconder el botón de añadir). Chevron animado con `animateFloatAsState`.
- **Progreso en vivo real documento × motor** en `MotorLoadingIndicator`: nuevos parámetros
  `activeDocLabel`/`progressCurrent`/`progressTotal` (con defaults que preservan el
  comportamiento anterior para cualquier otro llamador). Barra `LinearProgressIndicator`
  + porcentaje.
- **`MultiAiExtractor.extract()`**: nuevos parámetros `docNames` (nombres de archivo en
  paralelo a los payloads, solo para UI — nunca se serializan ni se mandan al proxy) y
  `onProgress(current, total)`. `onProviderStart` ahora también recibe la etiqueta del
  documento real en curso.
- **`WizardViewModel.runExtraction()`**: construye `docNames` en paralelo a los payloads
  (un PDF de N páginas repite el nombre base + `"(pág. i/N)"`; una imagen usa su nombre
  tal cual), y engancha los nuevos callbacks al estado.
- **`WizardUiState`**: nuevos campos `activeDocLabel`, `progressCurrent`, `progressTotal`.
- Motores y botón "Añadir documentos"/"Atrás" ahora deshabilitados durante `busy` (antes
  se podían tocar a mitad de una extracción en curso, lo que podía confundir sobre a qué
  tanda aplicaba el cambio).

---

## [0.5.6-actividad-checkbox-nie-prompt-refinado] — 2026-07-13

### Auditoría contra `contrato-base.pdf` real y `rellenador-pro.html` (web)
Confirmado con `pypdf get_fields()` sobre el AcroForm real: 26 campos totales frente a
los 20 de `ContractFields.CANON`. Comparado además con la construcción dinámica de la
lista de campos en la web (`S.fields` leído del PDF cargado, no una lista fija).

### Añadido
- **`ContractFields.CANON`**: dos campos reales del PDF que faltaban por completo
  (migración incompleta de la web a Android — ambos ya se mencionaban en el prompt de
  IA de ambas apps, pero no estaban conectados a ningún campo real en Android):
  - `"Actividad principal del negocio"` — confirmado como campo de texto real; se
    extrae sobre todo del certificado de situación censal (IAE).
  - `"Profesión puestos de trabajo datos no económicos de nómina historial del
    trabajador"` — campo real pero de significado ambiguo; añadido a `CANON` para
    relleno manual, SIN regla de extracción de IA dedicada (ninguno de los documentos
    de referencia —escritura/CIF/DNI/IAE/censal/036/IBAN— contiene este dato de forma
    evidente; pendiente de que Pablo aclare su uso real).
  - Ambos añadidos a la sección "Empresa / Identificación" en `FillStep.kt` (Tanda 3).
- **`ContractFields.CHECKBOX_NIE = "undefined"`**: nueva constante para la 3ª casilla
  de tipo de identificación, que el AcroForm real tiene pero quedó sin nombre propio al
  exportarse (literalmente `"undefined"` — confirmado con pypdf; la web ya la detecta
  así: `cbs.find(f=>norm(f.name)==="undefined")`). No es un nombre que se pueda cambiar:
  para marcar esa casilla en el PDF real hay que usar exactamente ese valor.

### Corregido
- **`checkboxStateFor()`**: antes NIE no marcaba ninguna casilla (premisa incorrecta:
  "el contrato solo tiene casillas NIF y CIF"). Con un titular NIE, el contrato firmado
  quedaba con el tipo de identificación sin marcar — bug real de corrección legal del
  documento, no solo un hueco de datos. Ahora marca `CHECKBOX_NIE` a `/On` y las otras
  dos a `/Off` cuando el tipo es NIE.

### Prompt (`ExtractionPrompt.kt`) — reforzado a partir de auditoría con documentos reales
(escritura/CIF/TIE/IAE/censal/Modelo 036/certificado IBAN):
- Domicilio personal del representante (DNI/NIE) nunca se propone como dirección fiscal
  de una empresa con CIF.
- Modelo 036: direcciones de local (página de actividades) no son la fiscal directa.
- Ejemplo de reordenación de nombre en formato tarjeta de identidad (sin coma).
- Aviso de posible domicilio desactualizado en escrituras de constitución.
- "Actividad principal del negocio": prioriza el certificado censal (IAE) como fuente.

---

## [0.5.5-tanda3-revision-relleno-secciones] — 2026-07-12

### Añadido — Revisión IA + Relleno agrupado en secciones (Tanda 3 M3 Expressive)
- **`FillStep.kt` (Paso 4) rediseñado por completo**:
  - Los 20 campos canónicos agrupados en 5 secciones temáticas sobre
    `surfaceContainer` (Empresa/Identificación, Dirección fiscal, Dirección
    comercio/PdV, Contacto, Datos bancarios) + una sección aparte para Fecha.
  - Cada cabecera de sección muestra un tick de completitud cuando todos sus
    campos están rellenos y pasan validación.
  - **Tipo de identificación (NIF/CIF/NIE) ahora editable** vía
    `SingleChoiceSegmentedButtonRow` — antes solo lo fijaba la IA extraída y no
    había forma de corregirlo en la UI, pese a que determina qué casilla del PDF
    se marca al firmar (hallazgo real de esta tanda, no cosmético).
  - Barra de progreso real (`LinearProgressIndicator`, X/20 campos) sustituyendo
    el texto plano anterior.
  - Botón "Copiar fiscal" en la cabecera de Dirección comercio/PdV: copia los 4
    campos de la dirección fiscal al bloque `_2` de un toque (no sobrescribe con
    vacío). Nueva función `WizardViewModel.copyFiscalToComercio()`.
  - Fecha (`Fecha`/`de`/`año`) ahora en una fila compacta de 3 campos día/mes/año
    en vez de 3 campos apilados sueltos.
  - "Responsable Comercial" con su propia mini-sección en vez de `AssistChip`
    huérfano al final de la lista.
  - Barra de acción inferior con el padding 20dp/14dp ya fijado como estándar en
    `ContractStep`/`DocumentsStep`.
- **`WizardViewModel.kt`**: nuevas funciones `setTipoIdentificacion(tipo)` y
  `copyFiscalToComercio()`.
- **`ReviewStep.kt` (Paso 3)** — ajustes de coherencia:
  - Formas de tarjeta unificadas a `MaterialTheme.shapes.medium` (antes
    `RoundedCornerShape(10.dp)` suelto).
  - Candidatos de cada campo ahora como `FilterChip` seleccionables (antes lista
    de `RadioButton`), con el logo real del motor que propuso cada valor
    (`ProviderLogo` de la Tanda 2) en vez de solo el nombre en texto plano.

---

## [0.5.4-tanda2-documentacion-motor-activo] — 2026-07-12

### Añadido — identidad visual por proveedor + progreso en vivo (Tanda 2 M3 Expressive)
- **`AiProvider`** (`AiModels.kt`) ampliado con `brandColor` (color oficial de marca,
  público en brand guidelines), `initial` (glyph fallback de 1–2 caracteres) y
  `drawableName` (recurso `res/drawable/ic_provider_*`).
- **9 drawables placeholder** (`ic_provider_claude.xml`, `ic_provider_gemini.xml`,
  `ic_provider_groq.xml`, `ic_provider_grok.xml`, `ic_provider_mistral.xml`,
  `ic_provider_scaleway.xml`, `ic_provider_ovh.xml`, `ic_provider_nebius.xml`,
  `ic_provider_eurouter.xml`): disco con el color de marca, listos para sustituir por
  el SVG oficial de cada proveedor sin tocar código (mismo nombre de recurso). Ver
  `LOGOS_TODO.md` para las URLs de los brand kits oficiales.
- **`ExpressiveComponents.kt`**: nuevos `ProviderGlyph` (círculo + inicial, fallback
  total), `ProviderLogo` (drawable + inicial superpuesta, con fallback automático a
  `ProviderGlyph` si el recurso no existiera), `EngineChip` (filter chip con logo,
  badge 🇪🇺, halo pulsante cuando el motor está trabajando) y
  `MotorLoadingIndicator` (sustituye el `busyMsg` de texto plano: logo grande del
  motor activo + `LoadingIndicator` Expressive + fila de estado por motor
  pendiente/actual/hecho con tick).
- **`MultiAiExtractor.extract()`**: nuevos parámetros opcionales `onProviderStart` /
  `onProviderFinish` (defaults no-op, sin romper llamadores existentes), invocados
  alrededor de cada llamada al proxy — permiten reflejar en vivo qué motor concreto
  está procesando en cada momento.
- **`WizardUiState`**: nuevos campos `activeProvider` y `finishedProviders` para el
  progreso en vivo; `WizardViewModel.runExtraction()` los actualiza vía los
  callbacks del extractor y los limpia al terminar (éxito o error).
- **`DocumentsStep.kt`** rediseñado: tarjeta Expressive de estado de subida (icono en
  blob), chips de motor (`EngineChip`) con logo real, `TipBanner` sobre proveedores
  🇪🇺, y `MotorLoadingIndicator` contextual durante la extracción en vez del texto
  genérico "Analizando con IA…". Paddings alineados al criterio ya fijado en
  `ContractStep` (20dp horizontal / 16dp vertical exterior, 20dp/14dp en la barra de
  acción anclada).

### Pendiente (no código, tarea de Pablo)
- Sustituir los 9 drawables placeholder por los SVG oficiales de cada proveedor
  (ver `LOGOS_TODO.md`).

---

## [ai-proxy 2026-07-11-b] — mismo día, sin bump de versionName de app

### Corregido (ai-proxy.php — solo servidor, no requiere nueva build de la app)
- **Groq/Qwen 413 "Request too large"**: `qwen/qwen3.6-27b` tiene un TPM (tokens/minuto,
  entrada+salida) muy ajustado de 8000 — con el techo global de 8192 tokens de salida
  recién subido, una sola imagen ya empujaba el total por encima (visto: 8225/8000).
  Fix: `callGroqSrv` ahora limita la salida a 1500 tokens SOLO para la llamada de
  visión de Groq, dejando margen de sobra para la imagen de entrada.
- **Gemini "respuesta no parseable" con JSON cortado a mitad de un valor** (visto en
  producción: `"Provincia": "VALENC` sin cerrar): el suelo de `maxOutputTokens` para
  modelos 3.x estaba en 4096, pero como el cliente ya manda 4096 por defecto,
  `max(4096, 4096)` no subía nada — el "thinking" interno se comía el presupuesto antes
  de llegar al texto visible. Subido el suelo a 8192, independiente de lo que pida el
  cliente.
- **Mistral "no se ha proporcionado ningún documento" pese a que SÍ había imagen**:
  confirmado que es alucinación del modelo (formato de imagen ya verificado correcto),
  no un fallo de nombre de modelo. Añadido detector heurístico + reintento automático
  (hasta 2 intentos) cuando la respuesta con éxito (200 OK) parece indicar "no hay
  documento" pese a haberse mandado una imagen — el muestreo no es determinista, un
  segundo intento suele bastar.

---

## [0.5.3-mockup-contrato-ajustes-sheet] — 2026-07-11

### Añadido — implementación del mockup M3 Expressive (Contrato + Ajustes rápidos)
Implementado a partir de un mockup HTML/CSS aportado, con notas de diseño explícitas:

- **Formas orgánicas ("blob")**: nuevo `blobShape()` en `ExpressiveComponents.kt`
  (aproximación con `RoundedCornerShape` de radios asimétricos por esquina — Compose
  no soporta radios elípticos independientes por eje como el CSS del mockup, pero da
  el mismo efecto "no es un círculo perfecto"). Aplicado al botón de ajustes y a los
  iconos de las tarjetas de Contrato.
- **`ContractOptionCard` rediseñada**: sustituido `ListItem`+`RadioButton` por icono
  en blob + marca de verificación circular (rellena si está seleccionada) — la
  selección se ve de un vistazo, no solo se lee. Color de selección cambiado a
  `primaryContainer` (antes `secondaryContainer` — ahora es el color de marca).
- **`TipBanner`** (nuevo, en `ExpressiveComponents.kt`): aviso con el color terciario
  — primer uso real de ese rol fuera de la paleta base de Tanda 0.
- **Botón "Continuar"** con icono de flecha (`ExpressiveButton` ahora acepta
  `trailingIcon` opcional).
- **Pulso en el paso actual del stepper**: animación infinita sutil de escala
  (`rememberInfiniteTransition`) — se nota "vivo" sin tocar nada, fiel al mockup.
- **Ajustes rápidos como bottom sheet**: el botón de ajustes ahora abre un
  `ModalBottomSheet` con perfil comercial + motores IA (lo que más se cambia), en vez
  de navegar directo a la pantalla completa. Un enlace "Más ajustes" dentro sigue
  llevando a `AjustesScreen` para la URL del proxy y lo menos frecuente — nada se ha
  quitado, solo se adelanta el acceso rápido a lo habitual.

### Pendiente (sugerido en el propio mockup, para siguientes tandas)
Chips de motor con icono/bandera de proveedor (Documentación), agrupar campos del
formulario en secciones con `surfaceContainer` (Revisión/Relleno), cambiar el
selector Dibujar/Extraer de foto de `TabRow` (deprecated) a segmented button (Firma),
loading global mostrando qué motor trabaja en cada momento.

---

## [0.5.2-m3-expressive-tanda1-wizard-shell] — 2026-07-11

### Añadido — Tanda 1 de rediseño visual: shell del wizard + Contrato
- **`WizardScreen` (shell)**: `TopAppBar` con color propio (`primaryContainer`, antes
  heredaba `surface` y se confundía con el fondo). Stepper con 3 estados reales
  (pendiente/actual/completado) en vez del binario anterior — pendiente ahora es un
  círculo con borde visible sobre el fondo cálido (antes `surfaceVariant` plano casi
  invisible), completado suma un check, actual flota con elevación propia. El overlay
  de carga usa `LoadingIndicator` (forma animada Expressive) dentro de una tarjeta
  elevada en vez de flotar directo sobre el scrim.
- **`ui/components/ExpressiveComponents.kt`** (nuevo): `ExpressiveSurface` y
  `ExpressiveButton` — componentes compartidos con la forma/color unificados del
  rediseño, para reutilizar en las siguientes pantallas sin repetir estilos sueltos.
- **`ContractStep` rediseñado**: contenido en scroll con botón de acción anclado abajo
  (antes quedaba un hueco vacío grande en pantallas altas); tarjetas de opción que
  cambian de color completo al seleccionar (no solo el radio button); `selectableGroup`
  para accesibilidad; transición `AnimatedContent` hacia el editor de mapeo en vez de
  un `return` abrupto que rompía el ciclo de vida de Compose.

---

## [0.5.1-m3-expressive-downgrade-alpha] — 2026-07-11

### Corregido
- **Build roto por `material3:1.5.0-alpha22`**: arrastra una dependencia transitiva
  (`androidx.compose.animation:animation-core-android:1.12.0-alpha03`) que exige
  `compileSdk 37` (no público todavía) y Android Gradle Plugin `9.1.0` (el proyecto
  usa 8.7.3) — un salto de todo el toolchain, no solo de la librería de temas.
  Bajado a `material3:1.4.0-alpha16`, que ya trae `MaterialExpressiveTheme`,
  `MotionScheme.expressive()` y `MaterialShapes` (la API Expressive es estable desde
  aprox. alpha14) sin ese arrastre.
- **Nota de incertidumbre honesta**: no hay forma de verificar la compilación real
  sin SDK disponible aquí; esta versión es una elección razonada (más conservadora,
  varias versiones por detrás de la que falló) pero no 100% garantizada. Si esta
  build también fallara por otra incompatibilidad de versión, el siguiente paso
  sería bajar aún más (p.ej. 1.4.0-alpha10) o considerar subir compileSdk/AGP como
  alternativa, según lo que diga el log.

---

## [0.5.0-m3-expressive-tanda0] — 2026-07-11

### Añadido — Fundación M3 Expressive (Tanda 0 de la fase de diseño visual)
Primera tanda del rediseño visual completo (M3 Expressive, https://m3.material.io/blog/
building-with-m3-expressive), aceptado en alpha a petición explícita — no toca ninguna
pantalla todavía, solo la base sobre la que se construirán las siguientes tandas.

- **Dependencia**: `material3` fijado explícitamente a `1.5.0-alpha22` (17-jun-2026),
  por encima de lo que fija el BOM estable (2024.12.01) — ya incluye
  `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `MaterialShapes`,
  `LoadingIndicator`, `FloatingToolbar` (recién graduado a estable), etc.
- **Paleta de color completa**: antes solo se sobreescribía `primary` (todo lo demás
  quedaba en el morado por defecto de M3). Ahora TODOS los roles se derivan del
  naranja de marca (primary/secondary/tertiary + sus containers, superficies con
  tinte cálido, outline) — con un terciario azul-verdoso frío como contrapunto de
  color (principio Expressive: paleta más rica para marcar jerarquía).
- **Escala de formas** más generosa (8/12/16/24/32dp vs. los radios base de M3) —
  las formas empiezan a "dirigir la atención", no solo decorar.
- **Tipografía** con más peso en títulos (SemiBold/Bold) para dar jerarquía visual
  con personalidad.
- **`MotionScheme.expressive()`**: física de resortes con rebote en vez de easing/
  duración fijos — las interacciones (ya existentes, sin tocar ninguna pantalla)
  deberían notarse más vivas de inmediato en botones, cambios de estado, etc.
- `RellenadorTheme` pasa de `MaterialTheme` a `MaterialExpressiveTheme` — cambio
  aislado en `ui/theme/Theme.kt`, verificado que ningún otro archivo del proyecto
  rompe (todos los usos de `MaterialTheme.colorScheme/.typography/.shapes` siguen
  funcionando igual, ya que Expressive expone los mismos CompositionLocals).

### Pendiente (siguientes tandas, pantalla por pantalla)
Wizard shell (barra superior + stepper) → Contrato → Documentación → Revisión IA →
Relleno → Firma → Ajustes/Historial/Mapeo. Cada una en su propia tanda verificable,
como el resto de esta migración.

---

## [0.4.1-fallback-modelos-banner] — 2026-07-11

### Añadido (ai-proxy.php — entregado aparte, vive en cPanel)
- **Fallback de modelo por proveedor**: `callOpenAICompatSrv` ahora prueba modelos
  ALTERNATIVOS (`ALT_MODELS`) en orden si el modelo principal falla con 400/403/404
  (nombre no reconocido, bloqueado a nivel de proyecto, deprecado) — antes de rendirse
  con ese motor. Poblado para `eurouter` (varias grafías plausibles de nombre, dado
  que su catálogo no coincide 1:1 con el de Mistral directo), `grok` (grok-4.1-fast
  como reserva del flagship 4.3) y `mistral` (mistral-medium-latest de reserva). NO se
  reintenta en 429/503 (eso no es problema del NOMBRE del modelo, es cuota/demanda —
  reintentar con otro modelo no ayuda ahí).
- Diagnóstico real de esta sesión (capturas de Pablo) — la mayoría NO eran bugs:
  - Groq 403 "qwen/qwen3.6-27b blocked at project level" → hay que habilitarlo en
    console.groq.com/settings/project/limits (no hay otro modelo de visión disponible
    en Groq ahora mismo — llama-4-scout está deprecado, qwen3.6-27b es la única opción).
  - Claude 400 "credit balance too low" → cuenta de Anthropic sin saldo, no es bug.
  - Gemini 503 "high demand" → sobrecarga temporal de Google, no es bug.
  - Mistral "no se ha proporcionado ningún documento" → el formato de imagen enviado
    es correcto (verificado); es el propio modelo alucinando que no hay imagen. No es
    un problema de nombre de modelo — mistral-small-latest sigue siendo la elección
    correcta (alias que Mistral actualiza automáticamente, confirmado en su doc oficial).

### Cambiado (app Android)
- El banner rojo genérico de error YA NO se muestra para fallos de extracción por
  motor — esa información solo aparece ahora dentro de "Ver motores no disponibles"
  (colapsable, oculto por defecto), evitando duplicar el mismo mensaje dos veces.
  Otros errores no relacionados con la extracción (p.ej. exportar el PDF) siguen
  mostrando el banner normalmente, ya que no tienen panel alternativo.

---

## [0.4.0-proxy-robusto-modelos] — 2026-07-11

### Añadido (app Android)
- Resolución máxima de imagen enviada al proxy subida de 1600px a 2000px (en
  DocumentLoader y en la extracción de firma), para aprovechar el nuevo techo del
  servidor y mejorar el detalle/OCR sin cambiar el contrato de la API.

### Nota — auditoría completa del ai-proxy.php (entregado aparte, vive en cPanel)
Revisado el archivo completo. Cambios aplicados: límites subidos de forma coherente
(set_time_limit 240s, CURLOPT_TIMEOUT 180s, MAX_BODY 20MB, MAX_DOCS 12, MAX_IMG_SIDE
2000px, JPEG_QUALITY 85, RATE_MAX 60/10min, techo max_tokens 8192) y modelos
actualizados con evidencia real: Claude → claude-sonnet-5, Groq texto → openai/gpt-oss-120b
y Groq visión → qwen/qwen3.6-27b (llama-3.3-70b y llama-4-scout DEPRECADOS por Groq el
17-jun-2026 — explica varios fallos históricos), Grok → grok-4.3 (Grok 2 era ya muy
antiguo). Gemini y Mistral ya estaban correctos, sin cambios.

Hallazgo importante: el proxy permite sobrescribir modelo/endpoint de los proveedores
OpenAI-compatibles (grok/mistral/scaleway/ovh/nebius/eurouter) desde ai-proxy.config.php
(`$CFG['models'][id]`, `$CFG['endpoints'][id]`) — SI existieran overrides antiguos ahí,
anularían en silencio estos arreglos para esos 6 motores. Claude/Gemini/Groq NO tienen
este riesgo (modelo fijo en el propio ai-proxy.php). ai-proxy.config.sample.php
actualizado para incluir claves de scaleway/ovh/nebius/eurouter (antes solo mostraba
claude/gemini/groq/grok/mistral) y documentar el mecanismo de override opcional.

---

## [0.3.9-recorte-manual-firma] — 2026-07-11

### Añadido
- **Recorte MANUAL de la firma al subir una foto**: tras elegir "Elegir foto" en
  "Extraer de foto", se abre un recorte a pantalla completa donde el usuario arrastra
  el dedo para marcar exactamente la zona de la firma, antes de procesar nada. Sustituye
  la dependencia total en la localización automática por IA, que en dos rondas de ajuste
  (0.3.4 umbral de caja, 0.3.5 relajación Otsu×1.15) seguía perdiendo trazos o recortando
  mal en algunas fotos. El recorte manual NO pasa por la IA de localización — va directo
  al pipeline de tinta (aplanar + Otsu + recorte a bounding-box) sobre la región elegida.
  Botón "Foto completa" para quien prefiera el camino automático de siempre.
- **"Recortar de nuevo"**: si el resultado no convence, un botón junto a la firma
  cargada reabre el recorte sobre la MISMA foto ya elegida, sin tener que volver a
  subirla. La foto original se recuerda mientras dura la sesión.
- El recorte manual se integra con el resto de funciones ya existentes: cambiar color
  de tinta o fondo después reprocesa en vivo igual que con el flujo automático.

### Nota de build
Comprobado contra el HEAD real del repo (vía conector de lectura de GitHub) que el
fragmento de diagnóstico de Groq/Mistral (0.3.7) y el fix de Gemini (0.3.8) SÍ están
presentes en el código actual, pese a no aparecer como commits individuales en el
historial (se aplicaron como parte de entregas acumulativas). Si en una prueba no se ve
el fragmento de texto real en "respuesta no parseable", puede que el APK instalado en
el móvil sea de un build anterior — reinstalar la última versión del workflow.

---

## [0.3.8-fix-gemini-mode-real] — 2026-07-11

### Corregido — CAUSA RAÍZ REAL del 500 de Gemini encontrada (no era timeout)
Confirmado con el log de errores de PHP real del servidor (no una hipótesis más):

```
PHP Warning: Undefined array key "gemini_mode" en ai-proxy.php línea 259
PHP Fatal error: Uncaught TypeError: callGeminiSrv(): Argument #6 ($mode) must be
of type string, null given
```

El proxy NUNCA recibía el campo `gemini_mode` en la petición del cliente Android, pese
a que `ProxyRequest.geminiMode` tiene default `"g35"` y el código siempre lo manda con
ese valor. Causa: el `Json` de Retrofit (`AppModule.kt`) no tenía `encodeDefaults = true`
— y el default de kotlinx.serialization es `false` — así que CUALQUIER campo que valga
exactamente su valor por defecto (como `geminiMode="g35"` casi siempre) se OMITE del
JSON serializado por completo. El proxy PHP veía la clave inexistente, y un bug propio
en la línea 259 (reutilizaba `$in['gemini_mode']` directamente en la rama "true" del
ternario, sin el `?? 'g35'` de respaldo) convertía eso en `null`, que revienta contra
la firma `string $mode` no-nulo de `callGeminiSrv()` (el archivo usa `strict_types=1`).

Esto explica TODO lo observado: por qué solo Gemini fallaba (único motor con este
parámetro no-nulo obligatorio), por qué era consistente y no intermitente (el bug de
serialización no depende de tiempos ni cargas), y por qué el timeout de PHP (0.3.5/
0.3.6, `max_execution_time`) no lo arreglaba — nunca fue un problema de tiempo.

### Fix (dos lados, complementarios)
- **Android** (`AppModule.kt`): `encodeDefaults = true` en el `Json` — ahora el JSON
  enviado refleja de verdad los valores que el código dice que manda.
- **PHP** (aplicar manualmente en `ai-proxy.php`, línea 259 — vive en el hosting de
  Pablo, fuera de este repo): usar una variable intermedia para el valor con `??` de
  respaldo, en vez de re-consultar `$in['gemini_mode']` sin respaldo en el ternario.
  Esto protege al proxy de cualquier cliente (presente o futuro) que omita el campo.

---

## [0.3.7-diag-mistral-snippet] — 2026-07-11

### Corregido
- **`maxTokens=4096` (0.3.6) NO resolvió "Mistral: respuesta incompleta"** — confirmado
  en una prueba real posterior con el mismo fallo. La hipótesis del corte por límite de
  tokens queda descartada; la causa real de que `AiJsonParser.parse()` devuelva null es
  otra, y sin ver el texto crudo no se puede saber cuál.
- **Diagnóstico ampliado**: cuando el parseo del JSON falla, el mensaje agrupado ahora
  incluye un fragmento del texto real que devolvió el motor (`"respuesta no parseable —
  \"<primeros 180 caracteres>\""`), mismo principio que `realErrorMessage()` para errores
  HTTP (0.3.1). Sin esto no se puede distinguir entre: JSON cortado a medias, texto plano
  sin JSON, un mensaje de error del propio motor camuflado como éxito, o un formato
  inesperado — cada causa necesitaría un fix distinto.

### Investigación Gemini — el aumento de max_execution_time a 240s NO resolvió el 500
Confirmado en prueba real posterior al cambio en cPanel: Gemini sigue devolviendo HTTP 500
sin cuerpo. Esto apunta a que el límite real no es (solo) el `max_execution_time` de PHP
— sospecha siguiente: **timeout de proxy/FastCGI de Apache o LiteSpeed**, que en muchos
hostings cPanel es un ajuste SEPARADO del PHP y con su propio tope, no necesariamente
visible ni modificable desde el MultiPHP INI Editor. Pendiente: revisar el log de errores
de cPanel para la prueba más reciente (post-240s) y, si no aparece ya el mensaje de tiempo
excedido, consultar con soporte del hosting sobre el timeout de proxy/FastCGI del dominio.

---

## [0.3.6-maxtokens-mistral] — 2026-07-11

### Corregido
- **Mistral: "respuesta incompleta"** en la extracción — a diferencia de los otros
  fallos (400/404/429/500), Mistral respondía HTTP 200 (éxito) pero el JSON no se podía
  parsear. Causa probable: `maxTokens=2048` cortaba a mitad la respuesta para un JSON
  completo (sugerencias + alternativas + paquetes puede ser verboso en contratos con
  varios documentos). Subido a `maxTokens=4096`, el propio techo que ya aplica el proxy
  (`min(4096, ...)`) — antes se usaba solo la mitad del margen permitido.
- `locate_signature` (SignatureLocator) se deja en 300 tokens: no tiene este riesgo,
  la respuesta esperada es solo un JSON pequeño de coordenadas.

### Pendiente (diagnóstico en curso, no app-side)
- **EUrouter 404** — "Model 'mistral-small-latest' not found or has no available
  providers". Configuración del proxy PHP (`ai-proxy.config.php`), no de la app.
- **Groq 429** — cuota de tokens/minuto casi agotada (28863/30000 usados). Externo,
  no requiere cambio de código; se resuelve con tiempo o subiendo de plan en Groq.
- **Gemini 500 sin mensaje real** — el diagnóstico de 0.3.1 (`realErrorMessage()`) no
  encuentra body JSON legible para este fallo concreto, lo que apunta a un error de PHP
  a nivel más bajo (excepción no capturada / timeout de cURL) antes de que el proxy
  llegue a formatear la respuesta de error. Requiere revisar el log de errores del
  servidor (cPanel) — no se puede diagnosticar más desde el cliente.

---

## [0.3.5-restaura-umbral-otsu] — 2026-07-11

### Corregido — auditoría completa del historial (otro fix perdido, distinto de 0.3.2)
Revisando TODO el historial de commits con el conector de GitHub (list_commits/get_commit),
se encontró un grupo de 8 commits de otra sesión en la noche del 07-09 (21:53-23:44) que
nunca se habían incorporado — distintos de la regresión ya corregida en 0.3.2. La mayoría
eran arreglos mecánicos de sus propios errores de compilación (llaves huérfanas, residuos
de `toSignatureData`) o un experimento de upscale a 1500px que ELLOS MISMOS revirtieron
(por eso no hace falta restaurarlo). Pero uno seguía siendo relevante y perdido:

- **`processInk` umbral demasiado estricto**: la condición para descartar un píxel como
  "fondo" era `lum > threshold`. Un commit de esa noche la relajó a `lum > threshold * 1.15`
  para capturar el trazo completo — sin este margen, bordes antialiaseados o trazos algo
  más claros de la tinta real se descartaban, dejando solo el núcleo más oscuro. Esto
  encaja con el fragmento irreconocible reportado (0.3.4 solo arregló la localización y
  el recorte; este es un tercer factor independiente sobre la MISMA foto de prueba).
- Verificado con el conector de GitHub (no solo el CHANGELOG) leyendo el diff exacto del
  commit `e2cbc7f` — cambio de una sola línea, restaurado sin reconstrucción de memoria.

### Nota sobre el conector de GitHub
Confirmado en esta sesión: el conector tiene permiso de LECTURA (`list_commits`,
`get_commit`, `get_file_contents` — funcionan perfectamente, sin caché desactualizada) pero
NO de escritura (`create_or_update_file` da 403 "Resource not accessible by integration").
Se sigue usando ZIP + terminal de Pablo para aplicar cambios; el conector de lectura se usa
para verificar el HEAD real y auditar el historial antes de generar cada entrega.

---

## [0.3.4-fix-localizacion-firma] — 2026-07-11

### Corregido
- **Firma extraída de foto irreconocible (fragmento diminuto sin relación con el trazo
  real)**: comparando la firma real (foto aportada) contra el resultado en la app, el
  recorte capturaba solo una esquina minúscula. Causa: `SignatureBox.valid` aceptaba
  cualquier caja con w>2%,h>2% — demasiado permisivo; un error de localización de la IA
  (fragmento equivocado, pequeño) pasaba como "válido" y el recorte a bounding-box
  (0.3.3) lo acotaba aún más sobre esa región ya errónea.
- **Fix 1**: `SignatureBox.valid` ahora exige w>15%,h>8% — descarta cajas demasiado
  pequeñas para ser una firma real localizada con fiabilidad.
- **Fix 2**: cuando no hay caja fiable (`box == null`), ya NO se ofrece la foto cruda
  sin procesar. Se aplica el mismo pipeline completo (aplanado + Otsu + recorte a
  bounding-box) a la foto ENTERA. Esto resuelve muy bien el caso de una foto que YA es
  solo la firma aislada sobre fondo claro (sin documento alrededor) — no hace falta que
  la IA "localice" nada dentro de una imagen que ya es solo la firma.

---

## [0.3.3-fix-crop-firma] — 2026-07-11

### Corregido
- **Firma de foto se ampliaba y recortaba en exceso al aplicarla al PDF final**: la
  0.3.2 desactivó el recorte a bounding-box (`applyBoundingCrop = false`) cuando el
  locator ya daba una caja, pensando que evitaba un problema de 0.2.0 (desviarse a una
  esquina por sombras). En la práctica, si la caja del locator viene floja (con margen
  vacío alrededor del trazo real), el resultado es un lienzo grande casi en blanco con
  el trazo diminuto en el centro — que luego el encaje "letterbox" (0.2.2) amplía para
  llenar la caja calibrada del contrato, recortando/deformando el trazo real.
- **Fix**: `applyBoundingCrop` vuelve a estar activo SIEMPRE tras el recorte del locator
  (`extractSignatureFromPhoto` y `reprocessSignatureFromRaw`). El riesgo de "esquina
  desviada" de 0.2.0 solo aplicaba a fotos COMPLETAS sin recortar — ese caso ya no se
  procesa en absoluto (fallback de 0.2.0: se ofrece la foto tal cual sin tintar). Sobre
  una región ya acotada por el locator, recortar de nuevo al trazo real es seguro y
  necesario para que la firma final no quede minúscula dentro de un recuadro vacío.

---

## [0.3.2-restaura-0.2.x] — 2026-07-11

### Corregido — restaura la regresión documentada en 0.3.1
La fusión "Ajustes + letterbox real" (commit "Fusion...") había mezclado la pantalla de
Ajustes con una versión de `MultiAiExtractor.kt`/`SignatureLocator.kt`/`ReviewStep.kt`/
`SignaturePageDetector.kt` anterior a 0.2.0, revirtiendo sin querer varias mejoras. Todas
reconstruidas ahora usando el CHANGELOG (0.2.0/0.2.1) como especificación exacta:

- **MultiAiExtractor**: short-circuit por motor (`dead` set — un motor que falla 4xx/5xx
  no se reintenta en la sesión), backoff cooperativo de 2,5 s en 429, orden fiable
  (Groq → Mistral → Claude → Gemini → Scaleway → EUrouter), errores agrupados por motor
  (una línea con el último estado, no una por documento). Aprovecha `HttpException.
  realErrorMessage()` (0.3.1) para que el detalle incluya el mensaje real del proveedor.
- **SignaturePageDetector**: la página 24 (índice 23) se fuerza como candidata siempre
  que el documento tenga ≥24 páginas — no tiene ningún campo AcroForm propio, así que la
  detección estructural nunca podía encontrarla por ese camino.
- **SignatureLocator**: reordenado a Mistral → Scaleway → Claude → Gemini → Grok (Groq
  excluido: no tiene visión real, es un motor de texto que "especularía" el JSON).
- **Actualización en vivo de tinta/fondo**: se guarda el bitmap "crudo" (antes de tintar)
  tanto para fotos como para dibujos a mano; cambiar color o fondo reprocesa de inmediato
  sin volver a llamar a la IA de localización (`reprocessSignatureFromRaw()`).
- **Fallback razonable cuando el locator falla**: si ningún motor localiza la firma en una
  foto, ya NO se aplica `processInk` a la foto entera (sacaba resultados basura); se
  ofrece la foto original tal cual sin tintar.
- **`applyBoundingCrop` opcional** en `processInk`/`fromPhoto`: desactivado cuando la
  imagen ya viene recortada de forma fiable por `SignatureLocator`, evitando el doble
  recorte que desviaba el resultado a una esquina.
- **Panel de detalle de motor caído** restaurado en `ReviewStep` (colapsable, bajo un
  botón "Ver motores no disponibles (N)"), con el mensaje real de cada motor.
- **Chip informativo**: "Firma cargada ✓ · lista para N páginas" sustituye al chip mudo.
- **Previsualización de firma reubicada**: justo tras elegir Dibujar/Extraer, antes de
  las opciones de color/fondo.

---

## [0.3.1-diag-errorbody] — 2026-07-10

### Añadido
- **Instrumentación de diagnóstico para 400/404/500 de cualquier motor** (investigación Gemini): `ai-proxy.php` ya reenvía el mensaje real del proveedor upstream (`{"ok":false,"error":"Gemini: <mensaje real de Google>"}`) con el código HTTP real de Google/Anthropic/etc. — pero Retrofit, al ver un código no-2xx, lanza `HttpException` sin deserializar ese body, y nadie lo leía (`e.message` por defecto solo da algo genérico tipo "HTTP 500 Internal Server Error"). Nuevo `HttpException.realErrorMessage()` en `MultiAiExtractor.kt` lee `errorBody()` a mano y extrae el campo `error`. Se usa en:
  - `MultiAiExtractor.kt`: el banner de error de `WizardScreen` ahora incluye el mensaje real además del código HTTP.
  - `SignatureLocator.kt`: antes se tragaba cualquier fallo en silencio (`runCatching{}.getOrNull() ?: continue`, sin log); ahora loguea motor + código + mensaje real con `Log.w`.
- Verificado contra la documentación oficial de Gemini (jul 2026) que el payload que arma `callGeminiSrv()` en el proxy es correcto: modelo `gemini-3.5-flash` es GA vigente, `thinkingConfig.thinkingLevel` (sin `thinkingBudget` a la vez, evitando el 400 documentado) y formato `inline_data`/`mime_type` coinciden con los ejemplos oficiales de `generativelanguage.googleapis.com`. No se encontró ningún parámetro mal formado por inspección estática — el paso obligado ahora es leer el mensaje real que esta build ya expone.

### ⚠️ Regresión detectada en 0.3.0 (sin corregir en esta build — pendiente de decisión)
El commit "Fusion: Ajustes + letterbox real" mezcló la nueva pantalla de Ajustes con una
versión **anterior a 0.2.0** de `MultiAiExtractor.kt`/`SignatureLocator.kt`/`ReviewStep.kt`,
revirtiendo sin querer:
- El short-circuit por motor caído y el backoff cooperativo en 429 (`dead`/`perProviderStatus` de 0.2.0) — un motor roto vuelve a fallar una vez por cada documento en vez de una sola vez.
- El panel de detalle "Motores no disponibles" en Revisión IA (`ReviewStep.kt`) desapareció por completo — solo queda el banner genérico de `state.error`.
- El orden del `SignatureLocator` volvió a `[Claude, Gemini, Groq, Grok, Mistral]` (Claude/Gemini primero pese a estar caídos hoy; Groq de vuelta en la lista pese a no tener visión real — ver nota en 0.2.1).
- `CHANGELOG.md` no se actualizó para 0.3.0 (versionName saltó de 0.2.2 a "0.3.0-ajustes-letterbox" sin entrada aquí).
No se corrige aquí para no mezclar con el objetivo único de esta build (instrumentación Gemini). Restaurar si se confirma que se quiere.

### Pendiente (siguiente paso de la investigación Gemini)
- Con esta build, reproducir el fallo de Gemini y capturar el mensaje real en el banner de error (o en logcat, tag `MultiAiExtractor`/`SignatureLocator`) para aislar la causa exacta (cuota, billing, argumento inválido, etc.) y proponer el fix quirúrgico correspondiente — en el PHP o en el cliente, según lo que diga Google.

---

## [0.2.2-stamp-letterbox] — 2026-07-10

### Corregido
- **La firma se recortaba o ampliaba en exceso al generar el PDF final**, aunque la previsualización se viera bien. Causa raíz: `AcroFormFiller` calculaba el alto del estampado como `w * signature.aspectRatio` — es decir, forzaba la altura a partir del aspect ratio real de la imagen de la firma procesada (que varía mucho según el trazo, el recorte, la foto de origen), en vez de respetar el tamaño real del hueco de firma del contrato. Una firma con trazo muy ancho y fino, o muy vertical, deformaba o desbordaba el hueco calibrado.
- **Fix**: `SignatureStamp` ahora tiene también `heightRel` (antes solo `widthRel`), definiendo una CAJA fija calibrada contra el contrato real (0.256 × 0.114 en las 5 páginas de firma). La firma se escala en modo *letterbox* — cabe dentro de esa caja respetando su propio aspect ratio, sin deformarse, tomando como límite el lado (ancho o alto) que primero se alcance. Esto es coherente con lo que ya se ve en la previsualización (que muestra la firma como el bitmap la tiene, sin forzarla), y ahora el PDF final refleja fielmente el mismo resultado.
- Slider manual de "Tamaño" en `SignatureStep` ahora escala la caja completa (ancho + alto proporcional) en vez de solo el ancho, para no reintroducir deformación al ajustar a mano.

---

## [0.2.1-firma-fix] — 2026-07-10

### Corregido
- **Página 24 no aparecía en "páginas de firma detectadas"**: verificado con pypdf contra `contrato-relleno-a1.pdf` que la página 24 **no tiene ningún campo AcroForm propio** — la detección estructural (basada en campos multipágina) nunca podía encontrarla por ese camino. Se fuerza ahora como candidata siempre que el documento tenga ≥24 páginas, independientemente de la detección estructural.
- **Coordenadas de firma corregidas a partir de una nueva calibración más precisa**: la calibración anterior (0.2.0) usaba la esquina superior-izquierda de la imagen de firma como si fuera el centro (`xRel`/`yRel` esperan CENTRO, ver `AcroFormFiller`). Con pdfplumber se identificó el rótulo "EL DISTRIBUIDOR" real en cada página (aislando bien "EL"+"DISTRIBUIDOR" de otros textos en la misma línea, ya que hay páginas con dos rótulos distintos a la misma altura — p.ej. "XFERA MÓVILES" y "EL DISTRIBUIDOR" comparten renglón en las páginas 30/33), se cruzó con la imagen de firma inmediatamente asociada, y se convirtió a centro real:
  - Página 24: xRel 0.275, yRel 0.463 (izquierda)
  - Página 30: xRel 0.722, yRel 0.261 (**derecha** — el bloque del distribuidor está a la derecha en esta página)
  - Página 33: xRel 0.220, yRel 0.940 (izquierda, muy abajo)
  - Página 45: xRel 0.222, yRel 0.853 (izquierda)
  - Página 54: xRel 0.183, yRel 0.886 (izquierda)
  - La firma ahora queda centrada e inmediatamente debajo del rótulo, no desplazada a un lado.
- **Firma sin actualización en vivo al cambiar color/fondo**: `setInkColor` y `setSigBackground` solo actualizaban el estado sin reprocesar el bitmap. Ahora se guarda el bitmap "crudo" (antes de tintar) tanto para fotos como para dibujos a mano, y cambiar color o fondo reprocesa inmediatamente sin volver a llamar a la IA de localización.
- **Locator de firma reordenado**: Claude/Gemini están caídos (400/500) y Groq no tiene visión real (es un motor de texto que "especula" el JSON). El orden pasa a ser Mistral → Scaleway → Claude → Gemini → Grok, priorizando los que sí tienen visión y funcionan hoy.

### Añadido
- **Confirmación visual de firma cargada**: chip "Firma cargada ✓ · lista para N páginas" junto a la previsualización, sustituye al chip mudo "Firma preparada ✓" que no informaba nada útil.
- **Previsualización de firma reubicada**: ahora aparece justo debajo del selector Dibujar/Extraer de foto, antes de las opciones de color/fondo — el usuario ve el resultado inmediatamente en vez de tener que bajar mucho en la pantalla.

### Pendiente (fuera de esta tanda, para no sobrecargar — ver plan de fases)
- Pinch-to-zoom y arrastre táctil directo sobre la miniatura de previsualización de firma (hoy solo existe arrastre sobre el marcador ✍ en la previsualización completa del PDF de 54 páginas).
- Separar "colocar página por página, una a una, en cualquier momento" de "rellenar solo las que faltan" como dos acciones distintas del botón de estampado masivo (hoy `stampAllPages` sobreescribe todas).

---

## [0.2.0-firma] — 2026-07-10

### Corregido
- **Firma recortada a un puntito**: la firma extraída de foto se veía como un cuadro casi vacío con un píxel en el centro. Causa: `processInk` recortaba a bounding box mínimo aun cuando `SignatureLocator` ya había recortado con la caja de la IA, y sobre fotos completas cualquier píxel oscuro (sombra, arruga) desviaba el bounding box a una esquina. Ahora `fromPhoto` acepta `applyBoundingCrop` opcional, y `extractSignatureFromPhoto` lo desactiva si ya hay caja del locator.
- **Fallback razonable cuando el locator falla**: si Claude/Gemini caen y ningún motor localiza la firma, ya no se aplica `processInk` a toda la foto (que sacaba resultados basura), sino que se ofrece la foto original tal cual y se avisa al usuario.
- **Errores agrupados por motor** en `MultiAiExtractor`: en vez de listar cada 400/500/429 repetido por documento, se muestra una sola línea con el último estado por motor. Además:
  - Short-circuit: un motor que falla con 4xx/5xx no se reintenta en la sesión de extracción.
  - Backoff cooperativo de 2,5 s cuando algún motor devuelve 429, para no quemar la cuota `RATE_MAX=30/10min` del proxy.
  - Orden fiable: Groq → Mistral → Claude → Gemini → Scaleway → EUrouter, prioriza los que suelen ir bien.

### Añadido
- **Coordenadas de firma calibradas** con `contrato-relleno-a1.pdf` (referencia real). Nuevos defaults por página:
  - Página 24: xRel 0.147, yRel 0.406 (izquierda-centro)
  - Página 30: xRel 0.594, yRel 0.204 (derecha — el rótulo "EL DISTRIBUIDOR" en esa página está a la derecha, no a la izquierda)
  - Página 33: xRel 0.092, yRel 0.883 (izquierda-abajo)
  - Página 45: xRel 0.093, yRel 0.796
  - Página 54: xRel 0.055, yRel 0.829
  - Ancho constante: wRel 0.256, aspect ratio h/w = 0.44 (más apaisado que antes).
- **Panel de detalle de motor caído** en el UI de Revisión IA: muestra el mensaje del proxy (task, código HTTP y snippet del body) para diagnosticar 400/500 sin necesidad de `adb logcat`.
- **Nombrado versionado del APK y del artifact** del workflow: `rellenador-<versionName>.apk` en vez de `app-debug.apk`.
- **CHANGELOG.md** (este archivo), retroactivo desde el arranque del proyecto.

### Notas técnicas
- El logging BODY de OkHttp sigue activo en debug. Con `adb logcat -s okhttp.OkHttpClient` (o el visor del móvil filtrado por tag `okhttp`) se ve el JSON exacto que se envía al proxy en cada llamada.
- El fix de errores no arregla los 400/500/404 upstream — arregla la cascada. Los 3 motores rotos siguen rotos, pero ya no ahogan al resto.

---

## [0.1.7-fix-nullable] — 2026-07-10

### Corregido
- **Build rojo por `Float?` en `PdfPreview.kt`**: `var curX = stampXRel` y `var curY = stampYRel` heredaban `Float?` de la firma nullable de `SignatureStamp`, y luego el operador `+` con `dragAmount` fallaba con `Operator call is prohibited on a nullable receiver of type 'Float?'`. Fix mínimo: `stampXRel ?: 0.5f` para forzar `Float` no-nullable.

---

## [0.1.6-post-tanda-f] — 2026-07-09

### Corregido
- **HTTP 400/500/429 al extraer desde foto**: `DocumentLoader` mandaba las fotos del cliente sin redimensionar (varios MB de móvil a resolución completa), a diferencia de las páginas de PDF rasterizadas. Añadido `downscaleIfNeeded(1600)` antes de codificar en base64, en `DocumentLoader` y en `extractSignatureFromPhoto`.
- **Preview visual de la firma procesada** en `SignatureStep` (antes solo había un chip de texto sin imagen).
- **Gesto de arrastre de la firma**: antes respondía en toda la página e interfería con el scroll de la lista de páginas. Ahora vive solo en el marcador ✍: primer toque lo selecciona (se resalta) y solo entonces se puede arrastrar, acumulando posición localmente para no cancelar el gesto a mitad de camino.

---

## [0.1.5-tanda-f] — Persistencia

### Añadido
- **Perfil comercial y templates por fingerprint** (`ContractProfile.kt` + `TemplateFingerprint`): huella = nº páginas + nombres de campo normalizados.
- **PrefsRepository**: `saveTemplate` / `findTemplate` (reaplica mapeo automático si ya se vio ese PDF), `saveToHistory` / `listHistory` / `deleteFromHistory`, `exportProfileJson` / `importProfileJson`.
- **HistoryPanel.kt**: diálogo con historial de contratos desde el botón "Historial" en `FillStep`.

### Notas
- Se limpiaron archivos huérfanos de una fase anterior mal llamada "Tanda F" (`ui/history/HistorialScreen.kt`, `ui/history/HistorialViewModel.kt`, `ui/settings/AjustesScreen.kt`, `ui/settings/AjustesViewModel.kt`). No deben volver a aparecer.

---

## [0.1.4-tanda-e] — Firma avanzada

### Añadido
- `SignatureProcessor` ampliado con Otsu automático, `flattenIllumination` (corrige iluminación desigual en fotos), `processInk` (tintado con alpha graduado + recorte a bounding box + fondo transparente/blanco), `fromPhoto` (pipeline completo).
- Color de tinta (azul, negro, azul claro) y fondo (transparente, blanco) elegibles en `SignatureStep`.
- Firmas guardadas reutilizables vía `PrefsRepository.saveSignature` / `listSignatures` / `getSignature` (DataStore).

---

## [0.1.3-tanda-d] — Extracción fina

### Añadido
- `DateAutofill.kt`: autorrelleno de fecha actual (día/mes en letras español, último dígito del año) para campos vacíos — verbatim de `autoFillDates()` de la web.

### Cambiado
- `MultiAiExtractor`: votación de tipo de identificación por mayoría (antes cogía el primero), corte inteligente (`earlyStop`) que deja de llamar motores cuando todos los campos ya están cubiertos.
- `ReviewStep`: tocar un candidato ya seleccionado lo desmarca (tap-again-to-deselect).

---

## [0.1.2-tanda-c] — Previsualización del PDF

### Añadido
- `PdfPageRenderer.kt`: renderiza páginas bajo demanda (caché LRU de 4) para no cargar las 54 de golpe.
- `PdfExporter.generatePreview()`: genera un PDF temporal para preview.
- `PdfPreview.kt`: LazyColumn de 54 páginas con badge "✍" en páginas de firma.

### Notas
- Altura fija en el contenedor (560dp) para evitar el crash clásico de Compose (`LazyColumn` dentro de scroll vertical sin restricción).

---

## [0.1.1-tanda-b] — Detección real de huecos de firma

### Añadido
- `SignaturePageDetector.kt`: usa pdfbox (widgets multipágina) + `PDFTextStripper` para localizar los huecos reales.
- `WizardViewModel`: `detectSignaturePages()`, `addSignPage` / `removeSignPage`, `stampAllPages` (masivo), `stampOnePage` (una a una).

### Corregido
- `det.signAnchors` → `det.anchors` (el campo de la `data class Detection` se llama `anchors`, no `signAnchors`).

### Notas técnicas
- Los huecos de firma reales del contrato son las páginas **24, 30, 33, 45, 54** (no solo la 24 fija). Verificado cruzando campos AcroForm multipágina con la presencia del rótulo "EL DISTRIBUIDOR" en el texto.

---

## [0.1.0-fase-4] — Mapeo de PDF propio + campos verificados

### Añadido
- `TemplateMapper.kt`: auto-mapeo de nombres reales de un PDF de usuario a claves canónicas por similitud normalizada (sin acentos, minúsculas, espacios colapsados).
- `MappingEditor.kt`: UI para revisar/corregir el mapeo.

### Corregido
- Verificado con la skill `pdf` contra el contrato real: **23 campos (20 texto + 3 checkbox)**. `Email Comercial` (1 espacio) y `Email  Facturación` (2 espacios) son **campos distintos**, no uno solo. Checkboxes `NIF` / `CIF` (valores `/On` / `/Off`) se marcan solo si `tipo_identificacion` es concluyente.

---

## [0.0.9-fase-3] — Paquetes en bloque

### Añadido
- `PackageApplier`: aplica un paquete completo (dirección fiscal / comercio / empresa / persona / banco) de un toque en Revisión.
- Bloque `_2` (Dirección_2 / CP_2 / Población_2 / Provincia_2 = comercio/PdV) añadido a `CANON`.

---

## [0.0.8-fase-2] — Firma básica + PDF final

### Añadido
- `AcroFormFiller.generate()`: rellena AcroForm + estampa firma con pdfbox-android.
- `SignatureCanvas.kt`: canvas de dibujo para firma manuscrita.
- `PdfExporter`: genera a `filesDir/output`, comparte por FileProvider o guarda vía SAF.

---

## [0.0.7-fase-1] — Wizard de 5 pasos

### Añadido
- Flujo completo: Contrato → Documentación → Revisión IA → Relleno → Firma.
- `WizardViewModel` + `WizardState` orquestan el flujo.
- `ExtractionPrompt.kt` con el prompt de extracción **verbatim** de la web (reglas de dirección fiscal vs `_2`, autónomo vs CIF, formato IBAN/CP/CNAE).
- `ContractFields.CANON` con las claves canónicas (con dobles espacios frágiles preservados).
