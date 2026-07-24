# Changelog — Rellenador de Contratos (Android)

Todas las versiones que han llegado a **build verde** en el workflow. Se sigue [Keep a
Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado semántico. El nombre del
artifact / APK del workflow coincide con `versionName` para poder distinguirlos.

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
